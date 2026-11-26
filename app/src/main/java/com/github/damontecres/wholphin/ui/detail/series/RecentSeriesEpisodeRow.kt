// Modified by Lunoco contributors on 2026-11-25 for the Phase 41 episode hub redesign.
// Modified by Lunoco contributors on 2026-11-25 for the Phase 40A episode browser.
package com.github.damontecres.wholphin.ui.detail.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.flow.first
import kotlin.time.Duration

private enum class EpisodePicker { SEASON, EPISODE, BROWSE_SEASON }

private val HubInset = 32.dp
private const val EpisodeColumns = 3

@Composable
private fun seasonLabel(season: BaseItem): String =
    season.name?.takeIf { it.isNotBlank() }
        ?: season.indexNumber?.let { formatSeasonNumber(it) }
        ?: stringResource(R.string.tv_season)

@Composable
private fun episodeLabel(episode: BaseItem?, seasonNumber: Int? = null): String {
    val number = episode?.indexNumber
    val title = episode?.name?.trim()?.takeIf { it.isNotBlank() }
    return when {
        title != null && number != null && seasonNumber != null ->
            stringResource(R.string.episode_browser_context_title, seasonNumber, number, title)
        title != null && number != null -> stringResource(R.string.episode_browser_numbered_title, number, title)
        title != null -> title
        number != null -> stringResource(R.string.episode_browser_numbered_fallback, number)
        episode == null -> stringResource(R.string.loading)
        else -> stringResource(R.string.episode_browser_untitled)
    }
}

private fun readableOverview(overview: String?): String? =
    overview?.takeIf { it.isNotBlank() }?.let {
        HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
            .replace(Regex("[\\s\\u00A0]+"), " ").trim().takeIf(String::isNotEmpty)
    }

/** Displays the same paged episodes in the quick row and expanded browser. */
@Composable
internal fun SeriesEpisodeHub(
    seriesTitle: String?,
    recentEpisodeKey: String?,
    selection: RecentSeriesEpisodes,
    seasons: List<BaseItem?>,
    loading: Boolean,
    loadFailed: Boolean,
    requestInitialFocus: Boolean,
    onInitialFocus: () -> Unit,
    onSelectSeason: (BaseItem, Int, Boolean) -> Unit,
    onClick: (BaseItem) -> Unit,
    onOverview: (BaseItem) -> Unit,
    onLongClick: (Int, BaseItem) -> Unit,
    episodeFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val seasonStates = rememberSaveableStateHolder()
    var picker by remember { mutableStateOf<EpisodePicker?>(null) }
    var browseOrigin by remember { mutableStateOf<Pair<BaseItem, Int>?>(null) }
    var restoreBrowseOrigin by remember { mutableStateOf(false) }
    var revealEpisode by remember { mutableStateOf(false) }
    var restoreSeasonControl by remember { mutableStateOf(false) }

    LaunchedEffect(restoreBrowseOrigin, loading, selection.season.id) {
        if (!restoreBrowseOrigin || loading) return@LaunchedEffect
        val origin = browseOrigin
        restoreBrowseOrigin = false
        browseOrigin = null
        if (origin != null && origin.first.id != selection.season.id) {
            onSelectSeason(origin.first, origin.second, true)
        } else {
            revealEpisode = true
        }
    }

    val activePicker = picker
    BackHandler(enabled = activePicker != null) {
        when (activePicker) {
            EpisodePicker.BROWSE_SEASON -> picker = EpisodePicker.EPISODE
            EpisodePicker.EPISODE -> {
                picker = null
                restoreBrowseOrigin = true
            }
            EpisodePicker.SEASON -> {
                picker = null
                restoreSeasonControl = true
            }
            null -> Unit
        }
    }

    seasonStates.SaveableStateProvider(selection.season.id.toString()) {
        val rowState = rememberLazyListState(initialFirstVisibleItemIndex = selection.episodeIndex)
        var selectedIndex by rememberInt(selection.episodeIndex)
        var browseIndex by rememberInt(selection.episodeIndex)
        val index = selectedIndex.coerceIn(0, selection.episodes.lastIndex.coerceAtLeast(0))
        val targetFocus = remember { FocusRequester() }
        val seasonFocus = remember { FocusRequester() }
        val playFocus = remember { FocusRequester() }
        val browseFocus = remember { FocusRequester() }
        val overviewFocus = remember { FocusRequester() }
        val episodeReturn = if (selection.episodes.isEmpty()) FocusRequester.Default else targetFocus
        val currentOnInitialFocus by rememberUpdatedState(onInitialFocus)

        LaunchedEffect(activePicker, restoreSeasonControl, requestInitialFocus, revealEpisode) {
            if (activePicker != null) return@LaunchedEffect
            if (restoreSeasonControl) {
                withFrameNanos { }
                seasonFocus.tryRequestFocus()
                restoreSeasonControl = false
            }
            if (!requestInitialFocus && !revealEpisode) return@LaunchedEffect
            if (selection.episodes.isEmpty()) {
                withFrameNanos { }
                if (!seasonFocus.tryRequestFocus()) return@LaunchedEffect
            } else {
                if (rowState.layoutInfo.visibleItemsInfo.none { it.index == index }) rowState.scrollToItem(index)
                snapshotFlow { rowState.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
                withFrameNanos { }
                if (!targetFocus.tryRequestFocus()) return@LaunchedEffect
            }
            revealEpisode = false
            if (requestInitialFocus) currentOnInitialFocus()
        }

        Column(
            modifier = modifier.focusGroup().testTag("episode-hub"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val seasonName = seasonLabel(selection.season)
            val seasonControlLabel = stringResource(R.string.episode_browser_season_dropdown, seasonName)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = HubInset),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 20.dp)) {
                    seriesTitle?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it, style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        stringResource(if (activePicker == EpisodePicker.EPISODE) R.string.episode_browser_all else R.string.episodes),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                if (activePicker == null) {
                    Button(
                        onClick = { if (!loading) picker = EpisodePicker.SEASON },
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.widthIn(max = 250.dp).focusRequester(seasonFocus)
                            .then(if (selection.episodes.isEmpty()) Modifier.focusRequester(episodeFocusRequester) else Modifier)
                            .focusProperties {
                                down = if (selection.episodes.isEmpty()) FocusRequester.Default else targetFocus
                                if (selection.episodes.isNotEmpty()) left = browseFocus
                                right = FocusRequester.Cancel
                            }
                            .testTag("episode-browser-season").semantics { selected = true },
                    ) { Text(seasonControlLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }

            val focused = selection.episodes.getOrNull(index)
            val rawOverview = focused?.data?.overview
            val overview = remember(rawOverview) { readableOverview(rawOverview) }
            var overviewOverflows by remember(overview) { mutableStateOf(false) }
            val hasOverview = overviewOverflows && focused != null
            val feature: @Composable (Modifier, Float) -> Unit = { featureModifier, textFraction ->
                FeaturedEpisode(
                    episode = focused,
                    seasonNumber = focused?.data?.parentIndexNumber ?: selection.season.indexNumber,
                    overview = overview,
                    runtime = focused?.timeRemainingOrRuntime,
                    onOverviewOverflow = { if (activePicker == null) overviewOverflows = it },
                    isRecent = recentEpisodeKey != null && recentEpisodeKey == focused?.id?.toString(),
                    status = when {
                        loading -> stringResource(R.string.loading)
                        loadFailed -> stringResource(R.string.episode_browser_season_error)
                        selection.episodes.isEmpty() -> stringResource(R.string.episode_browser_empty)
                        else -> null
                    },
                    textFraction = textFraction,
                    modifier = featureModifier,
                )
            }
            when (activePicker) {
                null -> {
                    feature(Modifier.fillMaxWidth().padding(horizontal = HubInset), .63f)
                    if (selection.episodes.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(horizontal = HubInset).testTag("episode-browser-actions"),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = { focused?.let(onClick) },
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color(0xFF121318),
                                ),
                                scale = ButtonDefaults.scale(focusedScale = 1f),
                                border = ButtonDefaults.border(focusedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                                    inset = 2.dp,
                                )),
                                modifier = Modifier.focusRequester(playFocus).focusProperties {
                                    canFocus = focused != null
                                    up = seasonFocus
                                    down = episodeReturn
                                    right = browseFocus
                                }.testTag("episode-browser-play"),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.episode_browser_play))
                            }
                            Button(
                                onClick = {
                                    if (!loading) {
                                        browseIndex = index
                                        browseOrigin = selection.season to selection.seasonIndex
                                        picker = EpisodePicker.EPISODE
                                    }
                                },
                                scale = ButtonDefaults.scale(focusedScale = 1f),
                                modifier = Modifier.focusRequester(browseFocus).focusProperties {
                                    up = seasonFocus
                                    down = episodeReturn
                                    left = if (focused == null) FocusRequester.Cancel else playFocus
                                    right = if (hasOverview) overviewFocus else seasonFocus
                                }.testTag("episode-browser-all"),
                            ) { Text(stringResource(R.string.episode_browser_all)) }
                            if (hasOverview) {
                                Button(
                                    onClick = { onOverview(focused) },
                                    scale = ButtonDefaults.scale(focusedScale = 1f),
                                    modifier = Modifier.focusRequester(overviewFocus).focusProperties {
                                        up = seasonFocus
                                        down = episodeReturn
                                        left = browseFocus
                                        right = seasonFocus
                                    }.testTag("episode-browser-overview-action"),
                                ) { Text(stringResource(R.string.episode_browser_full_overview)) }
                            }
                        }

                        val imageHeight = 98.dp
                        LazyRow(
                            state = rowState,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = HubInset - 14.dp)
                                .focusRestorer(targetFocus).focusGroup()
                                .testTag("episode-browser-rail"),
                        ) {
                            items(selection.episodes.size) { itemIndex ->
                                val episode = selection.episodes[itemIndex]
                                val label = episodeLabel(episode)
                                Column(Modifier.width(imageHeight * AspectRatios.WIDE)) {
                                    BannerCard(
                                        item = episode, name = null, cornerText = episode?.ui?.episodeCornerText,
                                        played = episode?.played ?: false,
                                        playPercent = episode?.data?.userData?.playedPercentage ?: 0.0,
                                        aspectRatio = AspectRatios.WIDE, cardHeight = imageHeight,
                                        useSeriesForPrimary = false,
                                        onClick = { episode?.let(onClick) },
                                        onLongClick = { episode?.let { onLongClick(itemIndex, it) } },
                                        modifier = Modifier
                                            .then(if (itemIndex == index) {
                                                Modifier.focusRequester(targetFocus).focusRequester(episodeFocusRequester)
                                            } else Modifier)
                                            .focusProperties {
                                                up = seasonFocus
                                                if (itemIndex == 0) left = FocusRequester.Cancel
                                                if (itemIndex == selection.episodes.lastIndex) right = FocusRequester.Cancel
                                            }
                                            .onFocusChanged { if (it.isFocused) selectedIndex = itemIndex }
                                            .testTag("recent-series-episode-${episode?.id}")
                                            .semantics { contentDescription = label },
                                    )
                                    Text(
                                        label, style = MaterialTheme.typography.bodyLarge,
                                        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 6.dp).testTag("episode-browser-card-title-${episode?.id}"),
                                    )
                                }
                            }
                        }
                    }
                }

                EpisodePicker.SEASON, EpisodePicker.BROWSE_SEASON -> Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = HubInset),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    feature(Modifier.weight(3f), 1f)
                    SeasonPicker(
                        seasons = seasons,
                        selectedIndex = selection.seasonIndex,
                        onSelect = { season, index ->
                            val fromBrowse = activePicker == EpisodePicker.BROWSE_SEASON
                            picker = if (fromBrowse) EpisodePicker.EPISODE else null
                            if (!fromBrowse) restoreSeasonControl = true
                            onSelectSeason(season, index, !fromBrowse)
                        },
                        modifier = Modifier.weight(2f),
                    )
                }

                EpisodePicker.EPISODE -> ExpandedEpisodeBrowser(
                    episodes = selection.episodes,
                    seasonLabel = seasonControlLabel,
                    selectedIndex = browseIndex,
                    loading = loading,
                    loadFailed = loadFailed,
                    onFocusEpisode = { browseIndex = it },
                    onChangeSeason = { if (!loading) picker = EpisodePicker.BROWSE_SEASON },
                    onSelectEpisode = { option ->
                        selectedIndex = option
                        browseOrigin = null
                        picker = null
                        revealEpisode = true
                    },
                    modifier = Modifier.padding(horizontal = HubInset),
                )
            }
            // Secondary rows are reached by scrolling past the complete hub.
            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun FeaturedEpisode(
    episode: BaseItem?,
    seasonNumber: Int?,
    overview: String?,
    runtime: Duration?,
    onOverviewOverflow: (Boolean) -> Unit,
    isRecent: Boolean,
    status: String?,
    textFraction: Float,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val scrim = remember(textFraction) {
        Brush.horizontalGradient(
            0f to Color(0xF01A1B20),
            .64f to Color(0xEC17181C),
            1f to if (textFraction == 1f) Color(0xEF17181C) else Color(0x1017181C),
        )
    }
    Box(
        modifier.drawBehind {
            val height = if (overview == null) 68.dp.toPx().coerceAtMost(size.height) else size.height
            drawRoundRect(brush = scrim, size = Size(size.width, height), cornerRadius = CornerRadius(22.dp.toPx()))
        }.padding(horizontal = 20.dp, vertical = 12.dp).testTag("episode-browser-context"),
    ) {
        Column(
            Modifier.fillMaxWidth(textFraction),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier.height(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRecent) {
                    Text(
                        stringResource(R.string.recently_added),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        modifier = Modifier.background(Color.White.copy(alpha = .12f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 2.dp).testTag("episode-browser-recent"),
                    )
                }
                if (status != null && episode != null) {
                    Text(status, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (runtime != null) {
                    QuickDetails(null, runtime)
                }
            }
            Text(
                if (episode == null && status != null) status else episodeLabel(episode, seasonNumber),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("episode-browser-featured-title"),
            )
            Text(
                overview.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().testTag("episode-browser-description"),
                onTextLayout = { onOverviewOverflow(it.hasVisualOverflow) },
            )
        }
    }
}

@Composable
private fun SeasonPicker(
    seasons: List<BaseItem?>,
    selectedIndex: Int,
    onSelect: (BaseItem, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = selectedIndex.coerceIn(0, seasons.lastIndex.coerceAtLeast(0))
    val state = rememberLazyListState(first)
    val currentOption = remember { FocusRequester() }
    val inView = remember { BringIntoViewRequester() }
    LaunchedEffect(first, seasons.size) {
        if (seasons.isNotEmpty()) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.any { it.index == first } }.first { it }
            withFrameNanos { }
            currentOption.tryRequestFocus()
            inView.bringIntoView()
        }
    }
    Column(
        modifier.widthIn(max = 360.dp).fillMaxWidth().bringIntoViewRequester(inView)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.tv_seasons), style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            state = state,
            modifier = Modifier.heightIn(max = 294.dp).testTag("episode-browser-picker-list").focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(seasons.size) { option ->
                val season = seasons[option]
                Button(
                    onClick = { season?.let { onSelect(it, option) } },
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    modifier = Modifier.fillMaxWidth()
                        .then(if (option == first) Modifier.focusRequester(currentOption) else Modifier)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            if (option == 0) up = FocusRequester.Cancel
                            if (option == seasons.lastIndex) down = FocusRequester.Cancel
                        }
                        .testTag("episode-browser-choice-$option"),
                ) { Text(season?.let { seasonLabel(it) } ?: stringResource(R.string.loading), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun ExpandedEpisodeBrowser(
    episodes: List<BaseItem?>,
    seasonLabel: String,
    selectedIndex: Int,
    loading: Boolean,
    loadFailed: Boolean,
    onFocusEpisode: (Int) -> Unit,
    onChangeSeason: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusedIndex = selectedIndex.coerceIn(0, episodes.lastIndex.coerceAtLeast(0))
    val initialIndex = remember { focusedIndex }
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = initialIndex / EpisodeColumns * EpisodeColumns)
    val itemFocus = remember { FocusRequester() }
    val seasonFocus = remember { FocusRequester() }
    val inView = remember { BringIntoViewRequester() }
    LaunchedEffect(episodes.isEmpty()) {
        if (episodes.isEmpty()) {
            withFrameNanos { }
            seasonFocus.tryRequestFocus()
        } else {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.any { it.index == initialIndex } }.first { it }
            withFrameNanos { }
            itemFocus.tryRequestFocus()
        }
        inView.bringIntoView()
    }
    Column(
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(inView).focusGroup()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val status = when {
                loading -> R.string.loading
                loadFailed -> R.string.episode_browser_season_error
                episodes.isEmpty() -> R.string.episode_browser_empty
                else -> null
            }
            Text(
                if (status == null) pluralStringResource(R.plurals.episode_browser_episode_count, episodes.size, episodes.size)
                else stringResource(status),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = onChangeSeason,
                scale = ButtonDefaults.scale(focusedScale = 1f),
                modifier = Modifier.widthIn(max = 250.dp).focusRequester(seasonFocus).focusProperties {
                    up = FocusRequester.Cancel
                    if (episodes.isNotEmpty()) down = itemFocus
                }.testTag("episode-browser-browse-season"),
            ) { Text(seasonLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(EpisodeColumns),
            state = state,
            modifier = Modifier.fillMaxWidth().heightIn(max = 302.dp).testTag("episode-browser-picker-list").focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(episodes.size) { option ->
                val episode = episodes[option]
                Button(
                    onClick = { if (!loading && episode != null) onSelectEpisode(option) },
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    modifier = Modifier.height(54.dp)
                        .then(if (option == focusedIndex) Modifier.focusRequester(itemFocus) else Modifier)
                        .focusProperties {
                            if (option < EpisodeColumns) up = seasonFocus
                            if (option % EpisodeColumns == 0) left = FocusRequester.Cancel
                            if (option % EpisodeColumns == EpisodeColumns - 1 || option == episodes.lastIndex) right = FocusRequester.Cancel
                            if (option + EpisodeColumns >= episodes.size) down = FocusRequester.Cancel
                        }
                        .onFocusChanged { if (it.isFocused) onFocusEpisode(option) }
                        .testTag("episode-browser-choice-$option"),
                ) { Text(episodeLabel(episode), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

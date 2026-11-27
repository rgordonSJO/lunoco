// Modified by Lunoco contributors on 2026-11-26 to simplify series episode browsing.
// Modified by Lunoco contributors on 2026-11-26 for episode focus and layout.
// Modified by Lunoco contributors on 2026-11-25 for the Phase 41 episode hub redesign.
// Modified by Lunoco contributors on 2026-11-25 for the Phase 40A episode browser.
package com.github.damontecres.wholphin.ui.detail.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import com.github.damontecres.wholphin.ui.main.JellyLuHomeBringIntoViewSpec
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.flow.first

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

private fun plainEpisodeOverview(overview: String?): String? =
    overview?.takeIf(String::isNotBlank)?.let {
        HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim().takeIf(String::isNotEmpty)
    }

private val overviewWhitespace = Regex("[\\s\\u00A0]+")

@Composable
internal fun SeriesEpisodeRow(
    selection: RecentSeriesEpisodes,
    seasons: List<BaseItem?>,
    loading: Boolean,
    loadFailed: Boolean,
    requestInitialFocus: Boolean,
    onInitialFocus: () -> Unit,
    onSelectSeason: (BaseItem, Int) -> Unit,
    onClick: (BaseItem) -> Unit,
    onLongClick: (Int, BaseItem) -> Unit,
    episodeFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val seasonStates = rememberSaveableStateHolder()
    val seasonFocus = remember { FocusRequester() }
    var showSeasons by remember { mutableStateOf(false) }
    var restoreSeasonFocus by remember { mutableStateOf(false) }
    var returnToEpisode by remember { mutableStateOf(false) }
    val currentOnInitialFocus by rememberUpdatedState(onInitialFocus)

    BackHandler(enabled = showSeasons) {
        showSeasons = false
        restoreSeasonFocus = true
    }

    seasonStates.SaveableStateProvider(selection.season.id.toString()) {
        val episodes = selection.episodes
        val rowState = rememberLazyListState(initialFirstVisibleItemIndex = selection.episodeIndex)
        var selectedIndex by rememberInt(selection.episodeIndex)
        val index = selectedIndex.coerceIn(0, episodes.lastIndex.coerceAtLeast(0))
        val selectedEpisodeFocus = remember { FocusRequester() }

        LaunchedEffect(showSeasons, loading, restoreSeasonFocus, returnToEpisode, requestInitialFocus) {
            if (showSeasons) return@LaunchedEffect
            if (restoreSeasonFocus) {
                withFrameNanos { }
                seasonFocus.tryRequestFocus()
                restoreSeasonFocus = false
            }
            if (loading || (!requestInitialFocus && !returnToEpisode)) return@LaunchedEffect
            if (episodes.isEmpty()) {
                withFrameNanos { }
                if (!seasonFocus.tryRequestFocus()) return@LaunchedEffect
            } else {
                if (rowState.layoutInfo.visibleItemsInfo.none { it.index == index }) rowState.scrollToItem(index)
                snapshotFlow { rowState.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
                withFrameNanos { }
                if (!selectedEpisodeFocus.tryRequestFocus()) return@LaunchedEffect
            }
            returnToEpisode = false
            if (requestInitialFocus) currentOnInitialFocus()
        }

        Column(
            modifier = modifier.focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.episodes),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("episode-browser-heading"),
                )
                if (!showSeasons) {
                    Button(
                        onClick = { if (!loading) showSeasons = true },
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.widthIn(max = 250.dp)
                            .focusRequester(seasonFocus)
                            .then(if (episodes.isEmpty()) Modifier.focusRequester(episodeFocusRequester) else Modifier)
                            .focusProperties {
                                if (episodes.isNotEmpty()) down = selectedEpisodeFocus
                            }
                            .testTag("episode-browser-season"),
                    ) {
                        Text(
                            stringResource(R.string.episode_browser_season_dropdown, seasonLabel(selection.season)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (showSeasons) {
                SeasonPicker(
                    seasons = seasons,
                    selectedIndex = selection.seasonIndex,
                    onSelect = { season, seasonIndex ->
                        showSeasons = false
                        if (season.id == selection.season.id) {
                            returnToEpisode = true
                        } else {
                            restoreSeasonFocus = true
                            onSelectSeason(season, seasonIndex)
                        }
                    },
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 32.dp),
                )
            } else {
                val episode = episodes.getOrNull(index)
                val overviewText = episode?.data?.overview
                val overview = remember(overviewText) {
                    plainEpisodeOverview(overviewText)?.replace(overviewWhitespace, " ")
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                    Text(
                        text = when {
                            loading -> stringResource(R.string.loading)
                            loadFailed -> stringResource(R.string.episode_browser_season_error)
                            episodes.isEmpty() -> stringResource(R.string.episode_browser_empty)
                            else -> episodeLabel(episode, episode?.data?.parentIndexNumber ?: selection.season.indexNumber)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().testTag("series-episode-title"),
                    )
                    if (episodes.isNotEmpty()) {
                        Text(
                            text = overview.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.58f).testTag("series-episode-description"),
                        )
                    }
                }
                if (episodes.isNotEmpty()) {
                    val imageHeight = 88.dp
                    LazyRow(
                        state = rowState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                            .focusRestorer(selectedEpisodeFocus)
                            .testTag("episode-browser-rail"),
                    ) {
                        items(episodes.size) { itemIndex ->
                            val item = episodes[itemIndex]
                            val label = episodeLabel(item)
                            Column(Modifier.width(imageHeight * AspectRatios.WIDE)) {
                                BannerCard(
                                    item = item,
                                    name = null,
                                    cornerText = item?.ui?.episodeCornerText,
                                    played = item?.played ?: false,
                                    playPercent = item?.data?.userData?.playedPercentage ?: 0.0,
                                    aspectRatio = AspectRatios.WIDE,
                                    cardHeight = imageHeight,
                                    useSeriesForPrimary = false,
                                    onClick = { item?.let(onClick) },
                                    onLongClick = { item?.let { onLongClick(itemIndex, it) } },
                                    modifier = Modifier
                                        .then(if (itemIndex == index) {
                                            Modifier.focusRequester(selectedEpisodeFocus)
                                                .focusRequester(episodeFocusRequester)
                                        } else Modifier)
                                        .focusProperties { up = seasonFocus }
                                        .onFocusChanged { if (it.isFocused) selectedIndex = itemIndex }
                                        .testTag("recent-series-episode-${item?.id}")
                                        .semantics { contentDescription = label },
                                )
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    minLines = 2,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp)
                                        .testTag("episode-browser-card-title-${item?.id}"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    val menuScroll = remember { JellyLuHomeBringIntoViewSpec(inset = 0f) }
    LaunchedEffect(first, seasons.size) {
        if (seasons.isNotEmpty()) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.any { it.index == first } }.first { it }
            withFrameNanos { }
            currentOption.tryRequestFocus()
        }
    }
    Column(
        modifier.widthIn(max = 300.dp).fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.tv_seasons), style = MaterialTheme.typography.titleMedium)
        // Only the season list scrolls when a choice needs to be revealed.
        CompositionLocalProvider(LocalBringIntoViewSpec provides menuScroll) {
            LazyColumn(
                state = state,
                modifier = Modifier.heightIn(max = 208.dp).testTag("episode-browser-picker-list").focusGroup(),
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
}

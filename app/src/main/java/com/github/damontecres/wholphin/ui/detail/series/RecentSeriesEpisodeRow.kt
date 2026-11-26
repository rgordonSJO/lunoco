// Modified by Lunoco contributors on 2026-11-25 for the Phase 40A episode browser.
package com.github.damontecres.wholphin.ui.detail.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
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

private enum class EpisodePicker { SEASON, EPISODE }

@Composable
private fun seasonLabel(season: BaseItem): String =
    season.name?.takeIf { it.isNotBlank() }
        ?: season.indexNumber?.let { formatSeasonNumber(it) }
        ?: stringResource(R.string.tv_season)

@Composable
private fun episodeLabel(episode: BaseItem?): String {
    val number = episode?.indexNumber
    val title = episode?.name?.trim()?.takeIf { it.isNotBlank() }
    return when {
        title != null && number != null -> stringResource(R.string.episode_browser_numbered_title, number, title)
        title != null -> title
        number != null -> stringResource(R.string.episode_browser_numbered_fallback, number)
        episode == null -> stringResource(R.string.loading)
        else -> stringResource(R.string.episode_browser_untitled)
    }
}

/** Browses the existing paged episode list and keeps navigation within the series page. */
@Composable
internal fun RecentSeriesEpisodeRow(
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
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = selection.episodeIndex)
    var selectedIndex by rememberInt(selection.episodeIndex)
    val index = selectedIndex.coerceIn(0, (selection.episodes.size - 1).coerceAtLeast(0))
    val focused = selection.episodes.getOrNull(index)
    val targetFocus = remember { FocusRequester() }
    val seasonFocus = remember { FocusRequester() }
    val browseFocus = remember { FocusRequester() }
    val episodeReturn = if (selection.episodes.isEmpty()) FocusRequester.Default else targetFocus
    var picker by rememberSaveable { mutableStateOf<EpisodePicker?>(null) }
    var openedFromBrowse by rememberSaveable { mutableStateOf(false) }
    val pickerOrigin = if (openedFromBrowse) browseFocus else seasonFocus
    var restoreControl by remember { mutableStateOf<FocusRequester?>(null) }
    var revealEpisode by remember { mutableStateOf(false) }
    val currentOnInitialFocus by rememberUpdatedState(onInitialFocus)
    val seasonName = seasonLabel(selection.season)

    LaunchedEffect(restoreControl) {
        restoreControl?.let { requester ->
            withFrameNanos { }
            requester.tryRequestFocus()
            restoreControl = null
        }
    }
    LaunchedEffect(requestInitialFocus, revealEpisode) {
        if (!requestInitialFocus && !revealEpisode) return@LaunchedEffect
        if (selection.episodes.isEmpty()) {
            withFrameNanos { }
            seasonFocus.tryRequestFocus()
        } else {
            if (rowState.layoutInfo.visibleItemsInfo.none { it.index == index }) rowState.scrollToItem(index)
            snapshotFlow { rowState.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
            withFrameNanos { }
            if (!targetFocus.tryRequestFocus()) return@LaunchedEffect
        }
        revealEpisode = false
        if (requestInitialFocus) currentOnInitialFocus()
    }

    val activePicker = picker
    BackHandler(enabled = activePicker != null) {
        picker = null
        if (openedFromBrowse) revealEpisode = true else restoreControl = seasonFocus
    }
    Column(modifier = modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.episodes), style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { if (!loading) { openedFromBrowse = false; picker = EpisodePicker.SEASON } },
                scale = ButtonDefaults.scale(focusedScale = 1f),
                modifier = Modifier.widthIn(max = 280.dp).focusRequester(seasonFocus)
                    .focusProperties { if (activePicker == null) down = episodeReturn }
                    .testTag("episode-browser-season").semantics { selected = true },
            ) { Text(seasonName + " ▼", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (selection.episodes.isNotEmpty()) {
                Button(
                    onClick = { if (!loading) { openedFromBrowse = true; picker = EpisodePicker.EPISODE } },
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    modifier = Modifier.focusRequester(browseFocus)
                        .focusProperties { if (activePicker == null) down = episodeReturn }
                        .testTag("episode-browser-all"),
                ) { Text(stringResource(R.string.episode_browser_all)) }
            }
        }
        if (activePicker == null) {
            Column(Modifier.padding(horizontal = 24.dp).testTag("episode-browser-context")) {
                val number = focused?.indexNumber
                val season = focused?.data?.parentIndexNumber ?: selection.season.indexNumber
                val name = focused?.name?.trim()?.takeIf { it.isNotBlank() }
                val title = if (name != null && number != null && season != null) {
                    stringResource(R.string.episode_browser_context_title, season, number, name)
                } else episodeLabel(focused)
                when {
                    loading -> Text(stringResource(R.string.loading))
                    loadFailed -> Text(stringResource(R.string.episode_browser_season_error))
                }
                if (selection.episodes.isEmpty()) {
                    Text(stringResource(R.string.episode_browser_empty))
                } else {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    focused?.timeRemainingOrRuntime?.let { QuickDetails(null, it) }
                    focused?.data?.overview?.trim()?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (selection.episodes.isNotEmpty()) {
                val imageHeight = 96.dp
                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().focusRestorer(targetFocus).focusGroup(),
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
                                    .focusProperties { up = seasonFocus }
                                    .onFocusChanged { if (it.isFocused) selectedIndex = itemIndex }
                                    .testTag("recent-series-episode-${episode?.id}")
                                    .semantics { contentDescription = label },
                            )
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                minLines = 2, maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }
            }
        } else {
            key(activePicker) {
                val pickingSeason = activePicker == EpisodePicker.SEASON
                val count = if (pickingSeason) seasons.size else selection.episodes.size
                val first = (if (pickingSeason) selection.seasonIndex else index)
                    .coerceIn(0, (count - 1).coerceAtLeast(0))
                val state = rememberLazyListState(first)
                val currentOption = remember { FocusRequester() }
                val pickerInView = remember { BringIntoViewRequester() }
                LaunchedEffect(Unit) {
                    if (count > 0) {
                        snapshotFlow { state.layoutInfo.visibleItemsInfo.any { it.index == first } }.first { it }
                        withFrameNanos { }
                        currentOption.tryRequestFocus()
                        pickerInView.bringIntoView()
                    }
                }
                Column(
                    Modifier.padding(horizontal = 24.dp).widthIn(max = 520.dp).fillMaxWidth()
                        .bringIntoViewRequester(pickerInView)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(if (pickingSeason) R.string.tv_seasons else R.string.episode_browser_all),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyColumn(
                        state = state,
                        modifier = Modifier.heightIn(max = 320.dp).testTag("episode-browser-picker-list"),
                    ) {
                        items(count) { option ->
                            val item = if (pickingSeason) seasons[option] else selection.episodes[option]
                            val title = if (pickingSeason && item != null) seasonLabel(item) else episodeLabel(item)
                            Button(
                                onClick = {
                                    if (item != null) {
                                        picker = null
                                        if (!pickingSeason) {
                                            selectedIndex = option
                                            revealEpisode = true
                                        } else {
                                            restoreControl = pickerOrigin
                                            onSelectSeason(item, option)
                                        }
                                    }
                                },
                                scale = ButtonDefaults.scale(focusedScale = 1f),
                                modifier = Modifier.fillMaxWidth()
                                    .then(if (option == first) Modifier.focusRequester(currentOption) else Modifier)
                                    .focusProperties {
                                        if (!pickingSeason) left = seasonFocus
                                        if (pickingSeason && option == 0) up = FocusRequester.Cancel
                                        if (option == count - 1) down = FocusRequester.Cancel
                                    }
                                    .testTag("episode-browser-choice-$option"),
                            ) { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

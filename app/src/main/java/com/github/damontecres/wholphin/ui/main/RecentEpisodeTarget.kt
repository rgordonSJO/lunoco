// Modified by Lunoco contributors. Phase 38C changes incorporated into this release source on 2026-11-22.
package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

/** Shared by Home and its Recently Added View More grid; never invoked on focus. */
internal suspend fun resolveRecentSeriesDestination(
    api: org.jellyfin.sdk.api.client.ApiClient, userId: UUID?, row: HomeRowConfig?, item: BaseItem,
    cache: RecentEpisodeTargetCache = RecentEpisodeTargetCache(),
): com.github.damontecres.wholphin.ui.nav.Destination {
    val fallback = homeItemDestination(row, item)
    if (userId == null || row !is HomeRowConfig.RecentlyAdded ||
        fallback !is com.github.damontecres.wholphin.ui.nav.Destination.MediaItem ||
        fallback.type != BaseItemKind.SERIES
    ) return fallback
    return withTimeoutOrNull(3_000) {
        resolveRecentSeriesEntry(
            row, item,
            seriesAvailable = { id ->
                api.userLibraryApi.getItem(id, userId = userId).content.type == BaseItemKind.SERIES
            },
            episodeTarget = { seriesId ->
                cache.get(api.baseUrl, userId, item) {
                    resolveRecentEpisodeTarget(
                        row, item, seriesId,
                        getEpisode = { api.userLibraryApi.getItem(it, userId = userId).content },
                        latestEpisodes = { api.itemsApi.getItems(recentEpisodeRequest(userId, it)).content.items },
                    )
                }
            },
        )
    } ?: fallback
}

/** Attach a target only to this activated Recently Added route, never to ordinary series entries. */
internal suspend fun resolveRecentSeriesEntry(
    row: HomeRowConfig?, item: BaseItem,
    seriesAvailable: suspend (UUID) -> Boolean,
    episodeTarget: suspend (UUID) -> SeasonEpisodeIds?,
): com.github.damontecres.wholphin.ui.nav.Destination {
    val destination = resolveHomeItemDestination(row, item, seriesAvailable)
    if (row !is HomeRowConfig.RecentlyAdded ||
        destination !is com.github.damontecres.wholphin.ui.nav.Destination.MediaItem ||
        destination.type != BaseItemKind.SERIES
    ) return destination
    return destination.copy(recentEpisode = episodeTarget(destination.itemId))
}

/** An activation-only, user-scoped lookup. DateCreated means library addition, not air date/Next Up. */
internal fun recentEpisodeRequest(userId: UUID, parentId: UUID) = GetItemsRequest(
    userId = userId, parentId = parentId, recursive = true,
    includeItemTypes = listOf(BaseItemKind.EPISODE),
    sortBy = listOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME),
    sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
    limit = 20, enableTotalRecordCount = false, isMissing = false, isPlaceHolder = false,
    excludeLocationTypes = listOf(LocationType.VIRTUAL),
    fields = listOf(ItemFields.PARENT_ID, ItemFields.DATE_CREATED, ItemFields.SORT_NAME, ItemFields.PLAY_ACCESS, ItemFields.MEDIA_SOURCE_COUNT),
)

internal fun recentEpisodeIds(episode: BaseItemDto, seriesId: UUID): SeasonEpisodeIds? {
    val seasonId = (episode.seasonId ?: episode.parentId)?.takeUnless { it == UUID(0, 0) }
    if (episode.id == UUID(0, 0) || episode.type != BaseItemKind.EPISODE ||
        episode.seriesId != seriesId || seasonId == null || episode.extraType != null ||
        episode.isPlaceHolder == true || episode.locationType == LocationType.VIRTUAL ||
        episode.playAccess?.let { it != PlayAccess.FULL } == true ||
        episode.mediaSourceCount?.let { it <= 0 } == true
    ) return null
    return SeasonEpisodeIds(seasonId, episode.parentIndexNumber, episode.id, episode.indexNumber)
}

/** Exact IDs win. Ambiguous/missing dates never become an invented 'latest' episode. Specials are valid. */
internal suspend fun resolveRecentEpisodeTarget(
    row: HomeRowConfig?,
    item: BaseItem,
    seriesId: UUID,
    getEpisode: suspend (UUID) -> BaseItemDto,
    latestEpisodes: suspend (UUID) -> List<BaseItemDto>,
): SeasonEpisodeIds? {
    if (row !is HomeRowConfig.RecentlyAdded || item.data.extraType != null ||
        item.type !in listOf(BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE)
    ) return null
    return try {
        if (item.type == BaseItemKind.EPISODE) {
            val episode = if (recentEpisodeIds(item.data, seriesId) != null) item.data else getEpisode(item.id)
            if (episode.id != item.id) null else recentEpisodeIds(episode, seriesId)
        } else {
            val parent = if (item.type == BaseItemKind.SEASON) item.id else seriesId
            val candidates = latestEpisodes(parent)
            val selected = candidates
                .filter { recentEpisodeIds(it, seriesId) != null && it.dateCreated != null }
                .filter { item.type != BaseItemKind.SEASON || recentEpisodeIds(it, seriesId)?.seasonId == item.id }
                .sortedWith(compareByDescending<BaseItemDto> { it.dateCreated }
                    .thenBy { it.sortName ?: it.name.orEmpty() }.thenBy { it.id.toString() })
                .firstOrNull() ?: return null
            // The server orders by addition date and sort name. If a full result window
            // ends inside the winning tie, unseen IDs could change the final tie-breaker.
            val boundary = candidates.lastOrNull()
            if (candidates.size >= 20 && boundary != null && boundary.dateCreated == selected.dateCreated &&
                (boundary.sortName ?: boundary.name.orEmpty()) == (selected.sortName ?: selected.name.orEmpty())
            ) null else recentEpisodeIds(selected, seriesId)
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (_: Exception) {
        null // Full series page remains usable if optional targeting cannot be resolved.
    }
}

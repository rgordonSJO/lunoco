// Modified by Lunoco contributors. Phase 38C changes incorporated into this release source on 2026-11-22.
package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.successResponse
import io.mockk.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class SeriesLandingEntryTest {
    private val series = UUID(20, 1)
    private val user = UUID(3, 1)
    private val episode = BaseItemDto(
        id = UUID(20, 9), type = BaseItemKind.EPISODE, seriesId = series, seasonId = UUID(20, 2),
        parentIndexNumber = 2, indexNumber = 6, dateCreated = LocalDateTime.of(2026, 1, 1, 0, 0),
    )
    private val target = SeasonEpisodeIds(episode.seasonId!!, 2, episode.id, 6)
    private val seriesCard get() = BaseItem(BaseItemDto(id = series, type = BaseItemKind.SERIES))
    private val recent = HomeRowConfig.RecentlyAdded(UUID(1, 1))

    @Test fun showsAndKidsShowsActivationCarriesExactEpisodeIntoNormalSeriesDetails() = runTest {
        for (library in listOf(UUID(1, 1), UUID(1, 2))) {
            val route = resolveRecentSeriesEntry(HomeRowConfig.RecentlyAdded(library), BaseItem(episode),
                { assertEquals(series, it); true }, { assertEquals(series, it); target })
            assertEquals(Destination.MediaItem(series, BaseItemKind.SERIES, recentEpisode = target), route)
            val json = kotlinx.serialization.json.Json { classDiscriminator = "_destination" }
            assertEquals(route, json.decodeFromString(Destination.serializer(), json.encodeToString(Destination.serializer(), route)))
        }
    }

    @Test fun groupedSeriesActivationQueriesLatestOnceAndUsesUserScopedCache() = runTest {
        val api = mockk<ApiClient>()
        val items = mockk<ItemsApi>()
        every { api.baseUrl } returns "http://synthetic.invalid"
        every { api.itemsApi } returns items
        coEvery { items.getItems(any<GetItemsRequest>()) } answers {
            val request = firstArg<GetItemsRequest>()
            assertEquals(user, request.userId)
            assertEquals(series, request.parentId)
            successResponse(BaseItemDtoQueryResult(items = listOf(episode), totalRecordCount = 1, startIndex = 0))
        }
        val cache = RecentEpisodeTargetCache()
        repeat(2) {
            assertEquals(Destination.MediaItem(series, BaseItemKind.SERIES, recentEpisode = target),
                resolveRecentSeriesDestination(api, user, recent, seriesCard, cache))
        }
        coVerify(exactly = 1) { items.getItems(any<GetItemsRequest>()) }
    }

    @Test fun emptyOrUnresolvableTargetKeepsNormalSeriesPage() = runTest {
        assertEquals(Destination.MediaItem(series, BaseItemKind.SERIES),
            resolveRecentSeriesEntry(recent, seriesCard, { error("No parent lookup") }, { null }))
    }

    @Test fun ordinaryLibrarySearchAndResumeEntriesNeverResolveRecentTarget() = runTest {
        for (row in listOf(null, HomeRowConfig.ContinueWatching(), HomeRowConfig.NextUp())) {
            assertEquals(seriesCard.destination(),
                resolveRecentSeriesEntry(row, seriesCard, { error("No parent query") }, { error("No target query") }))
        }
        val movie = BaseItem(BaseItemDto(id = UUID(50, 1), type = BaseItemKind.MOVIE))
        assertEquals(movie.destination(),
            resolveRecentSeriesEntry(recent, movie, { error("No query") }, { error("No query") }))
    }

    @Test fun unavailableParentDoesNotAttachAnEpisodeToAnotherDestination() = runTest {
        val item = BaseItem(episode)
        assertEquals(item.destination(),
            resolveRecentSeriesEntry(recent, item, { false }, { error("No target lookup") }))
    }

    @Test fun absentUserMakesNoNetworkRequest() = runTest {
        val api = mockk<ApiClient>()
        assertEquals(Destination.MediaItem(series, BaseItemKind.SERIES),
            resolveRecentSeriesDestination(api, null, recent, seriesCard))
        verify { api wasNot Called }
    }

    @Test fun slowLookupTimesOutToNormalSeriesWithoutBlockingNavigationIndefinitely() = runTest {
        val api = mockk<ApiClient>()
        val items = mockk<ItemsApi>()
        every { api.baseUrl } returns "http://synthetic.invalid"
        every { api.itemsApi } returns items
        coEvery { items.getItems(any<GetItemsRequest>()) } coAnswers { awaitCancellation() }
        val before = testScheduler.currentTime
        assertEquals(Destination.MediaItem(series, BaseItemKind.SERIES),
            resolveRecentSeriesDestination(api, user, recent, seriesCard))
        assertEquals(3_000L, testScheduler.currentTime - before)
    }
}

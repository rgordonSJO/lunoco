package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.successResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpotlightRepositoryCurationTest {
    private val scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3))
    private val server = mockk<ServerRepository>()
    private val api = mockk<ApiClient>()
    private val items = mockk<ItemsApi>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun setup() {
        context.getSharedPreferences("lunoco_spotlight_history", Context.MODE_PRIVATE).edit().clear().commit()
        every { server.currentServer } returns mockk<JellyfinServer> { every { id } returns scope.serverId }
        every { server.currentUser } returns mockk<JellyfinUser> { every { id } returns scope.userId }
        every { server.apiClient } returns api
        every { api.itemsApi } returns items
        val movies = (1..50).map { BaseItemDto(id = UUID(0, it.toLong()), name = "Feature #$it",
            type = BaseItemKind.MOVIE, criticRating = 85f, playAccess = PlayAccess.FULL) }
        coEvery { items.getItems(any<GetItemsRequest>()) } answers {
            val request = firstArg<GetItemsRequest>()
            assertEquals(scope.userId, request.userId)
            successResponse(BaseItemDtoQueryResult(
                items = if (request.includeItemTypes == listOf(BaseItemKind.BOX_SET)) emptyList() else movies,
                totalRecordCount = 0, startIndex = 0))
        }
    }
    @Test fun dailyPoolCacheAndPagingDoNotMakePerAdvanceRequestsAndRestartKeepsTodaysIds() = runTest {
        val repository = SpotlightRepository(server, SpotlightHistoryStore(context))
        val today = repository.load(scope, 100)
        repeat(24) {
            spotlightPageIndex(it % 5, 5, 1)
            assertEquals(today, repository.load(scope, 100))
        }
        coVerify(exactly = 4) { items.getItems(any<GetItemsRequest>()) }
        val nextDay = repository.load(scope, 101)
        assertTrue(nextDay.none { next -> today.any { it.id == next.id } })
        coVerify(exactly = 8) { items.getItems(any<GetItemsRequest>()) }
        val recreated = SpotlightRepository(server, SpotlightHistoryStore(context))
        assertEquals(nextDay.map { it.id }, recreated.load(scope, 101).map { it.id })
    }
    @Test fun accountDiscardClearsOnlyMatchingHistoryAndPool() = runTest {
        val history = SpotlightHistoryStore(context)
        val repository = SpotlightRepository(server, history)
        repository.load(scope, 100)
        assertFalse(history.read(scope, 100).isEmpty())
        repository.discard(scope)
        assertTrue(history.read(scope, 100).isEmpty())
        repository.load(scope, 100)
        coVerify(exactly = 8) { items.getItems(any<GetItemsRequest>()) }
    }
}

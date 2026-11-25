package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RecentEpisodeTargetCacheTest {
    private val user = UUID(1, 1)
    private val card = BaseItem(BaseItemDto(id = UUID(2, 1), type = BaseItemKind.SERIES))
    private val target = SeasonEpisodeIds(UUID(3, 1), 2, UUID(4, 1), 6)

    @Test fun repeatsAreCachedButExpireAfterFifteenSeconds() = runTest {
        var now = 0L
        var requests = 0
        val cache = RecentEpisodeTargetCache { now }
        suspend fun get() = cache.get("synthetic", user, card) { requests++; target }
        assertEquals(target, get())
        assertEquals(target, get())
        assertEquals(1, requests)
        now = 15_000_000_000L
        assertEquals(target, get())
        assertEquals(2, requests)
    }
    @Test fun serverUserAndItemIdentityScopeTheCache() = runTest {
        val cache = RecentEpisodeTargetCache()
        var requests = 0
        for (server in listOf("one", "two")) for (account in listOf(user, UUID(1, 2))) {
            repeat(2) { cache.get(server, account, card) { requests++; target } }
        }
        assertEquals(4, requests)
        cache.get("one", user, BaseItem(BaseItemDto(id = UUID(2, 2), type = BaseItemKind.SERIES))) { requests++; target }
        assertEquals(5, requests)
    }
    @Test fun missingResultsAndCancellationAreNotCachedAndMutexIsReleased() = runTest {
        val cache = RecentEpisodeTargetCache()
        assertNull(cache.get("one", user, card) { null })
        try {
            cache.get("one", user, card) { throw CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(target, cache.get("one", user, card) { target })
    }
    @Test fun cacheHasBoundedCapacity() = runTest {
        val cache = RecentEpisodeTargetCache { 0L }
        cache.get("one", user, card) { target }
        repeat(32) { index ->
            cache.get("one", user, BaseItem(BaseItemDto(id = UUID(8, index.toLong()), type = BaseItemKind.SERIES))) { target }
        }
        var reloaded = false
        cache.get("one", user, card) { reloaded = true; target }
        assertTrue(reloaded)
    }
}

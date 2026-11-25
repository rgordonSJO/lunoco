package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Short-lived activation cache owned by a browsing ViewModel, never by the Home composition. */
internal class RecentEpisodeTargetCache(
    private val now: () -> Long = System::nanoTime,
) {
    private data class Key(val server: String?, val user: UUID, val item: UUID, val series: UUID?)
    private data class Entry(val storedAt: Long, val target: SeasonEpisodeIds)
    private val mutex = Mutex()
    private val entries = linkedMapOf<Key, Entry>()

    suspend fun get(
        server: String?, user: UUID, item: BaseItem,
        load: suspend () -> SeasonEpisodeIds?,
    ): SeasonEpisodeIds? = mutex.withLock {
        val key = Key(server, user, item.id, item.data.seriesId)
        val time = now()
        entries.entries.removeAll { time - it.value.storedAt >= 15_000_000_000L }
        entries[key]?.let { return@withLock it.target }
        // Do not cache failed/missing targets. Cancellation propagates and cannot populate the cache.
        val target = load() ?: return@withLock null
        entries[key] = Entry(now(), target)
        while (entries.size > 32) entries.remove(entries.keys.first())
        target
    }
}

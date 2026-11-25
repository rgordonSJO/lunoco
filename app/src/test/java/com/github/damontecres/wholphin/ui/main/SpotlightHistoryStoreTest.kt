package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.jellyfin.sdk.model.api.BaseItemDto
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpotlightHistoryStoreTest {
    private val scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3))
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset() {
        context.getSharedPreferences("lunoco_spotlight_history", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun movies(start: Int = 1) = (start until start + 5).map { BaseItemDto(id = UUID(0, it.toLong()), type = org.jellyfin.sdk.model.api.BaseItemKind.MOVIE) }

    @Test fun newStoreInstanceReadsActualRecordedDaysWithoutFabricatingMissingDays() {
        SpotlightHistoryStore(context).record(scope, 100, movies())
        SpotlightHistoryStore(context).record(scope, 103, movies(10))
        val records = SpotlightHistoryStore(context).read(scope, 103)
        assertEquals(listOf(100L, 103L), records.map { it.day })
        assertEquals(movies().map { it.id.toString() }, records.first().ids)
    }
    @Test fun storageIsBoundedToEightDaysAndFortyIdsAndExpiresTheEighthPriorDay() {
        val store = SpotlightHistoryStore(context)
        for (day in 90L..110L) store.record(scope, day, movies(day.toInt()))
        val days = store.read(scope, 110)
        assertEquals((103L..110L).toList(), days.map { it.day })
        assertEquals(40, days.sumOf { it.ids.size })
        assertEquals(listOf(110L), store.read(scope, 117).map { it.day })
        assertTrue(store.read(scope, 118).isEmpty())
    }
    @Test fun serverUserAndLibraryChangesResetScopedHistoryWithoutCrossAccountReuse() {
        for (other in listOf(scope.copy(serverId = UUID(9, 1)),
            scope.copy(userId = UUID(9, 2)), scope.copy(libraryId = UUID(9, 3)))) {
            val store = SpotlightHistoryStore(context)
            store.record(scope, 100, movies())
            assertTrue(store.read(other, 100).isEmpty())
            assertTrue(store.read(scope, 100).isEmpty())
        }
    }
    @Test fun cleanupOfAnOldAccountCannotEraseTheNewAccountsRecord() {
        val store = SpotlightHistoryStore(context)
        val other = scope.copy(userId = UUID(9, 2))
        store.record(other, 100, movies())
        store.clear(scope)
        assertEquals(1, store.read(other, 100).size)
        store.clear(other)
        assertTrue(store.read(other, 100).isEmpty())
    }
    @Test fun corruptOrOversizedStateSafelyFallsBackAndCanBeReplaced() {
        val preferences = context.getSharedPreferences("lunoco_spotlight_history", Context.MODE_PRIVATE)
        val store = SpotlightHistoryStore(context)
        store.record(scope, 100, movies())
        preferences.edit().putString("days", "invalid-json").commit()
        assertTrue(store.read(scope, 100).isEmpty())
        store.record(scope, 100, movies())
        preferences.edit().putString("days", "x".repeat(20_000)).commit()
        assertTrue(store.read(scope, 100).isEmpty())
        store.record(scope, 101, movies())
        assertEquals(listOf(101L), store.read(scope, 101).map { it.day })
    }
}

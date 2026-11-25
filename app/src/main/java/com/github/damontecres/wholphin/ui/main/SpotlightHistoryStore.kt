package com.github.damontecres.wholphin.ui.main

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One active account/library, today plus seven days, at most five IDs per day. No inferred past days. */
@Singleton
internal class SpotlightHistoryStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("lunoco_spotlight_history", Context.MODE_PRIVATE)
    private fun key(scope: SpotlightScope) = scope.serverId.toString() + ":" + scope.userId + ":" + scope.libraryId

    fun read(scope: SpotlightScope, day: Long): List<SpotlightDay> {
        if (preferences.getString("scope", null) != key(scope)) {
            preferences.edit().clear().apply()
            return emptyList()
        }
        val raw = preferences.getString("days", null) ?: return emptyList()
        if (raw.length > 16_384) {
            preferences.edit().clear().apply()
            return emptyList()
        }
        return runCatching { Json.decodeFromString<List<SpotlightDay>>(raw) }.getOrDefault(emptyList())
            .filter { it.day in (day - 7)..day }
            .distinctBy { it.day }.sortedBy { it.day }.takeLast(8)
            .map { record -> record.copy(ids = record.ids.distinct().filter {
                runCatching { UUID.fromString(it) }.getOrNull()?.let { id -> id != UUID(0, 0) } == true
            }.take(5)) }
    }

    fun record(scope: SpotlightScope, day: Long, movies: List<org.jellyfin.sdk.model.api.BaseItemDto>) {
        if (movies.isEmpty()) return
        val days = read(scope, day).filter { it.day != day } +
            SpotlightDay(day, movies.distinctBy { it.id }.take(5).map { it.id.toString() })
        preferences.edit().putString("scope", key(scope))
            .putString("days", Json.encodeToString(days.sortedBy { it.day }.takeLast(8))).apply()
    }

    fun clear(scope: SpotlightScope) {
        if (preferences.getString("scope", null) == key(scope)) preferences.edit().clear().apply()
    }
}

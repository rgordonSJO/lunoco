package com.github.damontecres.wholphin.ui.main

import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.Locale
import java.util.UUID

@Serializable
internal data class SpotlightDay(val day: Long, val ids: List<String>)

/** Exact accessible collection IDs first; only explicit numbered sequel patterns supplement them. */
internal fun spotlightFranchiseKeys(
    movies: List<BaseItemDto>, collections: Map<UUID, Set<UUID>>,
): Map<UUID, Set<String>> {
    fun title(movie: BaseItemDto) = movie.name.orEmpty().lowercase(Locale.ROOT).trim().replace(spaces, " ")
    val titles = movies.map(::title).toSet()
    val parts = movies.mapNotNull { movie ->
        val match = sequelSuffix.matchEntire(title(movie)) ?: return@mapNotNull null
        val number = match.groupValues[2].toIntOrNull() ?: romanNumbers[match.groupValues[2]]
        if (number == null || number !in 1..10) return@mapNotNull null
        movie.id to (match.groupValues[1].trim() to number)
    }.toMap()
    val bases = parts.values.groupBy { it.first }.filter { (base, sequels) ->
        base.length >= 4 && (base in titles ||
            (' ' in base && sequels.any { it.second == 1 } && sequels.any { it.second == 2 }))
    }.keys
    return movies.associate { movie ->
        movie.id to buildSet {
            collections[movie.id].orEmpty().filter { it != UUID(0, 0) }.forEach { add("collection:$it") }
            val normalized = title(movie)
            val base = if (normalized in bases) normalized else parts[movie.id]?.first?.takeIf { it in bases }
            if (base != null) add("numbered:$base")
        }
    }
}

private val spaces = Regex("\\s+")
private val sequelSuffix = Regex("^(.*?)\\s+(?:part\\s+)?([0-9]{1,2}|ii|iii|iv|v|vi|vii|viii|ix|x)(?:\\s*[:\\-–—]\\s*.+)?$")
private val romanNumbers = mapOf("ii" to 2, "iii" to 3, "iv" to 4, "v" to 5, "vi" to 6,
    "vii" to 7, "viii" to 8, "ix" to 9, "x" to 10)

/** Once per daily pool: unused diverse, unused relaxed, then least-recent repeats in those two passes. */
internal fun curateSpotlightMovies(
    ordered: List<BaseItemDto>, epochDay: Long, count: Int,
    franchises: Map<UUID, Set<String>>, history: List<SpotlightDay>,
): List<BaseItemDto> {
    val pool = ordered.distinctBy { it.id }
    val byId = pool.associateBy { it.id.toString() }
    val selected = history.firstOrNull { it.day == epochDay }?.ids.orEmpty()
        .distinct().mapNotNull { byId[it] }.take(count).toMutableList()
    if (selected.size == count) return selected
    val lastSeen = mutableMapOf<String, Long>()
    history.filter { it.day < epochDay && it.day >= epochDay - 7 }.forEach { record ->
        record.ids.forEach { id -> lastSeen[id] = maxOf(lastSeen[id] ?: Long.MIN_VALUE, record.day) }
    }
    val usedIds = selected.map { it.id }.toMutableSet()
    val represented = selected.flatMap { franchises[it.id].orEmpty() }.toMutableSet()
    val fresh = pool.filter { it.id.toString() !in lastSeen }
    val repeated = pool.filter { it.id.toString() in lastSeen }
        .sortedBy { lastSeen.getValue(it.id.toString()) } // Stable daily order breaks equal-age ties.
    fun fill(candidates: List<BaseItemDto>, diverse: Boolean) {
        if (diverse) {
            // A film in two collections can otherwise greedily block two independent choices.
            // Search only this five-card problem, with a fixed budget independent of library size.
            var budget = 4_096
            val path = mutableListOf<BaseItemDto>()
            var best = emptyList<BaseItemDto>()
            fun search(start: Int, keys: Set<String>): Boolean {
                if (selected.size + path.size >= count) return true
                for (index in start until candidates.size) {
                    if (--budget < 0) return false
                    val movie = candidates[index]
                    if (movie.id in usedIds) continue
                    val memberships = franchises[movie.id].orEmpty()
                    if (memberships.any { it in keys }) continue
                    path += movie
                    if (path.size > best.size) best = path.toList()
                    if (search(index + 1, keys + memberships)) return true
                    path.removeAt(path.lastIndex)
                }
                return false
            }
            search(0, represented)
            best.forEach { movie ->
                selected += movie
                usedIds += movie.id
                represented += franchises[movie.id].orEmpty()
            }
            return
        }
        for (movie in candidates) {
            if (selected.size >= count) break
            if (movie.id in usedIds) continue
            val keys = franchises[movie.id].orEmpty()
            selected += movie
            usedIds += movie.id
            represented += keys
        }
    }
    fill(fresh, true)
    fill(fresh, false)
    fill(repeated, true)
    fill(repeated, false)
    return selected
}

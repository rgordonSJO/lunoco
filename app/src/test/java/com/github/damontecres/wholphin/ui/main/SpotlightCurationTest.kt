package com.github.damontecres.wholphin.ui.main

import org.jellyfin.sdk.model.api.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SpotlightCurationTest {
    private val scope = SpotlightScope(UUID(1, 1), UUID(2, 2), UUID(3, 3))
    private fun movie(n: Int, name: String = "Independent movie #$n") = BaseItemDto(
        id = UUID(0, n.toLong()), type = BaseItemKind.MOVIE, name = name,
        criticRating = 85f, playAccess = PlayAccess.FULL, backdropImageTags = listOf("art"),
    )
    private fun select(pool: List<BaseItemDto>, day: Long = 100, history: List<SpotlightDay> = emptyList(),
        collections: Map<UUID, Set<UUID>> = emptyMap()) =
        selectSpotlightMovies(pool.map { SpotlightCandidate(scope, it) }, scope, day,
            collections = collections, history = history)

    @Test fun toyStoryCarsAndIndependentFilmsProduceDifferentFranchises() {
        val toys = listOf(movie(1, "Toy Story"), movie(2, "Toy Story 2"), movie(3, "Toy Story 3"))
        val cars = listOf(movie(4, "Cars"), movie(5, "Cars 2"), movie(6, "Cars 3"))
        val pool = toys + cars + (7..20).map { movie(it) }
        for (day in 100L..113L) {
            val lineup = select(pool, day)
            assertEquals(5, lineup.size)
            assertTrue(lineup.count { it.id in toys.map { film -> film.id } } <= 1)
            assertTrue(lineup.count { it.id in cars.map { film -> film.id } } <= 1)
        }
    }
    @Test fun numberedFirstFilmAndRomanSequelsNeedACorroboratingBase() {
        val movies = listOf(movie(1, "Toy Story 1"), movie(2, "Toy Story 2"),
            movie(3, "Rocky"), movie(4, "Rocky II"), movie(5, "Rocky III"))
        val keys = spotlightFranchiseKeys(movies, emptyMap())
        assertEquals(keys[movies[0].id], keys[movies[1].id])
        assertEquals(keys[movies[2].id], keys[movies[3].id])
        assertEquals(keys[movies[3].id], keys[movies[4].id])
        assertTrue(keys[movies[0].id]!!.isNotEmpty())
    }
    @Test fun subtitlesNeedExplicitCollectionMetadataRatherThanSharedWords() {
        val pool = listOf(movie(1, "Voyage: A Beginning"), movie(2, "Voyage: Another World"),
            movie(3, "The Story"), movie(4, "A Different Story"), movie(5, "Apollo 13"),
            movie(6, "Apollo 14"), movie(7, "Blade Runner 2049"))
        assertTrue(spotlightFranchiseKeys(pool, emptyMap()).values.all { it.isEmpty() })
        val collection = UUID(9, 1)
        val keys = spotlightFranchiseKeys(pool, mapOf(pool[0].id to setOf(collection), pool[1].id to setOf(collection)))
        assertTrue(keys.getValue(pool[0].id).intersect(keys.getValue(pool[1].id)).isNotEmpty())
    }
    @Test fun explicitNumberWithSubtitleJoinsOnlyItsActualBase() {
        val pool = listOf(movie(1, "Example Journey"), movie(2, "Example Journey 2: The Return"),
            movie(3, "Example Journey Elsewhere"))
        val keys = spotlightFranchiseKeys(pool, emptyMap())
        assertEquals(keys[pool[0].id], keys[pool[1].id])
        assertTrue(keys.getValue(pool[2].id).isEmpty())
    }
    @Test fun overlappingCollectionsAvoidGreedyBridgeWhenFiveIndependentChoicesExist() {
        val pool = (1..6).map { movie(it) }
        val groups = mapOf(pool[0].id to setOf("a", "b"), pool[1].id to setOf("a"), pool[2].id to setOf("b"))
        val result = curateSpotlightMovies(pool, 100, 5, groups, emptyList())
        assertEquals(5, result.size)
        assertFalse(result.any { it.id == pool[0].id })
        assertTrue(result.flatMap { groups[it.id].orEmpty() }.groupingBy { it }.eachCount().values.all { it == 1 })
    }
    @Test fun ubiquitousCollectionAndSmallLibrariesStillFillWithoutDuplicates() {
        for (size in listOf(1, 3, 5, 20)) {
            val pool = (1..size).map { movie(it) }
            val collection = pool.associate { it.id to setOf(UUID(9, 1)) }
            val result = select(pool + pool, collections = collection)
            assertEquals(minOf(5, size), result.size)
            assertEquals(result.size, result.map { it.id }.distinct().size)
        }
    }
    @Test fun missingAndUncertainMetadataRemainIndependentAndInvalidMoviesStayExcluded() {
        val pool = (1..8).map { movie(it).copy(criticRating = null, backdropImageTags = null) }
        assertEquals(5, select(pool).size)
        val invalid = pool.map { it.copy(playAccess = PlayAccess.NONE) }
        assertTrue(select(invalid).isEmpty())
    }
    @Test fun sameDayIsDeterministicAcrossResponseOrderingAndRolloverChangesLineup() {
        val pool = (1..50).map { movie(it) }
        assertEquals(select(pool), select(pool.reversed()))
        assertEquals(select(pool), select(pool))
        assertNotEquals(select(pool).map { it.id }, select(pool, 101).map { it.id })
    }
    @Test fun recordedTodayPinsExactOrderAcrossPoolAndRatingChanges() {
        val pool = (1..50).map { movie(it) }
        val today = select(pool)
        val history = listOf(SpotlightDay(100, today.map { it.id.toString() }))
        assertEquals(today.map { it.id }, select(pool.reversed().map { it.copy(criticRating = 95f) }, history = history).map { it.id })
        val removed = today.first().id
        val result = select(pool.filter { it.id != removed }, history = history)
        assertEquals(5, result.size)
        assertEquals(today.drop(1).map { it.id }, result.take(4).map { it.id })
        assertFalse(result.any { it.id == removed })
    }
    @Test fun fortyMoviesSupplyEightDailyLineupsWithoutRepeatingAnyMovie() {
        val pool = (1..40).map { movie(it) }
        val history = mutableListOf<SpotlightDay>()
        val seen = mutableSetOf<UUID>()
        for (day in 100L..107L) {
            val result = select(pool, day, history)
            assertEquals(5, result.size)
            assertTrue(result.all { seen.add(it.id) })
            history += SpotlightDay(day, result.map { it.id.toString() })
        }
        assertEquals(40, seen.size)
    }
    @Test fun recentMoviesAreAvoidedEvenWhenUnratedAlternativesAreNeeded() {
        val rated = (1..5).map { movie(it) }
        val alternatives = (6..45).map { movie(it).copy(criticRating = null) }
        val history = listOf(SpotlightDay(100, rated.map { it.id.toString() }))
        assertTrue(select(rated + alternatives, 101, history).none { it.id in rated.map { ratedMovie -> ratedMovie.id } })
    }
    @Test fun scarcePoolUsesUnseenThenLeastRecentlySelectedAndNeverDuplicateIds() {
        val pool = (1..7).map { movie(it) }
        val history = listOf(SpotlightDay(99, pool.take(3).map { it.id.toString() }),
            SpotlightDay(100, pool.drop(3).take(3).map { it.id.toString() }))
        val result = select(pool, 101, history)
        assertEquals(pool.last().id, result.first().id)
        assertTrue(result.take(4).map { it.id }.containsAll(pool.take(3).map { it.id }))
        assertEquals(5, result.map { it.id }.distinct().size)
    }
    @Test fun seventhDayIsExcludedButEighthDayIsEligibleAgain() {
        val pool = (1..10).map { movie(it) }
        val history = listOf(SpotlightDay(100, pool.take(5).map { it.id.toString() }))
        assertTrue(select(pool, 107, history).none { it.id in pool.take(5).map { film -> film.id } })
        assertEquals(select(pool, 108), select(pool, 108, history))
    }
    @Test fun libraryChangesDoNotInvalidateRecordedRecentMovieIds() {
        val old = (1..5).map { movie(it) }
        val fresh = (50..70).map { movie(it) }
        val history = listOf(SpotlightDay(100, old.map { it.id.toString() }))
        assertTrue(select(old + fresh, 101, history).all { it.id in fresh.map { film -> film.id } })
    }
}

// Modified by Lunoco contributors. Phase 38C changes incorporated into this release source on 2026-11-22.
// Modified by Lunoco contributors on 2026-11-25 for the Phase 40A episode browser.
package com.github.damontecres.wholphin.ui.detail.series

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import com.github.damontecres.wholphin.ui.theme.LunocoContentTheme
import com.github.damontecres.wholphin.ui.theme.colors.JellyLuThemeColors
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.tv.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.main.resolveRecentSeriesEntry
import kotlinx.coroutines.runBlocking
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import dagger.hilt.android.testing.*
import io.mockk.*
import org.jellyfin.sdk.model.api.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.util.UUID

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class RecentSeriesFocusUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val registration = object : TestWatcher() {
        override fun starting(description: Description?) {
            val app: Application = ApplicationProvider.getApplicationContext()
            shadowOf(app.packageManager).addOrUpdateActivity(ActivityInfo().apply {
                name = TestActivity::class.java.name; packageName = app.packageName
            })
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<TestActivity>()
    @Before fun setup() { hilt.inject() }
    private val series = BaseItem(BaseItemDto(id = UUID(1, 1), type = BaseItemKind.SERIES, name = "A series"))
    private val seasons = List(4) { BaseItem(BaseItemDto(id = UUID(2, it.toLong()), type = BaseItemKind.SEASON,
        indexNumber = it, name = "Season $it")) }
    private val episodes = List(28) { BaseItem(BaseItemDto(id = UUID(3, it.toLong() + 1),
        type = BaseItemKind.EPISODE, name = "Episode ${it + 1}", indexNumber = it + 1,
        parentIndexNumber = 3, seasonId = seasons[3].id, seriesId = series.id,
        overview = "Overview for episode ${it + 1}")) }
    private var selected = RecentSeriesEpisodes(seasons[3], 3, episodes, 6, episodes[6].id)
    private var olderSeasonEpisodes = episodes
    private var selection by mutableStateOf<RecentSeriesEpisodes?>(null)
    private var loading by mutableStateOf(false)
    private var favorite by mutableStateOf(false)
    private val clicked = mutableListOf<UUID>()
    private var currentSeries by mutableStateOf(series)
    private var seriesVisible by mutableStateOf(true)
    private var openDetailsOnEpisode = false
    private fun render(target: Boolean = true, delayed: Boolean = false, library: UUID? = null, seasonCount: Int = 4) {
        val routeTarget = if (library == null) {
            if (target) selected.episodeId.toString() else null
        } else {
            (runBlocking {
                resolveRecentSeriesEntry(HomeRowConfig.RecentlyAdded(library), episodes[selected.episodeIndex],
                    { true }, { SeasonEpisodeIds(selected.season.id, selected.season.indexNumber, selected.episodeId, selected.episodeIndex + 1) })
            } as Destination.MediaItem).recentEpisode?.episodeId?.toString()
        }
        selection = if (routeTarget != null && !delayed) selected else null
        loading = delayed
        val images = mockk<ImageUrlService>(relaxed = true)
        every { images.getItemImageUrl(any<BaseItem>(), any(), any(), any(), any()) } returns null
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                CompositionLocalProvider(LocalImageUrlService provides images) {
                    val savedPages = rememberSaveableStateHolder()
                    if (seriesVisible) {
                    savedPages.SaveableStateProvider("series-entry") {
                    LunocoContentTheme { SeriesDetailsContent(
                        preferences = UserPreferences(AppPreferences.getDefaultInstance(), null),
                        series = currentSeries, seasons = seasons.take(seasonCount), similar = emptyList(), trailers = emptyList(),
                        extras = emptyList(), people = emptyList(), discovered = emptyList(), played = false,
                        favorite = favorite, canDelete = false, onClickItem = { _, _ -> }, onClickPerson = {},
                        onLongClickItem = { _, _ -> }, overviewOnClick = {}, playOnClick = {},
                        watchOnClick = {}, favoriteOnClick = {}, trailerOnClick = {}, onClickExtra = { _, _ -> },
                        onShowContextMenu = {}, actions = mockk<ContextMenuActions>(relaxed = true),
                        onClickDiscover = { _, _ -> }, discoverSeries = null, onClickDiscoverSeries = {},
                        recentEpisodes = selection, recentEpisodeLoading = loading,
                        recentEpisodeKey = routeTarget,
                        onSelectRecentSeason = { season, index ->
                            val seasonEpisodes = olderSeasonEpisodes.map {
                                BaseItem(it.data.copy(parentIndexNumber = season.indexNumber, seasonId = season.id))
                            }
                            selection = RecentSeriesEpisodes(
                                season, index, seasonEpisodes, 0, seasonEpisodes.firstOrNull()?.id,
                            )
                        },
                        onClickRecentEpisode = { clicked += it.id; if (openDetailsOnEpisode) seriesVisible = false },
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    ) }
                    }
                    } else Text("Episode details")
                }
            }
        }
        compose.waitForIdle()
    }
    private fun episode(index: Int) = compose.onNodeWithTag("recent-series-episode-${episodes[index].id}")
    @Test fun targetsSeasonThreeEpisodeSevenWithoutAutoplay() {
        render()
        episode(6).assertIsDisplayed().assertIsFocused()
        capture("targeted-series")
        assertTrue(clicked.isEmpty())
        episode(6).performKeyInput { pressKey(Key.DirectionCenter) }
        assertEquals(listOf(episodes[6].id), clicked)
    }
    @Test fun pagingAndRecompositionDoNotResetEpisodeFocus() {
        render()
        episode(6).performKeyInput { pressKey(Key.DirectionRight) }
        episode(7).assertIsFocused()
        compose.runOnIdle { favorite = true }
        episode(7).assertIsFocused()
        assertTrue(clicked.isEmpty())
    }
    @Test fun delayedLoadingCannotStealFocusAfterUserInput() {
        render(delayed = true)
        compose.onNodeWithText("Play").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Shuffle All").assertIsFocused()
        compose.runOnIdle { selection = selected; loading = false }
        compose.onNodeWithText("Shuffle All").assertIsFocused()
        episode(6).assertDoesNotExist() // No late row insertion underneath an active browser.
        assertTrue(clicked.isEmpty())
    }
    private fun assertRecentTarget(library: UUID, seasonCount: Int) {
        if (seasonCount == 1) selected = selected.copy(season = seasons[0], seasonIndex = 0)
        render(library = library, seasonCount = seasonCount)
        episode(6).assertIsDisplayed().assertIsFocused()
        compose.onNode(isSelected()).assertExists()
        assertTrue(clicked.isEmpty())
    }
    @Test fun showsSingleSeasonTargetsEpisode() = assertRecentTarget(UUID(10, 1), 1)
    @Test fun showsMultipleSeasonsTargetEpisode() = assertRecentTarget(UUID(10, 1), 4)
    @Test fun kidsShowsSingleSeasonTargetsEpisode() = assertRecentTarget(UUID(10, 2), 1)
    @Test fun kidsShowsMultipleSeasonsTargetEpisode() = assertRecentTarget(UUID(10, 2), 4)

    @Test fun seasonTwoEpisodeSixIsVisibleFocusedAndNavigableWithoutAutoplay() {
        selected = selected.copy(season = seasons[2], seasonIndex = 2, episodeIndex = 5, episodeId = episodes[5].id)
        render()
        episode(5).assertIsDisplayed().assertIsFocused()
        compose.onNode(isSelected()).assertExists()
        episode(5).performKeyInput { pressKey(Key.DirectionRight) }
        episode(6).assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        episode(5).assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNode(isFocused()).assertExists()
        assertTrue(clicked.isEmpty())
    }

    @Test fun targetTakesFocusOnlyAfterDelayedEpisodesAreReady() {
        render(delayed = true)
        compose.onNodeWithText("Play").assertIsFocused()
        compose.runOnIdle { selection = selected; loading = false }
        episode(6).assertIsDisplayed().assertIsFocused()
        compose.runOnIdle { favorite = true }
        episode(6).assertIsFocused()
        assertTrue(clicked.isEmpty())
    }

    @Test fun differentSeriesDoesNotInheritActionFocus() {
        render(target = false)
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Shuffle All").assertIsFocused()
        compose.runOnIdle {
            currentSeries = BaseItem(BaseItemDto(id = UUID(8, 8), type = BaseItemKind.SERIES, name = "Another series"))
        }
        compose.onNodeWithText("Play").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithText("Another series").assertIsDisplayed()
        assertTrue(clicked.isEmpty())
    }

    @Test fun existingEpisodeSelectionSurvivesDetailsBackRestoration() {
        openDetailsOnEpisode = true
        render() // Target focus is consumed once; returning preserves the user-selected episode.
        episode(6).performKeyInput { pressKey(Key.DirectionRight) }
        episode(7).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithText("Episode details").assertExists()
        compose.runOnIdle { seriesVisible = true }
        episode(7).assertIsDisplayed().assertIsFocused()
        assertEquals(listOf(episodes[7].id), clicked)
    }

    private fun capture(name: String, node: SemanticsNodeInteraction = compose.onRoot()) {
        val image = node.captureToImage().asAndroidBitmap()
        val output = File("build/reports/episode-browser-40a/$name.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun cardTitlesRemainVisibleAndEpisodeContextTracksFocus() {
        render()
        compose.onNodeWithText("E7 — Episode 7").assertIsDisplayed()
        compose.onNodeWithText("E8 — Episode 8").assertIsDisplayed()
        compose.onNodeWithText("Overview for episode 7").assertIsDisplayed()
        episode(6).performKeyInput { pressKey(Key.DirectionRight) }
        episode(7).assertIsFocused()
        compose.onNodeWithText("Overview for episode 8").assertIsDisplayed()
        compose.onNodeWithText("Overview for episode 7").assertDoesNotExist()
        capture("different-episode")
    }

    @Test fun seasonControlIsOnePressUpAndReturnsToTheSameEpisode() {
        render()
        val season = compose.onNodeWithTag("episode-browser-season")
        episode(6).performKeyInput { pressKey(Key.DirectionUp) }
        season.assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        episode(6).assertIsFocused()
    }

    private fun openPickerFromEpisode(index: Int, browse: Boolean = false) {
        episode(index).performKeyInput { pressKey(Key.DirectionUp) }
        val season = compose.onNodeWithTag("episode-browser-season").assertIsFocused()
        if (browse) {
            season.performKeyInput { pressKey(Key.DirectionRight) }
            compose.onNodeWithTag("episode-browser-all").assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionCenter) }
        } else {
            season.performKeyInput { pressKey(Key.DirectionCenter) }
        }
    }

    @Test fun chooseOlderSeasonAndBrowseToEarlierEpisodeWithoutPlaying() {
        render()
        openPickerFromEpisode(6)
        val list = compose.onNodeWithTag("episode-browser-picker-list")
        list.assertIsDisplayed()
        capture("season-picker", list)
        compose.onNodeWithTag("episode-browser-choice-3").assertIsFocused().performKeyInput {
            repeat(2) { pressKey(Key.DirectionUp) }
            pressKey(Key.DirectionCenter)
        }
        episode(0).assertIsFocused()
        compose.onNodeWithText("Season 1 ▼").assertIsDisplayed()
        compose.onNodeWithText("S1 E1 — Episode 1").assertIsDisplayed()
        capture("older-season")
        openPickerFromEpisode(0, browse = true)
        compose.onNodeWithTag("episode-browser-choice-0").assertIsFocused().performKeyInput {
            repeat(2) { pressKey(Key.DirectionDown) }
            pressKey(Key.DirectionCenter)
        }
        episode(2).assertIsFocused()
        assertTrue(clicked.isEmpty())
    }

    @Test fun missingTitleUsesNumberAndLongTitleKeepsCardVisible() {
        val missing = BaseItem(episodes[6].data.copy(name = null, overview = null))
        val long = BaseItem(episodes[7].data.copy(name = "A long episode title ".repeat(18)))
        selected = selected.copy(episodes = episodes.toMutableList().apply {
            this[6] = missing
            this[7] = long
        })
        render()
        episode(6).assertIsFocused()
        compose.onNode(hasText("Episode 7") and
            hasAnyAncestor(hasTestTag("episode-browser-context"))).assertIsDisplayed()
        compose.onNodeWithText("Overview for episode 7").assertDoesNotExist()
        capture("missing-metadata")
        episode(6).performKeyInput { pressKey(Key.DirectionRight) }
        episode(7).assertIsDisplayed().assertIsFocused()
    }

    @Test fun seasonZeroCanOpenASingleWatchedEpisode() {
        val special = BaseItem(episodes[0].data.copy(
            name = "The making of the series",
            parentIndexNumber = 0,
            seasonId = seasons[0].id,
            userData = UserItemDataDto(
                playbackPositionTicks = 0, played = true, playCount = 1,
                isFavorite = false, key = "test", itemId = episodes[0].id,
            ),
        ))
        selected = RecentSeriesEpisodes(seasons[0], 0, listOf(special), 0, special.id)
        render()
        episode(0).assertIsFocused()
        compose.onNodeWithText("E1 — The making of the series").assertIsDisplayed()
        compose.onNodeWithText("S0 E1 — The making of the series").assertIsDisplayed()
        capture("special-watched")
    }

    @Test fun partiallyWatchedEpisodeStillShowsTitleAndContext() {
        val partial = BaseItem(episodes[6].data.copy(
            userData = UserItemDataDto(
                playbackPositionTicks = 0, played = false, playedPercentage = 50.0,
                playCount = 0, isFavorite = false, key = "test", itemId = episodes[6].id,
            ),
        ))
        selected = selected.copy(episodes = episodes.toMutableList().apply { this[6] = partial })
        render()
        episode(6).assertIsFocused()
        compose.onNodeWithText("E7 — Episode 7").assertIsDisplayed()
        compose.onNodeWithText("Overview for episode 7").assertIsDisplayed()
        capture("partially-watched")
    }

    @Test fun emptySeasonKeepsTheSeasonSelectorAvailable() {
        olderSeasonEpisodes = emptyList()
        render()
        openPickerFromEpisode(6)
        compose.onNodeWithTag("episode-browser-choice-3").assertIsFocused().performKeyInput {
            repeat(3) { pressKey(Key.DirectionUp) }
            pressKey(Key.DirectionCenter)
        }
        compose.onNodeWithTag("episode-browser-season").assertIsFocused()
        compose.onNodeWithText("No episodes in this season.").assertIsDisplayed()
        compose.onNodeWithTag("episode-browser-all").assertDoesNotExist()
    }

    @Test fun backFromInlineBrowserRestoresTheHighlightedEpisode() {
        render()
        episode(6).performKeyInput { pressKey(Key.DirectionRight) }
        episode(7).assertIsFocused()
        openPickerFromEpisode(7, browse = true)
        compose.onNodeWithTag("episode-browser-picker-list").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        episode(7).assertIsDisplayed().assertIsFocused()
        assertTrue(clicked.isEmpty())
    }

    @Test fun ordinarySeriesKeepsPlayFocusAndNoRecentEpisodeRow() {
        render(target = false)
        compose.onNodeWithText("Play").assertIsFocused()
        episode(6).assertDoesNotExist()
        assertTrue(clicked.isEmpty())
    }
}

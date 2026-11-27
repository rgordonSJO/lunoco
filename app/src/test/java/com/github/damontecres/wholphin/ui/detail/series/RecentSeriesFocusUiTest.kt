// Modified by Lunoco contributors on 2026-11-26 to verify Phase 45 series action placement and remote navigation.
// Modified by Lunoco contributors on 2026-11-26 to check selector alignment and exact episode landing.
// Modified by Lunoco contributors on 2026-11-26 to cover simplified series details with remote keys.
// Modified by Lunoco contributors on 2026-11-26 for episode focus and layout.
// Modified by Lunoco contributors on 2026-11-25 for the Phase 41 episode hub redesign.
// Modified by Lunoco contributors. Phase 38C changes incorporated into this release source on 2026-11-22.
// Modified by Lunoco contributors on 2026-11-25 for the Phase 40A episode browser.
package com.github.damontecres.wholphin.ui.detail.series

import android.app.Application
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.github.damontecres.wholphin.ui.theme.LunocoContentTheme
import com.github.damontecres.wholphin.ui.theme.colors.JellyLuThemeColors
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.tv.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.ui.main.resolveRecentSeriesEntry
import kotlinx.coroutines.runBlocking
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LunocoTopNavigation
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
    private val seasons = List(36) { BaseItem(BaseItemDto(id = UUID(2, it.toLong()), type = BaseItemKind.SEASON,
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
    private val seriesActions = mutableListOf<String>()
    private var currentSeries by mutableStateOf(series)
    private var seriesVisible by mutableStateOf(true)
    private var openDetailsOnEpisode = false
    private fun render(
        target: Boolean = true,
        delayed: Boolean = false,
        library: UUID? = null,
        seasonCount: Int = 4,
        chrome: Boolean = false,
        optionalActions: Boolean = false,
    ) {
        val routeTarget = if (library == null) {
            if (target) selected.episodeId.toString() else null
        } else {
            (runBlocking {
                resolveRecentSeriesEntry(HomeRowConfig.RecentlyAdded(library), episodes[selected.episodeIndex],
                    { true }, { SeasonEpisodeIds(selected.season.id, selected.season.indexNumber, selected.episodeId, selected.episodeIndex + 1) })
            } as Destination.MediaItem).recentEpisode?.episodeId?.toString()
        }
        selection = if (delayed) null else selected
        loading = delayed
        val images = mockk<ImageUrlService>(relaxed = true)
        every { images.getItemImageUrl(any<BaseItem>(), any(), any(), any(), any()) } returns null
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                CompositionLocalProvider(LocalImageUrlService provides images) {
                    val savedPages = rememberSaveableStateHolder()
                    if (seriesVisible) {
                    savedPages.SaveableStateProvider("series-entry") {
                    LunocoContentTheme {
                        Box(Modifier.fillMaxSize()) {
                            TestSeriesBackdrop()
                            if (chrome) {
                                val navFocus = remember { FocusRequester() }
                                Box(Modifier.zIndex(1f).testTag("series-floating-nav")) {
                                    LunocoTopNavigation(
                                        destination = Destination.MediaItem(currentSeries.id, BaseItemKind.SERIES),
                                        groups = emptyList(), selectedLibraryId = null, homeFocus = navFocus,
                                        onProfile = {}, onHome = {}, onOpenLink = {}, onSearch = {}, onSettings = {},
                                        onNavigationFocus = {},
                                    )
                                }
                            }
                            Box(
                                if (chrome) Modifier.fillMaxSize().padding(top = 56.dp, start = 24.dp,
                                    end = if (optionalActions) 412.dp else 24.dp)
                                    .focusRestorer().focusGroup() else Modifier.fillMaxSize(),
                            ) {
                            SeriesDetailsContent(
                        preferences = UserPreferences(AppPreferences.getDefaultInstance(), null),
                        series = currentSeries, seasons = seasons.take(seasonCount), similar = emptyList(),
                        trailers = if (optionalActions) listOf(mockk(relaxed = true)) else emptyList(),
                        extras = emptyList(),
                        people = if (chrome) listOf(Person(UUID(8, 1), "Fixture Actor", "Role", PersonKind.ACTOR, null, false)) else emptyList(),
                        discovered = emptyList(), played = false,
                        favorite = favorite, canDelete = optionalActions, onClickItem = { _, _ -> }, onClickPerson = {},
                        onLongClickItem = { _, _ -> }, overviewOnClick = { seriesActions += "overview" },
                        playOnClick = { seriesActions += if (it) "shuffle" else "play" },
                        watchOnClick = { seriesActions += "watched" }, favoriteOnClick = { seriesActions += "favorite" },
                        trailerOnClick = { seriesActions += "trailer" }, onClickExtra = { _, _ -> },
                        onShowContextMenu = {}, actions = mockk<ContextMenuActions>(relaxed = true),
                        onClickDiscover = { _, _ -> }, discoverSeries = if (optionalActions) mockk(relaxed = true) else null,
                        onClickDiscoverSeries = { seriesActions += "discover" },
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
                        modifier = Modifier.fillMaxSize(),
                            )
                            }
                        }
                    }
                    }
                    } else Text("Episode details")
                }
            }
        }
        compose.waitForIdle()
    }
    private fun sendRemoteKey(code: Int) {
        compose.runOnIdle {
            val downTime = SystemClock.uptimeMillis()
            for (action in listOf(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.ACTION_UP)) {
                compose.activity.dispatchKeyEvent(
                    AndroidKeyEvent(downTime, SystemClock.uptimeMillis(), action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_DPAD),
                )
            }
        }
        compose.waitForIdle()
    }

    @Test fun remoteKeysReachSeasonAndPlayTheSelectedEpisode() {
        render(chrome = true)
        val season = compose.onNodeWithTag("episode-browser-season")
        episode(6).assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        episode(7).assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        episode(6).assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        season.assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("episode-browser-choice-3").assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_BACK)
        season.assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        episode(6).assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(episodes[6].id), clicked)
    }

    @Test fun recentLandingKeepsHeadingTitleAndCardBelowNavigation() {
        render(chrome = true)
        episode(6).assertIsFocused()
        val header = compose.onNodeWithTag("episode-browser-heading").assertIsDisplayed()
        val headerBounds = header.getUnclippedBoundsInRoot()
        val titleBounds = compose.onNodeWithTag("series-episode-title")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val season = compose.onNodeWithTag("episode-browser-season").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Episode heading clears the floating navigation", headerBounds.top >= 56.dp)
        assertTrue("Episode title is below the heading", titleBounds.top >= headerBounds.bottom)
        val play = compose.onNodeWithText("Play").assertIsDisplayed().getUnclippedBoundsInRoot()
        val shuffle = compose.onNodeWithText("Shuffle All").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Season follows the standard series actions", play.right < shuffle.left && shuffle.right < season.left)
        assertEquals("Season and Play are centered on the same row",
            ((play.top + play.bottom) / 2).value, ((season.top + season.bottom) / 2).value, 1f)
        assertTrue("Episodes appears directly under the action row", season.bottom <= headerBounds.top)
        val description = compose.onNodeWithTag("series-episode-description").getUnclippedBoundsInRoot()
        assertEquals(headerBounds.left, titleBounds.left)
        assertEquals(headerBounds.left, description.left)
        assertTrue("Selected thumbnail fits in the same TV viewport", episode(6).getUnclippedBoundsInRoot().bottom <= 540.dp)
        capture("recent-landing")
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        episode(7).assertIsFocused()
        assertEquals("Changing episodes must not vertically move the heading", headerBounds.top, header.getUnclippedBoundsInRoot().top)
        capture("different-episode-with-navigation")
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
    @Test fun recentlyAddedEpisodeTwoKeepsItsExactIdAndTheWholeCardIsVisible() {
        selected = selected.copy(episodeIndex = 1, episodeId = episodes[1].id)
        render(library = UUID(10, 1), chrome = true)
        episode(1).assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("series-episode-title").assertTextEquals("S3 E2 — Episode 2")
        compose.onNodeWithTag("series-episode-description").assertTextEquals("Overview for episode 2")
        val heading = compose.onNodeWithTag("episode-browser-heading").getUnclippedBoundsInRoot()
        val season = compose.onNodeWithTag("episode-browser-season").assertIsDisplayed().getUnclippedBoundsInRoot()
        val card = episode(1).getUnclippedBoundsInRoot()
        val label = compose.onNodeWithTag("episode-browser-card-title-${episodes[1].id}").getUnclippedBoundsInRoot()
        assertTrue("The heading and selector clear navigation", heading.top >= 56.dp && season.top >= 56.dp)
        assertTrue("The focused artwork and both title lines fit below the navigation", card.top > season.bottom &&
            card.bottom < label.top && label.bottom <= 540.dp)
        assertTrue("Entering the page does not play an episode", clicked.isEmpty())
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(episodes[1].id), clicked)
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
        compose.onNodeWithTag("episode-browser-season").performScrollTo().assertIsDisplayed()
        episode(6).assertIsNotFocused()
        assertTrue(clicked.isEmpty())
    }
    private fun assertRecentTarget(library: UUID, seasonCount: Int) {
        if (seasonCount == 1) selected = selected.copy(season = seasons[0], seasonIndex = 0)
        render(library = library, seasonCount = seasonCount)
        episode(6).assertIsDisplayed().assertIsFocused()
        compose.onNodeWithTag("episode-browser-season").assertIsDisplayed()
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
        compose.onNodeWithTag("episode-browser-season").assertIsDisplayed()
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

    private fun capture(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals("TV capture width", 1920, image.width)
        assertEquals("TV capture height", 1080, image.height)
        val output = File("build/reports/simple-series-45/$name.png")
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

    @Test fun seasonControlIsOneStepAboveTheSelectedEpisode() {
        render()
        val season = compose.onNodeWithTag("episode-browser-season")
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        season.assertIsFocused()
        capture("season-focused")
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        episode(6).assertIsFocused()
    }

    private fun openPickerFromEpisode(index: Int) {
        episode(index).assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        compose.onNodeWithTag("episode-browser-season").assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
    }

    @Test fun choosingAnOlderSeasonHighlightsItsFirstEpisodeWithoutPlaying() {
        render()
        openPickerFromEpisode(6)
        compose.onNodeWithTag("episode-browser-picker-list").assertIsDisplayed()
        capture("season-picker")
        compose.onNodeWithTag("episode-browser-choice-3").assertIsFocused()
        repeat(2) { sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_UP) }
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        episode(0).assertIsFocused()
        compose.onNodeWithText("Season 1 ▼").assertIsDisplayed()
        compose.onNodeWithText("S1 E1 — Episode 1").assertIsDisplayed()
        capture("older-season")
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        episode(1).assertIsFocused()
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
        compose.onNodeWithTag("series-episode-title").assertTextEquals("Episode 7")
        compose.onNodeWithText("Overview for episode 7").assertDoesNotExist()
        capture("missing-metadata")
        episode(6).performKeyInput { pressKey(Key.DirectionRight) }
        episode(7).assertIsDisplayed().assertIsFocused()
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("episode-browser-card-title-${episodes[7].id}")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertEquals(2, layout.single().lineCount)
        assertTrue(layout.single().isLineEllipsized(1))
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
        compose.onNodeWithTag("episode-browser-rail").assertDoesNotExist()
    }

    @Test fun ordinarySeriesStartsAtItsRegularActionsAndOffersTheSameEpisodes() {
        render(target = false, chrome = true)
        compose.onNodeWithText("A series").assertIsDisplayed()
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Shuffle All").assertIsDisplayed()
        capture("ordinary-series")
        compose.onNodeWithTag("episode-browser-season").assertIsDisplayed()
        compose.onNodeWithTag("episode-browser-heading").assertIsDisplayed()
        assertTrue(clicked.isEmpty())
    }

    @Test fun standardSeriesActionsKeepTheirOrderAndCallbacks() {
        render(target = false, chrome = true, optionalActions = true)
        compose.onNodeWithText("Play").assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithText("Shuffle All").assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("play", "shuffle", "watched", "favorite", "trailer"), seriesActions)
    }

    @Test fun optionalActionsScrollIntoViewAndKeepSeasonReachable() {
        selected = selected.copy(season = BaseItem(seasons[3].data.copy(name = "Season with a long title ".repeat(8))))
        render(target = false, chrome = true, optionalActions = true)
        val row = compose.onNodeWithTag("series-action-row").getUnclippedBoundsInRoot()
        val season = compose.onNodeWithTag("episode-browser-season")
        repeat(7) { sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT) }
        season.assertIsFocused().assertIsDisplayed()
        val selector = season.getUnclippedBoundsInRoot()
        assertTrue("The focused selector fits inside the action viewport", selector.left >= row.left && selector.right <= row.right)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("discover"), seriesActions)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        season.assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        episode(6).assertIsFocused()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        season.assertIsFocused()
        capture("optional-actions")
    }

    @Test fun longSeriesOverviewUsesTwoLinesAndRemainsOpenable() {
        val overview = "A long series overview that should still be readable in a compact layout. ".repeat(20)
        currentSeries = BaseItem(series.data.copy(overview = overview))
        render(target = false, chrome = true)
        val node = compose.onNodeWithTag("series-overview")
        val layout = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertEquals(2, layout.single().lineCount)
        assertTrue(layout.single().isLineEllipsized(1))
        node.performClick()
        assertEquals(listOf("overview"), seriesActions)
        capture("compact-series-overview")
    }

    @Test fun longDescriptionUsesTwoLinesAndLeavesTheRowInPlace() {
        val longOverview = "<p>A lost explorer follows the winding river.</p><p>" +
            "There are many clues to see, and each takes them farther into the valley. ".repeat(15) + "</p>"
        selected = selected.copy(episodes = episodes.toMutableList().apply {
            this[6] = BaseItem(episodes[6].data.copy(overview = longOverview))
        })
        render()
        val node = compose.onNodeWithTag("series-episode-description")
        val geometry = node.getUnclippedBoundsInRoot()
        val context = compose.onNodeWithTag("series-episode-title").getUnclippedBoundsInRoot()
        val ratio = (geometry.right - geometry.left).value / (context.right - context.left).value
        assertTrue("Description width fraction was $ratio", ratio in 0.55f..0.60f)
        val layout = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertEquals(2, layout.single().lineCount)
        assertTrue(layout.single().isLineEllipsized(1))
        assertFalse(layout.single().layoutInput.text.text.contains("<p>"))
        assertTrue(layout.single().layoutInput.text.text.contains("winding river. There are"))
        capture("long-description")
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        episode(7).assertIsFocused()
        val shortGeometry = node.getUnclippedBoundsInRoot()
        assertEquals(geometry.bottom, shortGeometry.bottom)
    }

    @Test fun backFromSeasonPickerRestoresTheSeasonControlAndPreviousCard() {
        render()
        openPickerFromEpisode(6)
        compose.onNodeWithTag("episode-browser-choice-3").assertIsFocused()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("episode-browser-season").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        episode(6).assertIsFocused()
    }

    @Test fun returningToASeasonRestoresItsLastHighlightedEpisode() {
        render()
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        openPickerFromEpisode(7)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_UP)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        episode(0).assertIsFocused()
        compose.onNodeWithText("Season 2 ▼").assertIsDisplayed()
        openPickerFromEpisode(0)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        sendRemoteKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithText("Season 3 ▼").assertIsDisplayed()
        episode(7).assertIsFocused()
        assertTrue(clicked.isEmpty())
    }

    @Test fun manySeasonsOpenOnTheCurrentSeasonWithoutLoadingEveryEpisode() {
        selected = selected.copy(season = seasons[30], seasonIndex = 30)
        render(seasonCount = seasons.size)
        openPickerFromEpisode(6)
        compose.onNodeWithTag("episode-browser-choice-30").assertIsDisplayed().assertIsFocused().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }
        episode(0).assertIsFocused()
        compose.onNodeWithText("Season 31 ▼").assertIsDisplayed()
    }

    @Composable
    private fun TestSeriesBackdrop() {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF101115), Color(0xFF30343F), Color(0xFF384355))))
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFE0A988), Color(0xFFA37469), Color.Transparent),
                    center = Offset(size.width * .75f, size.height * .27f), radius = size.width * .35f),
                radius = size.width * .35f,
                center = Offset(size.width * .75f, size.height * .27f),
            )
            drawRect(Brush.verticalGradient(0f to Color(0x300B0D13), .57f to Color(0x15090A10), 1f to Color(0xEF090A10)))
        }
    }
}

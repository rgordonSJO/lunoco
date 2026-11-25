package com.github.damontecres.wholphin.ui.nav

import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.test.core.app.ApplicationProvider
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.theme.colors.JellyLuThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors
import dagger.hilt.android.testing.*
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class FloatingNavigationUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val registration = object : TestWatcher() {
        override fun starting(description: Description?) {
            val app: Application = ApplicationProvider.getApplicationContext()
            shadowOf(app.packageManager).addOrUpdateActivity(ActivityInfo().apply {
                name = TestActivity::class.java.name
                packageName = app.packageName
            })
        }
    }
    @get:Rule(order = 2) val compose = createAndroidComposeRule<TestActivity>()
    private var leanbackBefore = false
    @Before fun setup() {
        hilt.inject()
        leanbackBefore = compose.activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        shadowOf(compose.activity.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK, true)
    }
    @After fun restoreDeviceFeature() {
        shadowOf(compose.activity.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK, leanbackBefore)
    }

    private var destination by mutableStateOf<Destination>(Destination.Home())
    private var libraryId by mutableStateOf<UUID?>(null)
    private var profileOpens = 0
    private val libraries = listOf(
        "Movies" to CollectionType.MOVIES, "Kids Movies" to CollectionType.MOVIES,
        "Shows" to CollectionType.TVSHOWS, "Kids Shows" to CollectionType.TVSHOWS,
        "Live TV" to CollectionType.LIVETV, "Home Movies" to CollectionType.HOMEVIDEOS,
    ).mapIndexed { index, (name, type) ->
        val id = UUID(26, index.toLong())
        ServerNavDrawerItem(id, name, Destination.MediaItem(id, BaseItemKind.COLLECTION_FOLDER, type), type)
    }

    private fun render(bright: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                    Column(Modifier.fillMaxSize().background(if (bright) Color.White else JellyLuThemeColors.darkScheme.background)) {
                        LunocoTopNavigation(
                            destination, lunocoBrowseGroups(jellyLuBrowseLinks(libraries)), libraryId,
                            remember { FocusRequester() }, onProfile = { profileOpens++ },
                            onHome = { destination = Destination.Home(); libraryId = null },
                            onOpenLink = { destination = it.destination; libraryId = it.library.itemId },
                            onSearch = { destination = Destination.Search() },
                            onSettings = { destination = Destination.Settings(PreferenceScreenOption.BASIC) },
                            onNavigationFocus = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun home() = compose.onNodeWithContentDescription("Home")
    private fun search() = compose.onNodeWithContentDescription("Search")
    private fun settings() = compose.onNodeWithContentDescription("Settings")
    private fun key(key: Key) = compose.onNode(isFocused()).performKeyInput { pressKey(key) }
    private fun nodes() = listOf(compose.onNodeWithContentDescription("Lunoco"), home(),
        compose.onNodeWithText("Movies"), compose.onNodeWithText("Shows"),
        compose.onNodeWithText("Live TV"), compose.onNodeWithText("Home Movies"), search(), settings())

    private fun capture(label: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File("build/reports/floating-navigation26/" + label + ".png")
        checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun allControlsRenderAndFocusTraversalNeverLandsOnDecoration() {
        render()
        nodes().forEach { it.assertIsDisplayed().assertHasClickAction() }
        compose.onNodeWithText("Home").assertDoesNotExist()
        home().assertIsSelected()
        nodes().first().requestFocus()
        for (node in nodes().drop(1)) { key(Key.DirectionRight); node.assertIsFocused() }
        for (node in nodes().dropLast(1).reversed()) { key(Key.DirectionLeft); node.assertIsFocused() }
        key(Key.DirectionCenter)
        assertEquals(1, profileOpens)
    }

    @Test fun selectedHomeIsDistinctFromFocusedSettingsAndOnlyControlsPaint() {
        render(bright = true)
        settings().requestFocus()
        home().assertIsSelected().assertIsNotFocused()
        settings().assertIsFocused().assertIsNotSelected()
        val homeBounds = home().fetchSemanticsNode().boundsInRoot
        val moviesBounds = compose.onNodeWithText("Movies").fetchSemanticsNode().boundsInRoot
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val gapX = ((homeBounds.right + moviesBounds.left) / 2).toInt()
        assertEquals("No full-width bar is painted between pills", Color.White.toArgb(),
            bitmap.getPixel(gapX, homeBounds.center.y.toInt()))
        val selectedPixel = bitmap.getPixel(homeBounds.left.toInt() + 12, homeBounds.center.y.toInt())
        val focusBounds = settings().fetchSemanticsNode().boundsInRoot
        val focusedPixel = bitmap.getPixel(focusBounds.left.toInt() + 12, focusBounds.center.y.toInt())
        assertNotEquals("Selected and focused fills must remain distinct", selectedPixel, focusedPixel)
        capture("1080p-home-selected-settings-focused")
    }

    @Test fun selectedDestinationsUseOnlyPillFillWithoutBottomDecoration() {
        render()
        val checks = listOf<Triple<String, Destination, UUID?>>(
            Triple("Home", Destination.Home(), null),
            Triple("Movies", Destination.MediaItem(libraries[0].itemId, BaseItemKind.COLLECTION_FOLDER), libraries[0].itemId),
            Triple("Shows", Destination.MediaItem(libraries[2].itemId, BaseItemKind.COLLECTION_FOLDER), libraries[2].itemId),
            Triple("Live TV", Destination.MediaItem(libraries[4].itemId, BaseItemKind.COLLECTION_FOLDER), libraries[4].itemId),
            Triple("Home Movies", Destination.MediaItem(libraries[5].itemId, BaseItemKind.COLLECTION_FOLDER), libraries[5].itemId),
            Triple("Search", Destination.Search(), null),
            Triple("Settings", Destination.Settings(PreferenceScreenOption.BASIC), null),
        )
        for ((label, route, id) in checks) {
            compose.runOnIdle { destination = route; libraryId = id }
            compose.waitForIdle()
            val selected = when (label) {
                "Home" -> home()
                "Search" -> search()
                "Settings" -> settings()
                else -> compose.onNodeWithText(label)
            }
            val focused = if (label == "Settings") home() else settings()
            focused.requestFocus().assertIsFocused().assertIsNotSelected()
            selected.assertIsSelected().assertIsNotFocused()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            for (node in listOf(selected, focused)) {
                val bounds = node.fetchSemanticsNode().boundsInRoot
                assertEquals("Pill geometry has no extra indicator spacing", 80f, bounds.height, .1f)
                // Scan the former 14dp underline location, below text/icons and above the border.
                for (dx in -12..12) {
                    val reference = bitmap.getPixel(bounds.center.x.toInt() - 18, bounds.bottom.toInt() - 8)
                    val actual = bitmap.getPixel(bounds.center.x.toInt() + dx, bounds.bottom.toInt() - 8)
                    // Native rounded-gradient coverage varies by one channel level. A marker
                    // remains a large localized color discontinuity; geometry stays exact.
                    assertEquals(reference ushr 24, actual ushr 24)
                    for (shift in listOf(0, 8, 16)) assertTrue("No bottom decoration for " + label,
                        kotlin.math.abs(((reference ushr shift) and 255) - ((actual ushr shift) and 255)) <= 2)
                }
            }
        }
    }

    @Test fun libraryActionsKeepRealIdsAndGlobalSelectionDoesNotSelectRetainedLibrary() {
        render()
        for ((label, index) in listOf("Movies" to 0, "Shows" to 2)) {
            compose.onNodeWithText(label).requestFocus()
            key(Key.DirectionCenter)
            compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionCenter) }
            compose.runOnIdle {
                assertEquals(libraries[index].itemId, (destination as Destination.MediaItem).itemId)
                assertTrue((destination as Destination.MediaItem).browseGrid)
            }
            compose.onNodeWithText(label).assertIsSelected()
        }
        compose.onNodeWithText("Home Movies").requestFocus()
        key(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(libraries[5].itemId, (destination as Destination.MediaItem).itemId) }
        compose.onNodeWithText("Home Movies").assertIsSelected()
        search().requestFocus(); key(Key.DirectionCenter)
        search().assertIsSelected()
        compose.onNodeWithText("Home Movies").assertIsNotSelected()
        settings().requestFocus(); key(Key.DirectionCenter)
        settings().assertIsSelected()
        search().assertIsNotSelected()
        home().requestFocus(); key(Key.DirectionCenter)
        home().assertIsSelected()
        settings().assertIsNotSelected()
    }

    private fun assertBoundsFitAsFocused() {
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        for (node in nodes()) {
            node.requestFocus().assertIsFocused().assertIsDisplayed()
            val b = node.fetchSemanticsNode().boundsInRoot
            assertTrue("Control must fit horizontally: " + b, b.left >= viewport.left && b.right <= viewport.right)
            assertTrue("40dp targets/outlines fit the 56dp lane", b.top >= 0f && b.bottom <= 112f)
            assertTrue("Remote target height", b.height >= 79f)
        }
    }

    @Test fun normal1080pHasNoOverlapOrHorizontalOverflow() {
        render()
        assertBoundsFitAsFocused()
        val bounds = nodes().map { it.fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (a, b) -> assertTrue(a.right < b.left) }
        assertEquals(1920f, compose.onRoot().fetchSemanticsNode().boundsInRoot.width, .1f)
        capture("1080p-dark-home-selected-settings-focused")
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun reduced720pKeepsEveryControlReachableWithoutClipping() {
        render()
        assertBoundsFitAsFocused()
        assertEquals(1280f, compose.onRoot().fetchSemanticsNode().boundsInRoot.width, .1f)
        capture("720p-settings-focused")
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun reducedViewportAndLargeTextCanTraverseToSearchSettingsAndBack() {
        render(fontScale = 1.4f)
        nodes().first().requestFocus()
        for (node in nodes().drop(1)) { key(Key.DirectionRight); node.assertIsFocused().assertIsDisplayed() }
        for (node in nodes().dropLast(1).reversed()) { key(Key.DirectionLeft); node.assertIsFocused().assertIsDisplayed() }
        assertTrue(destination is Destination.Home)
        capture("720p-large-text-profile-focused")
    }

    @Test fun longLabelsStaySingleLineAndRetainFullAccessibleText() {
        val long = listOf("Films et collections récemment consultés", "Séries et programmes documentaires",
            "Télévision et enregistrements", "Vidéos personnelles et souvenirs")
        compose.setContent {
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                Column(Modifier.fillMaxSize()) {
                    FloatingNavigationRow({}) {
                        long.forEach { label ->
                            BrowseDropdown(label, listOf("Choice"), -1, {})
                        }
                    }
                }
            }
        }
        long.forEach { label ->
            val node = compose.onNodeWithText(label)
            node.requestFocus().assertIsFocused().assertIsDisplayed()
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
            assertTrue(results.isNotEmpty())
            assertEquals(1, results.last().lineCount)
        }
        capture("long-labels")
    }

    @Test fun ivoryTextMaintainsContrastOnBrightBackdropAndFocusedFill() {
        val ivory = LunocoUiColors.NavigationIvory.toArgb()
        val surface = androidx.core.graphics.ColorUtils.compositeColors(
            LunocoUiColors.NavigationSurface.toArgb(), Color.White.toArgb())
        listOf(surface, LunocoUiColors.NavigationSelected.toArgb(),
            LunocoUiColors.NavigationFocused.toArgb()).forEach {
            assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(ivory, it) >= 4.5)
        }
    }
}

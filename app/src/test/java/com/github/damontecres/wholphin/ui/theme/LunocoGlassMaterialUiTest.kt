package com.github.damontecres.wholphin.ui.theme

import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.main.SpotlightButton
import com.github.damontecres.wholphin.ui.nav.BrowseButton
import com.github.damontecres.wholphin.ui.theme.colors.JellyLuThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors
import dagger.hilt.android.testing.*
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

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class LunocoGlassMaterialUiTest {
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
    private var strength by mutableStateOf(LunocoGlassVariant.Balanced)
    private var selected by mutableStateOf(false)
    private var treatment by mutableStateOf(LunocoGlassTreatment.NeutralClear)
    private var neutralBackdrop by mutableStateOf<Color?>(null)

    private fun render(art: Glass31Artwork) {
        val image = glass31Artwork(art).asImageBitmap()
        compose.setContent {
            CompositionLocalProvider(
                LocalLunocoGlassVariant provides strength,
                LocalLunocoGlassTreatment provides treatment,
                LocalLunocoGlassBackdrop provides remember { LunocoGlassScene() },
            ) {
                MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        Box(Modifier.fillMaxWidth().height(286.dp)) {
                            LunocoGlassArtworkSource(true) {
                                if (neutralBackdrop != null) Box(Modifier.fillMaxSize().background(checkNotNull(neutralBackdrop)))
                                else Image(image, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
                            }
                        }
                        Row(Modifier.padding(40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BrowseButton({}, Modifier.width(120.dp), selected = selected) { Text("Dark Glass") }
                            SpotlightButton({}, Modifier.width(88.dp), primary = true) { Text("Play") }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bitmap() = compose.onRoot().captureToImage().asAndroidBitmap()
    private fun save(name: String) {
        val b = bitmap()
        val file = File("build/reports/adaptive-pills36b/material-" + name + "-api" + android.os.Build.VERSION.SDK_INT + ".png")
        checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use { assertTrue(b.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble(); val y = b.luminance().toDouble()
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }


    @Test fun adaptive36bAllActualNavigationCentersOccludeWhiteGlyphsButGapsStayOpen() {
        val scene = LunocoGlassScene().apply { available = true }
        val scrolled = mutableStateOf(true)
        var bright by mutableStateOf(false)
        var glyphs by mutableStateOf(false)
        val libraries = listOf("Movies" to org.jellyfin.sdk.model.api.CollectionType.MOVIES,
            "Shows" to org.jellyfin.sdk.model.api.CollectionType.TVSHOWS,
            "Live TV" to org.jellyfin.sdk.model.api.CollectionType.LIVETV,
            "Home Movies" to org.jellyfin.sdk.model.api.CollectionType.HOMEVIDEOS).mapIndexed { i, (name, type) ->
            val id = java.util.UUID(36, i.toLong())
            com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem(id, name,
                com.github.damontecres.wholphin.ui.nav.Destination.MediaItem(id,
                    org.jellyfin.sdk.model.api.BaseItemKind.COLLECTION_FOLDER), type)
        }
        compose.setContent {
            CompositionLocalProvider(LocalLunocoGlassBackdrop provides scene) {
                com.github.damontecres.wholphin.ui.main.BindHomeNavigationScroll(scrolled)
                MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                    Box(Modifier.fillMaxSize().background(if (bright) Color.White else Color.Black)) {
                        if (glyphs) Text(("WHITE SPOTLIGHT GLYPHS ").repeat(50), color = Color.White,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(top = 12.dp))
                        com.github.damontecres.wholphin.ui.nav.LunocoTopNavigation(
                            com.github.damontecres.wholphin.ui.nav.Destination.Home(),
                            com.github.damontecres.wholphin.ui.nav.lunocoBrowseGroups(
                                com.github.damontecres.wholphin.ui.nav.jellyLuBrowseLinks(libraries)),
                            null, remember { androidx.compose.ui.focus.FocusRequester() },
                            {}, {}, {}, {}, {}, {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val home = compose.onNodeWithContentDescription("Home")
        val settings = compose.onNodeWithContentDescription("Settings")
        home.requestFocus()
        val nodes = listOf(compose.onNodeWithContentDescription("Lunoco"), home,
            compose.onNodeWithText("Movies"), compose.onNodeWithText("Shows"),
            compose.onNodeWithText("Live TV"), compose.onNodeWithText("Home Movies"),
            compose.onNodeWithContentDescription("Search"), settings)
        fun centers(): List<IntArray> {
            compose.waitForIdle()
            val image = bitmap()
            return nodes.map { node ->
                val r = node.fetchSemanticsNode().boundsInRoot
                val inset = with(compose.density) { 12.dp.roundToPx() }
                val w = r.width.toInt() - 2 * inset; val h = r.height.toInt() - 2 * inset
                assertTrue(w > 0 && h > 0)
                IntArray(w * h).also { image.getPixels(it, 0, w, r.left.toInt() + inset, r.top.toInt() + inset, w, h) }
            }
        }
        for (focus in listOf(home, settings)) {
            focus.requestFocus(); compose.waitForIdle()
            compose.runOnIdle { bright = false; glyphs = false }
            val black = centers()
            compose.runOnIdle { glyphs = true }
            val letters = centers()
            save("adaptive36b-" + (if (focus === home) "home" else "settings") + "-dark-glyphs")
            compose.runOnIdle { bright = true; glyphs = false }
            val white = centers()
            save("adaptive36b-" + (if (focus === home) "home" else "settings") + "-dark-white")
            for (i in nodes.indices) {
                assertArrayEquals("Actual control $i blocks white underlying glyphs", black[i], letters[i])
                assertArrayEquals("Actual control $i blocks white underlying artwork", black[i], white[i])
            }
            assertEquals(Color.White.toArgb(), bitmap().getPixel(1450, 1))
            home.assertIsSelected()
            focus.assertIsFocused()
        }
        compose.runOnIdle { scrolled.value = false }
        val clear = centers()
        compose.runOnIdle { bright = false }
        val clearBlack = centers()
        save("adaptive36b-clear-return")
        assertTrue("Top mode still transmits artwork", clear.indices.all { !clear[it].contentEquals(clearBlack[it]) })
        assertTrue("95% source-over would still transmit visible white-glyph energy", (255f * (1f - .95f)) > 12f)
        for (color in listOf(LunocoUiColors.GlassCharcoal, LunocoUiColors.NavigationSelected, LunocoUiColors.NavigationFocused))
            assertTrue("Opaque state retains readable ivory contrast", contrast(LunocoUiColors.NavigationIvory, color) >= 4.5)
    }

    @Test fun adaptive36bRegistrationReleasesAndCannotClearANewerHomeOwner() {
        val scene = LunocoGlassScene()
        val first = mutableStateOf(true)
        val replacement = mutableStateOf(false)
        var bind by mutableStateOf(true)
        var home by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLunocoGlassBackdrop provides scene.takeIf { home }) {
                if (bind) com.github.damontecres.wholphin.ui.main.BindHomeNavigationScroll(first)
                BrowseButton({}, Modifier.width(120.dp)) { Text("Movies") }
            }
        }
        compose.waitForIdle()
        assertSame(first, scene.navigationScrolled)
        compose.runOnIdle { scene.navigationScrolled = replacement; bind = false }
        compose.waitForIdle()
        assertSame("Old owner disposal must preserve a newer registration", replacement, scene.navigationScrolled)
        compose.runOnIdle { bind = true }
        compose.waitForIdle()
        assertSame(first, scene.navigationScrolled)
        compose.runOnIdle { home = false }
        compose.waitForIdle()
        assertNull("Non-Home cannot retain the Home scroll registration", scene.navigationScrolled)
    }


    @Test fun balancedIsTheProductionDefaultWithoutAProvider() {
        var actual: LunocoGlassVariant? = null
        var profile: LunocoGlassTreatment? = null
        compose.setContent {
            val value = LocalLunocoGlassVariant.current
            val materialProfile = LocalLunocoGlassTreatment.current
            SideEffect { actual = value; profile = materialProfile }
        }
        compose.waitForIdle()
        assertEquals(LunocoGlassVariant.Balanced, actual)
        assertEquals(LunocoGlassTreatment.NeutralClear, profile)
    }

    @Test fun selectedAndFocusedLabelsStayReadableOverWhiteArtwork() {
        render(Glass31Artwork.White)
        val text = compose.onNodeWithText("Dark Glass", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        val space = layouts.single().getBoundingBox(4).center
        for (profile in LunocoGlassTreatment.entries) for (v in LunocoGlassVariant.entries) for (focus in listOf(false, true)) {
            compose.runOnIdle { treatment = profile; strength = v; selected = true }
            if (focus) compose.onNodeWithText("Dark Glass").requestFocus()
            else compose.onNodeWithText("Play").requestFocus()
            compose.waitForIdle()
            val bounds = text.fetchSemanticsNode().boundsInRoot
            val bg = Color(bitmap().getPixel((bounds.left + space.x).toInt(), (bounds.top + space.y).toInt()))
            val ratio = contrast(LunocoUiColors.NavigationIvory, bg)
            println("NEUTRAL35_STATE_CONTRAST_" + profile + "_" + v + "_focus" + focus + "=" + ratio)
            assertTrue("Selected/focused ivory text must retain contrast: " + v + ", focus=" + focus + ": " + ratio, ratio >= 4.5)
            assertTrue(compose.onNodeWithText("Dark Glass").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Selected])
        }
    }

    @Test fun clearStrengthsRetainReadableDarkLabelsOnWhiteInput() {
        render(Glass31Artwork.White)
        compose.onNodeWithText("Play").requestFocus()
        for (profile in LunocoGlassTreatment.entries) for (s in LunocoGlassVariant.entries) {
            compose.runOnIdle { treatment = profile; strength = s; selected = false }
            compose.waitForIdle()
            val text = compose.onNodeWithText("Dark Glass", useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
            val space = layouts.single().getBoundingBox(4).center
            val bounds = text.fetchSemanticsNode().boundsInRoot
            val b = bitmap()
            val bg = Color(b.getPixel((bounds.left + space.x).toInt(), (bounds.top + space.y).toInt()))
            val ratio = contrast(LunocoUiColors.NavigationIvory, bg)
            println("NEUTRAL35_WHITE_CONTRAST_" + profile + "_" + s + "=" + ratio + ";pixel=" + bg)
            save("white-" + profile + "-" + s)
            assertTrue("Ivory text over white input for " + s + ": " + ratio, ratio >= 4.5)
        }
    }

    @Test fun ivoryPlayRetainsDarkTextContrastOnBlackInput() {
        render(Glass31Artwork.Black)
        for (profile in LunocoGlassTreatment.entries) for (s in LunocoGlassVariant.entries) {
            compose.runOnIdle { treatment = profile; strength = s }
            compose.onNodeWithText("Dark Glass").requestFocus()
            val bounds = compose.onNodeWithText("Play").fetchSemanticsNode().boundsInRoot
            val b = bitmap()
            val bg = Color(b.getPixel(bounds.center.x.toInt(), bounds.bottom.toInt() - 10))
            val ratio = contrast(LunocoUiColors.GlassPrimaryContent, bg)
            println("NEUTRAL35_IVORY_CONTRAST_" + profile + "_" + s + "=" + ratio)
            assertTrue("Dark Play text on " + s + ": " + ratio, ratio >= 4.5)
        }
    }

    @Test @Config(sdk = [28])
    fun preRuntimeShaderAndroidUsesNativeDrawingWithoutAnEffectBackend() {
        detailedArtworkRemainsVisibleInsideTheActualMaterial()
    }

    @Test fun detailedArtworkRemainsVisibleInsideTheActualMaterial() {
        render(Glass31Artwork.HighDetail)
        compose.onNodeWithText("Play").requestFocus()
        val bounds = compose.onNodeWithText("Dark Glass").fetchSemanticsNode().boundsInRoot
        val geometry = compose.onNodeWithText("Play").fetchSemanticsNode().boundsInRoot
        for (profile in LunocoGlassTreatment.entries) for (s in LunocoGlassVariant.entries) {
            compose.runOnIdle { treatment = profile; strength = s }
            compose.waitForIdle()
            val b = bitmap()
            val samples = (-70..70 step 2).map { dx ->
                Color(b.getPixel(bounds.center.x.toInt() + dx, bounds.bottom.toInt() - 12)).luminance()
            }
            val range = checkNotNull(samples.maxOrNull()) - checkNotNull(samples.minOrNull())
            println("NEUTRAL35_INTERIOR_TEXTURE_" + profile + "_" + s + "=" + range)
            assertTrue("Artwork variation must survive the material center: " + s, range > .012f)
            compose.onNodeWithText("Play").assertIsFocused()
            assertEquals(geometry, compose.onNodeWithText("Play").fetchSemanticsNode().boundsInRoot)
            save("detail-" + profile + "-" + s)
        }
    }
    @Test fun neutralBodiesDoNotAddBlueOnNeutralSourcesAndKeepDistinctFocus() {
        render(Glass31Artwork.White)
        val node = compose.onNodeWithText("Dark Glass", useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        val space = layouts.single().getBoundingBox(4).center
        for (profile in LunocoGlassTreatment.entries) {
            for ((name, source) in listOf("white" to Color.White, "light-gray" to Color(.8f,.8f,.8f),
                "middle-gray" to Color(.5f,.5f,.5f), "black" to Color.Black)) {
                val pixels = mutableListOf<Int>()
                for (state in listOf("normal", "selected", "focused")) {
                    compose.runOnIdle { treatment = profile; strength = LunocoGlassVariant.Balanced
                        neutralBackdrop = source; selected = state != "normal" }
                    if (state == "focused") compose.onNodeWithText("Dark Glass").requestFocus()
                    else compose.onNodeWithText("Play").requestFocus()
                    compose.waitForIdle()
                    val bounds = node.fetchSemanticsNode().boundsInRoot
                    val pixel = bitmap().getPixel((bounds.left+space.x).toInt(), (bounds.top+space.y).toInt())
                    val channels = listOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel),
                        android.graphics.Color.blue(pixel))
                    assertTrue("No material color cast on neutral input: $profile/$name/$state/$channels",
                        channels.max() - channels.min() <= 2)
                    pixels += pixel
                    println("NEUTRAL35_RGB_$profile/$name/$state=$channels")
                    save("neutral-$profile-$name-$state")
                }
                assertNotEquals("Selection remains a visible material change", pixels[0], pixels[1])
                val focusBounds=compose.onNodeWithText("Dark Glass").fetchSemanticsNode().boundsInRoot
                val focusedImage=bitmap()
                assertTrue("Focus remains distinct through the immediate neutral outline",
                    (0..6).any { focusedImage.getPixel(focusBounds.left.toInt()+it,focusBounds.center.y.toInt()) ==
                        LunocoUiColors.NavigationFocusOutline.toArgb() })
            }
        }
    }

    @Test fun neutralCheckerTransmissionAndUpperReflectionRemainVisible() {
        val bitmap = Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        for (y in 0 until 360 step 12) for (x in 0 until 640 step 12) {
            paint.color = if ((x/12+y/12)%2==0) 0xFFE0E0E0.toInt() else 0xFF404040.toInt()
            canvas.drawRect(x.toFloat(),y.toFloat(),(x+12).toFloat(),(y+12).toFloat(),paint)
        }
        val image = bitmap.asImageBitmap()
        compose.setContent {
            CompositionLocalProvider(LocalLunocoGlassTreatment provides treatment,
                LocalLunocoGlassBackdrop provides remember { LunocoGlassScene() }) {
                MaterialTheme(colorScheme=JellyLuThemeColors.darkScheme) {
                    Box(Modifier.fillMaxSize()) {
                        LunocoGlassArtworkSource(true) { Image(image,null,Modifier.fillMaxSize(),
                            contentScale=androidx.compose.ui.layout.ContentScale.FillBounds) }
                        BrowseButton({},Modifier.padding(40.dp).width(120.dp)) { Text("Dark Glass") }
                    }
                }
            }
        }
        val bounds = compose.onNodeWithText("Dark Glass").fetchSemanticsNode().boundsInRoot
        for (profile in LunocoGlassTreatment.entries) {
            compose.runOnIdle { treatment=profile }
            compose.waitForIdle()
            val b=bitmap()
            val samples=(-70..70 step 2).map { dx ->
                val p=b.getPixel(bounds.center.x.toInt()+dx,bounds.bottom.toInt()-12)
                val ch=listOf(android.graphics.Color.red(p),android.graphics.Color.green(p),android.graphics.Color.blue(p))
                assertTrue(ch.max()-ch.min()<=2)
                Color(p).luminance()
            }
            val range=samples.max()-samples.min()
            println("NEUTRAL35_CHECKER_$profile=$range")
            assertTrue("Neutral checker remains transmitted",range>.012f)
            save("checker-$profile")
        }
    }

}
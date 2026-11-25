package com.github.damontecres.wholphin.ui.main

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors
import dagger.hilt.android.testing.*
import io.mockk.*
import org.jellyfin.sdk.model.api.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class HomeResumeTrackUiTest {
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
    @Before fun setUp() { hilt.inject() }

    @Test fun paintedTrackMatchesPreviousOverlayInLtr() = compareWithPrevious(LayoutDirection.Ltr)
    @Test fun paintedTrackMatchesPreviousOverlayInRtl() = compareWithPrevious(LayoutDirection.Rtl)

    private fun compareWithPrevious(direction: LayoutDirection) {
        var progress by mutableStateOf<Float?>(.5f)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Row {
                    listOf(false, true).forEach { optimized ->
                        val fraction = progress?.takeIf { it > 0f && it < 1f }
                        Box(Modifier.size(96.dp, 48.dp).testTag(if (optimized) "new" else "old")) {
                            Image(ColorPainter(Color.Red), null,
                                modifier = Modifier.fillMaxSize().then(
                                    if (optimized) Modifier.homeResumeProgressTrack(fraction != null) else Modifier))
                            if (fraction != null) {
                                // Original overlay is the pixel/semantics reference, not a second production path.
                                if (!optimized) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                                    .background(LunocoUiColors.ProgressTrack))
                                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(fraction).height(3.dp)
                                    .background(Color.Cyan)
                                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) })
                            }
                        }
                    }
                }
            }
        }
        // Includes adding/removing the draw modifier and endpoint/invalid-value handling.
        listOf<Float?>(.5f, 1f / 3f, .99f, 0f, 1f, Float.NaN, null, .25f).forEach { value ->
            compose.runOnIdle { progress = value }
            val old = compose.onNodeWithTag("old").captureToImage().toPixelMap()
            val new = compose.onNodeWithTag("new").captureToImage().toPixelMap()
            assertEquals(old.width, new.width)
            assertEquals(old.height, new.height)
            for (y in 0 until old.height) for (x in 0 until old.width) {
                assertEquals("Pixel ($x,$y), $direction, progress=$value", old[x, y].toArgb(), new[x, y].toArgb())
            }
            val fraction = value?.takeIf { it > 0f && it < 1f }
            if (fraction == null) {
                compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                    useUnmergedTree = true).assertCountEquals(0)
            } else {
                val nodes = compose.onAllNodes(SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(fraction, 0f..1f)),
                    useUnmergedTree = true).fetchSemanticsNodes()
                assertEquals(2, nodes.size)
                assertEquals(nodes[0].boundsInRoot.width, nodes[1].boundsInRoot.width, 0f)
                assertEquals(nodes[0].boundsInRoot.height, nodes[1].boundsInRoot.height, 0f)
            }
        }
    }

    @Test fun realResumeCardKeepsProgressSemanticsAndDpadFocusWithoutActivatingActions() {
        val images = mockk<ImageUrlService>()
        every { images.getItemImageUrl(any<BaseItem>(), any(), any(), any(), any()) } returns null
        val id = UUID(0, 17)
        val dto = BaseItemDto(id = id, name = "Synthetic resume card", type = BaseItemKind.MOVIE,
            runTimeTicks = 1000L, userData = UserItemDataDto(
                playbackPositionTicks = 500L, played = false, playCount = 0, isFavorite = false, key = "fixture", itemId = id))
        // Match the existing Hilt UI fixtures: BaseItem's real constructor needs WholphinApplication.
        val item = mockk<BaseItem> {
            every { data } returns dto
            every { type } returns dto.type
            every { title } returns dto.name
            every { name } returns dto.name
        }
        val focus = FocusRequester()
        var actions = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalImageUrlService provides images) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompactHomeResumeCard(item, 90.dp, 16f / 9f, ImageType.THUMB, ContentScale.Fit,
                            false, { actions++ }, { actions++ }, Modifier.focusRequester(focus).testTag("resume"))
                        Button(onClick = { actions++ }, modifier = Modifier.testTag("next")) { Text("Next") }
                    }
                }
            }
        }
        compose.runOnIdle { focus.requestFocus() }
        compose.onNodeWithTag("resume").assertIsFocused()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(.5f, 0f..1f)), useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("resume").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("next").assertIsFocused()
        compose.onNodeWithTag("next").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("resume").assertIsFocused()
        assertEquals(0, actions)
        verify(exactly = 1) { images.getItemImageUrl(any<BaseItem>(), any(), any(), any(), any()) }
    }
}

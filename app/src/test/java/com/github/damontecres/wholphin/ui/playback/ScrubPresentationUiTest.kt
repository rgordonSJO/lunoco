package com.github.damontecres.wholphin.ui.playback

import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.Gravity
import android.view.KeyEvent as NativeKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import com.github.damontecres.wholphin.test.TestActivity
import com.github.damontecres.wholphin.ui.playback.overlay.DpadSeekOverlay
import com.github.damontecres.wholphin.ui.theme.LunocoContentTheme
import com.github.damontecres.wholphin.ui.theme.colors.JellyLuThemeColors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.jellyfin.sdk.model.api.TrickplayInfo
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
import java.util.concurrent.atomic.AtomicInteger

@HiltAndroidTest
@Config(application = HiltTestApplication::class, sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, coil3.annotation.DelicateCoilApi::class)
class ScrubPresentationUiTest {
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

    private var media by mutableIntStateOf(0)
    private var menu by mutableStateOf(false)
    private var buffering by mutableStateOf(false)
    private var holdClockOverride: Long? = null
    private fun holdClock(): Long = holdClockOverride ?: compose.mainClock.currentTime

    private var position = 0L
    private var playing = false
    private lateinit var controller: InstantDpadScrub
    private lateinit var presentation: ScrubPresentation
    private var loader: ImageLoader? = null
    private var previousLoader: ImageLoader? = null
    private val player = mockk<Player>(relaxed = true) {
        every { playbackState } returns Player.STATE_READY
        every { playerError } returns null
        every { isCurrentMediaItemLive } returns false
        every { duration } returns 300_000L
        every { currentPosition } answers { position }
        every { playWhenReady } answers { playing }
        every { isCurrentMediaItemSeekable } returns true
        every { isCommandAvailable(any()) } returns true
        every { seekTo(any<Long>()) } answers { position = firstArg() }
        every { play() } answers { playing = true }
    }

    @Before fun setup() { hilt.inject() }
    @After fun cleanup() {
        previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
        loader?.shutdown()
    }

    private fun render(info: TrickplayInfo? = null, sheetUrl: ((Int) -> String)? = null) {
        compose.setContent {
            val scrub = remember(media) { InstantDpadScrub(10_000, 10_000) }
            val display = remember(media) { ScrubPresentation { compose.mainClock.currentTime } }
            val mediaKey = media
            val resolver: (Int) -> String? = remember(mediaKey, sheetUrl) {
                { index -> sheetUrl?.invoke(index) }
            }
            val frame = display.frame(scrub.preview, ScrubOwner.VIDEO.takeUnless { menu })
            SideEffect { controller = scrub; presentation = display }
            TrackScrubPresentation(display) { buffering }
            TrackInstantDpadHold(scrub, ScrubOwner.VIDEO.takeUnless { menu }, player, ::holdClock)
            LaunchedEffect(menu) { if (menu) { scrub.cancel(); display.clear() } }
            MaterialTheme(colorScheme = JellyLuThemeColors.darkScheme) {
                LunocoContentTheme {
                    Box(Modifier.fillMaxSize().background(Color(0xFF272727))) {
                        if (frame != null) DpadSeekOverlay(
                            player, frame.targetMs, info, resolver,
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).fillMaxWidth(.95f)
                                .graphicsLayer { alpha = display.alpha },
                            frame.direction,
                        )
                    }
                    if (menu) StreamChoiceBottomDialog(
                        choices = listOf(SimpleMediaStream(0, "English fixture", "Stereo")),
                        onDismissRequest = { menu = false },
                        onSelectChoice = { _, _ -> menu = false },
                        gravity = Gravity.END,
                        currentChoice = 0,
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }

    private fun send(action: Int, down: Long, key: Int = NativeKey.KEYCODE_DPAD_RIGHT, repeat: Int = 0) {
        compose.runOnIdle {
            val event = NativeKey(down, holdClock(), action, key, repeat, 0, 8, 0, 0)
            presentation.onKeyEvent(controller, KeyEvent(event), ScrubOwner.VIDEO.takeUnless { menu }, player) {
                throw AssertionError(it)
            }
        }
        step(48)
    }

    private fun step(ms: Long) {
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun display(target: Long, label: String = "Forward") {
        assertEquals(target, compose.runOnIdle { presentation.frame(controller.preview, ScrubOwner.VIDEO)?.targetMs })
        compose.onNodeWithText("$label ·", substring = true).assertIsDisplayed()
    }

    private fun hidden() {
        assertNull(compose.runOnIdle { presentation.frame(controller.preview, ScrubOwner.VIDEO.takeUnless { menu }) })
        compose.onNodeWithText("Forward ·", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Back ·", substring = true).assertDoesNotExist()
    }

    private fun capture(name: String, dialog: Boolean = false) {
        val node = if (dialog) compose.onNode(isDialog(), useUnmergedTree = true) else compose.onRoot()
        val bitmap = node.captureToImage().asAndroidBitmap()
        val path = File("build/reports/trickplay-speed38c/$name.png")
        checkNotNull(path.parentFile).mkdirs()
        path.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun quickTapRetainsFinalFrameAndFadesThroughTheProductionOverlay() {
        render()
        capture("01-before-1080p")
        send(NativeKey.ACTION_DOWN, 100)
        display(10_000)
        compose.onNodeWithContentDescription(compose.activity.getString(com.github.damontecres.wholphin.R.string.lunoco_trickplay_unavailable)).assertIsDisplayed()
        capture("02-first-key-down-1080p")
        verify(exactly = 0) { player.seekTo(any<Long>()) }
        send(NativeKey.ACTION_UP, 100)
        verify(exactly = 1) { player.seekTo(10_000L) }
        verify(exactly = 1) { player.play() }
        display(10_000)
        capture("05-release-1080p")
        val at = compose.runOnIdle { checkNotNull(presentation.release).atMs }
        for (elapsed in listOf(500L, 1_500L, 2_400L)) {
            step(at + elapsed - compose.mainClock.currentTime)
            display(10_000)
            assertEquals(1f, compose.runOnIdle { presentation.alpha }, 0f)
            capture("settled-$elapsed-ms-1080p")
        }
        step(at + 2_610L - compose.mainClock.currentTime)
        assertTrue(compose.runOnIdle { presentation.alpha in 0.01f..0.99f })
        display(10_000)
        capture("09-fade-1080p")
        step(240)
        hidden()
        capture("10-hidden-1080p")
        verify(exactly = 1) { player.seekTo(any<Long>()) }
        verify(exactly = 1) { player.play() }
    }

    @Test fun rapidTapsAndPressDuringFadeKeepOneOverlay() {
        render()
        repeat(5) { i ->
            val down = 100L + i * 500L
            send(NativeKey.ACTION_DOWN, down)
            display((i + 1) * 10_000L)
            if (i == 1 || i == 4) capture("rapid-tap-${i + 1}-1080p")
            send(NativeKey.ACTION_UP, down)
            step(if (i == 3) 2_350 else 200)
            display((i + 1) * 10_000L)
        }
        val at = compose.runOnIdle { checkNotNull(presentation.release).atMs }
        step(at + 2_610L - compose.mainClock.currentTime)
        assertTrue(compose.runOnIdle { presentation.alpha < 1f })
        send(NativeKey.ACTION_DOWN, 10_000, NativeKey.KEYCODE_DPAD_LEFT)
        display(40_000, "Back")
        assertEquals(1f, compose.runOnIdle { presentation.alpha }, 0f)
        capture("13-left-during-fade-1080p")
        step(600)
        // The first two 15-second hold ticks now move the same uninterrupted preview.
        display(10_000, "Back")
        send(NativeKey.ACTION_UP, 10_000, NativeKey.KEYCODE_DPAD_LEFT)
        display(10_000, "Back")
        verify(exactly = 2) { player.seekTo(10_000L) }
        verify(exactly = 6) { player.seekTo(any<Long>()) }
        verify(exactly = 1) { player.play() }
    }

    @Test fun holdsRepeatWithoutSeekAndBackCancels() {
        render()
        send(NativeKey.ACTION_DOWN, 100)
        repeat(12) { i ->
            step(300)
            send(NativeKey.ACTION_DOWN, 100, repeat = i + 1)
            compose.onNodeWithText("Forward ·", substring = true).assertIsDisplayed()
        }
        capture("03-right-hold-1080p")
        verify(exactly = 0) { player.seekTo(any<Long>()) }
        send(NativeKey.ACTION_DOWN, 4_000, NativeKey.KEYCODE_DPAD_LEFT)
        compose.onNodeWithText("Back ·", substring = true).assertIsDisplayed()
        send(NativeKey.ACTION_UP, 100)
        send(NativeKey.ACTION_DOWN, 4_100, NativeKey.KEYCODE_BACK)
        hidden()
        send(NativeKey.ACTION_UP, 4_000, NativeKey.KEYCODE_DPAD_LEFT)
        verify(exactly = 0) { player.seekTo(any<Long>()) }
        verify(exactly = 0) { player.play() }
    }

    @Test fun bufferingIsBoundedAndAudioAndMediaReplacementClearThePreview() {
        render()
        compose.runOnIdle { buffering = true }
        send(NativeKey.ACTION_DOWN, 100)
        send(NativeKey.ACTION_UP, 100)
        step(4_800)
        display(10_000)
        step(600)
        hidden()
        verify(exactly = 1) { player.seekTo(any<Long>()) }

        send(NativeKey.ACTION_DOWN, 6_000)
        send(NativeKey.ACTION_UP, 6_000)
        display(20_000)
        compose.runOnIdle { menu = true }
        step(70)
        hidden()
        compose.onNodeWithText("English fixture").assertIsDisplayed()
        capture("16-audio-menu-1080p", dialog = true)
        compose.runOnIdle { menu = false }
        step(70)

        send(NativeKey.ACTION_DOWN, 8_000)
        send(NativeKey.ACTION_UP, 8_000)
        display(30_000)
        compose.runOnIdle { media++ }
        step(70)
        hidden()
        step(6_000)
        verify(exactly = 3) { player.seekTo(any<Long>()) }
    }

    @Test fun acceleratedOverlayStaysVisibleAt1080p() = acceleratedOverlay("1080p")

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun acceleratedOverlayStaysVisibleAt720p() = acceleratedOverlay("720p")

    private fun acceleratedOverlay(size: String) {
        every { player.duration } returns 10_000_000L
        render()
        val startedAt = compose.mainClock.currentTime
        send(NativeKey.ACTION_DOWN, 100)
        display(10_000)
        capture("accelerated-press-$size")
        val checkpoints = linkedMapOf(500L to 25_000L, 1_000L to 70_000L, 2_000L to 235_000L,
            3_000L to 505_000L, 4_000L to 925_000L)
        checkpoints.forEach { (elapsed, position) ->
            step(startedAt + elapsed + 16 - compose.mainClock.currentTime)
            display(position)
            capture("accelerated-$elapsed-ms-$size")
            if (elapsed == 2_000L) send(NativeKey.ACTION_DOWN, 100, repeat = 1)
        }
        verify(exactly = 0) { player.seekTo(any<Long>()) }
        send(NativeKey.ACTION_UP, 100)
        verify(exactly = 1) { player.seekTo(925_000L) }
        verify(exactly = 1) { player.play() }
        display(925_000)
        capture("accelerated-release-$size")
        val releasedAt = compose.runOnIdle { checkNotNull(presentation.release).atMs }
        for (elapsed in listOf(500L, 2_400L, 2_610L)) {
            step(releasedAt + elapsed - compose.mainClock.currentTime)
            display(925_000)
            capture("accelerated-after-release-$elapsed-ms-$size")
        }
        step(240)
        hidden()
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun slowSheetKeepsLastTileAndChangesTheTargetImmediatelyAt720p() {
        val requestCount = AtomicInteger()
        val nextSheet = CompletableDeferred<Unit>()
        val yellow = android.graphics.Color.YELLOW
        val cyan = android.graphics.Color.CYAN
        val files = listOf(yellow, cyan).mapIndexed { index, color ->
            File(compose.activity.cacheDir, "phase38b-sheet-$index.png").apply {
                val bitmap = Bitmap.createBitmap(128, 36, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
        }
        previousLoader = SingletonImageLoader.get(compose.activity)
        loader = ImageLoader.Builder(compose.activity).components {
            add(Interceptor { chain ->
                requestCount.incrementAndGet()
                if (chain.request.data.toString().endsWith("phase38b-sheet-1.png")) nextSheet.await()
                chain.proceed()
            })
        }.build().also { SingletonImageLoader.setUnsafe(it) }
        val info = mockk<TrickplayInfo> {
            every { interval } returns 10_000
            every { width } returns 64
            every { height } returns 36
            every { tileWidth } returns 2
            every { tileHeight } returns 1
            every { thumbnailCount } returns 10
        }
        render(info) { index -> android.net.Uri.fromFile(files[index]).toString() }
        fun hasColor(color: Int): Boolean {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            assertEquals(1280, bitmap.width)
            assertEquals(720, bitmap.height)
            for (y in 380 until bitmap.height step 8) for (x in 0 until 330 step 8) {
                if (bitmap.getPixel(x, y) == color) return true
            }
            return false
        }
        holdClockOverride = 0L
        send(NativeKey.ACTION_DOWN, 100)
        compose.mainClock.autoAdvance = true
        try {
            compose.waitUntil(8_000) { hasColor(yellow) }
        } catch (error: ComposeTimeoutException) {
            capture("debug-first-sheet-720p")
            throw AssertionError("Expected the first local sheet. Coil requests: ${requestCount.get()}", error)
        }
        compose.mainClock.autoAdvance = false
        assertEquals(1, requestCount.get())
        holdClockOverride = 500L
        step(520)
        display(25_000)
        compose.waitUntil(8_000) { requestCount.get() == 2 }
        step(70)
        assertTrue(hasColor(yellow))
        compose.onNodeWithText("Previous frame · loading").assertIsDisplayed()
        capture("15-loading-previous-frame-720p")
        nextSheet.complete(Unit)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(8_000) { hasColor(cyan) }
        compose.mainClock.autoAdvance = false
        display(25_000)
        capture("04-new-sheet-loaded-720p")
        assertEquals(2, requestCount.get())
        send(NativeKey.ACTION_UP, 100)
        verify(exactly = 1) { player.seekTo(25_000L) }
    }
}

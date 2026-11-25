package com.github.damontecres.wholphin.ui.playback

import android.view.KeyEvent as NativeKey
import androidx.compose.ui.input.key.KeyEvent
import androidx.media3.common.Player
import com.github.damontecres.wholphin.ui.playback.overlay.TrickplayFrame
import com.github.damontecres.wholphin.ui.playback.overlay.trickplayFrame
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstantDpadScrubTest {
    private val player = mockk<Player>(relaxed = true) {
        every { playbackState } returns Player.STATE_READY
        every { playerError } returns null
        every { playWhenReady } returns false
        every { isCurrentMediaItemLive } returns false
        every { duration } returns 300_000L
        every { currentPosition } returns 50_123L
        every { isCurrentMediaItemSeekable } returns true
        every { isCommandAvailable(any()) } returns true
    }
    private val scrub = InstantDpadScrub(10_000, 10_000)
    private val errors = mutableListOf<Throwable>()

    private fun send(
        action: Int = NativeKey.ACTION_DOWN,
        key: Int = NativeKey.KEYCODE_DPAD_RIGHT,
        down: Long = 1_000,
        at: Long = down,
        repeats: Int = 0,
        owner: ScrubOwner? = ScrubOwner.VIDEO,
        flags: Int = 0,
    ): Boolean = scrub.onKeyEvent(
        KeyEvent(NativeKey(down, at, action, key, repeats, 0, 8, 0, flags)),
        owner,
        player,
    ) { errors.add(it) }

    private fun tick(at: Long, owner: ScrubOwner? = ScrubOwner.VIDEO): Long? =
        scrub.activePress?.let { scrub.advanceHold(it, at, owner, player) }

    private fun noPlaybackDispatch() {
        verify(exactly = 0) { player.seekTo(any<Long>()) }
        verify(exactly = 0) { player.play() }
        verify(exactly = 0) { player.pause() }
    }

    @Test fun tapPreviewsBeforeReleaseAndResumesPausedPlayerExactlyOnce() {
        assertEquals(300_000L, player.scrubDuration())
        assertTrue(send())
        assertEquals(60_123L, scrub.preview?.targetMs)
        noPlaybackDispatch()
        assertTrue(send(action = NativeKey.ACTION_UP, at = 1_120))
        assertTrue(send(action = NativeKey.ACTION_UP, at = 1_121))
        assertNull(scrub.preview)
        verify(exactly = 1) { player.seekTo(60_123L) }
        verify(exactly = 1) { player.seekTo(any<Long>()) }
        verify(exactly = 1) { player.play() }
    }

    @Test fun nativeRepeatsDoNotAdvanceTheTargetOrDuplicateTheReleaseCommit() {
        every { player.playWhenReady } returns true
        send()
        assertTrue(send(at = 1_600, repeats = 1))
        assertTrue(send(at = 1_650, repeats = 2))
        assertTrue(send(at = 1_850, repeats = 3))
        assertEquals(60_123L, scrub.preview?.targetMs)
        tick(1_850)
        tick(1_850)
        assertEquals(105_123L, scrub.preview?.targetMs)
        noPlaybackDispatch()
        send(action = NativeKey.ACTION_UP, at = 1_975)
        verify(exactly = 1) { player.seekTo(105_123L) }
        verify(exactly = 1) { player.seekTo(any<Long>()) }
        verify(exactly = 0) { player.play() }
    }

    @Test fun deterministicHoldStepsAndFasterTiers() {
        every { player.duration } returns 10_000_000L
        send()
        val checkpoints = linkedMapOf(
            300L to 60_123L,
            500L to 75_123L,
            800L to 105_123L,
            1_500L to 165_123L,
            2_500L to 375_123L,
            4_000L to 975_123L,
            6_000L to 1_755_123L,
        )
        checkpoints.forEach { (elapsed, target) ->
            val time = 1_000 + elapsed
            // Sparse hardware repeats keep the press alive but do not determine its speed.
            if (elapsed in listOf(1_500L, 2_500L, 4_000L, 6_000L)) send(at = time, repeats = 1)
            tick(time)
            assertEquals("after ${elapsed}ms", target, scrub.preview?.targetMs)
        }
        val press = checkNotNull(scrub.activePress)
        noPlaybackDispatch()
        send(action = NativeKey.ACTION_UP, at = 7_001)
        assertNull(scrub.advanceHold(press, 7_150, ScrubOwner.VIDEO, player))
        verify(exactly = 1) { player.seekTo(1_755_123L) }
        verify(exactly = 1) { player.play() }
    }

    @Test fun ownerChangeCancelsAWorkingHold() {
        send()
        tick(1_500)
        assertNull(tick(1_650, owner = null))
        send(action = NativeKey.ACTION_UP, at = 1_700, owner = null)
        noPlaybackDispatch()
    }

    @Test fun leftTapAndHoldAreSymmetricAndDoNotCrossZero() {
        val left = NativeKey.KEYCODE_DPAD_LEFT
        send(key = left)
        assertEquals(40_123L, scrub.preview?.targetMs)
        tick(1_500)
        assertEquals(25_123L, scrub.preview?.targetMs)
        tick(1_650)
        assertEquals(10_123L, scrub.preview?.targetMs)
        tick(1_800)
        assertEquals(0L, scrub.preview?.targetMs)
        noPlaybackDispatch()
        send(action = NativeKey.ACTION_UP, key = left, at = 1_900)
        verify(exactly = 1) { player.seekTo(0L) }
    }

    @Test fun reversingRetainsThePreviewAnchorAndOldReleaseCannotCommit() {
        send()
        tick(1_500)
        assertEquals(75_123L, scrub.preview?.targetMs)
        send(key = NativeKey.KEYCODE_DPAD_LEFT, down = 1_580)
        assertEquals(65_123L, scrub.preview?.targetMs)
        assertTrue(send(action = NativeKey.ACTION_UP, at = 1_600))
        noPlaybackDispatch()
        send(action = NativeKey.ACTION_UP, key = NativeKey.KEYCODE_DPAD_LEFT, down = 1_580, at = 1_630)
        verify(exactly = 1) { player.seekTo(65_123L) }
        verify(exactly = 1) { player.seekTo(any<Long>()) }
    }

    @Test fun cancellationAndLostReleaseDiscardPreviewAndQuarantineLateKeys() {
        send()
        assertNotNull(tick(3_499))
        assertNull(tick(3_500))
        assertNull(scrub.preview)
        assertTrue(send(at = 3_600, repeats = 1))
        assertTrue(send(action = NativeKey.ACTION_UP, at = 3_650, owner = null))
        noPlaybackDispatch()
        assertTrue(send(down = 3_800))
        assertNotNull(scrub.preview)
    }

    @Test fun timelineOwnsItsKeysButMenusAndMediaButtonsKeepTheirInput() {
        assertFalse(send(owner = null))
        assertFalse(send(action = NativeKey.ACTION_UP, owner = null))
        assertFalse(send(key = NativeKey.KEYCODE_MEDIA_FAST_FORWARD))
        assertFalse(send(key = NativeKey.KEYCODE_DPAD_CENTER))
        assertTrue(send(owner = ScrubOwner.TIMELINE))
        assertEquals(ScrubOwner.TIMELINE, scrub.preview?.owner)
        send(action = NativeKey.ACTION_UP, owner = ScrubOwner.VIDEO)
        noPlaybackDispatch()
    }

    @Test fun physicalBackCancelsWithoutClosingTheTimelineOrCommitting() {
        send(owner = ScrubOwner.TIMELINE)
        assertTrue(send(key = NativeKey.KEYCODE_BACK, down = 1_300, owner = ScrubOwner.TIMELINE))
        assertNull(scrub.preview)
        assertTrue(send(action = NativeKey.ACTION_UP, key = NativeKey.KEYCODE_BACK, down = 1_300, owner = ScrubOwner.TIMELINE))
        assertTrue(send(action = NativeKey.ACTION_UP, at = 1_350, owner = ScrubOwner.TIMELINE))
        assertFalse(send(key = NativeKey.KEYCODE_BACK, down = 1_400, owner = ScrubOwner.TIMELINE))
        noPlaybackDispatch()
    }

    @Test fun cancelledAndroidReleaseNeverSeeks() {
        send()
        send(action = NativeKey.ACTION_UP, flags = NativeKey.FLAG_CANCELED)
        assertNull(scrub.preview)
        noPlaybackDispatch()
    }

    @Test fun liveUnknownUnavailableAndEndedPlayersNeverPreviewOrSeek() {
        every { player.isCurrentMediaItemLive } returns true
        send()
        send(action = NativeKey.ACTION_UP)
        every { player.isCurrentMediaItemLive } returns false
        every { player.duration } returns androidx.media3.common.C.TIME_UNSET
        send(down = 2_000)
        send(action = NativeKey.ACTION_UP, down = 2_000)
        every { player.duration } returns 100_000L
        every { player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) } returns false
        send(down = 3_000)
        send(action = NativeKey.ACTION_UP, down = 3_000)
        every { player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) } returns true
        every { player.playbackState } returns Player.STATE_ENDED
        send(down = 4_000)
        send(action = NativeKey.ACTION_UP, down = 4_000)
        assertNull(scrub.preview)
        noPlaybackDispatch()
    }

    @Test fun shortContentClampsToPlayableTailAndChangingRangeNeverChangesCommitSilently() {
        every { player.duration } returns 10L
        send()
        assertEquals(9L, scrub.preview?.targetMs)
        every { player.duration } returns 7L
        send(action = NativeKey.ACTION_UP)
        noPlaybackDispatch()
        send(down = 2_000)
        assertEquals(6L, scrub.preview?.targetMs)
        send(action = NativeKey.ACTION_UP, down = 2_000)
        verify(exactly = 1) { player.seekTo(6L) }
    }

    @Test fun failedSeekClearsUiAndDoesNotDispatchPlay() {
        every { player.seekTo(any<Long>()) } throws IllegalStateException("synthetic backend failure")
        send()
        send(action = NativeKey.ACTION_UP)
        assertNull(scrub.preview)
        assertEquals(1, errors.size)
        verify(exactly = 0) { player.play() }
    }

    @Test fun thumbnailMappingUsesMatchingSheetAndClampsPartialFinalSheet() {
        val info = mockk<TrickplayInfo> {
            every { interval } returns 10_000
            every { tileWidth } returns 3
            every { tileHeight } returns 2
            every { width } returns 320
            every { height } returns 180
            every { thumbnailCount } returns 8
        }
        assertEquals(TrickplayFrame(0, 2, 1), trickplayFrame(59_999, info))
        assertEquals(TrickplayFrame(1, 0, 0), trickplayFrame(60_000, info))
        assertEquals(TrickplayFrame(1, 1, 0), trickplayFrame(290_000, info))
        every { info.interval } returns 0
        assertNull(trickplayFrame(60_000, info))
    }
}

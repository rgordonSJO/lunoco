package com.github.damontecres.wholphin.ui.playback

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.media3.common.Player
import kotlinx.coroutines.delay

internal enum class ScrubDirection { BACKWARD, FORWARD }
internal enum class ScrubOwner { VIDEO, TIMELINE }

internal data class ScrubPress(val direction: ScrubDirection, val deviceId: Int, val downTime: Long)
internal data class ScrubPreview(val targetMs: Long, val direction: ScrubDirection, val owner: ScrubOwner)

/** A held direction owns preview state only. The matching release may dispatch exactly one seek. */
internal class InstantDpadScrub(
    seekBackMs: Long,
    seekForwardMs: Long,
) {
    private val backward = seekBackMs.coerceIn(1, 300_000)
    private val forward = seekForwardMs.coerceIn(1, 300_000)
    private val captured = mutableMapOf<ScrubDirection, ScrubPress>()
    private class Hold(val press: ScrubPress, val startedAt: Long) {
        var lastInput = startedAt
        var distance = 0L
    }

    private var hold by mutableStateOf<Hold?>(null)
    val activePress: ScrubPress? get() = hold?.press
    private var cancelledBack: Pair<Int, Long>? = null

    var preview by mutableStateOf<ScrubPreview?>(null)
        private set

    fun onKeyEvent(
        event: KeyEvent,
        owner: ScrubOwner?,
        player: Player,
        onCommitted: (ScrubPreview) -> Unit = {},
        onError: (Throwable) -> Unit,
    ): Boolean {
        val native = event.nativeKeyEvent
        if (isBackKey(event)) {
            val back = native.deviceId to native.downTime
            if (event.type == KeyEventType.KeyDown && preview != null) {
                cancelledBack = back
                cancel()
                return true
            }
            if (cancelledBack != back) return false
            if (event.type == KeyEventType.KeyUp) cancelledBack = null
            return true
        }
        val direction = when {
            isDpadLeft(event) -> ScrubDirection.BACKWARD
            isDpadRight(event) -> ScrubDirection.FORWARD
            else -> {
                if (event.type == KeyEventType.KeyDown) cancel()
                return false
            }
        }
        val press = ScrubPress(direction, native.deviceId, native.downTime)
        val duration = owner?.let { player.scrubDuration() }
        return when (event.type) {
            KeyEventType.KeyDown ->
                down(press, native.repeatCount, native.eventTime, owner, duration, player.currentPosition)
            KeyEventType.KeyUp ->
                up(press, native.isCanceled, native.eventTime, owner, duration) { target ->
                    runCatching { player.commitScrub(target.targetMs) }
                        .onSuccess { onCommitted(target) }
                        .onFailure(onError)
                }
            else -> false
        }
    }

    private fun down(
        press: ScrubPress,
        repeatCount: Int,
        eventTime: Long,
        owner: ScrubOwner?,
        durationMs: Long?,
        currentMs: Long,
    ): Boolean {
        val before = preview
        val matching = captured[press.direction] == press
        if (owner == null) {
            if (matching && activePress == press) cancel()
            return matching
        }
        if (repeatCount == 0 && !matching) {
            captured[press.direction] = press
            val maximum = durationMs?.let(::lastPreviewPosition)
            if (maximum == null || maximum <= 0) {
                cancel()
                return true
            }
            hold = Hold(press, eventTime)
            val start = before?.takeIf { it.owner == owner }?.targetMs ?: currentMs
            val step = if (press.direction == ScrubDirection.BACKWARD) backward else forward
            preview = ScrubPreview(advance(start, press.direction, maximum, step), press.direction, owner)
        } else {
            // A native repeat confirms the key is still held. The timer alone advances the target.
            val current = hold
            if (current != null && current.press == press && before != null) {
                val maximum = durationMs?.let(::lastPreviewPosition)
                if (owner != before.owner || maximum == null || before.targetMs > maximum ||
                    eventTime - current.lastInput >= LOST_RELEASE_MS
                ) {
                    cancel()
                } else {
                    current.lastInput = maxOf(current.lastInput, eventTime)
                }
            }
        }
        return true
    }

    private fun up(
        press: ScrubPress,
        cancelled: Boolean,
        eventTime: Long,
        owner: ScrubOwner?,
        durationMs: Long?,
        commit: (ScrubPreview) -> Unit,
    ): Boolean {
        if (captured[press.direction] != press) return owner != null
        captured.remove(press.direction)
        val current = hold?.takeIf { it.press == press } ?: return true
        val target = preview
        cancel()
        if (!cancelled && eventTime - current.lastInput < LOST_RELEASE_MS &&
            target != null && owner == target.owner &&
            durationMs != null && target.targetMs <= lastPreviewPosition(durationMs)
        ) {
            commit(target)
        }
        return true
    }

    fun cancel() {
        hold = null
        preview = null
        // Retain the captured key identity until release, so cancellation never becomes a legacy seek.
    }

    /** Advance only the matching hold and return the time until its next update or expiry. */
    fun advanceHold(press: ScrubPress, now: Long, owner: ScrubOwner?, player: Player): Long? {
        val current = hold?.takeIf { it.press == press } ?: return null
        val before = preview ?: return null
        val remaining = LOST_RELEASE_MS - (now - current.lastInput)
        val maximum = player.scrubDuration()?.let(::lastPreviewPosition)
        if (remaining <= 0 || owner != before.owner || maximum == null || before.targetMs > maximum) {
            cancel()
            return null
        }
        val elapsed = (now - current.startedAt).coerceAtLeast(0)
        val distance = holdDistance(elapsed)
        if (distance > current.distance) {
            val position = advance(before.targetMs, press.direction, maximum, distance - current.distance)
            current.distance = distance
            if (position != before.targetMs) preview = before.copy(targetMs = position)
        }
        val nextTick = HOLD_START_MS + ticksThrough(elapsed) * TICK_MS
        return minOf(nextTick - elapsed, remaining)
    }

    private fun advance(
        start: Long,
        direction: ScrubDirection,
        maximum: Long,
        step: Long,
    ): Long {
        val position = start.coerceIn(0, maximum)
        return when (direction) {
            ScrubDirection.BACKWARD -> (position - step).coerceAtLeast(0)
            ScrubDirection.FORWARD -> position + step.coerceAtMost(maximum - position)
        }
    }

    private companion object {
        const val HOLD_START_MS = 500L
        const val TICK_MS = 150L
        const val SECOND_TIER_MS = 1_500L
        const val THIRD_TIER_MS = 2_500L
        const val LOST_RELEASE_MS = 2_500L

        fun ticksThrough(elapsed: Long): Long =
            if (elapsed < HOLD_START_MS) 0 else 1 + (elapsed - HOLD_START_MS) / TICK_MS

        fun holdDistance(elapsed: Long): Long {
            val ticks = ticksThrough(elapsed)
            val first = minOf(ticks, ticksThrough(SECOND_TIER_MS - 1))
            val throughSecond = minOf(ticks, ticksThrough(THIRD_TIER_MS - 1))
            return first * 15_000 + (throughSecond - first) * 30_000 + (ticks - throughSecond) * 60_000
        }
    }
}

/** Use the same monotonic clock as Android key events and one timer for the active press. */
@Composable
internal fun TrackInstantDpadHold(
    scrub: InstantDpadScrub,
    owner: ScrubOwner?,
    player: Player,
    nowMs: () -> Long = SystemClock::uptimeMillis,
) {
    val press = scrub.activePress
    LaunchedEffect(scrub, press, owner, player) {
        if (press != null) {
            while (true) {
                val next = scrub.advanceHold(press, nowMs(), owner, player) ?: break
                delay(next)
            }
        }
    }
}

/** Leave a small playable tail when scrubbing to the end, including on very short clips. */
internal fun lastPreviewPosition(durationMs: Long): Long = durationMs - (durationMs / 10).coerceIn(1, 1_000)

internal fun Player.scrubDuration(): Long? =
    duration.takeIf {
        it > 1 && playerError == null && playbackState != Player.STATE_IDLE &&
            playbackState != Player.STATE_ENDED && !isCurrentMediaItemLive && isCurrentMediaItemSeekable &&
            isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
            isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
    }

/** Both in-app backends honor play intent across their asynchronous seek/buffering. */
internal fun Player.commitScrub(positionMs: Long) {
    seekTo(positionMs)
    if (!playWhenReady) play()
}

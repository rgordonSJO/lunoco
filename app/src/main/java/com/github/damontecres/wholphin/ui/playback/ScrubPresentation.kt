package com.github.damontecres.wholphin.ui.playback

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal const val SCRUB_LINGER_MS = 2_500L
internal const val SCRUB_BUFFERING_LIMIT_MS = 5_000L
internal const val SCRUB_FADE_MS = 200

/** Presentation of a completed seek. Active input and player dispatch remain in InstantDpadScrub. */
internal class ScrubPresentation(
    private val nowMs: () -> Long = SystemClock::uptimeMillis,
) {
    class Release(val frame: ScrubPreview, val atMs: Long)

    var release by mutableStateOf<Release?>(null)
        private set
    private var exiting by mutableStateOf(false)
    private val fade = Animatable(1f)
    val alpha: Float get() = if (exiting) fade.value else 1f

    fun onKeyEvent(
        scrub: InstantDpadScrub,
        event: KeyEvent,
        owner: ScrubOwner?,
        player: Player,
        onError: (Throwable) -> Unit,
    ): Boolean {
        if (event.type == KeyEventType.KeyDown && !isDpadLeft(event) && !isDpadRight(event)) clear()
        val consumed = scrub.onKeyEvent(event, owner, player, onCommitted = ::settle, onError = onError)
        if (scrub.preview != null) clear()
        return consumed
    }

    fun frame(active: ScrubPreview?, owner: ScrubOwner?): ScrubPreview? =
        (active ?: release?.frame)?.takeIf { it.owner == owner }

    fun clear() {
        release = null
        exiting = false
    }

    private fun settle(frame: ScrubPreview) {
        exiting = false
        release = Release(frame, nowMs())
    }

    suspend fun dismiss(expected: Release, buffering: () -> Boolean) {
        delay((expected.atMs + SCRUB_LINGER_MS - nowMs()).coerceAtLeast(0))
        if (release !== expected) return
        if (buffering()) {
            val remaining = (expected.atMs + SCRUB_BUFFERING_LIMIT_MS - nowMs()).coerceAtLeast(0)
            withTimeoutOrNull(remaining) { snapshotFlow { buffering() }.first { !it } }
        }
        if (release !== expected) return
        fade.snapTo(1f)
        if (release !== expected) return
        exiting = true
        fade.animateTo(0f, tween(durationMillis = SCRUB_FADE_MS, easing = LinearEasing))
        if (release === expected) clear()
    }
}

@Composable
internal fun TrackScrubPresentation(presentation: ScrubPresentation, buffering: () -> Boolean) {
    val currentBuffering by rememberUpdatedState(buffering)
    val release = presentation.release
    LaunchedEffect(presentation, release) {
        if (release != null) presentation.dismiss(release) { currentBuffering() }
    }
}

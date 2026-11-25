// Modified by Lunoco contributors. Phase 38C changes incorporated into this release source on 2026-11-22.
package com.github.damontecres.wholphin.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal const val SPOTLIGHT_ROTATION_INTERVAL_MS = 10_000L

// A State is provided so scrolling invalidates only the timer, not the Home composition.
internal val LocalSpotlightVisibility = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(true) }

internal fun spotlightMayAutoRotate(count: Int, visible: Boolean) = count > 1 && visible

/** One cancellable foreground loop; no tick state, metadata access or focus requests. */
internal suspend fun rotateSpotlightWhileResumed(lifecycle: Lifecycle, advance: () -> Unit) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (currentCoroutineContext().isActive) {
            delay(SPOTLIGHT_ROTATION_INTERVAL_MS)
            advance()
        }
    }
}

@Composable
internal fun SpotlightAutoRotation(
    movieCount: Int,
    selection: State<String?>,
    manualNavigation: State<Long>,
    onNext: () -> Unit,
) {
    val viewport = LocalSpotlightVisibility.current
    val visible by viewport
    val selectedId = selection.value // Immutable snapshot for the old tick's identity guard.
    val manualGeneration = manualNavigation.value
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val advance by rememberUpdatedState(onNext)
    // Paging starts a fresh interval; ordinary focus changes do not restart or pause it.
    // The generation also covers rapid manual round trips back to the same selected ID.
    LaunchedEffect(lifecycle, selection, movieCount, selectedId, manualNavigation, manualGeneration, visible) {
        if (spotlightMayAutoRotate(movieCount, visible)) {
            rotateSpotlightWhileResumed(lifecycle) {
                // A manual action or lifecycle/visibility change can precede effect cancellation.
                // Never let an already-waiting tick overwrite that newer interaction.
                if (selection.value == selectedId && manualNavigation.value == manualGeneration &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    spotlightMayAutoRotate(movieCount, viewport.value)) {
                    advance()
                }
            }
        }
    }
}

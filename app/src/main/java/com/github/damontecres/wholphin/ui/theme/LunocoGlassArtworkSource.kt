package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.damontecres.wholphin.ui.main.LocalSpotlightVisibility

/**
 * Tracks visible artwork availability only. Owns no image, capture, graphics layer or shader.
 * Scroll/lifecycle reads stay here; unavailable artwork gives pinned controls a legible tint.
 */
@Composable
internal fun LunocoGlassArtworkSource(
    hasArtwork: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = LocalLunocoGlassBackdrop.current
    val visible by LocalSpotlightVisibility.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val enabled = glassArtworkAvailable(state != null, hasArtwork, visible,
        lifecycle.isAtLeast(Lifecycle.State.RESUMED))
    DisposableEffect(state, enabled) {
        state?.available = enabled
        onDispose { state?.available = false }
    }
    Box(
        Modifier.fillMaxSize(),
        content = content,
    )
}

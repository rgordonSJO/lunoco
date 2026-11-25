package com.github.damontecres.wholphin.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import com.github.damontecres.wholphin.ui.theme.LocalLunocoGlassBackdrop

/** Legacy policy name retained; only individual navigation materials consume this threshold. */
internal fun homeNavigationBackdropRequired(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int): Boolean =
    firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0

/** Bind the existing list's derived state before drawing; no asynchronous boolean mirror. */
@Composable
internal fun BindHomeNavigationScroll(scrolled: State<Boolean>) {
    val scene = LocalLunocoGlassBackdrop.current
    DisposableEffect(scene, scrolled) {
        scene?.navigationScrolled = scrolled
        onDispose {
            // An outgoing Home must not clear a newer Home owner's registration.
            if (scene != null && scene.navigationScrolled === scrolled) scene.navigationScrolled = null
        }
    }
}

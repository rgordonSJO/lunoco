package com.github.damontecres.wholphin.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors

/** Home-scoped draw inputs only; owns no artwork, renderer, scroll owner or coroutine. */
@Stable
internal class LunocoGlassScene {
    var available by mutableStateOf(false)
    var navigationScrolled: State<Boolean>? by mutableStateOf(null)
}
internal val LocalLunocoGlassBackdrop = staticCompositionLocalOf<LunocoGlassScene?> { null }

/** Retain the TV Material focus outline; this material paints an asymmetric normal rim. */
@Composable
internal fun lunocoGlassHasOpticalRim(): Boolean = LocalLunocoGlassBackdrop.current?.available == true

/** Visual modifier only; existing TV controls own every action, focus target and semantic. */
@Composable
internal fun LunocoGlassSurface(
    interactionSource: MutableInteractionSource,
    selected: Boolean = false,
    primary: Boolean = false,
    adaptiveNavigation: Boolean = false,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    val available = LocalLunocoGlassBackdrop.current?.available == true
    val material = lunocoGlassMaterial(LocalLunocoGlassVariant.current, LocalLunocoGlassTreatment.current)
    val color = when {
        primary -> if (focused) Color(0xFFFAFAF6) else Color(0xFFECECE8)
        focused -> LunocoUiColors.NavigationFocused
        selected -> LunocoUiColors.NavigationSelected
        else -> LunocoUiColors.GlassCharcoal
    }
    val tint = animateColorAsState(color, tween(150), label = "Native glass tint")
    val light = animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "Native glass light")
    val emphasis = if (primary) { if (focused) .04f else 0f } else
        if (focused) .11f else if (selected) .05f else 0f
    return Modifier.lunocoGlassRenderer(material, tint, light, primary, available, emphasis,
        navigationScene = LocalLunocoGlassBackdrop.current.takeIf { adaptiveNavigation })
}

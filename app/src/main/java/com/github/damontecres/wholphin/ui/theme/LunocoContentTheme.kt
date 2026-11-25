package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.JellyLuThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors

internal val LocalLunocoContentStyle = staticCompositionLocalOf { false }

internal object LunocoContentStyle {
    val Surface = LunocoUiColors.GlassCharcoal
    val Panel = Color(0xFF232323)
    val Selected = LunocoUiColors.NavigationSelected
    val Focused = LunocoUiColors.NavigationFocused
    val Ivory = LunocoUiColors.NavigationIvory
    val SecondaryText = Color(0xFFC3C3BE)
    val DialogShape = RoundedCornerShape(12.dp)
    val FocusBorder = Border(BorderStroke(2.dp, LunocoUiColors.NavigationFocusOutline))
}

private val legacyTv = JellyLuThemeColors.darkScheme
private val lunocoTv = androidx.tv.material3.darkColorScheme(
    primary = LunocoContentStyle.Ivory,
    onPrimary = LunocoContentStyle.Surface,
    primaryContainer = LunocoContentStyle.Selected,
    onPrimaryContainer = LunocoContentStyle.Ivory,
    secondary = LunocoContentStyle.SecondaryText,
    onSecondary = LunocoContentStyle.Surface,
    secondaryContainer = LunocoContentStyle.Selected,
    onSecondaryContainer = LunocoContentStyle.Ivory,
    tertiary = legacyTv.tertiary,
    onTertiary = legacyTv.onTertiary,
    tertiaryContainer = legacyTv.tertiaryContainer,
    onTertiaryContainer = legacyTv.onTertiaryContainer,
    error = legacyTv.error,
    onError = legacyTv.onError,
    errorContainer = legacyTv.errorContainer,
    onErrorContainer = legacyTv.onErrorContainer,
    background = legacyTv.background,
    onBackground = LunocoContentStyle.Ivory,
    surface = LunocoContentStyle.Surface,
    onSurface = LunocoContentStyle.Ivory,
    surfaceVariant = LunocoContentStyle.Panel,
    onSurfaceVariant = LunocoContentStyle.SecondaryText,
    scrim = legacyTv.scrim,
    inverseSurface = LunocoContentStyle.Ivory,
    inverseOnSurface = LunocoContentStyle.Surface,
    inversePrimary = LunocoContentStyle.Selected,
    border = LunocoUiColors.NavigationFocusOutline,
)

private val lunocoMaterial = JellyLuThemeColors.darkSchemeMaterial.copy(
    primary = LunocoContentStyle.Ivory,
    onPrimary = LunocoContentStyle.Surface,
    primaryContainer = LunocoContentStyle.Selected,
    onPrimaryContainer = LunocoContentStyle.Ivory,
    secondary = LunocoContentStyle.SecondaryText,
    onSecondary = LunocoContentStyle.Surface,
    secondaryContainer = LunocoContentStyle.Selected,
    onSecondaryContainer = LunocoContentStyle.Ivory,
    surface = LunocoContentStyle.Surface,
    onSurface = LunocoContentStyle.Ivory,
    surfaceVariant = LunocoContentStyle.Panel,
    onSurfaceVariant = LunocoContentStyle.SecondaryText,
    outline = LunocoUiColors.NavigationFocusOutline,
    surfaceDim = LunocoContentStyle.Surface,
    surfaceBright = LunocoContentStyle.Focused,
    surfaceContainerLowest = legacyTv.background,
    surfaceContainerLow = LunocoContentStyle.Surface,
    surfaceContainer = LunocoContentStyle.Panel,
    surfaceContainerHigh = LunocoContentStyle.Selected,
    surfaceContainerHighest = LunocoContentStyle.Focused,
)

/** Destination content opts in at its boundary. The existing shell retains its own palette. */
@Composable
internal fun LunocoContentTheme(content: @Composable () -> Unit) {
    if (LocalTheme.current !in listOf(AppThemeColors.PURPLE, AppThemeColors.UNRECOGNIZED) ||
        MaterialTheme.colorScheme.background == JellyLuThemeColors.lightScheme.background
    ) {
        content()
    } else {
        CompositionLocalProvider(
            LocalLunocoContentStyle provides true,
            androidx.tv.material3.LocalContentColor provides lunocoTv.onSurface,
            androidx.compose.material3.LocalContentColor provides lunocoMaterial.onSurface,
        ) {
            androidx.compose.material3.MaterialTheme(colorScheme = lunocoMaterial) {
                MaterialTheme(colorScheme = lunocoTv, content = content)
            }
        }
    }
}

@Composable
internal fun lunocoActionColors(primary: Boolean = false) = ClickableSurfaceDefaults.colors(
    containerColor = if (primary) LunocoContentStyle.Ivory else LunocoContentStyle.Surface,
    contentColor = if (primary) LunocoContentStyle.Surface else LunocoContentStyle.Ivory,
    focusedContainerColor = if (primary) Color.White else LunocoContentStyle.Focused,
    focusedContentColor = if (primary) LunocoContentStyle.Surface else LunocoContentStyle.Ivory,
    pressedContainerColor = LunocoContentStyle.Selected,
    pressedContentColor = LunocoContentStyle.Ivory,
    disabledContainerColor = LunocoContentStyle.Surface,
    disabledContentColor = LunocoContentStyle.SecondaryText,
)

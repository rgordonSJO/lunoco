package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.ui.theme.LunocoGlassSurface
import com.github.damontecres.wholphin.ui.theme.lunocoGlassHasOpticalRim
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors

internal val LocalFloatingNavigationCompact = staticCompositionLocalOf { false }
// The shared Home shell supplies its existing content group; other destinations keep spatial search.
internal val LocalFloatingNavigationDown = staticCompositionLocalOf<FocusRequester?> { null }
private val NavigationPillShape = RoundedCornerShape(50)

/** Transparent reserved lane; only its controls paint a surface. Never scrolls Home. */
@Composable
internal fun FloatingNavigationRow(
    onFocusChanged: (Boolean) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp)) {
        val compact = maxWidth < 800.dp
        val width = maxWidth
        val scroll = rememberScrollState()
        CompositionLocalProvider(LocalFloatingNavigationCompact provides compact) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .focusProperties { canFocus = false }
                    .horizontalScroll(scroll)
                    .widthIn(min = width).height(56.dp)
                    .padding(horizontal = if (compact) 16.dp else 20.dp)
                    .onFocusChanged { onFocusChanged(it.hasFocus) }
                    .focusGroup(),
                content = content,
            )
        }
    }
}

/** Selected destination uses its pill surface; remote focus adds a separate 2dp outline. */
@Composable
internal fun BrowseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val downFocus = LocalFloatingNavigationDown.current
    val interaction = remember { MutableInteractionSource() }
    val glass = LunocoGlassSurface(interaction, selected = selected, adaptiveNavigation = true)
    val opticalRim = lunocoGlassHasOpticalRim()
    Button(
        interactionSource = interaction,
        onClick = onClick,
        scale = ButtonDefaults.scale(focusedScale = 1f),
        shape = ButtonDefaults.shape(NavigationPillShape),
        border = ButtonDefaults.border(
            border = if (opticalRim) Border.None else Border(BorderStroke(1.dp, LunocoUiColors.NavigationBorder), shape = NavigationPillShape),
            focusedBorder = Border(BorderStroke(2.dp, LunocoUiColors.NavigationFocusOutline), shape = NavigationPillShape),
        ),
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = LunocoUiColors.NavigationIvory,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = LunocoUiColors.NavigationIvory,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier.focusProperties { downFocus?.let { down = it } }
            .height(40.dp).then(glass).semantics { this.selected = selected },
        content = content,
    )
}

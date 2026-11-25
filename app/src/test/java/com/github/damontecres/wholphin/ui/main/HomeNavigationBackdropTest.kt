package com.github.damontecres.wholphin.ui.main

import org.junit.Assert.*
import org.junit.Test

class HomeNavigationBackdropTest {
    @Test fun firstScrollPixelProtectsHeaderAndExactTopRestoresArtwork() {
        assertFalse(homeNavigationBackdropRequired(0, 0))
        assertTrue(homeNavigationBackdropRequired(0, 1))
        assertTrue(homeNavigationBackdropRequired(0, 300))
        assertTrue(homeNavigationBackdropRequired(1, 0))
        assertTrue(homeNavigationBackdropRequired(8, 0))
        assertFalse(homeNavigationBackdropRequired(0, 0))
    }
    @Test fun scrolledNavigationTintsAreOpaqueAndNeutral() {
        val colors = com.github.damontecres.wholphin.ui.theme.colors.LunocoUiColors
        for (tint in listOf(colors.GlassCharcoal, colors.NavigationSelected, colors.NavigationFocused)) {
            assertEquals(1f, tint.alpha)
            assertEquals(tint.red, tint.green)
            assertEquals(tint.green, tint.blue)
        }
    }
}

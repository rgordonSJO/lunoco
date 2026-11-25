package com.github.damontecres.wholphin.ui.theme

import org.junit.Assert.*
import org.junit.Test

class LunocoGlassPolicyTest {
    @Test fun availabilityRequiresAnActualVisibleResumedHero() {
        assertTrue(glassArtworkAvailable(true, true, true, true))
        assertFalse(glassArtworkAvailable(false, true, true, true))
        assertFalse(glassArtworkAvailable(true, false, true, true))
        assertFalse(glassArtworkAvailable(true, true, false, true))
        assertFalse(glassArtworkAvailable(true, true, true, false))
    }
    @Test fun materialCanBeSelectedWithoutOwningArtworkOrPlatformResources() {
        val state = LunocoGlassScene()
        assertFalse(state.available)
        for (variant in LunocoGlassVariant.entries) {
            assertTrue(lunocoGlassMaterial(variant).bodyOpacity < 1f)
            assertFalse(state.available)
        }
    }
}

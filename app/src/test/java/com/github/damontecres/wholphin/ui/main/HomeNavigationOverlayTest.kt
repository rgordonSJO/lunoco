package com.github.damontecres.wholphin.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNavigationOverlayTest {
    @Test fun overlayReservesOnlyTheTopFocusInset() {
        val spec = JellyLuHomeBringIntoViewSpec(inset = 32f, topInset = 88f)
        assertEquals(-68f, spec.calculateScrollDistance(20f, 124f, 540f), .01f)
        assertEquals(0f, spec.calculateScrollDistance(100f, 124f, 540f), .01f)
        assertEquals(66f, spec.calculateScrollDistance(450f, 124f, 540f), .01f)
        assertEquals(12f, spec.calculateScrollDistance(100f, 700f, 540f), .01f)
    }

}

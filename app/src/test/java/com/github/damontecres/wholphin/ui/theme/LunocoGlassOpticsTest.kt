package com.github.damontecres.wholphin.ui.theme

import org.junit.Assert.*
import org.junit.Test

class LunocoGlassOpticsTest {
    @Test fun nativeMaterialsKeepContrastAndTransmissionWithoutOpticsParameters() {
        for (v in LunocoGlassVariant.entries) {
            val m = lunocoGlassMaterial(v)
            assertTrue(m.bodyOpacity in .65f.. .78f)
            assertTrue(m.edgeOpacity < m.bodyOpacity)
            assertTrue(m.ivoryOpacity in .70f.. .86f)
            assertTrue(m.reflection in 0f.. .25f)
            assertSame(m, lunocoGlassMaterial(v))
        }
    }
    @Test fun strongerLightingDoesNotRequireAnotherRenderingModeOrSource() {
        val p = LunocoGlassVariant.entries.map(::lunocoGlassMaterial)
        p.zipWithNext().forEach { (a, b) ->
            assertTrue(a.rimLight < b.rimLight)
            assertTrue(a.reflection < b.reflection)
            assertTrue(a.bodyOpacity >= b.bodyOpacity)
        }
    }
    @Test fun bothNeutralProfilesReuseBoundedImmutableMaterials() {
        for (profile in LunocoGlassTreatment.entries) for (variant in LunocoGlassVariant.entries) {
            val m=lunocoGlassMaterial(variant,profile)
            assertSame(m,lunocoGlassMaterial(variant,profile))
            assertTrue(m.bodyOpacity in .65f.. .74f)
            assertTrue(m.edgeOpacity<m.bodyOpacity)
            assertTrue(m.reflection>0f && m.reflection<=.25f)
        }
        assertTrue(lunocoGlassMaterial(LunocoGlassVariant.Balanced,LunocoGlassTreatment.NeutralClear).bodyOpacity <
            lunocoGlassMaterial(LunocoGlassVariant.Balanced,LunocoGlassTreatment.FrostedSmoke).bodyOpacity)
    }

    @Test fun neutralOpacitySweepExplainsContrastBoundWithoutChangingTheThreshold() {
        fun linear(c:Double):Double = if(c<=.04045) c/12.92 else Math.pow((c+.055)/1.055,2.4)
        val foreground=.2126*linear(241.0/255)+.7152*linear(240.0/255)+.0722*linear(236.0/255)
        fun contrast(alpha:Double):Double {
            val gray=(20*alpha+255*(1-alpha))/255
            return (foreground+.05)/(linear(gray)+.05)
        }
        for(percent in 45..70 step 5) println("NEUTRAL35_OPACITY_SWEEP_$percent="+contrast(percent/100.0))
        assertTrue("An excessively clear body fails on white",contrast(.45)<4.5)
        for(profile in LunocoGlassTreatment.entries) {
            val alpha=lunocoGlassMaterial(LunocoGlassVariant.Balanced,profile).bodyOpacity
            assertTrue(contrast(alpha.toDouble())>=4.5)
        }
    }

}
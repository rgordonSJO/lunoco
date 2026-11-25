package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Original Lunoco materials. All modes use Compose drawing, not backdrop refraction. */
internal enum class LunocoGlassVariant { Lightweight, Balanced, Enhanced }
internal val LocalLunocoGlassVariant = staticCompositionLocalOf { LunocoGlassVariant.Balanced }
// Internal styling profiles of the same renderer; no user-facing theme setting.
internal enum class LunocoGlassTreatment { NeutralClear, FrostedSmoke }
internal val LocalLunocoGlassTreatment = staticCompositionLocalOf { LunocoGlassTreatment.NeutralClear }

@Immutable
internal data class LunocoGlassMaterial(
    val edgeOpacity: Float,
    val bodyOpacity: Float,
    val ivoryOpacity: Float,
    val reflection: Float,
    val rimLight: Float,
)

/** Shared immutable values; no platform, TV input, image ownership or shader objects. */
internal fun lunocoGlassMaterial(variant: LunocoGlassVariant): LunocoGlassMaterial = when (variant) {
    LunocoGlassVariant.Lightweight -> LightweightMaterial
    LunocoGlassVariant.Balanced -> BalancedMaterial
    LunocoGlassVariant.Enhanced -> EnhancedMaterial
}

internal fun lunocoGlassMaterial(variant: LunocoGlassVariant, treatment: LunocoGlassTreatment): LunocoGlassMaterial =
    if (treatment == LunocoGlassTreatment.NeutralClear) lunocoGlassMaterial(variant) else when (variant) {
        LunocoGlassVariant.Lightweight -> SmokeLightweightMaterial
        LunocoGlassVariant.Balanced -> SmokeBalancedMaterial
        LunocoGlassVariant.Enhanced -> SmokeEnhancedMaterial
    }

private val LightweightMaterial = LunocoGlassMaterial(.48f, .70f, .82f, .10f, .28f)
private val BalancedMaterial = LunocoGlassMaterial(.36f, .65f, .77f, .16f, .48f)
private val EnhancedMaterial = LunocoGlassMaterial(.28f, .65f, .74f, .22f, .62f)
private val SmokeLightweightMaterial = LunocoGlassMaterial(.52f, .74f, .84f, .12f, .30f)
private val SmokeBalancedMaterial = LunocoGlassMaterial(.42f, .70f, .80f, .20f, .50f)
private val SmokeEnhancedMaterial = LunocoGlassMaterial(.34f, .70f, .77f, .24f, .64f)

/** Availability affects legibility tint only. No rendering mode records a source. */
internal fun glassArtworkAvailable(hasScene: Boolean, hasArtwork: Boolean, visible: Boolean, resumed: Boolean) =
    hasScene && hasArtwork && visible && resumed

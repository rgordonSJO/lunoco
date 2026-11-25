package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * Independently authored from Compose DrawScope/Brush/Path APIs and Lunoco's requirements.
 * Alpha reveals the existing composition. No source sampling, blur, refraction, pixel copy,
 * shader, graphicsLayer or additional layout node is used.
 */
internal fun Modifier.lunocoGlassRenderer(
    material: LunocoGlassMaterial,
    tint: State<Color>,
    illumination: State<Float>,
    primary: Boolean,
    artworkAvailable: Boolean,
    emphasis: Float,
    navigationScene: LunocoGlassScene? = null,
): Modifier = drawWithCache {
    val radius = CornerRadius(size.height / 2f)
    val capsule = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }
    val edge = if (!artworkAvailable) .92f else
        ((if (primary) material.ivoryOpacity - .20f else material.edgeOpacity) + emphasis * .35f).coerceAtMost(.92f)
    val center = if (!artworkAvailable) if (primary) .97f else .94f else
        ((if (primary) material.ivoryOpacity else material.bodyOpacity) + emphasis).coerceAtMost(.94f)
    val body = Brush.verticalGradient(
        0f to Color.White.copy(alpha = edge),
        .14f to Color.White.copy(alpha = center),
        .86f to Color.White.copy(alpha = center),
        1f to Color.White.copy(alpha = edge + (center - edge) * .5f))
    val gloss = Brush.verticalGradient(
        0f to Color.White.copy(alpha = material.reflection),
        .40f to Color.Transparent, 1f to Color.Transparent)
    val rim = Brush.linearGradient(
        0f to Color(0xFFF4F4F4).copy(alpha = material.rimLight),
        .35f to Color.White.copy(alpha = material.rimLight * .20f),
        .60f to Color.Transparent, 1f to Color.Black.copy(alpha = .38f),
        start = Offset.Zero, end = Offset(size.width, size.height))
    val stroke = Stroke(1.dp.toPx())
    val inset = .6.dp.toPx()
    onDrawBehind {
        clipPath(capsule) {
            // Consume one shared threshold in draw, never raw scroll pixels in composition.
            // Snap to full opacity on entry; keep the same capsule, gloss and rim draws.
            if (navigationScene?.navigationScrolled?.value == true) {
                drawRect(tint.value.copy(alpha = 1f))
            } else {
                drawRect(body, colorFilter = ColorFilter.tint(tint.value))
            }
            drawRect(gloss, alpha = .75f + .25f * illumination.value)
            drawRoundRect(rim, topLeft = Offset(inset, inset),
                size = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f)),
                cornerRadius = CornerRadius((radius.x - inset).coerceAtLeast(0f)), style = stroke,
                alpha = .70f + .30f * illumination.value)
        }
    }
}

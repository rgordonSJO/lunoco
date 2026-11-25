package com.github.damontecres.wholphin.ui.theme

import android.graphics.*
import java.util.Random

/** Procedural test art only; not a licensed movie photograph or production image pipeline. */
internal enum class Glass31Artwork { BrightLandscape, DarkScene, Saturated, HighDetail, Mixed, White, Black }

internal fun glass31Artwork(kind: Glass31Artwork): Bitmap {
    val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val dark = kind == Glass31Artwork.DarkScene
    if (kind == Glass31Artwork.White || kind == Glass31Artwork.Black) {
        canvas.drawColor(if (kind == Glass31Artwork.White) Color.WHITE else Color.BLACK)
        return bitmap
    }
    paint.shader = LinearGradient(0f, 0f, 640f, 360f,
        if (dark) intArrayOf(0xFF071423.toInt(), 0xFF253D68.toInt(), 0xFF102029.toInt())
        else intArrayOf(0xFF9ED3EB.toInt(), 0xFFFFE3AC.toInt(), 0xFF627C72.toInt()), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 640f, 360f, paint)
    paint.shader = null
    paint.color = if (dark) 0xFFBFD5DF.toInt() else 0xFFFFF0BC.toInt()
    canvas.drawCircle(470f, 85f, 43f, paint)
    val random = Random(31028L)
    for (layer in 0..3) {
        val path = Path().apply {
            moveTo(0f, 360f)
            lineTo(0f, 180f + layer * 24)
            for (x in 0..16) lineTo(x * 42f, 110f + layer * 38 + random.nextInt(90))
            lineTo(640f, 360f); close()
        }
        paint.color = if (dark) Color.rgb(12 + layer * 4, 24 + layer * 6, 40 + layer * 8)
            else Color.rgb(70 + layer * 12, 100 + layer * 14, 105 + layer * 10)
        canvas.drawPath(path, paint)
    }
    for (i in 0..220) {
        val x = random.nextInt(640).toFloat()
        val y = random.nextInt(360).toFloat()
        paint.color = if (dark) Color.argb(100, 236, 210, 145) else Color.argb(65, 249, 244, 220)
        canvas.drawRect(x, y, x + 2, y + 1, paint)
    }
    if (kind == Glass31Artwork.Saturated) {
        val colors = intArrayOf(0xFFB23968.toInt(), 0xFF1D858A.toInt(), 0xFFE6A13E.toInt(), 0xFF5148A1.toInt())
        for (i in 0..15) {
            paint.color = colors[i % colors.size]
            canvas.drawCircle(i * 47f, if (i % 2 == 0) 110f else 280f, 105f, paint)
        }
    }
    if (kind == Glass31Artwork.HighDetail || kind == Glass31Artwork.Mixed) {
        val cell = if (kind == Glass31Artwork.HighDetail) 6 else 48
        for (y in 0 until 360 step cell) for (x in 0 until 640 step cell) {
            paint.color = if ((x / cell + y / cell) % 2 == 0) 0xFFF0E2BA.toInt() else 0xFF183E62.toInt()
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + cell).toFloat(), (y + cell).toFloat(), paint)
        }
    }
    return bitmap
}

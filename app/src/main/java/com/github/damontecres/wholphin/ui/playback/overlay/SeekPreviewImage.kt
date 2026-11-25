// Modified by Lunoco contributors. Phase 38C changes incorporated into this release source on 2026-11-22.
package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.annotation.FloatRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.theme.LunocoContentStyle
import org.jellyfin.sdk.model.api.TrickplayInfo

fun Modifier.offsetByPercent(
    xPercentage: Float,
    yOffset: Int,
) = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(
                x =
                    ((constraints.maxWidth * xPercentage).toInt() - placeable.width / 2)
                        .coerceIn(0, constraints.maxWidth - placeable.width),
                y = constraints.maxHeight - yOffset, // (constraints.maxHeight * yPercentage).toInt() - (placeable.height / 1.33f).toInt(),
            )
        }
    },
)

/**
 * Offset the composable by a percentage of the available x direction
 *
 * This will account for the composable actual width so it won't be pushed off screen.
 * In other words, 0% means the left edge of the composable will be at the left end of the x-axis.
 *
 * @param xPercentage percent offset between 0 inclusive and 1 inclusive
 */
fun Modifier.offsetByPercent(
    @FloatRange(0.0, 1.0) xPercentage: Float,
) = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(
                x =
                    ((constraints.maxWidth * xPercentage).toInt() - placeable.width / 2)
                        .coerceIn(0, constraints.maxWidth - placeable.width),
                y = 0,
            )
        }
    },
)


private data class LoadedTile(val painter: Painter, val frame: TrickplayFrame)

/** Keep one stable preview slot, and never draw an old sheet with the new tile coordinates. */
@Composable
internal fun SeekPreviewImage(
    previewImageUrl: String?,
    frame: TrickplayFrame?,
    info: TrickplayInfo?,
) {
    val context = LocalContext.current
    var lastTile by remember(info) { mutableStateOf<LoadedTile?>(null) }
    val requestState = if (previewImageUrl.isNullOrBlank() || frame == null || info == null) {
        null
    } else {
        // Only the request changes identity. The slot and last successfully rendered tile stay mounted.
        key(previewImageUrl) {
            val request = remember(context, previewImageUrl) {
                ImageRequest.Builder(context).data(previewImageUrl).size(coil3.size.Size.ORIGINAL).build()
            }
            val painter = rememberAsyncImagePainter(request, contentScale = ContentScale.None)
            val state by painter.state.collectAsState()
            state
        }
    }
    val loaded = (requestState as? AsyncImagePainter.State.Success)?.let { success ->
        frame?.let { LoadedTile(success.painter, it) }
    }
    val loading = requestState is AsyncImagePainter.State.Loading || requestState is AsyncImagePainter.State.Empty
    val displayed = loaded ?: lastTile.takeIf { loading }
    SideEffect {
        when {
            loaded != null -> lastTile = loaded
            !loading -> lastTile = null
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val placeholder = stringResource(R.string.lunoco_trickplay_unavailable)
        val shape = RoundedCornerShape(8.dp)
        Box(
            Modifier.size(128.dp, 72.dp).clip(shape).background(LunocoContentStyle.Surface)
                .then(if (displayed == null) Modifier.semantics { contentDescription = placeholder } else Modifier),
        ) {
            if (displayed != null && info != null) {
                Canvas(Modifier.matchParentSize()) {
                    val ratio = minOf(size.width / info.width, size.height / info.height)
                    val left = (size.width - info.width * ratio) / 2f
                    val top = (size.height - info.height * ratio) / 2f
                    clipRect(left, top, left + info.width * ratio, top + info.height * ratio) {
                        translate(left, top) {
                            scale(ratio, ratio, pivot = Offset.Zero) {
                                translate(-displayed.frame.column.toFloat() * info.width, -displayed.frame.row.toFloat() * info.height) {
                                    with(displayed.painter) {
                                        draw(Size(info.width * info.tileWidth.toFloat(), info.height * info.tileHeight.toFloat()))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                val label = if (displayed != null) R.string.lunoco_trickplay_loading_previous else R.string.lunoco_trickplay_loading
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = LunocoContentStyle.Ivory)
            }
        }
    }
}

// Modified by Lunoco contributors. Phase 38C changes incorporated into this release source on 2026-11-22.
package com.github.damontecres.wholphin.ui.playback.overlay

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.theme.LunocoContentStyle
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration.Companion.seconds

@Composable
fun TrickplayPreview(
    seekProgressMs: Long,
    trickplayInfo: TrickplayInfo?,
    trickplayUrlFor: (Int) -> String?,
    modifier: Modifier = Modifier,
    directionLabel: String? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        val address = trickplayInfo?.let { trickplayFrame(seekProgressMs, it) }
        val imageUrl = remember(address?.sheet, trickplayInfo, trickplayUrlFor) {
            address?.let { trickplayUrlFor(it.sheet) }
        }
        key(trickplayUrlFor) {
            SeekPreviewImage(previewImageUrl = imageUrl, frame = address, info = trickplayInfo)
        }
        val resources = LocalResources.current
        val seekProgressSec = seekProgressMs / 1000L
        val seekText = remember(seekProgressSec, resources, directionLabel != null) {
            if (directionLabel == null) {
                resources.formatDuration(seekProgressSec.seconds)
            } else {
                DateUtils.formatElapsedTime(seekProgressSec)
            }
        }
        Text(
            text = directionLabel?.let { "$it · $seekText" } ?: seekText,
            color = LunocoContentStyle.Ivory,
            style = MaterialTheme.typography.labelLarge,
            modifier =
                Modifier
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

internal data class TrickplayFrame(val sheet: Int, val column: Int, val row: Int)

internal fun trickplayFrame(positionMs: Long, info: TrickplayInfo): TrickplayFrame? {
    if (positionMs < 0 || info.interval <= 0 || info.width <= 0 || info.height <= 0 ||
        info.tileWidth <= 0 || info.tileHeight <= 0 || info.thumbnailCount <= 0
    ) return null
    val frame = (positionMs / info.interval).coerceAtMost(info.thumbnailCount.toLong() - 1)
    val tiles = info.tileWidth.toLong() * info.tileHeight
    val tile = frame % tiles
    return TrickplayFrame((frame / tiles).toInt(), (tile % info.tileWidth).toInt(), (tile / info.tileWidth).toInt())
}

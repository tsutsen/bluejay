package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.ui.AsyncImage
import kotlin.math.roundToInt

private const val QUEUE_ROW_HEIGHT_DP = 68

/**
 * Shared vertical queue list (player queue sheet, second-screen Queue tab).
 * Tap a row to play it, drag the handle to reorder, tap X to remove.
 */
@Composable
fun QueueList(
    items: List<ContentItem>,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Nothing queued",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val rowHeightPx =
        with(androidx.compose.ui.platform.LocalDensity.current) {
            QUEUE_ROW_HEIGHT_DP.dp.toPx()
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = rememberLazyListState(),
    ) {
        itemsIndexed(items, key = { _, item -> item.url }) { index, item ->
            QueueRow(
                item = item,
                isDragging = index == dragIndex,
                dragOffsetPx = if (index == dragIndex) dragOffsetPx else 0f,
                onPlay = { onPlay(index) },
                onRemove = { onRemove(index) },
                onDragStart = {
                    dragIndex = index
                    dragOffsetPx = 0f
                },
                onDrag = { dyPx -> dragOffsetPx += dyPx },
                onDragEnd = {
                    val from = dragIndex
                    val offset = dragOffsetPx
                    dragIndex = -1
                    dragOffsetPx = 0f
                    if (from != index) return@QueueRow
                    val delta = (offset / rowHeightPx).roundToInt()
                    val to = (index + delta).coerceIn(0, items.size - 1)
                    if (to != index) onMove(index, to)
                },
            )
        }
    }
}

@Composable
private fun QueueRow(
    item: ContentItem,
    isDragging: Boolean,
    dragOffsetPx: Float,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(QUEUE_ROW_HEIGHT_DP.dp)
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer { translationY = dragOffsetPx }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail (16:9, never stretched), duration badge, tap to play.
        Box(
            modifier =
                Modifier
                    .width(112.dp)
                    .height(63.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF202124))
                    .clickable(onClick = onPlay),
        ) {
            item.thumbnailUrl?.let { url ->
                AsyncImage(
                    url = url,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                )
            }
            item.durationMs?.takeIf { it > 0 }?.let { ms ->
                Text(
                    text = formatDuration(ms),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    buildString {
                        item.author?.name?.let { append(it) }
                        item.durationMs?.takeIf { it > 0 }?.let {
                            if (isNotEmpty()) append(" • ")
                            append(formatDuration(it))
                        }
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove from queue",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .size(28.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { change, delta ->
                                    onDrag(delta.y)
                                    change.consume()
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                            )
                        },
            )
        }
    }
}

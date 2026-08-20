package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.ui.AsyncImage
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CURRENT_CARD_WIDTH_DP = 220
private const val QUEUE_CARD_WIDTH_DP = 168
private const val QUEUE_SPACING_DP = 12

/**
 * Horizontal queue strip (top of the Feed tab): the playing video as a
 * larger card with a play/pause button, then the queued videos — tap to
 * play, swipe up/down to remove, drag the handle to reorder.
 */
@Composable
fun QueueStripCard(
    current: ContentItem?,
    isPlaying: Boolean,
    queue: List<ContentItem>,
    onPlayPause: () -> Unit,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (current == null && queue.isEmpty()) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
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
    var dragDx by remember { mutableFloatStateOf(0f) }
    val slotPx =
        with(LocalDensity.current) {
            (QUEUE_CARD_WIDTH_DP + QUEUE_SPACING_DP).dp.toPx()
        }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = rememberLazyListState(),
        horizontalArrangement = Arrangement.spacedBy(QUEUE_SPACING_DP.dp),
        contentPadding = PaddingValues(horizontal = Tokens.SpaceLg, vertical = 4.dp),
    ) {
        current?.let { item ->
            item(key = "current") {
                QueueCurrentCard(item = item, isPlaying = isPlaying, onPlayPause = onPlayPause)
            }
        }
        itemsIndexed(queue, key = { _, item -> item.url }) { index, item ->
            QueueStripItem(
                item = item,
                isDragging = index == dragIndex,
                dragDx = if (index == dragIndex) dragDx else 0f,
                onPlay = { onPlay(index) },
                onRemove = { onRemove(index) },
                onDragStart = {
                    dragIndex = index
                    dragDx = 0f
                },
                onDragDx = { dx -> dragDx += dx },
                onDragEnd = {
                    val from = dragIndex
                    val dx = dragDx
                    dragIndex = -1
                    dragDx = 0f
                    if (from != index) return@QueueStripItem
                    val delta = (dx / slotPx).roundToInt()
                    val to = (index + delta).coerceIn(0, queue.size - 1)
                    if (to != index) onMove(index, to)
                },
            )
        }
    }
}

@Composable
private fun QueueCurrentCard(
    item: ContentItem,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
) {
    val width = CURRENT_CARD_WIDTH_DP.dp
    Column {
        Box(
            modifier =
                Modifier
                    .width(width)
                    .height(width * 9 / 16)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF202124)),
        ) {
            item.thumbnailUrl?.let { url ->
                AsyncImage(
                    url = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
            }
            // Play/pause: filled circle over the thumbnail centre.
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(onClick = onPlayPause)
                        .align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                )
            }
        }
        QueueCardTexts(item = item, handle = null)
    }
}

@Composable
private fun QueueStripItem(
    item: ContentItem,
    isDragging: Boolean,
    dragDx: Float,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDragDx: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var swipeDy by remember { mutableFloatStateOf(0f) }
    val width = QUEUE_CARD_WIDTH_DP.dp

    Column(
        modifier =
            Modifier
                .width(width)
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationX = dragDx
                    translationY = swipeDy
                }
                .alpha(
                    if (swipeDy != 0f)
                        (1f - abs(swipeDy.coerceIn(-120f, 120f)) / 160f).coerceAtLeast(0.2f)
                    else 1f
                )
                .pointerInput(Unit) {
                    // Swipe the card up/down to remove it.
                    detectVerticalDragGestures(
                        onDragStart = { swipeDy = 0f },
                        onVerticalDrag = { change, dy ->
                            swipeDy += dy
                            change.consume()
                        },
                        onDragEnd = {
                            if (abs(swipeDy) > 64f) onRemove()
                            swipeDy = 0f
                        },
                        onDragCancel = { swipeDy = 0f },
                    )
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(width * 9 / 16)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF202124))
                    .clickable(onClick = onPlay),
        ) {
            item.thumbnailUrl?.let { url ->
                AsyncImage(
                    url = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
            }
            item.durationMs?.takeIf { it > 0 }?.let { ms ->
                Text(
                    text = formatDuration(ms),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .padding(4.dp),
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove from queue",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        QueueCardTexts(
            item = item,
            handle = {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { onDragStart() },
                                    onDrag = { change, delta ->
                                        onDragDx(delta.x)
                                        change.consume()
                                    },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragEnd() },
                                )
                            },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = "Reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
    }
}

/** Name + channel/duration under a strip card, with an optional drag handle. */
@Composable
private fun QueueCardTexts(
    item: ContentItem,
    handle: (@Composable () -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        handle?.invoke()
    }
}

package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.ui.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val QUEUE_ROW_HEIGHT_DP = 68
private const val ANIM = 180

/**
 * Shared vertical queue list (player queue sheet, second-screen Queue tab).
 * Tap a row to play it, drag the handle to reorder (live — rows rearrange
 * while dragging), tap X to remove (animated).
 */
@Composable
fun QueueList(
    items: List<ContentItem>,
    onPlay: (Int) -> Unit,
    onRemove: (String) -> Unit,
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

    val rowHeightPx =
        with(LocalDensity.current) { QUEUE_ROW_HEIGHT_DP.dp.toPx() }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragTotalPx by remember { mutableFloatStateOf(0f) }
    val removing = remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    // FLIP: where each row sat in the previous composition, so moved rows
    // slide to their new slot instead of jumping.
    val prevIndexes = remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val currentIndexes = items.withIndex().associate { (i, it) -> it.url to i }
    LaunchedEffect(items) { prevIndexes.value = currentIndexes }

    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val isDragging = index == dragIndex
            val dragDy =
                if (isDragging)
                    (dragStartIndex * rowHeightPx + dragTotalPx - index * rowHeightPx)
                else 0f
            var hasAppeared by remember(item.url) { mutableStateOf(false) }
            LaunchedEffect(Unit) { hasAppeared = true }

            AnimatedVisibility(
                visible = hasAppeared && item.url !in removing.value,
                enter = fadeIn(tween(ANIM)) + expandVertically(tween(ANIM)),
                exit = fadeOut(tween(ANIM)) + shrinkVertically(tween(ANIM)),
            ) {
                QueueRow(
                    item = item,
                    index = index,
                    prevIndex = prevIndexes.value[item.url],
                    rowHeightPx = rowHeightPx,
                    isDragging = isDragging,
                    dragDy = dragDy,
                    onPlay = { onPlay(index) },
                    onRemove = {
                        if (item.url in removing.value) return@QueueRow
                        removing.value += item.url
                        scope.launch {
                            delay(ANIM.toLong())
                            removing.value = removing.value - item.url
                            onRemove(item.url)
                        }
                    },
                    onDragStart = {
                        dragIndex = index
                        dragStartIndex = index
                        dragTotalPx = 0f
                    },
                    onDrag = { dyPx ->
                        dragTotalPx += dyPx
                        val target =
                            (dragStartIndex + (dragTotalPx / rowHeightPx).roundToInt()).coerceIn(
                                0,
                                items.size - 1,
                            )
                        if (target != dragIndex) {
                            onMove(dragIndex, target)
                            dragIndex = target
                        }
                    },
                    onDragEnd = {
                        dragIndex = -1
                        dragStartIndex = -1
                        dragTotalPx = 0f
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: ContentItem,
    index: Int,
    prevIndex: Int?,
    rowHeightPx: Float,
    isDragging: Boolean,
    dragDy: Float,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    // FLIP slide for this row when its slot changes (not while dragging).
    val placement = remember(item.url) { Animatable(0f) }
    LaunchedEffect(prevIndex) {
        if (prevIndex != null && !isDragging) {
            val delta = (prevIndex - index) * rowHeightPx
            if (delta != 0f) {
                placement.snapTo(delta)
                placement.animateTo(0f, tween(ANIM))
            }
        }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(QUEUE_ROW_HEIGHT_DP.dp)
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (isDragging) dragDy else placement.value
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail (16:9, never stretched), duration badge, tap to play.
        Box(
            modifier =
                Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f)
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
                            .fillMaxWidth()
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
                            .background(
                                Color.Black.copy(alpha = 0.75f),
                                RoundedCornerShape(4.dp),
                            )
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
                text = item.author?.name ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove from queue",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        // Dotted drag handle.
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Outlined.DragIndicator,
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

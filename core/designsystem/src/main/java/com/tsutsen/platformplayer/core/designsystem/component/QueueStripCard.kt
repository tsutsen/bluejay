package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Both cards share the blockout's outer size (1.5:1); the current card is
// emphasised by a full-bleed thumbnail + play button, not a bigger card.
private val CARD_W = 240.dp
private val CARD_H = 160.dp
private val STRIP_GAP = 12.dp
private val ANIM = 180

/**
 * Horizontal queue strip: the now-playing card first (full-bleed thumbnail
 * with a bottom gradient so the title/channel/duration stay readable, and a
 * play/pause button), then the queued cards (thumbnail, title, channel,
 * duration, dotted drag handle).
 *
 * Drag feedback is live: rows rearrange while the handle is being dragged,
 * and insert/delete animate (items slide to make/leave room).
 */
@Composable
fun QueueStripCard(
    current: ContentItem?,
    isPlaying: Boolean,
    queue: List<ContentItem>,
    onPlayPause: () -> Unit,
    onPlay: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(Tokens.SpaceLg)) {
            Text("Queue", style = MaterialTheme.typography.titleSmall)
            if (current == null && queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nothing queued",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
            ) {
                current?.let { item ->
                    NowPlayingCard(
                        item = item,
                        isPlaying = isPlaying,
                        onPlayPause = onPlayPause,
                    )
                }
                QueuedCardStrip(
                    items = queue,
                    onPlay = onPlay,
                    onRemove = onRemove,
                    onMove = onMove,
                )
            }
        }
    }
}

/** Now-playing card: full-bleed thumbnail, gradient, play/pause, meta. */
@Composable
private fun NowPlayingCard(
    item: ContentItem,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier =
            Modifier
                .width(CARD_W)
                .height(CARD_H)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1F1F1F)),
    ) {
        AsyncImage(
            url = item.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        // Bottom gradient (transparent -> card gray) so the title, channel
        // and duration stay readable over the thumbnail.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            surfaceVariant,
                                            surfaceVariant,
                                        ),
                                    startY = size.height * 0.45f,
                                    endY = size.height,
                                ),
                        )
                    },
        )
        IconButton(
            onClick = onPlayPause,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 42.dp)
                    .size(48.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xCC009369)),
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 72.dp, bottom = 10.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
        item.durationMs
            ?.takeIf { it > 0 }
            ?.let { ms ->
                Text(
                    text = formatDuration(ms),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
    }
}

/**
 * The queued cards: live drag-reorder (cards rearrange while dragging),
 * swipe up/down to remove, animated insert/delete.
 */
@Composable
private fun QueuedCardStrip(
    items: List<ContentItem>,
    onPlay: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val slotPx =
        with(LocalDensity.current) { (CARD_W + STRIP_GAP).toPx() }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragTotalPx by remember { mutableFloatStateOf(0f) }
    val removing = remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    // FLIP: where each card sat in the previous composition, so moved cards
    // slide to their new slot instead of jumping.
    val prevIndexes = remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val currentIndexes = items.withIndex().associate { (i, it) -> it.url to i }
    LaunchedEffect(items) { prevIndexes.value = currentIndexes }

    items.forEachIndexed { index, item ->
        val isDragging = index == dragIndex
        // The dragged card sticks to the finger; when the list order jumps
        // (live move), the grid slot changes by exactly one card, so the
        // residual offset keeps the card pinned.
        val dragDx =
            if (isDragging)
                (dragStartIndex * slotPx + dragTotalPx - index * slotPx)
            else 0f
        // Entry animation: items start collapsed and expand in.
        var hasAppeared by remember(item.url) { mutableStateOf(false) }
        LaunchedEffect(Unit) { hasAppeared = true }

        AnimatedVisibility(
            visible = hasAppeared && item.url !in removing.value,
            enter = fadeIn(tween(ANIM)) + expandHorizontally(tween(ANIM)),
            exit = fadeOut(tween(ANIM)) + shrinkHorizontally(tween(ANIM)),
        ) {
            QueueStripItem(
                item = item,
                index = index,
                prevIndex = prevIndexes.value[item.url],
                slotPx = slotPx,
                isDragging = isDragging,
                dragDx = dragDx,
                onPlay = { onPlay(index) },
                onRemove = {
                    if (item.url in removing.value) return@QueueStripItem
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
                onDrag = { dPx ->
                    dragTotalPx += dPx
                    val target =
                        (dragStartIndex + (dragTotalPx / slotPx).roundToInt()).coerceIn(
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

@Composable
private fun QueueStripItem(
    item: ContentItem,
    index: Int,
    prevIndex: Int?,
    slotPx: Float,
    isDragging: Boolean,
    dragDx: Float,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    var swipeDy by remember { mutableFloatStateOf(0f) }
    // FLIP slide for this card when its slot changes (not while dragging —
    // the drag offset is compensated exactly, animating would make it lag).
    val placement = remember(item.url) { Animatable(0f) }
    LaunchedEffect(prevIndex) {
        if (prevIndex != null && !isDragging) {
            val delta = (prevIndex - index) * slotPx
            if (delta != 0f) {
                placement.snapTo(delta)
                placement.animateTo(0f, tween(ANIM))
            }
        }
    }
    Box(
        modifier =
            Modifier
                .width(CARD_W)
                .height(CARD_H)
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationX = if (isDragging) dragDx else placement.value
                    translationY = swipeDy.coerceIn(-96f, 96f)
                    alpha = (1f - 0.6f * (swipeDy.coerceIn(-96f, 96f) / 96f)).coerceAtLeast(0.4f)
                }
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
                }
                .clickable { onPlay() },
    ) {
        Column(
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F1F1F)),
            ) {
                AsyncImage(
                    url = item.thumbnailUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                )
                item.durationMs
                    ?.takeIf { it > 0 }
                    ?.let { ms ->
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
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
            }
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.author?.name ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Dotted drag handle (right column, like the blockout).
        IconButton(
            onClick = {},
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                imageVector = Icons.Outlined.DragIndicator,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .size(22.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { change, delta ->
                                    onDrag(delta.x)
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

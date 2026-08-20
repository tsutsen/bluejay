package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tsutsen.platformplayer.core.designsystem.reorder.ReorderableItem
import com.tsutsen.platformplayer.core.designsystem.reorder.detectReorder
import com.tsutsen.platformplayer.core.designsystem.reorder.rememberReorderableLazyListState
import com.tsutsen.platformplayer.core.designsystem.reorder.reorderable
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.ui.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

// Both cards share the blockout's outer size (1.5:1); the current card is
// emphasised by a full-bleed thumbnail + play button, not a bigger card.
private val CARD_W = 240.dp
private val CARD_H = 160.dp
private val STRIP_GAP = 12.dp
private val ANIM = 180
// Right-hand drag-handle column: its own zone so the dots never overlap the
// thumbnail or the text, and pressing them never triggers the card swipe.
private val HANDLE_W = 36.dp

/**
 * Horizontal queue strip: the now-playing card first (full-bleed thumbnail
 * with a bottom gradient so the title/channel/duration stay readable, and a
 * play/pause button), then the queued cards (thumbnail, title, channel,
 * duration in their own text row, dotted drag handle in its own column).
 *
 * Reordering uses [rememberReorderableLazyListState]: drag the dots and the
 * list reorders live, the dragged card follows the finger, and a cancelled
 * drag springs the card back. Swipe a card up/down to remove it — it flies
 * out in the direction it was dragged. Long-press a card for the video
 * options sheet.
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
    onLongClick: (ContentItem) -> Unit,
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
                        .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
            ) {
                current?.let { item ->
                    NowPlayingCard(
                        item = item,
                        isPlaying = isPlaying,
                        onPlayPause = onPlayPause,
                        onLongClick = { onLongClick(item) },
                    )
                }
                QueuedCardStrip(
                    items = queue,
                    onPlay = onPlay,
                    onRemove = onRemove,
                    onMove = onMove,
                    onLongClick = onLongClick,
                    modifier = Modifier.weight(1f),
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
    onLongClick: () -> Unit,
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier =
            Modifier
                .width(CARD_W)
                .height(CARD_H)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1F1F1F))
                .combinedClickable(onClick = onPlayPause, onLongClick = onLongClick),
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
 * The queued cards on a reorderable [LazyRow]. New items are revealed by
 * scrolling the strip to them (an item added at the end is otherwise
 * off-screen and reads as "didn't appear").
 */
@Composable
private fun QueuedCardStrip(
    items: List<ContentItem>,
    onPlay: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onLongClick: (ContentItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state =
        rememberReorderableLazyListState(
            onMove = { from, to -> onMove(from.index, to.index) },
        )

    // Scroll to newly added items so an off-screen add is never invisible.
    val prevUrls = remember { mutableStateOf<Set<String>>(items.mapTo(mutableSetOf()) { it.url }) }
    LaunchedEffect(items) {
        val urls = items.mapTo(mutableSetOf()) { it.url }
        val added = urls - prevUrls.value
        prevUrls.value = urls
        if (added.isNotEmpty() && items.isNotEmpty()) {
            val index = items.indexOfFirst { it.url in added }
            state.listState.animateScrollToItem(index.coerceIn(0, items.size - 1))
        }
    }

    LazyRow(
        state = state.listState,
        modifier = modifier.reorderable(state),
        horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
    ) {
        itemsIndexed(items, key = { _, it -> it.url }) { index, item ->
            ReorderableItem(state, key = item.url) { isDragging ->
                QueueStripItem(
                    item = item,
                    isDragging = isDragging,
                    onPlay = { onPlay(index) },
                    onRemove = { onRemove(item.url) },
                    onLongClick = { onLongClick(item) },
                    handleModifier = Modifier.detectReorder(state),
                )
            }
        }
    }
}

@Composable
private fun QueueStripItem(
    item: ContentItem,
    isDragging: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onLongClick: () -> Unit,
    handleModifier: Modifier,
) {
    val density = LocalDensity.current
    val swipe = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val swipeJob = remember { mutableStateOf<Job?>(null) }
    val swipeThresholdPx = remember { with(density) { 80.dp.toPx() } }
    val swipeFlyPx = remember { with(density) { 280.dp.toPx() } }
    // Entry animation: items start collapsed and expand in.
    var hasAppeared by remember(item.url) { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasAppeared = true }

    AnimatedVisibility(
        visible = hasAppeared,
        enter = fadeIn(tween(ANIM)) + expandHorizontally(tween(ANIM)),
        exit = fadeOut(tween(ANIM)) + shrinkHorizontally(tween(ANIM)),
    ) {
        Box(
            modifier =
                Modifier
                    .width(CARD_W)
                    .height(CARD_H)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = swipe.value
                        alpha =
                            (1f -
                                (abs(swipe.value) / swipeFlyPx).coerceIn(0f, 1f) * 0.8f)
                                .coerceAtLeast(0.2f)
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // Content area: swipe up/down to remove, tap to play, long-press
            // for the video sheet. The handle column below is a sibling, so
            // dragging the dots never triggers the swipe (and vice versa).
            Box(
                modifier =
                    Modifier
                        .width(CARD_W - HANDLE_W)
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // The reorder drag starts on the handle column
                            // (a sibling), so it can never collide with this
                            // vertical swipe.
                            detectVerticalDragGestures(
                                onDragStart = { swipeJob.value?.cancel() },
                                onVerticalDrag = { change, dy ->
                                    // snapTo is a suspend function in this
                                    // Compose version — route it through the
                                    // main-immediate scope.
                                    swipeScope.launch {
                                        swipe.snapTo(swipe.value + dy)
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    swipeJob.value =
                                        swipeScope.launch {
                                            val v = swipe.value
                                            if (abs(v) > swipeThresholdPx) {
                                                // Fly out in the direction it
                                                // was dragged, then remove.
                                                val dir = if (v > 0f) 1f else -1f
                                                swipe.animateTo(
                                                    dir * swipeFlyPx,
                                                    tween(160, easing = FastOutSlowInEasing),
                                                )
                                                onRemove()
                                            } else {
                                                // Snap back home.
                                                swipe.animateTo(
                                                    0f,
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium,
                                                    ),
                                                )
                                            }
                                        }
                                },
                                onDragCancel = {
                                    swipeJob.value =
                                        swipeScope.launch {
                                            swipe.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium,
                                                ),
                                            )
                                        }
                                },
                            )
                        }
                        .combinedClickable(
                            onClick = onPlay,
                            onLongClick = onLongClick,
                        ),
            ) {
                Column(
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
                )
                    {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(92.dp)
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
                        Spacer(modifier = Modifier.height(8.dp))
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
            }
            // Dotted drag handle in its own column: never overlaps the
            // thumbnail or text, drag starts only from here.
            Box(
                modifier =
                    Modifier
                        .width(HANDLE_W)
                        .fillMaxSize()
                        .align(Alignment.CenterEnd),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = {}, modifier = handleModifier) {
                    Icon(
                        imageVector = Icons.Outlined.DragIndicator,
                        contentDescription = "Reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

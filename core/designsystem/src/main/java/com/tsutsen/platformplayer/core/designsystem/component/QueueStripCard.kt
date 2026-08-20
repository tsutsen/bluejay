package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.reorder.FlipItem
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.ui.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

// Both cards share the blockout's outer size (1.5:1).
private val CARD_W = 240.dp
private val CARD_H = 160.dp
private val STRIP_GAP = 12.dp
private val ANIM = 180
// Right-hand move-button column: its own zone so the arrows never overlap
// the thumbnail or the text, and pressing them never triggers the card
// swipe.
private val HANDLE_W = 44.dp

/**
 * Horizontal queue strip. Queued cards: thumbnail, title, channel, duration
 * in their own text row, move buttons (< earlier / > later) in their own
 * column.
 *
 * Reordering: tap < or > — the card slides into the new slot and the
 * neighbours slide in. Adding or removing a card animates the rest into
 * their new positions. Swipe a card up/down to remove it — it flies out in
 * the direction it was dragged. Long-press a card for the video options
 * sheet.
 */
@Composable
fun QueueStripCard(
    queue: List<ContentItem>,
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
            if (queue.isEmpty()) {
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
            QueuedCardStrip(
                items = queue,
                onPlay = onPlay,
                onRemove = onRemove,
                onMove = onMove,
                onLongClick = onLongClick,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * The queued cards on a plain scrollable [Row] (non-lazy): hold a card's
 * dots to reorder, swipe up/down to remove. New items are revealed by
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
    val scrollState = rememberScrollState()

    // FLIP: whenever the queue order changes, slide each item from its old
    // slot to the new one. Every card is exactly [stepPx] apart, so the
    // delta is (newIndex - oldIndex) * step. Driven from data, never from
    // position callbacks (which feed back on the animation itself and fire
    // constantly while scrolling).
    val flipAnims = remember { mutableMapOf<String, Animatable<Offset, *>>() }
    val stepPx = with(LocalDensity.current) { (CARD_W + STRIP_GAP).toPx() }
    val lastOrder = remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(items) {
        val prev = lastOrder.value
        val cur = items.map { it.url }
        lastOrder.value = cur
        if (prev == null || prev == cur) return@LaunchedEffect
        // Adds/removals reflow via their AnimatedVisibility enter/exit — the
        // layout itself slides the neighbours. Only a pure reorder (drag)
        // jumps instantly and needs the FLIP slide.
        val prevSet = prev.toSet()
        val curSet = cur.toSet()
        if (prevSet != curSet) return@LaunchedEffect
        val oldIndex = prev.withIndex().associate { (i, k) -> k to i }
        items.forEachIndexed { newIdx, item ->
            val oldIdx = oldIndex[item.url] ?: return@forEachIndexed
            if (oldIdx == newIdx) return@forEachIndexed
            val anim = flipAnims.getOrPut(item.url) { Animatable(Offset.Zero, Offset.VectorConverter) }
            val delta = (newIdx - oldIdx).toFloat() * stepPx
            anim.snapTo(Offset(delta, 0f))
            // Launch per item so all cards slide concurrently — awaiting
            // animateTo inline would run them one by one.
            launch {
                anim.animateTo(
                    Offset.Zero,
                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
                )
            }
        }
    }
    // Scroll to newly added items so an off-screen add is never invisible.
    val prevUrls = remember { mutableStateOf<Set<String>>(items.mapTo(mutableSetOf()) { it.url }) }
    LaunchedEffect(items) {
        val urls = items.mapTo(mutableSetOf()) { it.url }
        val added = urls - prevUrls.value
        prevUrls.value = urls
        if (added.isNotEmpty() && items.isNotEmpty()) {
            val index = items.indexOfFirst { it.url in added }
            scrollState.animateScrollTo(index * stepPx.toInt())
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
    ) {
        items.forEachIndexed { index, item ->
            // Keyed by url: reorders must NOT reset per-card state (the
            // position-scoped remember used to reset hasAppeared on every
            // move, hiding the card forever).
            key(item.url) {
            FlipItem(
                flip = flipAnims.getOrPut(item.url) { Animatable(Offset.Zero, Offset.VectorConverter) },
            ) {
                QueueStripItem(
                    item = item,
                    onPlay = { onPlay(index) },
                    onRemove = { onRemove(item.url) },
                    onLongClick = { onLongClick(item) },
                    canMoveEarlier = index > 0,
                    canMoveLater = index < items.size - 1,
                    onMoveEarlier = { onMove(index, index - 1) },
                    onMoveLater = { onMove(index, index + 1) },
                )
            }
            }
        }
    }
}

@Composable
private fun QueueStripItem(
    item: ContentItem,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onLongClick: () -> Unit,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
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
            // for the video sheet. The move-button column below is a
            // sibling, so tapping it never triggers the swipe (and vice
            // versa).
            Box(
                modifier =
                    Modifier
                        .width(CARD_W - HANDLE_W)
                        .fillMaxSize()
                        .pointerInput(Unit) {
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
                                                        dampingRatio =
                                                            Spring.DampingRatioMediumBouncy,
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
                                                    dampingRatio =
                                                        Spring.DampingRatioMediumBouncy,
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
                    modifier =
                        Modifier.padding(
                            start = 10.dp,
                            top = 10.dp,
                            end = 6.dp,
                            bottom = 10.dp,
                        ),
                ) {
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
            // Move buttons in the handle column (where the drag dots were):
            // < earlier / > later in the queue.
            Box(
                modifier =
                    Modifier
                        .width(HANDLE_W)
                        .fillMaxSize()
                        .align(Alignment.CenterEnd),
                contentAlignment = Alignment.Center,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onMoveEarlier,
                        enabled = canMoveEarlier,
                        shape = RoundedCornerShape(10.dp),
                        modifier =
                            Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(10.dp),
                                ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronLeft,
                            contentDescription = "Move earlier in queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(
                        onClick = onMoveLater,
                        enabled = canMoveLater,
                        shape = RoundedCornerShape(10.dp),
                        modifier =
                            Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(10.dp),
                                ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = "Move later in queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

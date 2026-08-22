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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
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
private val HANDLE_W = 48.dp

/**
 * Horizontal queue strip. The now-playing card (full-bleed thumbnail,
 * bottom gradient, play/pause) sits at its natural position in the strip —
 * it scrolls, swaps and reflows like any other card. Queued cards: 
 * thumbnail, title, channel, duration, and move buttons (< earlier / > 
 * later) in their own column.
 *
 * Reordering: tap < or > — the card slides into the new slot and the
 * swapped neighbour slides in. Adding or removing a card animates the rest
 * into their new positions. Swipe a card up/down to remove it — it flies
 * out in the direction it was dragged. Long-press a card for the video
 * options sheet.
 */
@Composable
fun QueueStripCard(
    queue: List<ContentItem>,
    current: ContentItem?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlayPause: () -> Unit,
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
                // Same height as the populated strip (8dp gap + CARD_H) so
                // the feed layout never jumps when the queue empties.
                Box(
                    modifier = Modifier.fillMaxWidth().height(8.dp + CARD_H),
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
                current = current,
                isPlaying = isPlaying,
                onPlay = onPlay,
                onRemove = onRemove,
                onMove = onMove,
                onPlayPause = onPlayPause,
                onLongClick = onLongClick,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * The queue cards on a plain scrollable [Row] (non-lazy): the now-playing
 * card at its natural position, move buttons to swap neighbours, swipe
 * up/down to remove. New items are revealed by scrolling the strip to them
 * (an item added at the end is otherwise off-screen and reads as "didn't
 * appear").
 */
@Composable
private fun QueuedCardStrip(
    items: List<ContentItem>,
    current: ContentItem?,
    isPlaying: Boolean,
    onPlay: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlayPause: () -> Unit,
    onLongClick: (ContentItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val viewportPx = remember { mutableStateOf(0) }
    // The now-playing card's position: items can't move in front of it.
    val currentIndex = items.indexOfFirst { it.url == current?.url }

    // FLIP for every list change (move, add, remove): each card's x is
    // remembered from the last layout pass; when a card's x jumps (a
    // neighbour appeared or vanished) it starts displaced by the delta and
    // springs back, so survivors slide into their new slots. Scrolling
    // moves every card by the same amount, so deltas cancel out and no
    // spurious animation fires.
    val flipAnims = remember { mutableMapOf<String, Animatable<Offset, *>>() }
    val flipJobs = remember { mutableMapOf<String, Job>() }
    val lastX = remember { mutableMapOf<String, Float>() }
    val stepPx = with(LocalDensity.current) { (CARD_W + STRIP_GAP).toPx() }

    /** Start a FLIP displacement of [url] by [dx] px, easing back to rest. */
    fun flip(url: String, dx: Float) {
        flipJobs[url]?.cancel()
        val anim = flipAnims.getOrPut(url) { Animatable(Offset.Zero, Offset.VectorConverter) }
        flipJobs[url] =
            scope.launch {
                anim.snapTo(Offset(dx, 0f))
                // Fixed-duration tween: consistent timing, no overshoot. A
                // spring lagged and bounced — the "laggy / abrupt" feel.
                anim.animateTo(Offset.Zero, tween(ANIM, easing = FastOutSlowInEasing))
            }
    }
    // Keep a reordered card centered so the user can follow it.
    fun followCard(index: Int) {
        val target = ((index + 0.5f) * stepPx - viewportPx.value / 2f).toInt()
        scope.launch {
            scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
        }
    }

    // Scroll to newly added items so an off-screen add is never invisible;
    // drop FLIP state of removed cards.
    val prevUrls = remember { mutableStateOf<Set<String>>(items.mapTo(mutableSetOf()) { it.url }) }
    LaunchedEffect(items) {
        val urls = items.mapTo(mutableSetOf()) { it.url }
        val added = urls - prevUrls.value
        prevUrls.value = urls
        lastX.keys.removeAll { it !in urls }
        if (added.isNotEmpty() && items.isNotEmpty()) {
            val index = items.indexOfFirst { it.url in added }
            scrollState.animateScrollTo(index * stepPx.toInt())
        }
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { viewportPx.value = it.width }
                .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(STRIP_GAP),
    ) {
        items.forEachIndexed { index, item ->
            // Keyed by url: reorders must NOT reset per-card state (the
            // position-scoped remember used to reset hasAppeared on every
            // move, hiding the card forever).
            key(item.url) {
            // FLIP measurement wrapper (outside the flip offset, so it
            // always reads the true layout position).
            Box(
                modifier =
                    Modifier.onGloballyPositioned {
                        // Add the scroll offset back so x is in content
                        // space: scrolling shifts the window x but leaves
                        // this value unchanged, so only a real reorder
                        // (index change) triggers a flip — scrolling never
                        // causes a spurious one on every card.
                        val x = it.boundsInWindow().left + scrollState.value
                        val old = lastX[item.url]
                        lastX[item.url] = x
                        // Epsilon: ignore sub-pixel drift from multi-fire /
                        // float noise. A real reorder moves a card by
                        // ~stepPx, far above this.
                        if (old != null && abs(old - x) > 2f) flip(item.url, old - x)
                    },
            ) {
            FlipItem(
                flip = flipAnims.getOrPut(item.url) { Animatable(Offset.Zero, Offset.VectorConverter) },
            ) {
                if (item.url == current?.url) {
                    NowPlayingCard(
                        item = item,
                        isPlaying = isPlaying,
                        onPlayPause = onPlayPause,
                        onLongClick = { onLongClick(item) },
                    )
                } else {
                    QueueStripItem(
                        item = item,
                        onPlay = { onPlay(index) },
                        onRemove = { onRemove(item.url) },
                        onLongClick = { onLongClick(item) },
                        // Nothing may move in front of the now-playing card.
                        canMoveEarlier =
                            if (currentIndex >= 0) index > currentIndex + 1 else index > 0,
                        canMoveLater = index < items.size - 1,
                        onMoveEarlier = {
                            onMove(index, index - 1)
                            followCard(index - 1)
                        },
                        onMoveLater = {
                            onMove(index, index + 1)
                            followCard(index + 1)
                        },
                    )
                }
            }
            }
            }
        }
        // Trailing inset so the last card doesn't sit flush against the
        // container's right edge — a real Spacer, not a padding modifier
        // on the Row, so it can't get clipped away by horizontalScroll.
        Spacer(modifier = Modifier.width(Tokens.SpaceLg))
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
    // Arrow tiles: rounded on the left edge only — the square right edge
    // meets the card edge (the card's own clip rounds the outer corner).
    val arrowShape =
        RoundedCornerShape(
            topStart = 10.dp,
            topEnd = 0.dp,
            bottomStart = 10.dp,
            bottomEnd = 0.dp,
        )
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
            // < earlier / > later in the queue. Both stretch to fill the
            // card height (minus padding).
            Column(
                modifier =
                    Modifier
                        .width(HANDLE_W)
                        .fillMaxSize()
                        .align(Alignment.CenterEnd)
                        // No end padding: the square right edge meets the card edge.
                        .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Disabled buttons melt into the card (same surface, dim
                // icon) so "can't move earlier/later" reads at a glance.
                val earlierEnabled = canMoveEarlier
                IconButton(
                    onClick = onMoveEarlier,
                    enabled = earlierEnabled,
                    modifier =
                        Modifier
                            .weight(1f)
                            .background(
                                if (earlierEnabled)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                arrowShape,
                            ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronLeft,
                        contentDescription = "Move earlier in queue",
                        tint =
                            if (earlierEnabled)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                val laterEnabled = canMoveLater
                IconButton(
                    onClick = onMoveLater,
                    enabled = laterEnabled,
                    modifier =
                        Modifier
                            .weight(1f)
                            .background(
                                if (laterEnabled)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                arrowShape,
                            ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Move later in queue",
                        tint =
                            if (laterEnabled)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp),
                    )
                }
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
        // 16:9 thumbnail pinned to the top edge; the band below holds the
        // gradient + meta (same proportions as the queued cards' thumbnails).
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1F1F1F)),
        ) {
            AsyncImage(
                url = item.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Bottom gradient (transparent -> card gray) so the title, channel
        // and duration stay readable over the thumbnail. Clipped to the card
        // radius so the inner drawing can't exceed the rounded bounds.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
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
        // Rounded-square tile with a small centered icon (a full-size icon
        // in a circle read as a giant blob).
        IconButton(
            onClick = onPlayPause,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 42.dp)
                    .size(48.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC009369)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
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


package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.tsutsen.platformplayer.core.designsystem.reorder.FlipItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.ui.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private const val QUEUE_ROW_HEIGHT_DP = 80
private const val ANIM = 180

/**
 * Shared vertical queue list (player queue sheet). Tap a row to play it,
 * tap the up/down arrows to move it through the queue (the row slides into
 * the new slot, the others slide in), tap X to remove (animated — the
 * remaining rows slide up into the freed space), long-press for the video
 * sheet.
 */
@Composable
fun QueueList(
    items: List<ContentItem>,
    onPlay: (Int) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onLongClick: (ContentItem) -> Unit,
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

    val scrollState = rememberScrollState()
    val removing = remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    // FLIP: slide each row from its old slot to the new one whenever the
    // queue changes (rows are all [QUEUE_ROW_HEIGHT_DP]dp tall, no gap).
    // Driven from data, never from position callbacks.
    val flipAnims = remember { mutableMapOf<String, Animatable<Offset, *>>() }
    val stepPx = with(LocalDensity.current) { QUEUE_ROW_HEIGHT_DP.dp.toPx() }
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
            anim.snapTo(Offset(0f, delta))
            anim.animateTo(
                Offset.Zero,
                spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(scrollState),
    ) {
        items.forEachIndexed { index, item ->
            // Keyed by url: reorders must NOT reset per-row state (the
            // position-scoped remember used to reset hasAppeared on every
            // move, hiding the row forever since LaunchedEffect(Unit)
            // doesn't re-fire).
            key(item.url) {
            // Entry animation only; removals animate by holding the row in
            // [removing] for one exit cycle before the data drops it.
            var hasAppeared by remember(item.url) { mutableStateOf(false) }
            LaunchedEffect(Unit) { hasAppeared = true }

            AnimatedVisibility(
                visible = hasAppeared && item.url !in removing.value,
                enter = fadeIn(tween(ANIM)) + expandVertically(tween(ANIM)),
                exit = fadeOut(tween(ANIM)) + shrinkVertically(tween(ANIM)),
            ) {
                FlipItem(
                    flip = flipAnims.getOrPut(item.url) { Animatable(Offset.Zero, Offset.VectorConverter) },
                ) {
                    QueueRow(
                        item = item,
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
                        onLongClick = { onLongClick(item) },
                        canMoveUp = index > 0,
                        canMoveDown = index < items.size - 1,
                        onMoveUp = { onMove(index, index - 1) },
                        onMoveDown = { onMove(index, index + 1) },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: ContentItem,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onLongClick: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(QUEUE_ROW_HEIGHT_DP.dp)
                .combinedClickable(onClick = onPlay, onLongClick = onLongClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF202124)),
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
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
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
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove from queue",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        // Move buttons where the drag dots were: up/down in the queue.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                shape = RoundedCornerShape(10.dp),
                modifier =
                    Modifier
                        .size(34.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(10.dp),
                        ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ExpandLess,
                    contentDescription = "Move up in queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                shape = RoundedCornerShape(10.dp),
                modifier =
                    Modifier
                        .size(34.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(10.dp),
                        ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = "Move down in queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

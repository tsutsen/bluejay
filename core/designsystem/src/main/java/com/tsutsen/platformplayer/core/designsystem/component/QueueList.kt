package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.tsutsen.platformplayer.core.designsystem.reorder.ReorderableListItem
import com.tsutsen.platformplayer.core.designsystem.reorder.detectReorder
import com.tsutsen.platformplayer.core.designsystem.reorder.rememberReorderableState
import com.tsutsen.platformplayer.core.designsystem.reorder.reorderable
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.ui.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private const val QUEUE_ROW_HEIGHT_DP = 68
private const val ANIM = 180

/**
 * Shared vertical queue list (player queue sheet). Tap a row to play it,
 * HOLD the dotted handle to reorder (the rows rearrange live while
 * dragging, the others slide into their new slots; a cancelled drag
 * springs back), tap X to remove (animated — the remaining rows slide
 * up into the freed space), long-press for the video sheet.
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
    val state =
        rememberReorderableState(
            onMove = { from, to -> onMove(from.index, to.index) },
            scrollState = scrollState,
            orientation = Orientation.Vertical,
        )
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val removing = remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // Outermost: must see (and consume) moves before the scroll
                // node while a drag is active.
                .reorderable(state)
                .onGloballyPositioned { containerCoords = it }
                .verticalScroll(scrollState),
    ) {
        items.forEachIndexed { index, item ->
            // Entry animation only; removals animate by holding the row in
            // [removing] for one exit cycle before the data drops it.
            var hasAppeared by remember(item.url) { mutableStateOf(false) }
            LaunchedEffect(Unit) { hasAppeared = true }

            AnimatedVisibility(
                visible = hasAppeared && item.url !in removing.value,
                enter = fadeIn(tween(ANIM)) + expandVertically(tween(ANIM)),
                exit = fadeOut(tween(ANIM)) + shrinkVertically(tween(ANIM)),
            ) {
                ReorderableListItem(
                    state = state,
                    index = index,
                    key = item.url,
                    container = containerCoords,
                ) { isDragging ->
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
                        handleModifier = Modifier.detectReorder(state),
                    )
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
    handleModifier: Modifier,
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
        IconButton(onClick = {}, modifier = handleModifier) {
            Icon(
                imageVector = Icons.Outlined.DragIndicator,
                contentDescription = "Reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

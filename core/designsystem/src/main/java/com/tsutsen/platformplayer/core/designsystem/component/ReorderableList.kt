package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Drag-to-reorder vertical list (replaces the old up/down-arrow reorder
 * dialogs). Each row: optional enable checkbox, label, drag handle.
 *
 * [items] is (id, label) in the current display order; the list keeps a
 * local draft that moves synchronously during the drag, and [onReordered]
 * fires once when the drag settles with the new order (persist once per
 * drag, not per crossed row).
 *
 * Pass [enabledIds] + [onToggleEnabled] to show an enable checkbox per
 * row — order and visibility in one list, as in the dual-screen popups.
 * The caller bounds the height via [modifier] — a LazyColumn is greedy
 * inside a dialog.
 */
@Composable
fun ReorderableList(
    items: List<Pair<String, String>>,
    onReordered: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    enabledIds: List<String>? = null,
    onToggleEnabled: ((String, Boolean) -> Unit)? = null,
) {
    var draft by remember { mutableStateOf(items.map { it.first }) }
    val labels = remember(items) { items.associate { it.first to it.second } }
    val lazyListState = rememberLazyListState()
    val onReorderedState by rememberUpdatedState(onReordered)
    val haptics = LocalHapticFeedback.current

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val current = draft.toMutableList()
        val moved = current.removeAt(from.index)
        current.add(to.index, moved)
        draft = current
    }

    LazyColumn(
        modifier = modifier,
        state = lazyListState,
    ) {
        itemsIndexed(draft, key = { _, id -> id }) { _, id ->
            ReorderableItem(reorderableState, key = id) { isDragging ->
                val background by animateColorAsState(
                    targetValue =
                        if (isDragging) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            Color.Transparent
                        },
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "rowBackground",
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(BluejayTokens().radius.sm))
                            .background(background)
                            .padding(
                                horizontal = Tokens.SpaceMd,
                                vertical = Tokens.SpaceXs,
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onToggleEnabled != null) {
                        Checkbox(
                            checked = id in (enabledIds ?: emptyList()),
                            onCheckedChange = { onToggleEnabled(id, it) },
                        )
                    }
                    Text(
                        text = labels[id] ?: id,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        modifier =
                            Modifier.draggableHandle(
                                onDragStarted = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onDragStopped = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onReorderedState(draft)
                                },
                            ),
                        onClick = {},
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DragIndicator,
                            contentDescription = "Reorder",
                        )
                    }
                }
            }
        }
    }
}

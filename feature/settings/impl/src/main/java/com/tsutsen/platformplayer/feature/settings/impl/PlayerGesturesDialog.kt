package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.datastore.model.PlayerGesturePreferences
import com.tsutsen.platformplayer.core.model.PlayerGestures

/**
 * Per-slot player gesture editor (Settings > Gestures > Player gestures).
 *
 * Layout follows the mockup: a 4×4 grid — one column per gesture type
 * (swipe, side-swipe, double-tap, hold), one row per surface slot
 * (top / bottom-left / bottom-center / bottom-right). Tapping a cell opens
 * an action picker for that (slot, gesture-type) pair; the pick applies
 * immediately and persists.
 */
@Composable
internal fun PlayerGesturesDialog(
    prefs: PlayerGesturePreferences,
    onSlotChange: (String, Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Local editable copy; each cell pick updates it and pushes the whole
    // slot map back to the VM (single persistence unit per slot).
    var draft by remember {
        mutableStateOf(
            mapOf(
                "top" to prefs.top.toMutableMap(),
                "bottomLeft" to prefs.bottomLeft.toMutableMap(),
                "bottomCenter" to prefs.bottomCenter.toMutableMap(),
                "bottomRight" to prefs.bottomRight.toMutableMap(),
            ),
        )
    }
    // (slot, type) of the cell whose picker is open; non-null = the dialog
    // shows the action list for that cell instead of the grid.
    var picking by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun commitSlot(slot: String) {
        val map = draft[slot]?.toMap() ?: return
        onSlotChange(slot, map)
    }

    AlertDialog(
        onDismissRequest = { picking ?: run { onDismiss() } },
        title = {
            val pick = picking
            Text(
                if (pick == null) {
                    "Player gestures"
                } else {
                    "${PlayerGestures.SLOT_LABELS[pick.first].orEmpty()} — " +
                        "${PlayerGestures.TYPE_LABELS[pick.second].orEmpty()}"
                },
            )
        },
        text = {
            val pick = picking
            if (pick == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Tap a cell to change what that gesture does.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Header row: gesture-type labels.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.width(112.dp))
                    PlayerGestures.GESTURE_TYPES.forEach { type ->
                        Text(
                            text = PlayerGestures.TYPE_LABELS[type].orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // One row per surface slot.
                PlayerGestures.SLOTS.forEach { slot ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = PlayerGestures.SLOT_LABELS[slot].orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(112.dp),
                        )
                        PlayerGestures.GESTURE_TYPES.forEach { type ->
                            val action = draft[slot]?.get(type).orEmpty()
                            val label = PlayerGestures.DISPLAY_NAMES[action].orEmpty()
                            val isCustom = action != PlayerGestures.NONE
                            Box(
                                modifier =
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isCustom) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    )
                                    .clickable { picking = slot to type },
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color =
                                    if (isCustom) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            } else {
                // Action list for the selected cell.
                val (slot, type) = pick
                LazyColumn {
                    items(PlayerGestures.OPTIONS_BY_TYPE[type].orEmpty()) { id ->
                        val selected = draft[slot]?.get(type) == id
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        draft[slot]?.set(type, id)
                                        commitSlot(slot)
                                        picking = null
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected) {
                                Text(
                                    text = "✓ ",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            Text(
                                text = PlayerGestures.DISPLAY_NAMES[id].orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picking ?: run { onDismiss() } }) { Text("Done") }
        },
        dismissButton = {
            if (picking != null) {
                TextButton(onClick = { picking = null }) { Text("Back") }
            }
        },
    )
}

package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
 * Inline per-slot player gesture editor (Settings > Gestures).
 *
 * Four rectangles: one wide one for the top zone (maps to the whole top
 * sector row) and three tall ones for the remaining shape (bottom-left +
 * middle-left, bottom-center + middle-center, bottom-right + middle-right).
 * Each rectangle holds four "<gesture>: <action>" buttons — hold, double
 * tap, h-swipe, v-swipe. Tapping a button opens the only popup: an action
 * picker for that cell. Unset cells show the canonical defaults.
 */
@Composable
internal fun PlayerGesturesEditor(
    prefs: PlayerGesturePreferences,
    onCellChange: (
        slot: String,
        type: String,
        action: String,
    ) -> Unit,
) {
    // (slot, type) of the cell being picked; null = no popup.
    var picking by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GestureSlotCard(
            slot = "top",
            slotMap = prefs.top,
            onCellClick = { type -> picking = "top" to type },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GestureSlotCard(
                slot = "bottomLeft",
                slotMap = prefs.bottomLeft,
                onCellClick = { type -> picking = "bottomLeft" to type },
                modifier = Modifier.weight(1f),
            )
            GestureSlotCard(
                slot = "bottomCenter",
                slotMap = prefs.bottomCenter,
                onCellClick = { type -> picking = "bottomCenter" to type },
                modifier = Modifier.weight(1f),
            )
            GestureSlotCard(
                slot = "bottomRight",
                slotMap = prefs.bottomRight,
                onCellClick = { type -> picking = "bottomRight" to type },
                modifier = Modifier.weight(1f),
            )
        }
    }

    // The single popup: action list for the selected cell.
    val pick = picking
    if (pick != null) {
        val (slot, type) = pick
        val slotMap =
            when (slot) {
                "top" -> prefs.top
                "bottomLeft" -> prefs.bottomLeft
                "bottomCenter" -> prefs.bottomCenter
                "bottomRight" -> prefs.bottomRight
                else -> emptyMap()
            }
        AlertDialog(
            onDismissRequest = { picking = null },
            title = {
                Text(
                    "${PlayerGestures.SLOT_LABELS[slot].orEmpty()} — " +
                        "${PlayerGestures.TYPE_LABELS[type].orEmpty()}",
                )
            },
            text = {
                LazyColumn {
                    items(PlayerGestures.OPTIONS_BY_TYPE[type].orEmpty()) { id ->
                        val selected = PlayerGestures.resolve(slot, type, slotMap) == id
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onCellChange(slot, type, id)
                                        picking = null
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (selected) "✓ " else "   ",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = PlayerGestures.DISPLAY_NAMES[id].orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("Done") } },
        )
    }
}

/** One rectangle: a slot's four "<gesture>: <action>" buttons. */
@Composable
private fun GestureSlotCard(
    slot: String,
    slotMap: Map<String, String>,
    onCellClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = PlayerGestures.SLOT_LABELS[slot].orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            PlayerGestures.GESTURE_TYPES.forEach { type ->
                val action = PlayerGestures.resolve(slot, type, slotMap)
                val isAssigned = action != PlayerGestures.NONE
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isAssigned) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            )
                            .clickable { onCellClick(type) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${PlayerGestures.TYPE_LABELS[type].orEmpty()}:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = PlayerGestures.DISPLAY_NAMES[action].orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (isAssigned) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.datastore.model.PlayerGestureSlotSet
import com.tsutsen.platformplayer.core.model.PlayerGestures

// The gesture cards are always black (mimicking the video player surface),
// so the inner colors are fixed instead of theme-derived.
private val PlayerBackground = Color.Black
private val TileAssigned = Color(0xFF2B2B2B)
private val TileUnassigned = Color(0xFF191919)
private val TextBright = Color(0xFFF2F2F2)
private val TextDim = Color(0xFF9A9A9A)

// Corners: the 4 outer corners of the combined shape stay rounded, the
// inner corners (facing the gaps) are barely rounded.
private val OuterRadius = 12.dp
private val InnerRadius = 4.dp

/**
 * Inline per-slot player gesture editor (Settings > Gestures), one per
 * player mode.
 *
 * Four rectangles: one wide one for the top zone (maps to the whole top
 * sector row) and three tall ones for the remaining shape (bottom-left +
 * middle-left, bottom-center + middle-center, bottom-right + middle-right).
 * Each rectangle holds four "<gesture>: <action>" buttons — hold, double
 * tap, h-swipe, v-swipe. Tapping a button opens the action picker for that
 * cell (same style as the other settings pickers). Unset cells show the
 * canonical defaults for this mode.
 */
@Composable
internal fun PlayerGesturesEditor(
    mode: String,
    slotSet: PlayerGestureSlotSet,
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GestureSlotCard(
            mode = mode,
            slot = "top",
            slotMap = slotSet.top,
            onCellClick = { type -> picking = "top" to type },
            shape = RoundedCornerShape(OuterRadius, OuterRadius, InnerRadius, InnerRadius),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GestureSlotCard(
                mode = mode,
                slot = "bottomLeft",
                slotMap = slotSet.bottomLeft,
                onCellClick = { type -> picking = "bottomLeft" to type },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(InnerRadius, InnerRadius, InnerRadius, OuterRadius),
            )
            GestureSlotCard(
                mode = mode,
                slot = "bottomCenter",
                slotMap = slotSet.bottomCenter,
                onCellClick = { type -> picking = "bottomCenter" to type },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(InnerRadius),
            )
            GestureSlotCard(
                mode = mode,
                slot = "bottomRight",
                slotMap = slotSet.bottomRight,
                onCellClick = { type -> picking = "bottomRight" to type },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(InnerRadius, InnerRadius, OuterRadius, InnerRadius),
            )
        }
    }

    // The single popup: action list for the selected cell.
    picking?.let { (slot, type) ->
        val options =
            PlayerGestures.optionsFor(mode, type).map { id ->
                PlayerGestures.DISPLAY_NAMES[id].orEmpty() to id
            }
        ChoiceDialog(
            title =
                "${PlayerGestures.SLOT_LABELS[slot].orEmpty()} — " +
                    "${PlayerGestures.TYPE_LABELS[type].orEmpty()}",
            options = options,
            selected = PlayerGestures.resolve(mode, slot, type, slotSet[slot]),
            onSelected = { action ->
                onCellChange(slot, type, action)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

private operator fun PlayerGestureSlotSet.get(slot: String): Map<String, String> =
    when (slot) {
        "top" -> top
        "bottomLeft" -> bottomLeft
        "bottomCenter" -> bottomCenter
        "bottomRight" -> bottomRight
        else -> emptyMap()
    }

/** One rectangle: a slot's four "<gesture>: <action>" buttons. */
@Composable
private fun GestureSlotCard(
    mode: String,
    slot: String,
    slotMap: Map<String, String>,
    onCellClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = PlayerBackground,
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = PlayerGestures.SLOT_LABELS[slot].orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = TextBright,
            )
            PlayerGestures.GESTURE_TYPES.forEach { type ->
                val action = PlayerGestures.resolve(mode, slot, type, slotMap)
                val isAssigned = action != PlayerGestures.NONE
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isAssigned) TileAssigned else TileUnassigned,
                            )
                            .clickable { onCellClick(type) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${PlayerGestures.TYPE_LABELS[type].orEmpty()}:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = PlayerGestures.DISPLAY_NAMES[action].orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isAssigned) TextBright else TextDim,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

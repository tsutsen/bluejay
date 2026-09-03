package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.tsutsen.platformplayer.core.datastore.model.PlayerGestureSlotSet
import com.tsutsen.platformplayer.core.designsystem.icon.TouchDouble
import com.tsutsen.platformplayer.core.designsystem.icon.TouchLong
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.PlayerGestures

// The editor mimics a video player: pure black cards with flat tiles
// (no tile fill — state is shown through the tint only), so the inner
// colors are fixed instead of theme-derived.
private val CardBackground = Color.Black
private val TextBright = Color(0xFFF2F2F2)
private val TextDim = Color(0xFF9A9A9A)

private val GESTURE_ICONS: Map<String, ImageVector> =
    mapOf(
        "hold" to TouchLong,
        "double_tap" to TouchDouble,
        "swipe_h" to Icons.Filled.Swipe,
        "swipe_v" to Icons.Filled.SwipeVertical,
    )

/**
 * Per-slot player gesture editor for one player mode.
 *
 * Layout (mimicking the player screen): one panel with the top zone's four
 * tiles in a row, three panels with the bottom zones' tiles in a 2x2 grid,
 * and a slim decorative bar (back/play/forward) making the target clear.
 * Tiles show the gesture-type icon; the label is the selected action.
 * Tapping a tile opens the action picker for that cell (same style as the
 * other settings pickers). Unset cells show the canonical defaults.
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
    val radius = BluejayTokens().radius
    // Stacked-group corner language: the first/last element of the stack
    // keeps the outer rounding on its edge, everything in between and all
    // corners facing the gaps uses the smaller radius.
    val outer = radius.md
    val inner = radius.xs

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
    ) {
        // Top zone: four tiles in a row.
        GesturePanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(outer, outer, inner, inner),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
            ) {
                PlayerGestures.GESTURE_TYPES.forEach { type ->
                    GestureTile(
                        type = type,
                        action = PlayerGestures.resolve(mode, "top", type, slotSet.top),
                        modifier = Modifier.weight(1f),
                    ) { picking = "top" to type }
                }
            }
        }
        // Bottom zones: three panels, each a 2x2 tile grid. Middle of the
        // stack — the same small radius on every corner.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
        ) {
            listOf("bottomLeft", "bottomCenter", "bottomRight").forEach { slot ->
                val slotMap = slotSet[slot]
                GesturePanel(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(inner),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
                        ) {
                            listOf("hold", "double_tap").forEach { type ->
                                GestureTile(
                                    type = type,
                                    action =
                                        PlayerGestures.resolve(mode, slot, type, slotMap),
                                    modifier = Modifier.weight(1f),
                                ) { picking = slot to type }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
                        ) {
                            listOf("swipe_h", "swipe_v").forEach { type ->
                                GestureTile(
                                    type = type,
                                    action =
                                        PlayerGestures.resolve(mode, slot, type, slotMap),
                                    modifier = Modifier.weight(1f),
                                ) { picking = slot to type }
                            }
                        }
                    }
                }
            }
        }
        // Aesthetic hint bar: half a tile row tall, back/play/forward
        // centered — a reminder that this configures the player itself.
        // Last element of the stack: small radius on top, outer below.
        PlayerHintBar(shape = RoundedCornerShape(inner, inner, outer, outer))
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

/** Black card that groups a zone's tiles. */
@Composable
private fun GesturePanel(
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(CardBackground)
                .padding(Tokens.SpaceXs),
    ) {
        content()
    }
}

/** One flat tile: gesture-type icon on top, the selected action below. */
@Composable
private fun GestureTile(
    type: String,
    action: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isAssigned = action != PlayerGestures.NONE
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(BluejayTokens().radius.sm))
                .background(Color.Transparent)
                .clickable(onClick = onClick)
                .padding(vertical = Tokens.SpaceMd, horizontal = Tokens.SpaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = GESTURE_ICONS[type]!!,
                contentDescription = null,
                modifier = Modifier.size(Tokens.IconMd),
                tint = if (isAssigned) TextBright else TextDim,
            )
        }
        Spacer(modifier = Modifier.height(Tokens.SpaceXs))
        Text(
            text = PlayerGestures.DISPLAY_NAMES[action].orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isAssigned) TextBright else TextDim,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Decorative back/play/forward strip — half a tile row tall. */
@Composable
private fun PlayerHintBar(shape: RoundedCornerShape) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CardBackground)
                .height(Tokens.IconMd + Tokens.SpaceSm),
        horizontalArrangement =
            Arrangement.spacedBy(Tokens.SpaceXl, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Replay10,
            contentDescription = null,
            modifier = Modifier.size(Tokens.IconSm),
            tint = TextDim,
        )
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(Tokens.IconSm),
            tint = TextDim,
        )
        Icon(
            imageVector = Icons.Filled.Forward30,
            contentDescription = null,
            modifier = Modifier.size(Tokens.IconSm),
            tint = TextDim,
        )
    }
}

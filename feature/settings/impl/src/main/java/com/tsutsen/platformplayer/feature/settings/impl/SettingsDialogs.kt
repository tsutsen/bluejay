package com.tsutsen.platformplayer.feature.settings.impl

import com.tsutsen.platformplayer.core.ui.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.component.ReorderableList
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.PlaylistOption

/** Dialog for choosing a numeric value (subtitle size in pt, padding in dp). */
@Composable
internal fun StepperDialog(
    title: String,
    value: Int,
    step: Int,
    min: Int,
    max: Int,
    suffix: String,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { draft = (draft - step).coerceAtLeast(min) }) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.settings_decrease))
                }
                Text("$draft$suffix", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { draft = (draft + step).coerceAtMost(max) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_increase))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelected(draft) }) { Text(stringResource(R.string.settings_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
internal fun MultiSelectDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: List<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (label, value) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(value, value !in selected) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = value in selected,
                            onCheckedChange = { onToggle(value, it) },
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) }
        },
    )
}

@Composable
internal fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (label, value) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(value) }
                                .padding(vertical = Tokens.SpaceXs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelected(value) },
                        )
                        Spacer(Modifier.width(Tokens.SpaceSm))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        },
    )
}

/**
 * Reorder (and optionally enable/disable) a list of items via a
 * drag-reorderable list. [items] is (id, label) in the current display
 * order; [onChange] receives the new ordered ids when a drag settles.
 *
 * Pass [enabledIds] + [onToggleEnabled] to also show an enable/disable
 * checkbox per row — order and visibility in one popup.
 */
@Composable
internal fun ReorderDialog(
    title: String,
    items: List<Pair<String, String>>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    enabledIds: List<String>? = null,
    onToggleEnabled: ((String, Boolean) -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ReorderableList(
                items = items,
                onReordered = onChange,
                enabledIds = enabledIds,
                onToggleEnabled = onToggleEnabled,
                // ponytail: fixed cap — a LazyColumn is greedy inside an
                // AlertDialog and would expand the dialog to window height;
                // 400dp fits every current list (max 7 rows) without
                // scrolling. Replace with a token if one exists for dialog
                // max heights.
                modifier = Modifier.heightIn(max = 400.dp),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
}

/**
 * Thumbnail tint picker: preset hues at full color (strength is its own
 * slider below the Tint row), plus None.
 */
@Composable
internal fun ThumbnailTintDialog(
    current: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_thumbnail_tint)) },
        text = {
            Column {
                Text(
                    "The color laid over every video thumbnail. Strength and blend mode are set below the Tint row.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Tokens.SpaceMd))
                PresetColors.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
                        row.forEach { argb ->
                            val opaque = argb.toLong() or (0xFFL shl 24)
                            // Legacy tints carry a baked-in 30% alpha; match
                            // by RGB so the picked swatch still highlights.
                            ColorSwatch(
                                color = Color(opaque),
                                isSelected = current != null && (current and 0x00FFFFFFL) == (opaque and 0x00FFFFFFL),
                                onClick = { onPick(opaque) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Tokens.SpaceSm))
                TextButton(onClick = { onPick(null) }) {
                    Text(stringResource(R.string.settings_none), color = if (current == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
        },
    )
}

/**
 * Configure the four 2x2 second-screen library slots. Tapping a slot opens a
 * picker of all sections + all playlists; picking one assigns it to that slot.
 */
@Composable
internal fun SlotsDialog(
    slots: List<String>,
    sectionNames: Map<String, String>,
    playlists: List<PlaylistOption>,
    onSetSlot: (Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Library slots" else "Slot ${editing!! + 1}") },
        text = {
            if (editing == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until 4).forEach { index ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(BluejayTokens().radius.md))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { editing = index }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_slot_1_s, index + 1),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(Tokens.SpaceSm))
                            Text(
                                text = slotLabel(slots.getOrNull(index) ?: "", sectionNames, playlists),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    sectionNames.forEach { (id, name) ->
                        SlotPickRow(
                            name = name,
                            selected = slots.getOrNull(editing!!) == id,
                            onClick = {
                                onSetSlot(editing!!, id)
                                editing = null
                            },
                        )
                    }
                    if (playlists.isNotEmpty()) {
                        Spacer(Modifier.height(Tokens.SpaceSm))
                        Text(
                            text = stringResource(R.string.settings_playlists),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    playlists.forEach { p ->
                        val value = "playlist:${p.id}"
                        SlotPickRow(
                            name = p.name,
                            selected = slots.getOrNull(editing!!) == value,
                            onClick = {
                                onSetSlot(editing!!, value)
                                editing = null
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
}

@Composable
internal fun SlotPickRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = Tokens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(Tokens.SpaceSm))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

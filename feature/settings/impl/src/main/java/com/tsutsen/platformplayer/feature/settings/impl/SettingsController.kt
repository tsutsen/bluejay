package com.tsutsen.platformplayer.feature.settings.impl

import android.provider.Settings
import com.tsutsen.platformplayer.core.ui.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.groupShape
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.datastore.model.ControllerBinding
import com.tsutsen.platformplayer.core.model.PlayerControllerActions
import com.tsutsen.platformplayer.core.ui.GamepadKeyBus

internal fun writeSettingsGranted(context: android.content.Context): Boolean =
    runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

/**
 * One controller action: shows the bound key (and the device it was bound
 * with). Tap to bind a new key; Clear removes the custom binding (back to
 * the default key).
 */
/** Icon tile per controller action, matching the other settings rows. */
internal fun controllerActionIcon(id: String) =
    when (id) {
        PlayerControllerActions.PLAY_PAUSE -> Icons.Filled.PlayArrow
        PlayerControllerActions.SEEK_BACK -> Icons.Filled.Replay10
        PlayerControllerActions.SEEK_FORWARD -> Icons.Filled.Forward30
        PlayerControllerActions.SPEED_UP, PlayerControllerActions.SPEED_DOWN -> Icons.Filled.Speed
        PlayerControllerActions.NEXT -> Icons.Filled.SkipNext
        PlayerControllerActions.PREVIOUS -> Icons.Filled.SkipPrevious
        PlayerControllerActions.CLOSE -> Icons.Filled.Close
        PlayerControllerActions.BRIGHTNESS_UP, PlayerControllerActions.BRIGHTNESS_DOWN -> Icons.Filled.BrightnessAuto
        PlayerControllerActions.VOLUME_UP -> Icons.Filled.VolumeUp
        PlayerControllerActions.VOLUME_DOWN -> Icons.Filled.VolumeDown
        else -> Icons.Filled.Games
    }

@Composable
internal fun ControllerBindingRow(
    action: PlayerControllerActions.Action,
    binding: ControllerBinding?,
    onCapture: () -> Unit,
    onClear: () -> Unit,
    groupPosition: GroupPosition = GroupPosition.Single,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onCapture),
        shape = groupShape(groupPosition),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Tokens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(Tokens.AvatarMd)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(BluejayTokens().radius.sm),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    controllerActionIcon(action.id),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(Tokens.IconMd),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(action.label, style = MaterialTheme.typography.titleSmall)
                val b = binding
                Text(
                    text =
                        if (b == null) {
                            "${PlayerControllerActions.labelFor(action.defaultKeyCode)} (default)"
                        } else {
                            buildString {
                                append(PlayerControllerActions.labelFor(b.keyCode))
                                b.deviceName?.let { append("  ·  ").append(it) }
                            }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (binding != null) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.settings_clear)) }
            }
            Text(stringResource(R.string.settings_tap_to_bind), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Binding capture popup: the first controller/remote input that arrives is
 * bound to the action.
 *
 * Cemu's design (InputBindingPopup): a non-focusable Compose [Popup], not a
 * [Dialog] — the activity window keeps input focus the whole time, so
 * controller keys and generic motion events keep flowing through
 * `Activity.dispatchKeyEvent` / `dispatchGenericMotionEvent` into
 * [GamepadKeyBus.events] (a [Dialog] steals focus and the first press is
 * lost in the window-focus transition). While [GamepadKeyBus.beginCapture]
 * is active the activity consumes every input (Cemu's `hasSubscribers`
 * rule): nothing leaks to the screen behind and BACK cannot dismiss this
 * mid-capture.
 */
/**
 * Captures the next gamepad/TV-remote key press and binds it to [action].
 * Public so the getting-started tour can reuse the same capture UX.
 */
@Composable
fun ControllerBindingPopup(
    action: PlayerControllerActions.Action,
    onBound: (keyCode: Int, deviceName: String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) {
        GamepadKeyBus.beginCapture()
        try {
            GamepadKeyBus.events.collect { event ->
                // Bind on press edges only; a stray release from a button
                // held before the popup opened must not consume the slot.
                if (event.isPress) onBound(event.keyCode, event.deviceName)
            }
        } finally {
            GamepadKeyBus.endCapture()
        }
    }
    Popup(alignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier =
                    Modifier
                        .sizeIn(maxWidth = 560.dp, maxHeight = 560.dp)
                        .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_bind_1_s, action.label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.settings_press_a_button_d_pad_direction_or_push_a_stick_fully),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onClear) { Text(stringResource(R.string.settings_clear)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
                    }
                }
            }
        }
    }
}

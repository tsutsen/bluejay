package com.tsutsen.platformplayer.feature.player.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.model.CastDevice
import com.tsutsen.platformplayer.core.model.CastState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun CastingSheet(
    castState: StateFlow<CastState>,
    onConnect: (CastDevice) -> Unit,
    onConnectByUrl: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by castState.collectAsState()
    var urlInput by remember { mutableStateOf("") }

    // No verticalScroll here: BluejayModalBottomSheet (scroll = true,
    // the default) already wraps content in a scrollable column, and
    // nesting a second verticalScroll crashed on infinite height.
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val active = state.activeDevice
        if (active != null) {
            DeviceRow(
                device = active,
                trailing = {
                    TextButton(onClick = onDisconnect) {
                        Text(if (state.isConnecting) "Cancel" else "Disconnect")
                    }
                },
            )
            if (state.isConnecting && !state.isCasting) {
                Text(
                    text = "Connecting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 8.dp),
                )
            }
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        }

        if (state.discoveredDevices.isNotEmpty()) {
            Text(
                text = "Discovered",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
            state.discoveredDevices.forEach { device ->
                DeviceRow(device = device, onClick = { onConnect(device) })
            }
        }

        if (state.rememberedDevices.isNotEmpty()) {
            Text(
                text = "Saved",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
            state.rememberedDevices.forEach { device ->
                DeviceRow(device = device, onClick = { onConnect(device) })
            }
        }

        if (state.discoveredDevices.isEmpty() && state.rememberedDevices.isEmpty() &&
            active == null
        ) {
            Text(
                text = "No receivers found yet. Discovery runs in the background; " +
                    "you can also add one by address below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
            )
        }

        if (!state.isCasting && !state.isConnecting) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Receiver URL (http://ip:port)") },
                    placeholder = { Text("http://192.168.1.50:8000") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = urlInput.isBlank(),
                    ) {
                        Text("Cancel")
                    }
                    OutlinedButton(
                        onClick = {
                            onConnectByUrl(urlInput.trim())
                            urlInput = ""
                        },
                        enabled = urlInput.isNotBlank(),
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: CastDevice,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = device.type,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            IconButton(onClick = { onClick?.invoke() }) {
                Icon(
                    imageVector = Icons.Default.Cast,
                    contentDescription = "Connect to ${device.name}",
                )
            }
        }
    }
}

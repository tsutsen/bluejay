/*
 * Plugin Browser Scene (Compose)
 *
 * A Compose-based plugin browser that displays available Grayjay plugins.
 * Users can toggle plugins on/off to enable/disable them.
 */

package com.futo.platformplayer.compose.plugins

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.states.StatePlatform
import kotlinx.coroutines.launch

private const val TAG = "PluginBrowserScene"

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val configUrl: String,
    val isInstalled: Boolean,
    val isEnabled: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginBrowserScene() {
    val coroutineScope = rememberCoroutineScope()
    val enabledClientIds = remember { mutableStateOf(setOf<String>()) }
    val installedPlugins = remember { mutableStateOf<List<SourcePluginConfig>>(emptyList()) }

    // Load installed plugins
    LaunchedEffect(Unit) {
        Log.d(TAG, "Loading plugins...")
        val clients = StatePlatform.instance.getAvailableClients()
        Log.d(TAG, "Available clients: ${clients.size}")
        val enabledIds = StatePlatform.instance.getEnabledClients().map { it.id }.toSet()
        Log.d(TAG, "Enabled clients: ${enabledIds.size}, IDs: $enabledIds")
        val installed = clients.filterIsInstance<JSClient>().map { it.config }
        Log.d(TAG, "Installed plugins: ${installed.size}")
        installedPlugins.value = installed
        enabledClientIds.value = enabledIds
    }

    val plugins = remember(installedPlugins.value, enabledClientIds.value) {
        val result = installedPlugins.value.map { config ->
            PluginInfo(
                id = config.id,
                name = config.name,
                description = config.description ?: "",
                configUrl = config.sourceUrl ?: "",
                isInstalled = true,
                isEnabled = enabledClientIds.value.contains(config.id)
            )
        }
        Log.d(TAG, "Plugins list created with ${result.size} items")
        result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse Plugins") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text("Found ${plugins.size} plugins")
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(plugins, key = { it.id }) { plugin ->
                    PluginCard(
                        plugin = plugin,
                        onToggle = { isEnabled ->
                            Log.d(TAG, "Toggle ${plugin.name}: $isEnabled")
                            coroutineScope.launch {
                                try {
                                    if (isEnabled) {
                                        Log.d(TAG, "Enabling ${plugin.name}")
                                        StatePlatform.instance.enableClient(listOf(plugin.id))
                                    } else {
                                        Log.d(TAG, "Disabling ${plugin.name}")
                                        val currentEnabled = StatePlatform.instance.getEnabledClients()
                                        val newEnabled = currentEnabled.map { it.id }.filter { it != plugin.id }
                                        StatePlatform.instance.selectClients(*newEnabled.toTypedArray())
                                    }
                                    val updatedEnabled = StatePlatform.instance.getEnabledClients().map { it.id }.toSet()
                                    Log.d(TAG, "Updated enabled: $updatedEnabled")
                                    enabledClientIds.value = updatedEnabled
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error toggling plugin", e)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PluginCard(
    plugin: PluginInfo,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = plugin.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = plugin.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = plugin.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

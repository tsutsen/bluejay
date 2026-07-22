/*
 * Plugin Browser Scene (Compose)
 *
 * A Compose-based plugin browser that displays available Grayjay plugins.
 * Users can toggle plugins on/off to enable/disable them.
 */

package com.futo.platformplayer.compose.plugins

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futo.platformplayer.api.http.ManagedHttpClient
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
fun PluginBrowserScene(onPluginClick: (String) -> Unit = {}) {
    val coroutineScope = rememberCoroutineScope()
    val enabledClientIds = remember { mutableStateOf(setOf<String>()) }
    val installedPlugins = remember { mutableStateOf<List<SourcePluginConfig>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedPluginUrl by remember { mutableStateOf<String?>(null) }

    // If a plugin is selected, show its details
    if (selectedPluginUrl != null) {
        PluginDetailScene(
            configUrl = selectedPluginUrl!!,
            onBack = { selectedPluginUrl = null }
        )
        return
    }

    fun loadPluginsAndEnabledState() {
        val clients = StatePlatform.instance.getAvailableClients()
        val enabledIds = StatePlatform.instance.getEnabledClients().map { it.id }.toSet()
        val installed = clients.filterIsInstance<JSClient>().map { it.config }
        installedPlugins.value = installed
        enabledClientIds.value = enabledIds
    }

    // Load installed plugins on first composition
    LaunchedEffect(Unit) {
        Log.d(TAG, "Loading plugins (first time)...")
        loadPluginsAndEnabledState()
    }

    // Refresh when tab becomes visible (when refreshKey changes)
    LaunchedEffect(refreshKey) {
        Log.d(TAG, "Refreshing plugins (key: $refreshKey)...")
        loadPluginsAndEnabledState()
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
                                    refreshKey++
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error toggling plugin", e)
                                }
                            }
                        },
                        onClick = {
                            Log.d(TAG, "Clicked plugin: ${plugin.name}, URL: ${plugin.configUrl}")
                            selectedPluginUrl = plugin.configUrl
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
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                Log.d(TAG, "PluginCard clickable triggered: ${plugin.name}")
                onClick()
            },
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
            onCheckedChange = onToggle,
            enabled = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginDetailScene(configUrl: String, onBack: () -> Unit) {
    var config by remember { mutableStateOf<SourcePluginConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(configUrl) {
        try {
            Log.d(TAG, "Fetching plugin config from: $configUrl")
            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ManagedHttpClient().get(configUrl)
            }
            if (response.isOk && response.body != null) {
                val configJson = response.body.string()
                val loadedConfig = SourcePluginConfig.fromJson(configJson)
                config = loadedConfig
                Log.d(TAG, "Loaded config: ${loadedConfig.name}")
                
                // Check if this plugin is enabled
                val enabledClients = StatePlatform.instance.getEnabledClients()
                isEnabled = enabledClients.any { it.id == loadedConfig.id }
            } else {
                error = "Failed to load config"
                Log.e(TAG, "Failed to load config: ${response.isOk}, ${response.body}")
            }
            isLoading = false
        } catch (e: Exception) {
            error = e.message
            Log.e(TAG, "Error loading config", e)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugin Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        content = {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Error: $error")
                    }
                }
                config != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = config!!.name,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = config!!.description ?: "No description",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Version: ${config!!.version}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Author: ${config!!.author ?: "Unknown"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "URL: ${configUrl}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        // Update button
                        Button(
                            onClick = {
                                Log.d(TAG, "Update button clicked")
                                // TODO: Implement update functionality
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text("Check for Updates")
                        }

                        // Authentication buttons
                        if (config!!.authentication != null) {
                            val context = LocalContext.current
                            val loginUrl = config!!.authentication!!.loginUrl
                            
                            Button(
                                onClick = {
                                    Log.d(TAG, "Opening login URL: $loginUrl")
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to open login URL", e)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Login")
                            }
                            
                            // Show login warning if present
                            config!!.authentication!!.loginWarning?.let { warning ->
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            
                            // Show additional warnings
                            config!!.authentication!!.loginWarnings?.forEach { warning ->
                                Text(
                                    text = warning.text ?: warning.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Import buttons (shown when enabled)
                        if (isEnabled) {
                            Button(
                                onClick = {
                                    Log.d(TAG, "Import subscriptions button clicked")
                                    // TODO: Implement import subscriptions
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("Import Subscriptions")
                            }

                            Button(
                                onClick = {
                                    Log.d(TAG, "Import playlists button clicked")
                                    // TODO: Implement import playlists
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("Import Playlists")
                            }
                        }

                        // Uninstall button
                        OutlinedButton(
                            onClick = {
                                Log.d(TAG, "Uninstall button clicked")
                                // TODO: Implement uninstall functionality
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Uninstall")
                        }
                    }
                }
            }
        }
    )
}

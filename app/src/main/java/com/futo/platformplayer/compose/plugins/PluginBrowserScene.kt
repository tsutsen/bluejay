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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.futo.platformplayer.activities.LoginActivity
import com.futo.platformplayer.api.http.ManagedHttpClient
import com.futo.platformplayer.api.media.IPlatformClient
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StatePlugins
import com.futo.platformplayer.states.StatePlatform
import com.futo.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PluginBrowserScene"

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val configUrl: String,
    val isInstalled: Boolean,
    val isEnabled: Boolean,
    val isAuthenticated: Boolean = false
)

data class ChannelImportItem(
    val url: String,
    val name: String,
    val thumbnail: String?
)

data class PlaylistImportItem(
    val url: String,
    val name: String
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
            // Check if plugin has auth configured
            val descriptor = StatePlugins.instance.getPlugin(config.id)
            val isAuthenticated = descriptor?.getAuth() != null
            Log.d(TAG, "Plugin ${config.name} (${config.id}): descriptor=${descriptor != null}, auth=${descriptor?.getAuth() != null}")
            
            PluginInfo(
                id = config.id,
                name = config.name,
                description = config.description ?: "",
                configUrl = config.sourceUrl ?: "",
                isInstalled = true,
                isEnabled = enabledClientIds.value.contains(config.id),
                isAuthenticated = isAuthenticated
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = plugin.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (plugin.isAuthenticated) {
                    Text(
                        text = "✓ Logged In",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
    val coroutineScope = rememberCoroutineScope()
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
                
                // Check if plugin has auth
                val descriptor = StatePlugins.instance.getPlugin(loadedConfig.id)
                val hasAuth = descriptor?.getAuth() != null
                Log.d(TAG, "Plugin ${loadedConfig.name} auth status: $hasAuth")
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
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
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
                            
                            Button(
                                onClick = {
                                    Log.d(TAG, "Opening login activity for: ${config!!.name} (id: ${config!!.id})")
                                    try {
                                        LoginActivity.showLogin(context, config!!) { auth ->
                                            if (auth != null) {
                                                Log.d(TAG, "Login successful, saving auth for ${config!!.name}")
                                                Log.d(TAG, "Auth cookieMap size: ${auth.cookieMap?.size}, headers size: ${auth.headers?.size}")
                                                try {
                                                    StatePlugins.instance.setPluginAuth(config!!.id, auth)
                                                    Log.d(TAG, "Auth saved successfully")
                                                    // Enable the plugin if not already enabled
                                                    val currentEnabled = StatePlatform.instance.getEnabledClients()
                                                    if (!currentEnabled.any { it.id == config!!.id }) {
                                                        Log.d(TAG, "Enabling plugin ${config!!.name} after login")
                                                        StateApp.instance.scope.launch(Dispatchers.IO) {
                                                            StatePlatform.instance.enableClient(listOf(config!!.id))
                                                        }
                                                    }
                                                    // Reload the client to apply the new auth
                                                    StateApp.instance.scope.launch(Dispatchers.IO) {
                                                        StatePlatform.instance.reloadClient(context, config!!.id) {
                                                            Log.d(TAG, "Client reloaded after login")
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Failed to set plugin auth", e)
                                                }
                                            } else {
                                                Log.d(TAG, "Login cancelled")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to open login activity", e)
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

                        // Import buttons (shown when plugin has auth)
                        val descriptor = StatePlugins.instance.getPlugin(config!!.id)
                        val hasAuth = descriptor?.getAuth() != null
                        if (hasAuth) {
                            var showImportDialog by remember { mutableStateOf(false) }
                            var importType by remember { mutableStateOf<String?>(null) }
                            var isLoading by remember { mutableStateOf(false) }
                            var items by remember { mutableStateOf<List<Any>>(emptyList()) }
                            var selectedItems by remember { mutableStateOf<Set<Any>>(emptySet()) }

                            Button(
                                onClick = {
                                    importType = "subscriptions"
                                    showImportDialog = true
                                    isLoading = true
                                    selectedItems = emptySet()
                                    coroutineScope.launch {
                                        try {
                                            Log.d(TAG, "Getting subscriptions for plugin: ${config!!.name}")
                                            val client = StatePlatform.instance.getClient(config!!.id)
                                            Log.d(TAG, "Client type: ${client::class.simpleName}")
                                            val subs = withContext(Dispatchers.IO) {
                                                client.getUserSubscriptions()
                                            }
                                            Log.d(TAG, "Got ${subs?.size ?: 0} subscriptions")
                                            if (subs != null) {
                                                // Store URLs directly, resolve channels on demand
                                                items = subs.map { url ->
                                                    ChannelImportItem(url = url, name = url, thumbnail = null)
                                                }
                                            } else {
                                                items = emptyList()
                                            }
                                            isLoading = false
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to get subscriptions", e)
                                            isLoading = false
                                        }
                                    }
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
                                    importType = "playlists"
                                    showImportDialog = true
                                    isLoading = true
                                    selectedItems = emptySet()
                                    coroutineScope.launch {
                                        try {
                                            val client = StatePlatform.instance.getClient(config!!.id)
                                            val playlists = withContext(Dispatchers.IO) {
                                                client.getUserPlaylists()
                                            }
                                            items = playlists?.map { PlaylistImportItem(url = it, name = it) } ?: emptyList()
                                            isLoading = false
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to get playlists", e)
                                            isLoading = false
                                        }
                                    }
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

                            // Import dialog
                            if (showImportDialog) {
                                AlertDialog(
                                    onDismissRequest = { showImportDialog = false },
                                    title = { Text("Import $importType") },
                                    text = {
                                        if (isLoading) {
                                            CircularProgressIndicator()
                                        } else {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(300.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                Text("Select items to import:")
                                                items.forEach { item ->
                                                    when (item) {
                                                        is ChannelImportItem -> {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 4.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Checkbox(
                                                                    checked = selectedItems.contains(item),
                                                                    onCheckedChange = { checked ->
                                                                        if (checked) {
                                                                            selectedItems = selectedItems + item
                                                                        } else {
                                                                            selectedItems = selectedItems - item
                                                                        }
                                                                    }
                                                                )
                                                                if (item.thumbnail != null) {
                                                                    AsyncImage(
                                                                        model = item.thumbnail,
                                                                        contentDescription = null,
                                                                        modifier = Modifier
                                                                            .size(40.dp)
                                                                            .padding(start = 8.dp)
                                                                    )
                                                                }
                                                                Text(
                                                                    text = item.name,
                                                                    modifier = Modifier.padding(start = 8.dp)
                                                                )
                                                            }
                                                        }
                                                        is PlaylistImportItem -> {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 4.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Checkbox(
                                                                    checked = selectedItems.contains(item),
                                                                    onCheckedChange = { checked ->
                                                                        if (checked) {
                                                                            selectedItems = selectedItems + item
                                                                        } else {
                                                                            selectedItems = selectedItems - item
                                                                        }
                                                                    }
                                                                )
                                                                Text(
                                                                    text = item.name,
                                                                    modifier = Modifier.padding(start = 8.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    try {
                                                        Log.d(TAG, "Starting import of ${selectedItems.size} items")
                                                        when (importType) {
                                                            "subscriptions" -> {
                                                                var successCount = 0
                                                                for (item in selectedItems) {
                                                                    if (item is ChannelImportItem) {
                                                                        try {
                                                                            val channel = withContext(Dispatchers.IO) {
                                                                                StatePlatform.instance.getChannelLive(item.url, false)
                                                                            }
                                                                            StateSubscriptions.instance.addSubscription(channel)
                                                                            successCount++
                                                                            Log.d(TAG, "Added subscription: ${channel.name}")
                                                                        } catch (e: Exception) {
                                                                            Log.e(TAG, "Failed to add subscription: ${item.url}", e)
                                                                        }
                                                                    }
                                                                }
                                                                Log.d(TAG, "Import completed: $successCount/${selectedItems.size} items")
                                                            }
                                                            "playlists" -> {
                                                                // TODO: Implement playlist import
                                                            }
                                                        }
                                                        showImportDialog = false
                                                    } catch (e: Exception) {
                                                        Log.e(TAG, "Failed to import", e)
                                                    }
                                                }
                                            },
                                            enabled = selectedItems.isNotEmpty()
                                        ) {
                                            Text("Import (${selectedItems.size})")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showImportDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
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

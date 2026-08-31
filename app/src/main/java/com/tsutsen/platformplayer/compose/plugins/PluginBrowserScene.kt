/*
 * Plugin Browser Scene (Compose)
 *
 * A Compose-based plugin browser that displays available Bluejay plugins.
 * Users can toggle plugins on/off to enable/disable them.
 */

package com.tsutsen.platformplayer.compose.plugins

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tsutsen.platformplayer.UIDialogs
import com.tsutsen.platformplayer.api.http.ManagedHttpClient
import com.tsutsen.platformplayer.api.media.models.channels.IPlatformChannel
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylistDetails
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.auth.LoginDialog
import com.tsutsen.platformplayer.core.designsystem.component.LinkifiedText
import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.core.designsystem.component.SettingsSwitchOptionCard
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StateApp
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.states.StatePlaylists
import com.tsutsen.platformplayer.states.StatePlugins
import com.tsutsen.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PluginBrowserScene"

// Slow the one-by-one import resolution down (same rule as the legacy app) so
// large channel lists don't trip the platform's rate limit.
private const val IMPORT_SLOWDOWN_INDEX = 100
private const val IMPORT_SLOWDOWN_EVERY = 10
private const val IMPORT_SLOWDOWN_DELAY_MS = 1000L

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val configUrl: String,
    val isInstalled: Boolean,
    val isEnabled: Boolean,
    val isAuthenticated: Boolean = false,
    val iconUrl: String? = null,
)

// Import item — a raw URL resolved into real details (name, thumbnail, and for
// playlists the video count) one at a time while the import sheet loads.
sealed class ImportItem {
    abstract val url: String
    abstract val name: String
    abstract val thumbnail: String?
}

data class ImportChannel(
    override val url: String,
    override val name: String,
    override val thumbnail: String?,
    val channel: IPlatformChannel,
) : ImportItem()

data class ImportPlaylist(
    override val url: String,
    override val name: String,
    override val thumbnail: String?,
    val videoCount: Int,
    val details: IPlatformPlaylistDetails,
) : ImportItem()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginBrowserScene(onBack: (() -> Unit)? = null) {
    val coroutineScope = rememberCoroutineScope()
    val enabledClientIds = remember { mutableStateOf(setOf<String>()) }
    val installedPlugins = remember { mutableStateOf<List<SourcePluginConfig>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedPluginUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // If a plugin is selected, show its details
    if (selectedPluginUrl != null) {
        PluginDetailScene(
            configUrl = selectedPluginUrl!!,
            installedPlugins = installedPlugins.value,
            onBack = { selectedPluginUrl = null },
        )
        return
    }

    fun loadPluginsAndEnabledState() {
        val clients = StatePlatform.instance.getAvailableClients()
        val enabledIds =
            StatePlatform.instance
                .getEnabledClients()
                .map { it.id }
                .toSet()
        val installed = clients.filterIsInstance<JSClient>().map { it.config }
        installedPlugins.value = installed
        enabledClientIds.value = enabledIds
    }

    // Load installed plugins on first composition
    LaunchedEffect(Unit) {
        Logger.i(TAG, "Loading plugins (first time)...")
        // Ensure embedded plugins are installed before loading
        StatePlugins.instance.updateEmbeddedPlugins(context)
        StatePlugins.instance.installMissingEmbeddedPlugins(context)
        // Reload available clients to pick up newly installed plugins
        StatePlatform.instance.updateAvailableClients(context)
        loadPluginsAndEnabledState()
    }

    // Refresh when tab becomes visible (when refreshKey changes)
    LaunchedEffect(refreshKey) {
        Logger.i(TAG, "Refreshing plugins (key: $refreshKey)...")
        loadPluginsAndEnabledState()
    }

    val plugins =
        remember(installedPlugins.value, enabledClientIds.value) {
            val result =
                installedPlugins.value.map { config ->
                    // Check if plugin has auth configured
                    val descriptor = StatePlugins.instance.getPlugin(config.id)
                    val isAuthenticated = descriptor?.getAuth() != null
                    Logger.i(
                        TAG,
                        "Plugin ${config.name} (${config.id}): descriptor=${descriptor != null}, auth=${descriptor?.getAuth() != null}",
                    )

                    PluginInfo(
                        id = config.id,
                        name = config.name,
                        description = config.description ?: "",
                        configUrl = config.sourceUrl ?: "",
                        isInstalled = true,
                        isEnabled = enabledClientIds.value.contains(config.id),
                        isAuthenticated = isAuthenticated,
                        iconUrl = StatePlugins.instance.getPluginIconUriOrNull(config.id),
                    )
                }
            Logger.i(TAG, "Plugins list created with ${result.size} items")
            result.sortedBy { it.name.lowercase() }
        }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = { Text("Browse Plugins", style = MaterialTheme.typography.titleLarge) },
            leading = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            // Same card rhythm as the other settings sections.
            verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
        ) {
            items(plugins, key = { it.id }) { plugin ->
                SettingsSwitchOptionCard(
                    icon = Icons.Default.Extension,
                    iconUrl = plugin.iconUrl,
                    title = plugin.name,
                    subtitle =
                        if (plugin.isAuthenticated) {
                            "${plugin.description} • Logged in"
                        } else {
                            plugin.description
                        },
                    checked = plugin.isEnabled,
                    onCheckedChange = { isEnabled ->
                        Logger.i(TAG, "Toggle ${plugin.name}: $isEnabled")
                        // Optimistic: flip the switch immediately, run the slow V8
                        // init/stop in the background, then sync to the real state.
                        enabledClientIds.value =
                            if (isEnabled) {
                                enabledClientIds.value + plugin.id
                            } else {
                                enabledClientIds.value - plugin.id
                            }
                        UIDialogs.toast(context, "Toggling ${plugin.name}...")
                        coroutineScope.launch {
                            var failed = false
                            try {
                                if (isEnabled) {
                                    Logger.i(TAG, "Enabling ${plugin.name}")
                                    StatePlatform.instance.enableClient(listOf(plugin.id))
                                    // Launch auto-update only checks plugins that
                                    // are already enabled — a freshly enabled
                                    // plugin would stay on its old version. Update
                                    // it here, when it becomes relevant.
                                    if (Settings.instance.plugins.autoUpdatePlugins) {
                                        installedPlugins.value
                                            .find { it.id == plugin.id }
                                            ?.let { cfg ->
                                                StatePlugins.instance
                                                    .checkForUpdates(cfg)
                                                    ?.sourceUrl
                                                    ?.let { url ->
                                                        Logger.i(TAG, "Installing update for newly enabled ${plugin.name}")
                                                        StatePlugins.instance.installPlugins(
                                                            context,
                                                            coroutineScope,
                                                            listOf(url),
                                                            assumeReinstall = true,
                                                        )
                                                    }
                                            }
                                    }
                                } else {
                                    Logger.i(TAG, "Disabling ${plugin.name}")
                                    val newEnabled =
                                        StatePlatform.instance
                                            .getEnabledClients()
                                            .map { it.id }
                                            .filter { it != plugin.id }
                                    StatePlatform.instance.selectClients(*newEnabled.toTypedArray())
                                }
                            } catch (e: Exception) {
                                failed = true
                                Logger.e(TAG, "Error toggling plugin", e)
                            }
                            // selectClients swallows per-client init failures, so sync
                            // from backend truth either way (reverts the switch on failure).
                            enabledClientIds.value =
                                StatePlatform.instance
                                    .getEnabledClients()
                                    .map { it.id }
                                    .toSet()
                            refreshKey++
                            UIDialogs.toast(
                                context,
                                if (failed) {
                                    "Failed to toggle ${plugin.name}"
                                } else {
                                    "${plugin.name} ${if (isEnabled) "enabled" else "disabled"}"
                                },
                            )
                        }
                    },
                    onClick = {
                        Logger.i(TAG, "Clicked plugin: ${plugin.name}, URL: ${plugin.configUrl}")
                        selectedPluginUrl = plugin.configUrl
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginDetailScene(
    configUrl: String,
    installedPlugins: List<SourcePluginConfig>,
    onBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var config by remember { mutableStateOf<SourcePluginConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isEnabled by remember { mutableStateOf(false) }
    var isPluginInstalled by remember { mutableStateOf(false) }
    var pluginSettings by remember { mutableStateOf<MutableMap<String, String?>?>(null) }
    var pluginSettingsChanged by remember { mutableStateOf(false) }

    LaunchedEffect(configUrl) {
        try {
            // Check if plugin is already installed locally
            val installedConfig = installedPlugins.find { it.sourceUrl == configUrl }

            if (installedConfig != null) {
                // Plugin is already installed - use local config directly
                Logger.i(TAG, "Plugin ${installedConfig.name} is already installed, using local config")
                config = installedConfig
                isPluginInstalled = true
                pluginSettings =
                    HashMap(StatePlugins.instance.getPlugin(installedConfig.id)?.settings ?: emptyMap())

                // Check if this plugin is enabled
                val enabledClients = StatePlatform.instance.getEnabledClients()
                isEnabled = enabledClients.any { it.id == installedConfig.id }

                // Check if plugin has auth
                val descriptor = StatePlugins.instance.getPlugin(installedConfig.id)
                val hasAuth = descriptor?.getAuth() != null
                Logger.i(TAG, "Plugin ${installedConfig.name} auth status: $hasAuth")
            } else {
                // Plugin not installed locally - fetch from network
                Logger.i(TAG, "Fetching plugin config from: $configUrl")
                val response =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ManagedHttpClient().get(configUrl)
                    }
                if (response.isOk && response.body != null) {
                    val configJson = response.body.string()
                    val loadedConfig = SourcePluginConfig.fromJson(configJson)
                    config = loadedConfig
                    isPluginInstalled = false
                    Logger.i(TAG, "Loaded config: ${loadedConfig.name}")

                    // Check if this plugin is enabled
                    val enabledClients = StatePlatform.instance.getEnabledClients()
                    isEnabled = enabledClients.any { it.id == loadedConfig.id }

                    // Check if plugin has auth
                    val descriptor = StatePlugins.instance.getPlugin(loadedConfig.id)
                    val hasAuth = descriptor?.getAuth() != null
                    Logger.i(TAG, "Plugin ${loadedConfig.name} auth status: $hasAuth")
                } else {
                    error = "Failed to load config"
                    Logger.e(TAG, "Failed to load config: ${response.isOk}, ${response.body}")
                }
            }
            isLoading = false
        } catch (e: Exception) {
            error = e.message
            Logger.e(TAG, "Error loading config", e)
            isLoading = false
        }
    }

    // Save pending settings when leaving this screen (save-on-exit, like the legacy app),
    // then reload the plugin client so the new settings take effect.
    DisposableEffect(configUrl) {
        onDispose {
            val id = config?.id ?: return@onDispose
            val pending = pluginSettings ?: return@onDispose
            if (pluginSettingsChanged) {
                StatePlugins.instance.setPluginSettings(id, HashMap(pending))
                UIDialogs.toast(context, "Plugin settings saved")
                StateApp.instance.scope.launch(Dispatchers.IO) {
                    try {
                        StatePlatform.instance.reloadClient(context, id)
                    } catch (e: Exception) {
                        Logger.e(TAG, "Failed to reload plugin after saving settings", e)
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = { Text("Plugin Details", style = MaterialTheme.typography.titleLarge) },
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
        val context = LocalContext.current
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Error: $error")
                }
            }

            config != null -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Action-button state — the buttons themselves are
                    // rendered at the top, right under the description card.
                    val descriptor = StatePlugins.instance.getPlugin(config!!.id)
                    val hasAuth = descriptor?.getAuth() != null
                    var showImportSheet by remember { mutableStateOf(false) }
                    var importType by remember { mutableStateOf<String?>(null) }
                    var importGeneration by remember { mutableIntStateOf(0) }
                    var importTotal by remember { mutableIntStateOf(-1) }
                    var importItems by remember { mutableStateOf<List<ImportItem>>(emptyList()) }
                    var importSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
                    var importResolving by remember { mutableStateOf(false) }
                    var importError by remember { mutableStateOf<String?>(null) }
                    var isImporting by remember { mutableStateOf(false) }
                    var importedCount by remember { mutableIntStateOf(0) }

                    // Header card: name, description, and one secondary
                    // row with version, author and the (clickable)
                    // source URL.
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = config!!.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = config!!.description ?: "No description",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "v${config!!.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = config!!.author ?: "Unknown",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LinkifiedText(
                                    text = configUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                    onTimestampClick = {},
                                    onLinkClick = { url ->
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }

                    // Update button
                    Button(
                        onClick = {
                            val c = config ?: return@Button
                            Logger.i(TAG, "Update button clicked for ${c.name}")
                            coroutineScope.launch {
                                try {
                                    val newConfig = StatePlugins.instance.checkForUpdates(c)
                                    if (newConfig == null) {
                                        UIDialogs.toast(context, "${c.name} is up to date (v${c.version})")
                                    } else {
                                        UIDialogs.toast(
                                            context,
                                            "Update available: ${c.name} v${c.version} -> v${newConfig.version}. Installing...",
                                        )
                                        StatePlugins.instance.installPlugin(
                                            context,
                                            coroutineScope,
                                            c.sourceUrl!!,
                                        ) { success ->
                                            if (success) {
                                                config = newConfig
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Logger.e(TAG, "Update check failed", e)
                                    UIDialogs.toast(context, "Update check failed: ${e.message}")
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                    ) {
                        Text("Check for Updates")
                    }

                    // Authentication buttons
                    if (config!!.authentication != null) {
                        val context = LocalContext.current

                        Button(
                            onClick = {
                                Logger.i(TAG, "Opening login activity for: ${config!!.name} (id: ${config!!.id})")
                                try {
                                    LoginDialog.showLogin(config!!) { auth ->
                                        if (auth != null) {
                                            Logger.i(TAG, "Login successful, saving auth for ${config!!.name}")
                                            Logger.i(
                                                TAG,
                                                "Auth cookieMap size: ${auth.cookieMap?.size}, headers size: ${auth.headers?.size}",
                                            )
                                            try {
                                                StatePlugins.instance.setPluginAuth(config!!.id, auth)
                                                Logger.i(TAG, "Auth saved successfully")
                                                // Enable the plugin if not already enabled
                                                val currentEnabled = StatePlatform.instance.getEnabledClients()
                                                if (!currentEnabled.any { it.id == config!!.id }) {
                                                    Logger.i(TAG, "Enabling plugin ${config!!.name} after login")
                                                    StateApp.instance.scope.launch(Dispatchers.IO) {
                                                        StatePlatform.instance.enableClient(listOf(config!!.id))
                                                    }
                                                }
                                                // Reload the client to apply the new auth
                                                StateApp.instance.scope.launch(Dispatchers.IO) {
                                                    StatePlatform.instance.reloadClient(context, config!!.id) {
                                                        Logger.i(TAG, "Client reloaded after login")
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Logger.e(TAG, "Failed to set plugin auth", e)
                                            }
                                        } else {
                                            Logger.i(TAG, "Login cancelled")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Logger.e(TAG, "Failed to open login activity", e)
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                        ) {
                            Text("Login")
                        }

                        // Show login warning if present
                        config!!.authentication!!.loginWarning?.let { warning ->
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        // Show additional warnings
                        config!!.authentication!!.loginWarnings?.forEach { warning ->
                            Text(
                                text = warning.text ?: warning.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    // Import buttons (shown when plugin has auth)
                    if (hasAuth) {
                        Button(
                            onClick = {
                                importType = "subscriptions"
                                importItems = emptyList()
                                importSelected = emptySet()
                                importTotal = -1
                                importError = null
                                importedCount = 0
                                isImporting = false
                                importGeneration++
                                showImportSheet = true
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                ),
                        ) {
                            Text("Import Subscriptions")
                        }

                        Button(
                            onClick = {
                                importType = "playlists"
                                importItems = emptyList()
                                importSelected = emptySet()
                                importTotal = -1
                                importError = null
                                importedCount = 0
                                isImporting = false
                                importGeneration++
                                showImportSheet = true
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                ),
                        ) {
                            Text("Import Playlists")
                        }
                    }

                    // Uninstall button
                    OutlinedButton(
                        onClick = {
                            Logger.i(TAG, "Uninstall button clicked")
                            // TODO: Implement uninstall functionality
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text("Uninstall")
                    }

                    // Plugin settings
                    if (isPluginInstalled && pluginSettings != null) {
                        PluginSettingsSection(
                            config = config!!,
                            settings = pluginSettings!!,
                            onSettingChanged = { variable, value ->
                                // New map reference: in-place mutation of the
                                // remembered HashMap is invisible to Compose
                                // (the switch would not move until the screen
                                // recomposed for another reason).
                                pluginSettings =
                                    pluginSettings?.toMutableMap()?.apply { set(variable, value) }
                                pluginSettingsChanged = true
                            },
                        )
                    }

                    // Import sheet (overlay — its buttons live with the other
                    // actions at the top of the screen).
                    if (hasAuth && showImportSheet) {
                            val importSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                            ModalBottomSheet(
                                onDismissRequest = { showImportSheet = false },
                                sheetState = importSheetState,
                            ) {
                                LaunchedEffect(importGeneration) {
                                    importResolving = true
                                    importError = null
                                    try {
                                        val client = StatePlatform.instance.getClient(config!!.id)
                                        val urls =
                                            withContext(Dispatchers.IO) {
                                                when (importType) {
                                                    "subscriptions" -> {
                                                        client
                                                            .getUserSubscriptions()
                                                            .distinct()
                                                            .filter { !StateSubscriptions.instance.isSubscribed(it) }
                                                            .toList()
                                                    }

                                                    else -> {
                                                        client.getUserPlaylists().distinct().toList()
                                                    }
                                                }
                                            }
                                        importTotal = urls.size
                                        urls.forEachIndexed { index, url ->
                                            try {
                                                val item =
                                                    withContext(Dispatchers.IO) {
                                                        when (importType) {
                                                            "subscriptions" -> {
                                                                val channel =
                                                                    StatePlatform.instance.getChannelLive(url, false)
                                                                ImportChannel(
                                                                    url,
                                                                    channel.name,
                                                                    channel.thumbnail,
                                                                    channel,
                                                                )
                                                            }

                                                            else -> {
                                                                val playlist =
                                                                    StatePlatform.instance.getPlaylist(url)
                                                                ImportPlaylist(
                                                                    url,
                                                                    playlist.name,
                                                                    playlist.thumbnail,
                                                                    playlist.videoCount,
                                                                    playlist,
                                                                )
                                                            }
                                                        }
                                                    }
                                                importItems = importItems + item
                                            } catch (e: Exception) {
                                                Logger.w(TAG, "Failed to resolve $url", e)
                                            }
                                            if (
                                                index >= IMPORT_SLOWDOWN_INDEX &&
                                                index % IMPORT_SLOWDOWN_EVERY == 0
                                            ) {
                                                delay(IMPORT_SLOWDOWN_DELAY_MS)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Logger.e(TAG, "Failed to load import list", e)
                                        importError = e.message ?: "Failed to load"
                                        importTotal = 0
                                    }
                                    importResolving = false
                                }

                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 16.dp),
                                ) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Tokens.SpaceLg, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text =
                                                if (importTotal == -1) {
                                                    "Loading…"
                                                } else {
                                                    "${importItems.size}/$importTotal"
                                                },
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        if (importResolving) {
                                            Spacer(Modifier.width(12.dp))
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        val allSelected =
                                            importItems.isNotEmpty() &&
                                                importSelected.size == importItems.size
                                        TextButton(
                                            onClick = {
                                                importSelected =
                                                    if (allSelected) {
                                                        emptySet()
                                                    } else {
                                                        importItems.map { it.url }.toSet()
                                                    }
                                            },
                                        ) {
                                            Text(if (allSelected) "Deselect all" else "Select all")
                                        }
                                    }

                                    importError?.let { error ->
                                        Text(
                                            text = error,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = Tokens.SpaceLg),
                                        )
                                    }

                                    if (importTotal == 0 && importError == null) {
                                        Text(
                                            text =
                                                if (importType == "subscriptions") {
                                                    "You're already subscribed to all of the plugin's channels"
                                                } else {
                                                    "No playlists found"
                                                },
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(horizontal = Tokens.SpaceLg),
                                        )
                                    }

                                    LazyColumn(modifier = Modifier.weight(1f)) {
                                        items(importItems, key = { it.url }) { item ->
                                            Row(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            horizontal = Tokens.SpaceLg,
                                                            vertical = 4.dp,
                                                        ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Checkbox(
                                                    checked = importSelected.contains(item.url),
                                                    onCheckedChange = { checked ->
                                                        importSelected =
                                                            if (checked) {
                                                                importSelected + item.url
                                                            } else {
                                                                importSelected - item.url
                                                            }
                                                    },
                                                )
                                                if (item.thumbnail != null) {
                                                    AsyncImage(
                                                        model = item.thumbnail,
                                                        contentDescription = null,
                                                        modifier =
                                                            Modifier
                                                                .size(40.dp)
                                                                .padding(start = 8.dp)
                                                                .clip(RoundedCornerShape(8.dp)),
                                                        contentScale = ContentScale.Crop,
                                                    )
                                                }
                                                Column(
                                                    modifier =
                                                        Modifier
                                                            .weight(1f)
                                                            .padding(start = 8.dp),
                                                ) {
                                                    Text(
                                                        text = item.name,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                    )
                                                    if (item is ImportPlaylist) {
                                                        Text(
                                                            text =
                                                                "${item.videoCount} ${
                                                                    if (item.videoCount == 1) {
                                                                        "video"
                                                                    } else {
                                                                        "videos"
                                                                    }
                                                                }",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color =
                                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(start = Tokens.SpaceLg, end = Tokens.SpaceLg, top = 8.dp),
                                    ) {
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = { showImportSheet = false }) {
                                            Text("Cancel")
                                        }
                                        Button(
                                            onClick = {
                                                isImporting = true
                                                importedCount = 0
                                                val toImport =
                                                    importItems.filter { it.url in importSelected }
                                                coroutineScope.launch {
                                                    var success = 0
                                                    withContext(Dispatchers.IO) {
                                                        for (item in toImport) {
                                                            try {
                                                                when (item) {
                                                                    is ImportChannel -> {
                                                                        StateSubscriptions.instance.addSubscription(
                                                                            item.channel,
                                                                        )
                                                                    }

                                                                    is ImportPlaylist -> {
                                                                        StatePlaylists.instance.createOrUpdatePlaylist(
                                                                            item.details.toPlaylist(),
                                                                            true,
                                                                        )
                                                                    }
                                                                }
                                                                success++
                                                            } catch (e: Exception) {
                                                                Logger.e(
                                                                    TAG,
                                                                    "Failed to import ${item.url}",
                                                                    e,
                                                                )
                                                            }
                                                            importedCount = success
                                                        }
                                                    }
                                                    isImporting = false
                                                    showImportSheet = false
                                                    val noun =
                                                        if (importType == "playlists") {
                                                            "playlist"
                                                        } else {
                                                            "subscription"
                                                        }
                                                    UIDialogs.toast(
                                                        context,
                                                        "Imported $success $noun${
                                                            if (success == 1) "" else "s"
                                                        }",
                                                    )
                                                }
                                            },
                                            enabled = importSelected.isNotEmpty() && !isImporting,
                                        ) {
                                            Text(
                                                if (isImporting) {
                                                    "Importing $importedCount/${importSelected.size}…"
                                                } else {
                                                    "Import (${importSelected.size})"
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}

/**
 * Renders a plugin's own settings schema (Boolean switches, Dropdown pickers, Header sections).
 * Values are applied to [settings] via [onSettingChanged]; persistence + client reload happen
 * in the caller when the screen is disposed.
 */
@Composable
private fun PluginSettingsSection(
    config: SourcePluginConfig,
    settings: MutableMap<String, String?>,
    onSettingChanged: (String, String) -> Unit,
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var warningConfirm by remember { mutableStateOf<SourcePluginConfig.Setting?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)

        PluginSettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Show advanced settings",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = showAdvanced, onCheckedChange = { showAdvanced = it })
            }
        }

        config.settings.forEach { setting ->
            if (setting.isAdvanced == true && !showAdvanced) return@forEach
            when (setting.type) {
                "Header" -> {
                    // Same look as the app settings' subsection titles
                    // (labelLarge, primary, slight start offset) — not cards.
                    Text(
                        setting.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Tokens.SpaceSm, start = 4.dp),
                    )
                }

                "Boolean" -> {
                    val variable = setting.variableOrName
                    val current = (settings[variable] ?: setting.default) == "true"
                    PluginSettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(setting.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    setting.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = current,
                                onCheckedChange = { newValue ->
                                    // ponytail: no dependency handling; dependent settings can be toggled freely
                                    if (newValue && setting.warningDialog != null) {
                                        warningConfirm = setting
                                    } else {
                                        onSettingChanged(variable, newValue.toString())
                                    }
                                },
                            )
                        }
                    }
                }

                "Dropdown" -> {
                    // Values are 0-based indexes into [options] (plugin convention, e.g.
                    // SponsorBlock "0"="No skip", "1"="Manual", "2"="Automatic").
                    val variable = setting.variableOrName
                    val options = setting.options ?: emptyList()
                    val rawValue = settings[variable] ?: setting.default ?: "0"
                    val rawIndex = rawValue.toIntOrNull()
                    val current =
                        if (rawIndex != null && rawIndex in options.indices) {
                            options[rawIndex]
                        } else {
                            rawValue
                        }
                    PluginSettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(setting.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    setting.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                // Same trigger style as the search tab's sort pill.
                                // maxLines=1 + ellipsis: a long selected option
                                // must not wrap / overflow the chip in portrait.
                                FilterChip(
                                    selected = false,
                                    onClick = { menuExpanded = true },
                                    label = {
                                        Text(
                                            current,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(Tokens.IconSm),
                                        )
                                    },
                                )
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    options.forEachIndexed { index, option ->
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                if (option == current) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            },
                                            text = { Text(option) },
                                            onClick = {
                                                menuExpanded = false
                                                onSettingChanged(variable, index.toString())
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    warningConfirm?.let { setting ->
        AlertDialog(
            onDismissRequest = { warningConfirm = null },
            title = { Text("Enable ${setting.name}?") },
            text = { Text(setting.warningDialog ?: "") },
            confirmButton = {
                Button(
                    onClick = {
                        onSettingChanged(setting.variableOrName, "true")
                        warningConfirm = null
                    },
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { warningConfirm = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** One card per plugin setting (same surface as the rest of settings). */
@Composable
private fun PluginSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

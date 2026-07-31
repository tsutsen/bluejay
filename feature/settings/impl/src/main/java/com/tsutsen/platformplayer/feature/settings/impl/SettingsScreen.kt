package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.PlaybackPreferences
import com.tsutsen.platformplayer.core.designsystem.component.SettingsButtonCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsData
import com.tsutsen.platformplayer.core.designsystem.component.SettingsOptionCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsSwitchCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsTextCard
import com.tsutsen.platformplayer.core.navigation.Navigator

/**
 * Settings hub — top-level navigation for hierarchical settings.
 * Each category opens a new screen with its own items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navigator: Navigator? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    when (val state = uiState) {
        is SettingsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
        is SettingsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Text("Error loading settings")
                    Button(onClick = { /* retry */ }) {
                        Text("Retry")
                    }
                }
            }
        }
        is SettingsUiState.Loaded -> {
            if (selectedCategory != null) {
                if (selectedCategory == "plugin_browser") {
                    if (navigator != null) {
                        navigator.navigateToPluginBrowser()
                    }
                    selectedCategory = null
                } else {
                    CategoryScreen(
                        category = selectedCategory!!,
                        data = state.toSettingsData(),
                        onAppearanceChanged = { viewModel.updateAppearance(it) },
                        onPlaybackChanged = { viewModel.updatePlayback(it) },
                        onGeneralChanged = { key, value -> viewModel.updateGeneral(key, value) },
                        onResetToDefaults = { viewModel.resetToDefaults() },
                        onBack = { selectedCategory = null }
                    )
                }
            } else {
                SettingsHubContent(
                    onCategorySelected = { selectedCategory = it }
                )
            }
        }
    }
}

private fun SettingsUiState.Loaded.toSettingsData(): SettingsData = SettingsData(
    appearance = appearance,
    playback = playback,
    language = language,
    enableNotifications = enableNotifications,
    enableBackgroundPlayback = enableBackgroundPlayback,
    enablePictureInPicture = enablePictureInPicture,
    confirmExit = confirmExit,
    enableDeveloperOptions = enableDeveloperOptions
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHubContent(
    onCategorySelected: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(hubCategories) { category ->
                SettingsOptionCard(
                    icon = category.icon,
                    title = category.title,
                    subtitle = category.subtitle,
                    onClick = { onCategorySelected(category.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryScreen(
    category: String,
    data: SettingsData,
    onAppearanceChanged: (AppearancePreferences) -> Unit,
    onPlaybackChanged: (PlaybackPreferences) -> Unit,
    onGeneralChanged: (String, Any) -> Unit,
    onResetToDefaults: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryTitle(category)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categoryItems(category)) { item ->
                when (item.type) {
                    ItemType.OPTION -> SettingsOptionCard(
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        onClick = item.onClick ?: {}
                    )
                    ItemType.SWITCH -> SettingsSwitchCard(
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        checked = item.checked,
                        onCheckedChange = item.onCheckedChange ?: { }
                    )
                    ItemType.TEXT -> SettingsTextCard(
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.subtitle
                    )
                    ItemType.BUTTON -> SettingsButtonCard(
                        title = item.title,
                        onClick = item.onClick ?: {}
                    )
                }
            }
        }
    }
}

// Hub categories
private data class HubCategory(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

private val hubCategories = listOf(
    HubCategory("appearance", Icons.Default.Palette, "Appearance", "Theme, colors, typography, icons, contrast"),
    HubCategory("feed", Icons.Default.Feed, "Feed & Content", "Home feed, search, channels, subscriptions"),
    HubCategory("player", Icons.Default.PlayArrow, "Player", "Playback, downloads, gestures, casting"),
    HubCategory("privacy", Icons.Default.Lock, "Privacy & Data", "Privacy, data management, backup & restore"),
    HubCategory("sync", Icons.Default.Sync, "Sync & Identity", "Synchronization, Polycentric"),
    HubCategory("general", Icons.Default.Settings, "General", "Language, tabs, link handling, FAQ"),
    HubCategory("plugin_browser", Icons.Default.Extension, "Plugin Browser", "Browse and manage all plugins"),
    HubCategory("about", Icons.Default.Info, "About", "Version, license, payment")
)

// Category items
private data class CategoryItem(
    val type: ItemType,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val checked: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val onCheckedChange: ((Boolean) -> Unit)? = null
)

private enum class ItemType { OPTION, SWITCH, TEXT, BUTTON }

private fun categoryTitle(category: String): String = when (category) {
    "appearance" -> "Appearance"
    "feed" -> "Feed & Content"
    "player" -> "Player"
    "privacy" -> "Privacy & Data"
    "sync" -> "Sync & Identity"
    "general" -> "General"
    "plugin_browser" -> "Plugin Browser"
    "about" -> "About"
    else -> category
}

private fun categoryItems(category: String): List<CategoryItem> = when (category) {
    "appearance" -> listOf(
        CategoryItem(ItemType.OPTION, Icons.Default.BrightnessAuto, "Theme", "Follow system"),
        CategoryItem(ItemType.OPTION, Icons.Default.Palette, "Color Scheme", "Dynamic"),
        CategoryItem(ItemType.OPTION, Icons.Default.TextFields, "Typography", "Inter"),
        CategoryItem(ItemType.OPTION, Icons.Default.Star, "Icon Style", "Rounded"),
        CategoryItem(ItemType.OPTION, Icons.Default.Palette, "Contrast", "Standard")
    )
    "feed" -> listOf(
        CategoryItem(ItemType.OPTION, Icons.Default.Home, "Home Feed", "Default feed layout"),
        CategoryItem(ItemType.OPTION, Icons.Default.Search, "Search", "Search results behavior"),
        CategoryItem(ItemType.OPTION, Icons.Default.LiveTv, "Channels", "Channel display options"),
        CategoryItem(ItemType.OPTION, Icons.Default.Subscriptions, "Subscriptions", "Subscription feed settings")
    )
    "player" -> listOf(
        CategoryItem(ItemType.SWITCH, Icons.Default.PlayArrow, "Auto-play", "On", true),
        CategoryItem(ItemType.OPTION, Icons.Default.Hd, "Default Quality", "Auto"),
        CategoryItem(ItemType.SWITCH, Icons.Default.Speed, "Hardware Acceleration", "On", true),
        CategoryItem(ItemType.SWITCH, Icons.Default.ClosedCaption, "Show Subtitles", "On", true),
        CategoryItem(ItemType.SWITCH, Icons.Default.SkipNext, "Skip Silence", "Off", false),
        CategoryItem(ItemType.OPTION, Icons.Default.Download, "Downloads", "Download quality and storage"),
        CategoryItem(ItemType.OPTION, Icons.Default.TouchApp, "Gestures", "Video player gestures"),
        CategoryItem(ItemType.OPTION, Icons.Default.Cast, "Casting", "Cast device discovery")
    )
    "privacy" -> listOf(
        CategoryItem(ItemType.SWITCH, Icons.Default.Notifications, "Notifications", "On", true),
        CategoryItem(ItemType.SWITCH, Icons.Default.PlayArrow, "Background Playback", "On", true),
        CategoryItem(ItemType.SWITCH, Icons.Default.PictureInPictureAlt, "Picture-in-Picture", "On", true),
        CategoryItem(ItemType.OPTION, Icons.Default.Storage, "Data Management", "Cache, history, and local data"),
        CategoryItem(ItemType.OPTION, Icons.Default.Backup, "Backup & Restore", "Export and import settings")
    )
    "sync" -> listOf(
        CategoryItem(ItemType.OPTION, Icons.Default.Sync, "Synchronization", "Cross-device sync settings"),
        CategoryItem(ItemType.OPTION, Icons.Default.AccountCircle, "Polycentric", "Decentralized identity")
    )
    "general" -> listOf(
        CategoryItem(ItemType.OPTION, Icons.Default.Language, "Language", "en"),
        CategoryItem(ItemType.OPTION, Icons.Default.Tab, "Tabs", "Tab behavior and limits"),
        CategoryItem(ItemType.OPTION, Icons.Default.Link, "Link Handling", "How links are opened"),
        CategoryItem(ItemType.SWITCH, Icons.Default.Warning, "Confirm Exit", "Off", false),
        CategoryItem(ItemType.SWITCH, Icons.Default.Code, "Developer Options", "Off", false),
        CategoryItem(ItemType.BUTTON, Icons.Default.Refresh, "Reset to Defaults", "")
    )
    "about" -> listOf(
        CategoryItem(ItemType.TEXT, Icons.Default.Info, "Version", "3.0.0-alpha"),
        CategoryItem(ItemType.TEXT, Icons.Default.Description, "License", "AGPL-3.0"),
        CategoryItem(ItemType.OPTION, Icons.Default.MonetizationOn, "Payment", "Support Bluejay")
    )
    else -> emptyList()
}

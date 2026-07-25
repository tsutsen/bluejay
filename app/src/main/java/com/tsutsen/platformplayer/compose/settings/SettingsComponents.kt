package com.tsutsen.platformplayer.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Reusable UI components for settings-style screens.
 *
 * These components ensure visual consistency across all settings screens
 * in the app. Import this file in any settings fragment to use them.
 *
 * Usage in a fragment:
 *   @Composable override fun ComposeContent() {
 *       SettingsScreen(title = "Appearance", onBack = { navigateBack() }) {
 *           SettingsSection("Display")
 *           SettingsOptionCard(
 *               icon = Icons.Filled.DarkMode,
 *               title = "Theme",
 *               subtitle = "Dark mode"
 *           ) { showThemeDialog = true }
 *           if (showThemeDialog) {
 *               RadioButtonDialog(
 *                   title = "Theme",
 *                   options = themeOptions,
 *                   selected = currentTheme,
 *                   onSelected = { /* handle */ },
 *                   onDismiss = { showThemeDialog = false }
 *               )
 *           }
 *       }
 *   }
 */

/**
 * Full settings screen scaffold with top bar, back button, and scrollable content.
 *
 * @param title Screen title shown in the top bar
 * @param onBack Back navigation callback
 * @param content Settings items to display
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item { content() }
        }
    }
}

/**
 * A settings option card with icon, title, subtitle, and trailing chevron.
 * Used for navigable settings items.
 *
 * @param icon Icon displayed on the left
 * @param title Primary text
 * @param subtitle Secondary text (shown below title)
 * @param onClick Called when the card is tapped
 */
@Composable
fun SettingsOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Title and subtitle
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Chevron
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A settings section separator — a thin line with optional label.
 * Used to group related settings items.
 */
@Composable
fun SettingsSection(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * A reusable radio button dialog for settings options.
 *
 * @param title Dialog title
 * @param options Available options to choose from
 * @param selected Currently selected option
 * @param onSelected Called when an option is selected
 * @param onDismiss Called when the dialog is dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioButtonDialog(
    title: String,
    options: List<SettingsOption>,
    selected: SettingsOption,
    onSelected: (SettingsOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelected(option) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * A settings option for use with RadioButtonDialog.
 */
data class SettingsOption(
    val label: String
)

/**
 * A settings item with icon, title, subtitle, and optional dialog options.
 * Used by getItemsForCategory() to build settings screens.
 */
data class SettingsItem(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val dialogOptions: List<SettingsOption>? = null,
    val subCategory: String? = null
)

/**
 * Returns a list of settings items for the given category.
 * This replaces the getItemsForCategory() function that was previously
 * in fragment.settings.SettingsFragment.kt.
 */
fun getItemsForCategory(category: String): List<SettingsItem> {
    return when (category) {
        "appearance" -> listOf(
            SettingsItem("theme_mode", Icons.Filled.DarkMode, "Theme", "Follow system",
                listOf(SettingsOption("Follow system"), SettingsOption("Light"), SettingsOption("Dark"))),
            SettingsItem("color_scheme", Icons.Filled.Palette, "Color Scheme", "Dynamic",
                listOf(SettingsOption("Dynamic"), SettingsOption("Custom seed"), SettingsOption("Preset"))),
            SettingsItem("font", Icons.Filled.TextFields, "Typography", "Inter",
                listOf(SettingsOption("Inter"), SettingsOption("System"))),
            SettingsItem("icon_style", Icons.Filled.Star, "Icon Style", "Rounded",
                listOf(SettingsOption("Rounded"), SettingsOption("Sharp"), SettingsOption("Outlined"))),
            SettingsItem("contrast", Icons.Filled.Palette, "Contrast", "Standard",
                listOf(SettingsOption("Standard"), SettingsOption("Medium"), SettingsOption("High")))
        )
        "feed" -> listOf(
            SettingsItem("home_feed", Icons.Filled.Home, "Home Feed", "Default feed layout",
                subCategory = "home_feed"),
            SettingsItem("search", Icons.Filled.Search, "Search", "Search results behavior",
                subCategory = "search"),
            SettingsItem("channels", Icons.Filled.LiveTv, "Channels", "Channel display options",
                subCategory = "channels"),
            SettingsItem("subscriptions", Icons.Filled.Subscriptions, "Subscriptions", "Subscription feed settings",
                subCategory = "subscriptions")
        )
        "player" -> listOf(
            SettingsItem("playback", Icons.Filled.PlayArrow, "Playback", "Default playback behavior",
                subCategory = "playback"),
            SettingsItem("downloads", Icons.Filled.Download, "Downloads", "Download quality and storage",
                subCategory = "downloads"),
            SettingsItem("gestures", Icons.Filled.TouchApp, "Gestures", "Video player gestures",
                subCategory = "gestures"),
            SettingsItem("casting", Icons.Filled.Cast, "Casting", "Cast device discovery",
                subCategory = "casting")
        )
        "privacy" -> listOf(
            SettingsItem("privacy", Icons.Filled.Lock, "Privacy", "Privacy and tracking settings",
                subCategory = "privacy"),
            SettingsItem("data_management", Icons.Filled.Storage, "Data Management", "Cache, history, and local data",
                subCategory = "data_management"),
            SettingsItem("backup", Icons.Filled.Backup, "Backup & Restore", "Export and import settings",
                subCategory = "backup")
        )
        "sync" -> listOf(
            SettingsItem("sync", Icons.Filled.Sync, "Synchronization", "Cross-device sync settings",
                subCategory = "sync"),
            SettingsItem("polycentric", Icons.Filled.AccountCircle, "Polycentric", "Decentralized identity",
                subCategory = "polycentric")
        )
        "general" -> listOf(
            SettingsItem("language", Icons.Filled.Language, "Language", "App language",
                subCategory = "language"),
            SettingsItem("tabs", Icons.Filled.Tab, "Tabs", "Tab behavior and limits",
                subCategory = "tabs"),
            SettingsItem("link_handling", Icons.Filled.Link, "Link Handling", "How links are opened",
                subCategory = "link_handling"),
            SettingsItem("faq", Icons.Filled.Help, "FAQ / Issues", "Frequently asked questions",
                subCategory = "faq")
        )
        "about" -> listOf(
            SettingsItem("version", Icons.Filled.Info, "Version", "Current app version"),
            SettingsItem("license", Icons.Filled.Description, "License", "Open source licenses"),
            SettingsItem("payment", Icons.Filled.MonetizationOn, "Payment", "Support Bluejay")
        )
        "home_feed" -> listOf(
            SettingsItem("feed_layout", Icons.Filled.GridOn, "Feed Layout", "Grid or list layout",
                subCategory = "feed_layout"),
            SettingsItem("feed_sort", Icons.Filled.Sort, "Sort By", "Sort order for feed items",
                subCategory = "feed_sort"),
            SettingsItem("feed_tags", Icons.Filled.Label, "Tags", "Filter by tags",
                subCategory = "feed_tags")
        )
        "search" -> listOf(
            SettingsItem("search_results", Icons.Filled.Search, "Search Results", "Search result behavior",
                subCategory = "search_results")
        )
        "channels" -> listOf(
            SettingsItem("channel_layout", Icons.Filled.GridOn, "Channel Layout", "Channel display layout",
                subCategory = "channel_layout")
        )
        "subscriptions" -> listOf(
            SettingsItem("subscription_feed", Icons.Filled.Subscriptions, "Subscription Feed", "Subscription feed settings",
                subCategory = "subscription_feed")
        )
        "playback" -> listOf(
            SettingsItem("default_quality", Icons.Filled.Hd, "Default Quality", "Default playback quality",
                subCategory = "default_quality"),
            SettingsItem("autoplay", Icons.Filled.PlayArrow, "Autoplay", "Autoplay next video",
                subCategory = "autoplay")
        )
        "downloads" -> listOf(
            SettingsItem("download_quality", Icons.Filled.Download, "Download Quality", "Default download quality",
                subCategory = "download_quality"),
            SettingsItem("download_storage", Icons.Filled.Storage, "Download Storage", "Download storage location",
                subCategory = "download_storage")
        )
        "gestures" -> listOf(
            SettingsItem("gesture_swipe", Icons.Filled.Swipe, "Swipe Gestures", "Swipe gesture settings",
                subCategory = "gesture_swipe"),
            SettingsItem("gesture_tap", Icons.Filled.TouchApp, "Tap Gestures", "Tap gesture settings",
                subCategory = "gesture_tap")
        )
        "casting" -> listOf(
            SettingsItem("casting_discovery", Icons.Filled.Cast, "Discovery", "Cast device discovery",
                subCategory = "casting_discovery")
        )
        "data_management" -> listOf(
            SettingsItem("cache", Icons.Filled.Storage, "Cache", "Cache settings",
                subCategory = "cache"),
            SettingsItem("history_data", Icons.Filled.History, "History Data", "History data management",
                subCategory = "history_data"),
            SettingsItem("local_data", Icons.Filled.Storage, "Local Data", "Local data management",
                subCategory = "local_data")
        )
        "backup" -> listOf(
            SettingsItem("export", Icons.Filled.Upload, "Export", "Export settings",
                subCategory = "export"),
            SettingsItem("import", Icons.Filled.Download, "Import", "Import settings",
                subCategory = "import")
        )
        "language" -> listOf(
            SettingsItem("app_language", Icons.Filled.Language, "App Language", "App language",
                subCategory = "app_language")
        )
        "tabs" -> listOf(
            SettingsItem("tab_behavior", Icons.Filled.Tab, "Tab Behavior", "Tab behavior settings",
                subCategory = "tab_behavior")
        )
        "link_handling" -> listOf(
            SettingsItem("link_behavior", Icons.Filled.Link, "Link Behavior", "How links are opened",
                subCategory = "link_behavior")
        )
        "faq" -> listOf(
            SettingsItem("faq_list", Icons.Filled.Help, "FAQ", "Frequently asked questions",
                subCategory = "faq_list")
        )
        "feed_layout" -> listOf(
            SettingsItem("layout_type", Icons.Filled.GridOn, "Layout Type", "Grid or list layout",
                dialogOptions = listOf(SettingsOption("Grid"), SettingsOption("List")))
        )
        "feed_sort" -> listOf(
            SettingsItem("sort_order", Icons.Filled.Sort, "Sort Order", "Sort order for feed items",
                dialogOptions = listOf(SettingsOption("Newest"), SettingsOption("Oldest"), SettingsOption("Popular")))
        )
        "feed_tags" -> listOf(
            SettingsItem("tag_filters", Icons.Filled.Label, "Tag Filters", "Filter by tags",
                dialogOptions = listOf(SettingsOption("All"), SettingsOption("Favorites"), SettingsOption("Watch Later")))
        )
        "search_results" -> listOf(
            SettingsItem("result_type", Icons.Filled.Search, "Result Type", "Search result type",
                dialogOptions = listOf(SettingsOption("All"), SettingsOption("Videos"), SettingsOption("Channels")))
        )
        "channel_layout" -> listOf(
            SettingsItem("display_type", Icons.Filled.GridOn, "Display Type", "Channel display type",
                dialogOptions = listOf(SettingsOption("Grid"), SettingsOption("List")))
        )
        "subscription_feed" -> listOf(
            SettingsItem("feed_type", Icons.Filled.Subscriptions, "Feed Type", "Subscription feed type",
                dialogOptions = listOf(SettingsOption("All"), SettingsOption("Recent")))
        )
        "default_quality" -> listOf(
            SettingsItem("quality", Icons.Filled.Hd, "Quality", "Default playback quality",
                dialogOptions = listOf(SettingsOption("Auto"), SettingsOption("1080p"), SettingsOption("720p"), SettingsOption("480p")))
        )
        "autoplay" -> listOf(
            SettingsItem("enabled", Icons.Filled.PlayArrow, "Autoplay", "Autoplay next video",
                dialogOptions = listOf(SettingsOption("Enabled"), SettingsOption("Disabled")))
        )
        "download_quality" -> listOf(
            SettingsItem("quality", Icons.Filled.Download, "Quality", "Default download quality",
                dialogOptions = listOf(SettingsOption("Auto"), SettingsOption("1080p"), SettingsOption("720p"), SettingsOption("480p")))
        )
        "download_storage" -> listOf(
            SettingsItem("location", Icons.Filled.Storage, "Storage", "Download storage location",
                dialogOptions = listOf(SettingsOption("Internal"), SettingsOption("External")))
        )
        "gesture_swipe" -> listOf(
            SettingsItem("brightness", Icons.Filled.Swipe, "Brightness", "Swipe for brightness",
                dialogOptions = listOf(SettingsOption("Enabled"), SettingsOption("Disabled"))),
            SettingsItem("volume", Icons.Filled.Swipe, "Volume", "Swipe for volume",
                dialogOptions = listOf(SettingsOption("Enabled"), SettingsOption("Disabled")))
        )
        "gesture_tap" -> listOf(
            SettingsItem("double_tap", Icons.Filled.TouchApp, "Double Tap", "Double tap action",
                dialogOptions = listOf(SettingsOption("Seek"), SettingsOption("Play/Pause")))
        )
        "casting_discovery" -> listOf(
            SettingsItem("protocol", Icons.Filled.Cast, "Protocol", "Cast discovery protocol",
                dialogOptions = listOf(SettingsOption("Default"), SettingsOption("mDNS"), SettingsOption("SSDP")))
        )
        "tracking" -> listOf(
            SettingsItem("analytics", Icons.Filled.Lock, "Analytics", "Analytics collection",
                dialogOptions = listOf(SettingsOption("Enabled"), SettingsOption("Disabled")))
        )
        "history" -> listOf(
            SettingsItem("auto_delete", Icons.Filled.History, "Auto Delete", "Auto delete history",
                dialogOptions = listOf(SettingsOption("Disabled"), SettingsOption("30 days"), SettingsOption("90 days")))
        )
        "local_data" -> listOf(
            SettingsItem("clear_cache", Icons.Filled.Storage, "Clear Cache", "Clear local cache",
                dialogOptions = listOf(SettingsOption("Clear")))
        )
        "export" -> listOf(
            SettingsItem("format", Icons.Filled.Upload, "Format", "Export format",
                dialogOptions = listOf(SettingsOption("JSON"), SettingsOption("XML")))
        )
        "import" -> listOf(
            SettingsItem("source", Icons.Filled.Download, "Source", "Import source",
                dialogOptions = listOf(SettingsOption("File"), SettingsOption("URL")))
        )
        "app_language" -> listOf(
            SettingsItem("language", Icons.Filled.Language, "Language", "App language",
                dialogOptions = listOf(SettingsOption("System"), SettingsOption("English"), SettingsOption("Spanish"), SettingsOption("French")))
        )
        "tab_behavior" -> listOf(
            SettingsItem("max_tabs", Icons.Filled.Tab, "Max Tabs", "Maximum number of tabs",
                dialogOptions = listOf(SettingsOption("5"), SettingsOption("10"), SettingsOption("Unlimited")))
        )
        "link_behavior" -> listOf(
            SettingsItem("open_in_app", Icons.Filled.Link, "Open In App", "Open links in app",
                dialogOptions = listOf(SettingsOption("Always"), SettingsOption("Ask"), SettingsOption("Never")))
        )
        "faq_list" -> listOf(
            SettingsItem("categories", Icons.Filled.Help, "Categories", "FAQ categories",
                subCategory = "faq_categories")
        )
        else -> listOf()
    }
}

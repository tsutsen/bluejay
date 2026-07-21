package com.futo.platformplayer.fragment.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import com.futo.platformplayer.compose.settings.SettingsOptionCard
import com.futo.platformplayer.compose.settings.SettingsScreen
import com.futo.platformplayer.compose.settings.SettingsOption
import com.futo.platformplayer.compose.settings.RadioButtonDialog
import com.futo.platformplayer.compose.theme.ComposeThemeMode
import com.futo.platformplayer.compose.theme.ComposeColorSchemeMode
import com.futo.platformplayer.compose.theme.ComposeContrastLevel
import com.futo.platformplayer.compose.theme.ComposeFontChoice
import com.futo.platformplayer.compose.theme.ComposeIconStyle
import com.futo.platformplayer.compose.theme.rememberComposeThemeState
import com.futo.platformplayer.compose.theme.applyThemeModeToLegacy
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment

/**
 * Generic settings screen — renders a list of options with icons, titles, subtitles.
 *
 * Usage:
 *   SettingsFragment.newInstance("Appearance")
 *   SettingsFragment.newInstance("Feed & Content")
 *
 * The fragment looks up its items internally based on the category name.
 * Supports three item types:
 *   - Dialog items: have dialogOptions, shows a RadioButtonDialog
 *   - Subsection items: have subCategory, navigates to another settings screen
 *   - Regular items: no dialog, no subsection, does nothing on click
 */
class SettingsFragment : MainFragment() {
    override val isMainView: Boolean = true
    override val isComposeMode: Boolean = true
    override val hasBottomBar: Boolean get() = true

    private val category: String by lazy { arguments?.getString("category") ?: "appearance" }

    @Composable
    override fun ComposeContent() {
        val themeState = rememberComposeThemeState()
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var dialogItem by remember { mutableStateOf<SettingsItem?>(null) }
        val ma = activity as? com.futo.platformplayer.activities.MainActivity

        val items = getItemsForCategory(category)

        SettingsScreen(
            title = category.replace("_", " ").replaceFirstChar { it.uppercase() },
            onBack = { navigateBack() }
        ) {
            items.forEach { item ->
                SettingsOptionCard(
                    icon = item.icon,
                    title = item.title,
                    subtitle = item.subtitle
                ) {
                    // Handle different item types
                    when {
                        item.subCategory != null -> {
                            // Navigate to subsection
                            ma?.navigate(
                                SettingsFragment.newInstance(item.subCategory!!),
                                null, true, false
                            )
                        }
                        item.dialogOptions != null -> {
                            // Show dialog
                            dialogItem = item
                        }
                        // else: regular item, do nothing
                    }
                }
            }

            // Dialog for items with options
            dialogItem?.let { item ->
                val options = item.dialogOptions!!
                val selected = when (item.key) {
                    "theme_mode" -> SettingsOption(themeState.value.themeMode.label)
                    "color_scheme" -> SettingsOption(themeState.value.colorSchemeMode.label)
                    "contrast" -> SettingsOption(themeState.value.contrastLevel.label)
                    "font" -> SettingsOption(themeState.value.fontChoice.label)
                    "icon_style" -> SettingsOption(themeState.value.iconStyle.label)
                    else -> SettingsOption(item.subtitle)
                }
                RadioButtonDialog(
                    title = item.title,
                    options = options,
                    selected = selected,
                    onSelected = { selectedOption ->
                        when (item.key) {
                            "theme_mode" -> applyThemeModeToLegacy(ctx, selectedOption.label.toThemeMode())
                            "color_scheme" -> {}
                            "contrast" -> {}
                            "font" -> {}
                            "icon_style" -> {}
                        }
                        dialogItem = null
                    },
                    onDismiss = { dialogItem = null }
                )
            }
        }
    }

    companion object {
        fun newInstance(category: String) = SettingsFragment().apply {
            arguments = android.os.Bundle().apply {
                putString("category", category)
            }
        }
    }
}

/**
 * A single settings option item.
 */
data class SettingsItem(
    val key: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val dialogOptions: List<SettingsOption>? = null,
    val subCategory: String? = null
)

// Extension properties for enum labels
private val ComposeThemeMode.label get() = when (this) {
    ComposeThemeMode.AUTO -> "Follow system"
    ComposeThemeMode.LIGHT -> "Light"
    ComposeThemeMode.DARK -> "Dark"
}
private val ComposeColorSchemeMode.label get() = when (this) {
    ComposeColorSchemeMode.DYNAMIC -> "Dynamic"
    ComposeColorSchemeMode.CUSTOM_SEED -> "Custom"
    ComposeColorSchemeMode.PRESET -> "Preset"
}
private val ComposeContrastLevel.label get() = when (this) {
    ComposeContrastLevel.STANDARD -> "Standard"
    ComposeContrastLevel.MEDIUM -> "Medium"
    ComposeContrastLevel.HIGH -> "High"
}
private val ComposeFontChoice.label get() = when (this) {
    ComposeFontChoice.INTER -> "Inter"
    ComposeFontChoice.SYSTEM -> "System"
}
private val ComposeIconStyle.label get() = when (this) {
    ComposeIconStyle.ROUNDED -> "Rounded"
    ComposeIconStyle.SHARP -> "Sharp"
    ComposeIconStyle.OUTLINED -> "Outlined"
}

// Extension functions for label -> enum conversion
private fun String.toThemeMode(): ComposeThemeMode = when (lowercase()) {
    "follow system" -> ComposeThemeMode.AUTO
    "light" -> ComposeThemeMode.LIGHT
    "dark" -> ComposeThemeMode.DARK
    else -> ComposeThemeMode.AUTO
}

fun getItemsForCategory(category: String): List<SettingsItem> {
    return when (category) {
        "appearance" -> listOf(
            SettingsItem("theme_mode", Icons.Default.BrightnessAuto, "Theme", "Follow system",
                listOf(SettingsOption("Follow system"), SettingsOption("Light"), SettingsOption("Dark"))),
            SettingsItem("color_scheme", Icons.Default.Palette, "Color Scheme", "Dynamic",
                listOf(SettingsOption("Dynamic"), SettingsOption("Custom seed"), SettingsOption("Preset"))),
            SettingsItem("font", Icons.Default.TextFields, "Typography", "Inter",
                listOf(SettingsOption("Inter"), SettingsOption("System"))),
            SettingsItem("icon_style", Icons.Default.Star, "Icon Style", "Rounded",
                listOf(SettingsOption("Rounded"), SettingsOption("Sharp"), SettingsOption("Outlined"))),
            SettingsItem("contrast", Icons.Default.Palette, "Contrast", "Standard",
                listOf(SettingsOption("Standard"), SettingsOption("Medium"), SettingsOption("High")))
        )
        "feed" -> listOf(
            SettingsItem("home_feed", Icons.Default.Home, "Home Feed", "Default feed layout",
                subCategory = "home_feed"),
            SettingsItem("search", Icons.Default.Search, "Search", "Search results behavior",
                subCategory = "search"),
            SettingsItem("channels", Icons.Default.LiveTv, "Channels", "Channel display options",
                subCategory = "channels"),
            SettingsItem("subscriptions", Icons.Default.Subscriptions, "Subscriptions", "Subscription feed settings",
                subCategory = "subscriptions")
        )
        "player" -> listOf(
            SettingsItem("playback", Icons.Default.PlayArrow, "Playback", "Default playback behavior",
                subCategory = "playback"),
            SettingsItem("downloads", Icons.Default.Download, "Downloads", "Download quality and storage",
                subCategory = "downloads"),
            SettingsItem("gestures", Icons.Default.TouchApp, "Gestures", "Video player gestures",
                subCategory = "gestures"),
            SettingsItem("casting", Icons.Default.Cast, "Casting", "Cast device discovery",
                subCategory = "casting")
        )
        "privacy" -> listOf(
            SettingsItem("privacy", Icons.Default.Lock, "Privacy", "Privacy and tracking settings",
                subCategory = "privacy"),
            SettingsItem("data_management", Icons.Default.Storage, "Data Management", "Cache, history, and local data",
                subCategory = "data_management"),
            SettingsItem("backup", Icons.Default.Backup, "Backup & Restore", "Export and import settings",
                subCategory = "backup")
        )
        "sync" -> listOf(
            SettingsItem("sync", Icons.Default.Sync, "Synchronization", "Cross-device sync settings",
                subCategory = "sync"),
            SettingsItem("polycentric", Icons.Default.AccountCircle, "Polycentric", "Decentralized identity",
                subCategory = "polycentric")
        )
        "general" -> listOf(
            SettingsItem("language", Icons.Default.Language, "Language", "App language",
                subCategory = "language"),
            SettingsItem("tabs", Icons.Default.Tab, "Tabs", "Tab behavior and limits",
                subCategory = "tabs"),
            SettingsItem("link_handling", Icons.Default.Link, "Link Handling", "How links are opened",
                subCategory = "link_handling"),
            SettingsItem("faq", Icons.Default.Help, "FAQ / Issues", "Frequently asked questions",
                subCategory = "faq")
        )
        "about" -> listOf(
            SettingsItem("version", Icons.Default.Info, "Version", "Current app version"),
            SettingsItem("license", Icons.Default.Description, "License", "Open source licenses"),
            SettingsItem("payment", Icons.Default.MonetizationOn, "Payment", "Support Grayjay")
        )
        "home_feed" -> listOf(
            SettingsItem("feed_layout", Icons.Default.GridOn, "Feed Layout", "Grid or list layout",
                subCategory = "feed_layout"),
            SettingsItem("feed_sort", Icons.Default.Sort, "Sort By", "Sort order for feed items",
                subCategory = "feed_sort"),
            SettingsItem("feed_tags", Icons.Default.Label, "Tags", "Filter by tags",
                subCategory = "feed_tags")
        )
        "search" -> listOf(
            SettingsItem("search_results", Icons.Default.Search, "Search Results", "Search result behavior",
                subCategory = "search_results")
        )
        "channels" -> listOf(
            SettingsItem("channel_layout", Icons.Default.GridOn, "Channel Layout", "Channel display layout",
                subCategory = "channel_layout")
        )
        "subscriptions" -> listOf(
            SettingsItem("subscription_feed", Icons.Default.Subscriptions, "Subscription Feed", "Subscription feed settings",
                subCategory = "subscription_feed")
        )
        "playback" -> listOf(
            SettingsItem("default_quality", Icons.Default.Hd, "Default Quality", "Default playback quality",
                subCategory = "default_quality"),
            SettingsItem("autoplay", Icons.Default.PlayArrow, "Autoplay", "Autoplay next video",
                subCategory = "autoplay")
        )
        "downloads" -> listOf(
            SettingsItem("download_quality", Icons.Default.Download, "Download Quality", "Default download quality",
                subCategory = "download_quality"),
            SettingsItem("download_storage", Icons.Default.Storage, "Download Storage", "Download storage location",
                subCategory = "download_storage")
        )
        "gestures" -> listOf(
            SettingsItem("gesture_swipe", Icons.Default.Swipe, "Swipe Gestures", "Swipe gesture settings",
                subCategory = "gesture_swipe"),
            SettingsItem("gesture_tap", Icons.Default.TouchApp, "Tap Gestures", "Tap gesture settings",
                subCategory = "gesture_tap")
        )
        "casting" -> listOf(
            SettingsItem("casting_discovery", Icons.Default.Cast, "Discovery", "Cast device discovery",
                subCategory = "casting_discovery")
        )
        "privacy" -> listOf(
            SettingsItem("tracking", Icons.Default.Lock, "Tracking", "Tracking settings",
                subCategory = "tracking"),
            SettingsItem("history", Icons.Default.History, "History", "History settings",
                subCategory = "history")
        )
        "data_management" -> listOf(
            SettingsItem("cache", Icons.Default.Storage, "Cache", "Cache settings",
                subCategory = "cache"),
            SettingsItem("history_data", Icons.Default.History, "History Data", "History data management",
                subCategory = "history_data"),
            SettingsItem("local_data", Icons.Default.Storage, "Local Data", "Local data management",
                subCategory = "local_data")
        )
        "backup" -> listOf(
            SettingsItem("export", Icons.Default.Upload, "Export", "Export settings",
                subCategory = "export"),
            SettingsItem("import", Icons.Default.Download, "Import", "Import settings",
                subCategory = "import")
        )
        "sync" -> listOf(
            SettingsItem("sync_settings", Icons.Default.Sync, "Sync Settings", "Sync settings",
                subCategory = "sync_settings")
        )
        "polycentric" -> listOf(
            SettingsItem("identity", Icons.Default.AccountCircle, "Identity", "Decentralized identity",
                subCategory = "identity")
        )
        "language" -> listOf(
            SettingsItem("app_language", Icons.Default.Language, "App Language", "App language",
                subCategory = "app_language")
        )
        "tabs" -> listOf(
            SettingsItem("tab_behavior", Icons.Default.Tab, "Tab Behavior", "Tab behavior settings",
                subCategory = "tab_behavior")
        )
        "link_handling" -> listOf(
            SettingsItem("link_behavior", Icons.Default.Link, "Link Behavior", "How links are opened",
                subCategory = "link_behavior")
        )
        "faq" -> listOf(
            SettingsItem("faq_list", Icons.Default.Help, "FAQ", "Frequently asked questions",
                subCategory = "faq_list")
        )
        "feed_layout" -> listOf(
            SettingsItem("layout_type", Icons.Default.GridOn, "Layout Type", "Grid or list layout",
                dialogOptions = listOf(SettingsOption("Grid"), SettingsOption("List")))
        )
        "feed_sort" -> listOf(
            SettingsItem("sort_order", Icons.Default.Sort, "Sort Order", "Sort order for feed items",
                dialogOptions = listOf(SettingsOption("Newest"), SettingsOption("Oldest"), SettingsOption("Popular")))
        )
        "feed_tags" -> listOf(
            SettingsItem("tag_filter", Icons.Default.Label, "Tag Filter", "Filter by tags",
                dialogOptions = listOf(SettingsOption("All"), SettingsOption("Favorites")))
        )
        "search_results" -> listOf(
            SettingsItem("result_type", Icons.Default.Search, "Result Type", "Search result type",
                dialogOptions = listOf(SettingsOption("All"), SettingsOption("Videos"), SettingsOption("Channels")))
        )
        "channel_layout" -> listOf(
            SettingsItem("channel_display", Icons.Default.GridOn, "Channel Display", "Channel display layout",
                dialogOptions = listOf(SettingsOption("Grid"), SettingsOption("List")))
        )
        "subscription_feed" -> listOf(
            SettingsItem("feed_display", Icons.Default.Subscriptions, "Feed Display", "Subscription feed display",
                dialogOptions = listOf(SettingsOption("All"), SettingsOption("Unwatched")))
        )
        "default_quality" -> listOf(
            SettingsItem("quality", Icons.Default.Hd, "Quality", "Default playback quality",
                dialogOptions = listOf(SettingsOption("Auto"), SettingsOption("1080p"), SettingsOption("720p"), SettingsOption("480p")))
        )
        "autoplay" -> listOf(
            SettingsItem("autoplay_toggle", Icons.Default.PlayArrow, "Autoplay", "Autoplay next video",
                dialogOptions = listOf(SettingsOption("On"), SettingsOption("Off")))
        )
        "download_quality" -> listOf(
            SettingsItem("quality", Icons.Default.Download, "Quality", "Default download quality",
                dialogOptions = listOf(SettingsOption("Auto"), SettingsOption("1080p"), SettingsOption("720p"), SettingsOption("480p")))
        )
        "download_storage" -> listOf(
            SettingsItem("storage", Icons.Default.Storage, "Storage", "Download storage location",
                dialogOptions = listOf(SettingsOption("Internal"), SettingsOption("External")))
        )
        "gesture_swipe" -> listOf(
            SettingsItem("swipe_action", Icons.Default.Swipe, "Swipe Action", "Swipe gesture action",
                dialogOptions = listOf(SettingsOption("Seek"), SettingsOption("Volume"), SettingsOption("Brightness")))
        )
        "gesture_tap" -> listOf(
            SettingsItem("tap_action", Icons.Default.TouchApp, "Tap Action", "Tap gesture action",
                dialogOptions = listOf(SettingsOption("Play/Pause"), SettingsOption("Fullscreen"), SettingsOption("None")))
        )
        "casting_discovery" -> listOf(
            SettingsItem("discovery", Icons.Default.Cast, "Discovery", "Cast device discovery",
                dialogOptions = listOf(SettingsOption("On"), SettingsOption("Off")))
        )
        "tracking" -> listOf(
            SettingsItem("tracking_toggle", Icons.Default.Lock, "Tracking", "Tracking settings",
                dialogOptions = listOf(SettingsOption("On"), SettingsOption("Off")))
        )
        "history" -> listOf(
            SettingsItem("history_toggle", Icons.Default.History, "History", "History settings",
                dialogOptions = listOf(SettingsOption("On"), SettingsOption("Off")))
        )
        "cache" -> listOf(
            SettingsItem("cache_clear", Icons.Default.Storage, "Clear Cache", "Clear cached data",
                dialogOptions = listOf(SettingsOption("Clear"), SettingsOption("Cancel")))
        )
        "history_data" -> listOf(
            SettingsItem("history_clear", Icons.Default.History, "Clear History", "Clear watch history",
                dialogOptions = listOf(SettingsOption("Clear"), SettingsOption("Cancel")))
        )
        "local_data" -> listOf(
            SettingsItem("local_data_clear", Icons.Default.Storage, "Clear Local Data", "Clear local data",
                dialogOptions = listOf(SettingsOption("Clear"), SettingsOption("Cancel")))
        )
        "export" -> listOf(
            SettingsItem("export_settings", Icons.Default.Upload, "Export Settings", "Export settings to file",
                dialogOptions = listOf(SettingsOption("Export"), SettingsOption("Cancel")))
        )
        "import" -> listOf(
            SettingsItem("import_settings", Icons.Default.Download, "Import Settings", "Import settings from file",
                dialogOptions = listOf(SettingsOption("Import"), SettingsOption("Cancel")))
        )
        "sync_settings" -> listOf(
            SettingsItem("sync_toggle", Icons.Default.Sync, "Sync", "Cross-device sync",
                dialogOptions = listOf(SettingsOption("On"), SettingsOption("Off")))
        )
        "identity" -> listOf(
            SettingsItem("identity_setup", Icons.Default.AccountCircle, "Identity Setup", "Set up decentralized identity",
                dialogOptions = listOf(SettingsOption("Setup"), SettingsOption("Cancel")))
        )
        "app_language" -> listOf(
            SettingsItem("language_select", Icons.Default.Language, "Language", "App language",
                dialogOptions = listOf(SettingsOption("System"), SettingsOption("English"), SettingsOption("French"), SettingsOption("Spanish")))
        )
        "tab_behavior" -> listOf(
            SettingsItem("tab_limit", Icons.Default.Tab, "Tab Limit", "Maximum number of tabs",
                dialogOptions = listOf(SettingsOption("5"), SettingsOption("10"), SettingsOption("Unlimited")))
        )
        "link_behavior" -> listOf(
            SettingsItem("link_action", Icons.Default.Link, "Link Action", "How links are opened",
                dialogOptions = listOf(SettingsOption("In-app"), SettingsOption("Browser"), SettingsOption("Ask")))
        )
        "faq_list" -> listOf(
            SettingsItem("faq_content", Icons.Default.Help, "FAQ Content", "Frequently asked questions",
                dialogOptions = listOf(SettingsOption("View FAQ"), SettingsOption("Close")))
        )
        else -> emptyList()
    }
}

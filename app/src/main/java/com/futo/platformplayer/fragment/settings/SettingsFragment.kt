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
                    dialogItem = item
                }
            }

            // Dialog for items with options
            dialogItem?.let { item ->
                val options = item.dialogOptions!!
                val selected = com.futo.platformplayer.compose.settings.SettingsOption(
                    when (item.key) {
                        "theme_mode" -> themeState.value.themeMode.label
                        "color_scheme" -> themeState.value.colorSchemeMode.label
                        "contrast" -> themeState.value.contrastLevel.label
                        "font" -> themeState.value.fontChoice.label
                        "icon_style" -> themeState.value.iconStyle.label
                        else -> ""
                    }
                )
                RadioButtonDialog(
                    title = item.title,
                    options = options,
                    selected = selected,
                    onSelected = {
                        when (item.key) {
                            "theme_mode" -> applyThemeModeToLegacy(ctx, it.label.toThemeMode())
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
    val dialogOptions: List<com.futo.platformplayer.compose.settings.SettingsOption>? = null
)

// Extension properties for enum labels
private val ComposeThemeMode.label get() = when (this) {
    ComposeThemeMode.AUTO -> "Follow system"
    ComposeThemeMode.LIGHT -> "Light"
    ComposeThemeMode.DARK -> "Dark"
}
private val ComposeColorSchemeMode.label get() = when (this) {
    ComposeColorSchemeMode.DYNAMIC -> "Dynamic"
    ComposeColorSchemeMode.CUSTOM_SEED -> "Custom seed"
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

// Extension function to convert label back to enum
private fun String.toThemeMode(): ComposeThemeMode = when (this) {
    "Follow system" -> ComposeThemeMode.AUTO
    "Light" -> ComposeThemeMode.LIGHT
    "Dark" -> ComposeThemeMode.DARK
    else -> ComposeThemeMode.AUTO
}

// Predefined item lists for each settings category
fun getItemsForCategory(category: String): List<SettingsItem> {
    return when (category) {
        "appearance" -> listOf(
            SettingsItem("theme_mode", Icons.Default.BrightnessAuto, "Theme", "Follow system",
                listOf(com.futo.platformplayer.compose.settings.SettingsOption("Follow system"), com.futo.platformplayer.compose.settings.SettingsOption("Light"), com.futo.platformplayer.compose.settings.SettingsOption("Dark"))),
            SettingsItem("color_scheme", Icons.Default.Palette, "Color Scheme", "Dynamic",
                listOf(com.futo.platformplayer.compose.settings.SettingsOption("Dynamic"), com.futo.platformplayer.compose.settings.SettingsOption("Custom seed"), com.futo.platformplayer.compose.settings.SettingsOption("Preset"))),
            SettingsItem("font", Icons.Default.TextFields, "Typography", "Inter",
                listOf(com.futo.platformplayer.compose.settings.SettingsOption("Inter"), com.futo.platformplayer.compose.settings.SettingsOption("System"))),
            SettingsItem("icon_style", Icons.Default.Star, "Icon Style", "Rounded",
                listOf(com.futo.platformplayer.compose.settings.SettingsOption("Rounded"), com.futo.platformplayer.compose.settings.SettingsOption("Sharp"), com.futo.platformplayer.compose.settings.SettingsOption("Outlined"))),
            SettingsItem("contrast", Icons.Default.Palette, "Contrast", "Standard",
                listOf(com.futo.platformplayer.compose.settings.SettingsOption("Standard"), com.futo.platformplayer.compose.settings.SettingsOption("Medium"), com.futo.platformplayer.compose.settings.SettingsOption("High")))
        )
        "feed" -> listOf(
            SettingsItem("home_feed", Icons.Default.Home, "Home Feed", "Default feed layout"),
            SettingsItem("search", Icons.Default.Search, "Search", "Search results behavior"),
            SettingsItem("channels", Icons.Default.LiveTv, "Channels", "Channel display options"),
            SettingsItem("subscriptions", Icons.Default.Subscriptions, "Subscriptions", "Subscription feed settings")
        )
        "player" -> listOf(
            SettingsItem("playback", Icons.Default.PlayArrow, "Playback", "Default playback behavior"),
            SettingsItem("downloads", Icons.Default.Download, "Downloads", "Download quality and storage"),
            SettingsItem("gestures", Icons.Default.TouchApp, "Gestures", "Video player gestures"),
            SettingsItem("casting", Icons.Default.Cast, "Casting", "Cast device discovery")
        )
        "privacy" -> listOf(
            SettingsItem("privacy", Icons.Default.Lock, "Privacy", "Privacy and tracking settings"),
            SettingsItem("data_management", Icons.Default.Storage, "Data Management", "Cache, history, and local data"),
            SettingsItem("backup", Icons.Default.Backup, "Backup & Restore", "Export and import settings")
        )
        "sync" -> listOf(
            SettingsItem("sync", Icons.Default.Sync, "Synchronization", "Cross-device sync settings"),
            SettingsItem("polycentric", Icons.Default.AccountCircle, "Polycentric", "Decentralized identity")
        )
        "general" -> listOf(
            SettingsItem("language", Icons.Default.Language, "Language", "App language"),
            SettingsItem("tabs", Icons.Default.Tab, "Tabs", "Tab behavior and limits"),
            SettingsItem("link_handling", Icons.Default.Link, "Link Handling", "How links are opened"),
            SettingsItem("faq", Icons.Default.Help, "FAQ / Issues", "Frequently asked questions")
        )
        "about" -> listOf(
            SettingsItem("version", Icons.Default.Info, "Version", "Current app version"),
            SettingsItem("license", Icons.Default.Description, "License", "Open source licenses"),
            SettingsItem("payment", Icons.Default.MonetizationOn, "Payment", "Support Grayjay")
        )
        else -> emptyList()
    }
}

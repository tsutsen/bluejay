package com.futo.platformplayer.fragment.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.settings.*
import com.futo.platformplayer.compose.theme.ComposeThemeMode
import com.futo.platformplayer.compose.theme.ComposeColorSchemeMode
import com.futo.platformplayer.compose.theme.ComposeContrastLevel
import com.futo.platformplayer.compose.theme.ComposeFontChoice
import com.futo.platformplayer.compose.theme.ComposeIconStyle
import com.futo.platformplayer.compose.theme.rememberComposeThemeState
import com.futo.platformplayer.compose.theme.applyThemeModeToLegacy
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment

/**
 * Appearance Settings — theme, color scheme, typography, icons, contrast.
 * Demonstrates live theme reactivity via rememberComposeThemeState().
 */
class AppearanceSettingsFragment : MainFragment() {
    override val isMainView: Boolean = true
    override val isComposeMode: Boolean = true
    override val hasBottomBar: Boolean get() = true

    @Composable
    override fun ComposeContent() {
        val themeState = rememberComposeThemeState()
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var showThemeDialog by remember { mutableStateOf(false) }
        var showColorSchemeDialog by remember { mutableStateOf(false) }
        var showContrastDialog by remember { mutableStateOf(false) }
        var showFontDialog by remember { mutableStateOf(false) }
        var showIconStyleDialog by remember { mutableStateOf(false) }

        SettingsScreen(
            title = "Appearance",
            onBack = { navigateBack() }
        ) {
            // Theme
            SettingsOptionCard(
                icon = Icons.Default.BrightnessAuto,
                title = "Theme",
                subtitle = themeState.value.themeMode.label
            ) { showThemeDialog = true }

            // Color Scheme
            SettingsOptionCard(
                icon = Icons.Default.Palette,
                title = "Color Scheme",
                subtitle = themeState.value.colorSchemeMode.label
            ) { showColorSchemeDialog = true }

            // Typography
            SettingsOptionCard(
                icon = Icons.Default.TextFields,
                title = "Typography",
                subtitle = themeState.value.fontChoice.label
            ) { showFontDialog = true }

            // Icon Style
            SettingsOptionCard(
                icon = Icons.Default.Star,
                title = "Icon Style",
                subtitle = themeState.value.iconStyle.label
            ) { showIconStyleDialog = true }

            // Contrast
            SettingsOptionCard(
                icon = Icons.Default.Palette,
                title = "Contrast",
                subtitle = themeState.value.contrastLevel.label
            ) { showContrastDialog = true }

            // Dialogs
            if (showThemeDialog) {
                RadioButtonDialog(
                    title = "Theme",
                    options = ComposeThemeMode.values().map { SettingsOption(it.label) },
                    selected = SettingsOption(themeState.value.themeMode.label),
                    onSelected = {
                        applyThemeModeToLegacy(ctx, it.label.toThemeMode())
                        showThemeDialog = false
                    },
                    onDismiss = { showThemeDialog = false }
                )
            }

            if (showColorSchemeDialog) {
                RadioButtonDialog(
                    title = "Color Scheme",
                    options = ComposeColorSchemeMode.values().map { SettingsOption(it.label) },
                    selected = SettingsOption(themeState.value.colorSchemeMode.label),
                    onSelected = { showColorSchemeDialog = false },
                    onDismiss = { showColorSchemeDialog = false }
                )
            }

            if (showContrastDialog) {
                RadioButtonDialog(
                    title = "Contrast",
                    options = ComposeContrastLevel.values().map { SettingsOption(it.label) },
                    selected = SettingsOption(themeState.value.contrastLevel.label),
                    onSelected = { showContrastDialog = false },
                    onDismiss = { showContrastDialog = false }
                )
            }

            if (showFontDialog) {
                RadioButtonDialog(
                    title = "Typography",
                    options = ComposeFontChoice.values().map { SettingsOption(it.label) },
                    selected = SettingsOption(themeState.value.fontChoice.label),
                    onSelected = { showFontDialog = false },
                    onDismiss = { showFontDialog = false }
                )
            }

            if (showIconStyleDialog) {
                RadioButtonDialog(
                    title = "Icon Style",
                    options = ComposeIconStyle.values().map { SettingsOption(it.label) },
                    selected = SettingsOption(themeState.value.iconStyle.label),
                    onSelected = { showIconStyleDialog = false },
                    onDismiss = { showIconStyleDialog = false }
                )
            }
        }
    }

    companion object {
        fun newInstance() = AppearanceSettingsFragment().apply {}
    }
}

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

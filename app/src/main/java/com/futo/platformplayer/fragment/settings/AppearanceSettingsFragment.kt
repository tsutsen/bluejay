package com.futo.platformplayer.fragment.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    override fun ComposeContent() { AppearanceSettingsScreen { navigateBack() } }

    companion object {
        fun newInstance() = AppearanceSettingsFragment().apply {}
    }
}

data class AppearanceOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: @Composable () -> Unit,
    val onSelected: () -> Unit,
    val trailing: @Composable () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsScreen(onNavigateBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val themeState = rememberComposeThemeState()
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showColorSchemeDialog by remember { mutableStateOf(false) }
    var showContrastDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showIconStyleDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
            // Theme Mode
            items(listOf("theme_mode"), key = { it }) {
                AppearanceOptionCard(
                    option = AppearanceOption(
                        id = "theme_mode",
                        title = "Theme",
                        subtitle = when (themeState.value.themeMode) {
                            ComposeThemeMode.AUTO -> "Follow system"
                            ComposeThemeMode.LIGHT -> "Light"
                            ComposeThemeMode.DARK -> "Dark"
                        },
                        icon = { Icon(Icons.Default.BrightnessAuto, contentDescription = null) },
                        onSelected = { showThemeModeDialog = true },
                        trailing = {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                        }
                    )
                )
            }

            // Theme Mode Dialog
            if (showThemeModeDialog) {
                item {
                    val themeModes = listOf(
                        ComposeThemeMode.AUTO to "Follow system",
                        ComposeThemeMode.LIGHT to "Light",
                        ComposeThemeMode.DARK to "Dark"
                    )
                    AlertDialog(
                    onDismissRequest = { showThemeModeDialog = false },
                    title = { Text("Theme") },
                    text = {
                        Column {
                            themeModes.forEach { (mode, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            applyThemeModeToLegacy(ctx, mode)
                                            showThemeModeDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = themeState.value.themeMode == mode,
                                        onClick = {
                                            applyThemeModeToLegacy(ctx, mode)
                                            showThemeModeDialog = false
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(label)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showThemeModeDialog = false }) {
                            Text("Close")
                        }
                    }
                )
                }
            }

            // Color Scheme
            items(listOf("color_scheme"), key = { it }) {
                AppearanceOptionCard(
                    option = AppearanceOption(
                        id = "color_scheme",
                        title = "Color Scheme",
                        subtitle = when (themeState.value.colorSchemeMode) {
                            ComposeColorSchemeMode.DYNAMIC -> "Dynamic"
                            ComposeColorSchemeMode.CUSTOM_SEED -> "Custom seed"
                            ComposeColorSchemeMode.PRESET -> "Preset"
                        },
                        icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                        onSelected = { showColorSchemeDialog = true },
                        trailing = {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                        }
                    )
                )
            }

            // Font
            items(listOf("font"), key = { it }) {
                AppearanceOptionCard(
                    option = AppearanceOption(
                        id = "font",
                        title = "Typography",
                        subtitle = when (themeState.value.fontChoice) {
                            ComposeFontChoice.INTER -> "Inter"
                            ComposeFontChoice.SYSTEM -> "System"
                        },
                        icon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                        onSelected = { showFontDialog = true },
                        trailing = {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                        }
                    )
                )
            }

            // Icon Style
            items(listOf("icon_style"), key = { it }) {
                AppearanceOptionCard(
                    option = AppearanceOption(
                        id = "icon_style",
                        title = "Icon Style",
                        subtitle = when (themeState.value.iconStyle) {
                            ComposeIconStyle.ROUNDED -> "Rounded"
                            ComposeIconStyle.SHARP -> "Sharp"
                            ComposeIconStyle.OUTLINED -> "Outlined"
                        },
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        onSelected = { showIconStyleDialog = true },
                        trailing = {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                        }
                    )
                )
            }

            // Contrast
            items(listOf("contrast"), key = { it }) {
                AppearanceOptionCard(
                    option = AppearanceOption(
                        id = "contrast",
                        title = "Contrast",
                        subtitle = when (themeState.value.contrastLevel) {
                            ComposeContrastLevel.STANDARD -> "Standard"
                            ComposeContrastLevel.MEDIUM -> "Medium"
                            ComposeContrastLevel.HIGH -> "High"
                        },
                        icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                        onSelected = { showContrastDialog = true },
                        trailing = {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun AppearanceOptionCard(option: AppearanceOption) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = option.onSelected)
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                option.icon()
            }

            // Title and subtitle
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Trailing
            option.trailing()
        }
    }
}

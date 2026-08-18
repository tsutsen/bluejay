package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.datastore.model.ContrastLevel
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.component.SettingsButtonCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsOptionCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsSwitchCard
import com.tsutsen.platformplayer.core.navigation.Navigator

/**
 * Settings — grouped by section (Appearance, Gestures, Content, Playback,
 * General). Every live row is backed by a real setter on the Settings-backed
 * SettingsRepository; gesture slots are placeholders until their actions are
 * defined.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navigator: Navigator,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState as? SettingsUiState.Loaded
    var selectedChoice by remember { mutableStateOf<Choice?>(null) }

    when (val current = uiState) {
        is SettingsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        is SettingsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Error loading settings: ${current.message}",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            }
        }

        is SettingsUiState.Loaded -> {
            SettingsContent(
                state = current,
                viewModel = viewModel,
                onChoiceSelected = { selectedChoice = it },
                onPluginsClick = { navigator.navigateToPluginBrowser() },
            )
        }
    }

    val loaded = state
    when (val choice = selectedChoice) {
        null -> {
            Unit
        }

        Choice.THEME -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Theme",
                    options =
                        listOf(
                            "Follow system" to "AUTO",
                            "Light" to "LIGHT",
                            "Dark" to "DARK",
                        ),
                    selected = it.appearance.themeMode.name,
                    onSelected = { value ->
                        viewModel.updateAppearance(
                            it.appearance.copy(themeMode = ThemeMode.valueOf(value)),
                        )
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.CONTRAST -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Contrast",
                    options =
                        listOf(
                            "Standard" to "STANDARD",
                            "Medium" to "MEDIUM",
                            "High" to "HIGH",
                        ),
                    selected = it.appearance.contrastLevel.name,
                    onSelected = { value ->
                        viewModel.updateAppearance(
                            it.appearance.copy(contrastLevel = ContrastLevel.valueOf(value)),
                        )
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.GRID_COLUMNS -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Grid columns",
                    options =
                        listOf(
                            "2 columns" to "2",
                            "3 columns" to "3",
                            "4 columns" to "4",
                        ),
                    selected = it.gridColumns.toString(),
                    onSelected = { value ->
                        viewModel.updateGeneral("gridColumns", value.toInt())
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.DEFAULT_RESOLUTION -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Default resolution",
                    options =
                        listOf(
                            "Auto" to "auto",
                            "144p" to "144",
                            "240p" to "240",
                            "360p" to "360",
                            "480p" to "480",
                            "720p" to "720",
                            "1080p" to "1080",
                            "1440p" to "1440",
                        ),
                    selected = it.defaultResolution,
                    onSelected = { value ->
                        viewModel.updateGeneral("defaultResolution", value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.SUBTITLE_LANGUAGE -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Preferred subtitle language",
                    options =
                        listOf(
                            "Auto" to "auto",
                            "English" to "en",
                            "German" to "de",
                            "French" to "fr",
                            "Spanish" to "es",
                            "Italian" to "it",
                            "Japanese" to "ja",
                            "Korean" to "ko",
                            "Portuguese" to "pt",
                            "Russian" to "ru",
                        ),
                    selected = it.preferredSubtitleLanguage,
                    onSelected = { value ->
                        viewModel.updateGeneral("preferredSubtitleLanguage", value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }
    }
}

private enum class Choice {
    THEME,
    CONTRAST,
    GRID_COLUMNS,
    DEFAULT_RESOLUTION,
    SUBTITLE_LANGUAGE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    state: SettingsUiState.Loaded,
    viewModel: SettingsViewModel,
    onChoiceSelected: (Choice) -> Unit,
    onPluginsClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ==================== Appearance ====================
            item { SectionHeader("Appearance") }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.BrightnessAuto,
                    title = "Theme",
                    subtitle =
                        when (state.appearance.themeMode) {
                            ThemeMode.AUTO -> "Follow system"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                    onClick = { onChoiceSelected(Choice.THEME) },
                )
            }
            item {
                SettingsSwitchCard(
                    icon = Icons.Filled.Palette,
                    title = "Dynamic color",
                    subtitle = "Material You colors from wallpaper",
                    checked = state.appearance.dynamicColor,
                    onCheckedChange = { viewModel.updateGeneral("dynamicColor", it) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.Contrast,
                    title = "Contrast",
                    subtitle =
                        when (state.appearance.contrastLevel) {
                            ContrastLevel.STANDARD -> "Standard"
                            ContrastLevel.MEDIUM -> "Medium"
                            ContrastLevel.HIGH -> "High"
                        },
                    onClick = { onChoiceSelected(Choice.CONTRAST) },
                )
            }

            // ==================== Gestures ====================
            // Placeholder slots — actions to be defined.
            item { SectionHeader("Gestures") }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.SwipeLeft,
                    title = "Left slot",
                    subtitle = "No action assigned",
                    onClick = {},
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.SwipeRight,
                    title = "Right slot",
                    subtitle = "No action assigned",
                    onClick = {},
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.SwipeVertical,
                    title = "Top slot",
                    subtitle = "No action assigned",
                    onClick = {},
                )
            }

            // ==================== Content ====================
            item { SectionHeader("Content") }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.Extension,
                    title = "Plugins",
                    subtitle = "Manage installed source plugins",
                    onClick = onPluginsClick,
                )
            }
            item {
                SettingsSwitchCard(
                    icon = Icons.Filled.VideoLibrary,
                    title = "Show recommended videos",
                    subtitle = "Recommended tab on the video page",
                    checked = state.showRecommendedVideos,
                    onCheckedChange = { viewModel.updateGeneral("showRecommendedVideos", it) },
                )
            }
            item {
                SettingsSwitchCard(
                    icon = Icons.Filled.Chat,
                    title = "Show comments",
                    subtitle = "Comments tab on the video page",
                    checked = state.showComments,
                    onCheckedChange = { viewModel.updateGeneral("showComments", it) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.GridOn,
                    title = "Grid columns",
                    subtitle = "${state.gridColumns} columns",
                    onClick = { onChoiceSelected(Choice.GRID_COLUMNS) },
                )
            }

            // ==================== Playback ====================
            item { SectionHeader("Playback") }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.HighQuality,
                    title = "Default resolution",
                    subtitle = if (state.defaultResolution == "auto") "Auto" else "${state.defaultResolution}p",
                    onClick = { onChoiceSelected(Choice.DEFAULT_RESOLUTION) },
                )
            }
            item {
                SettingsSwitchCard(
                    icon = Icons.Filled.Headphones,
                    title = "Background playback",
                    subtitle = "Keep playing when the app is in the background",
                    checked = state.enableBackgroundPlayback,
                    onCheckedChange = { viewModel.updateGeneral("enableBackgroundPlayback", it) },
                )
            }
            item {
                SettingsSwitchCard(
                    icon = Icons.Filled.PictureInPictureAlt,
                    title = "Picture-in-picture",
                    subtitle = "Play video in a floating window",
                    checked = state.enablePictureInPicture,
                    onCheckedChange = { viewModel.updateGeneral("enablePictureInPicture", it) },
                )
            }
            item {
                SettingsSwitchCard(
                    icon = Icons.Filled.Subtitles,
                    title = "Remember subtitle state",
                    subtitle = "Keep subtitle on/off across sessions",
                    checked = state.rememberSubtitleState,
                    onCheckedChange = { viewModel.updateGeneral("rememberSubtitleState", it) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.Translate,
                    title = "Preferred subtitle language",
                    subtitle = subtitleLanguageLabel(state.preferredSubtitleLanguage),
                    onClick = { onChoiceSelected(Choice.SUBTITLE_LANGUAGE) },
                )
            }

            // ==================== General ====================
            item { SectionHeader("General") }
            item {
                SettingsSwitchCard(
                    icon = Icons.Filled.ExitToApp,
                    title = "Confirm exit",
                    subtitle = "Ask before closing the app",
                    checked = state.confirmExit,
                    onCheckedChange = { viewModel.updateGeneral("confirmExit", it) },
                )
            }
            item {
                SettingsSwitchCard(
                    icon = Icons.Filled.Code,
                    title = "Developer options",
                    subtitle = "Show advanced settings",
                    checked = state.enableDeveloperOptions,
                    onCheckedChange = { viewModel.updateGeneral("enableDeveloperOptions", it) },
                )
            }
            item {
                SettingsButtonCard(
                    title = "Reset to defaults",
                    onClick = { viewModel.resetToDefaults() },
                )
            }
        }
    }
}

private fun subtitleLanguageLabel(code: String): String =
    when (code) {
        "auto" -> "Auto"
        "en" -> "English"
        "de" -> "German"
        "fr" -> "French"
        "es" -> "Spanish"
        "it" -> "Italian"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        "pt" -> "Portuguese"
        "ru" -> "Russian"
        else -> code
    }

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(value) }
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelected(value) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

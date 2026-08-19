package com.tsutsen.platformplayer.feature.settings.impl

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.datastore.model.ContrastLevel
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.component.SettingsButtonCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsOptionCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsSwitchCard
import com.tsutsen.platformplayer.core.navigation.Navigator

/**
 * Settings master page: one row per section (Appearance, Gestures, Content,
 * Playback, General). Tapping a row opens the section detail page
 * ([SettingsSectionScreen]) via NavDestination.SettingsFragment(category).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navigator: Navigator,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

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
                    modifier = Modifier.align(Alignment.Center).padding(Tokens.SpaceLg),
                )
            }
        }

        is SettingsUiState.Loaded -> {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(title = { Text("Settings", style = MaterialTheme.typography.titleLarge) })
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Tokens.SpaceLg, end = Tokens.SpaceLg, bottom = Tokens.SpaceLg),
                    verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
                ) {
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.BrightnessAuto,
                            title = "Appearance",
                            subtitle = "Theme, dynamic color, contrast",
                            onClick = { navigator.navigateToSettingsFragment("appearance") },
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.Swipe,
                            title = "Gestures",
                            subtitle = "Screen swipe actions",
                            onClick = { navigator.navigateToSettingsFragment("gestures") },
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.Extension,
                            title = "Content",
                            subtitle = "Plugins, video page sections",
                            onClick = { navigator.navigateToSettingsFragment("content") },
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.PlayArrow,
                            title = "Playback",
                            subtitle = "Quality, subtitles, background playback",
                            onClick = { navigator.navigateToSettingsFragment("playback") },
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.Settings,
                            title = "General",
                            subtitle = "App behavior, reset to defaults",
                            onClick = { navigator.navigateToSettingsFragment("general") },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detail page for one settings section, reached from the master page.
 * [category] is the SettingsFragment destination argument.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSectionScreen(
    category: String,
    onBack: () -> Unit,
    onPluginsClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedChoice by remember { mutableStateOf<Choice?>(null) }
    val state = uiState as? SettingsUiState.Loaded

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = { Text(sectionTitle(category), style = MaterialTheme.typography.titleLarge) },
            leading = { BackIconButton(onBack) },
        )
        val loaded = state
        if (loaded != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Tokens.SpaceLg, end = Tokens.SpaceLg, bottom = Tokens.SpaceLg),
                verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
            ) {
                SectionItems(
                    category = category,
                    state = loaded,
                    viewModel = viewModel,
                    onChoiceSelected = { selectedChoice = it },
                    onPluginsClick = onPluginsClick,
                )
            }
        }
    }

    val loaded = state
    when (selectedChoice) {
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

@Composable
private fun BackIconButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
        )
    }
}

/** The cards belonging to one section. */
private fun LazyListScope.SectionItems(
    category: String,
    state: SettingsUiState.Loaded,
    viewModel: SettingsViewModel,
    onChoiceSelected: (Choice) -> Unit,
    onPluginsClick: () -> Unit,
) {
    when (category) {
        "appearance" -> {
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
        }

        "gestures" -> {
            // Placeholder slots — actions to be defined.
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
        }

        "content" -> {
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
        }

        "playback" -> {
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.HighQuality,
                    title = "Default resolution",
                    subtitle =
                        if (state.defaultResolution == "auto") {
                            "Auto"
                        } else {
                            "${state.defaultResolution}p"
                        },
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
        }

        "general" -> {
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
            if (state.enableDeveloperOptions) {
                item {
                    SettingsSwitchCard(
                        icon = Icons.Filled.DisplaySettings,
                        title = "Dual screen",
                        subtitle = "Show video controls on the second screen",
                        checked = state.dualScreen,
                        onCheckedChange = { viewModel.updateGeneral("dualScreen", it) },
                    )
                }
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

private fun sectionTitle(category: String): String =
    when (category) {
        "appearance" -> "Appearance"
        "gestures" -> "Gestures"
        "content" -> "Content"
        "playback" -> "Playback"
        "general" -> "General"
        else -> "Settings"
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

private enum class Choice {
    THEME,
    CONTRAST,
    GRID_COLUMNS,
    DEFAULT_RESOLUTION,
    SUBTITLE_LANGUAGE,
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
                                .padding(vertical = Tokens.SpaceXs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelected(value) },
                        )
                        Spacer(Modifier.width(Tokens.SpaceSm))
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

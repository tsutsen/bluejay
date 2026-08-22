package com.tsutsen.platformplayer.feature.settings.impl

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.model.PlaylistOption
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
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
                            subtitle = "Subtitles, default speed",
                            onClick = { navigator.navigateToSettingsFragment("playback") },
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.DisplaySettings,
                            title = "Dual screen",
                            subtitle = "Second display pages, tabs, sections",
                            onClick = { navigator.navigateToSettingsFragment("dual") },
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
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
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
                    playlists = playlists,
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

        Choice.SUBTITLE_FONT -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Subtitle font",
                    options =
                        listOf(
                            "Default" to "default",
                            "Sans-serif" to "sans",
                            "Serif" to "serif",
                            "Monospace" to "mono",
                        ),
                    selected = it.subtitle.font,
                    onSelected = { value ->
                        viewModel.updateGeneral("subtitleFont", value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.SUBTITLE_SIZE -> {
            loaded?.let {
                StepperDialog(
                    title = "Subtitle size",
                    value = it.subtitle.size,
                    step = 1,
                    min = 8,
                    max = 32,
                    suffix = " pt",
                    onSelected = { value ->
                        viewModel.updateGeneral("subtitleFontSize", value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.SUBTITLE_PADDING -> {
            loaded?.let {
                StepperDialog(
                    title = "Subtitle bottom padding",
                    value = it.subtitle.bottomPadding,
                    step = 4,
                    min = 0,
                    max = 80,
                    suffix = " dp",
                    onSelected = { value ->
                        viewModel.updateGeneral("subtitleBottomPadding", value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.DUAL_PAGES -> {
            loaded?.let {
                MultiSelectDialog(
                    title = "Pages",
                    options = dualPageNames.map { (key, label) -> label to key },
                    selected = it.dualScreenPages,
                    onToggle = { key, checked ->
                        viewModel.setDualScreenPages(
                            if (checked) it.dualScreenPages + key else it.dualScreenPages - key,
                        )
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.DUAL_TABS -> {
            loaded?.let {
                MultiSelectDialog(
                    title = "Video page tabs",
                    options = dualTabNames.map { (key, label) -> label to key },
                    selected = it.dualScreenVideoTabs,
                    onToggle = { key, checked ->
                        viewModel.setDualScreenVideoTabs(
                            if (checked) it.dualScreenVideoTabs + key else it.dualScreenVideoTabs - key,
                        )
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.DUAL_SLOTS -> {
            loaded?.let {
                SlotsDialog(
                    slots = it.dualScreenLibrarySlots,
                    sectionNames = librarySectionNames,
                    playlists = playlists,
                    onSetSlot = { index, value ->
                        val current = it.dualScreenLibrarySlots
                        val newList = (0 until 4).map { i -> current.getOrNull(i) ?: "watch_later" }.toMutableList()
                        newList[index] = value
                        viewModel.setDualScreenLibrarySlots(newList)
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.LIBRARY_SECTION_ORDER -> {
            loaded?.let {
                ReorderDialog(
                    title = "Library sections",
                    items = it.librarySectionOrder.map { id -> id to (librarySectionNames[id] ?: id) },
                    onChange = { newOrder -> viewModel.setLibrarySectionOrder(newOrder) },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.PLAYBACK_SPEED -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Default playback speedup",
                    options =
                        listOf(
                            "1.5x" to "1.5",
                            "2x" to "2.0",
                            "3x" to "3.0",
                            "4x" to "4.0",
                        ),
                    selected = it.defaultSpeedup.toString(),
                    onSelected = { value ->
                        viewModel.updateGeneral("defaultSpeedup", value.toFloat())
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }
        Choice.SPEEDUP_SENSITIVITY -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Speedup gesture sensitivity",
                    options =
                        listOf(
                            "Low" to "0.5",
                            "Normal" to "1.0",
                            "High" to "1.5",
                            "Very high" to "2.0",
                        ),
                    selected = it.speedupSensitivity.toString(),
                    onSelected = { value ->
                        viewModel.updateGeneral("speedupSensitivity", value.toFloat())
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }
        Choice.VIDEO_RESOLUTION -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Default video resolution",
                    options =
                        listOf(
                            "Auto" to "Auto",
                            "144p" to "144p",
                            "360p" to "360p",
                            "480p" to "480p",
                            "720p" to "720p",
                            "1080p" to "1080p",
                        ),
                    selected = it.defaultVideoResolution,
                    onSelected = { value ->
                        viewModel.updateGeneral("defaultVideoResolution", value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }
        Choice.DOWNLOAD_RESOLUTION -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Default download resolution",
                    options =
                        listOf(
                            "1080p" to "1080p",
                            "720p" to "720p",
                            "480p" to "480p",
                            "360p" to "360p",
                            "144p" to "144p",
                        ),
                    selected = it.defaultDownloadResolution,
                    onSelected = { value ->
                        viewModel.updateGeneral("defaultDownloadResolution", value)
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
    playlists: List<PlaylistOption>,
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
                    icon = Icons.Filled.GridOn,
                    title = "Grid columns",
                    subtitle = "${state.gridColumns} columns",
                    onClick = { onChoiceSelected(Choice.GRID_COLUMNS) },
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
                    icon = Icons.Filled.VideoLibrary,
                    title = "Library sections",
                    subtitle = "Order of sections on the library screen",
                    onClick = { onChoiceSelected(Choice.LIBRARY_SECTION_ORDER) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.Hd,
                    title = "Default video resolution",
                    subtitle = state.defaultVideoResolution,
                    onClick = { onChoiceSelected(Choice.VIDEO_RESOLUTION) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.Download,
                    title = "Default download resolution",
                    subtitle = state.defaultDownloadResolution,
                    onClick = { onChoiceSelected(Choice.DOWNLOAD_RESOLUTION) },
                )
            }
        }

        "playback" -> {
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.Speed,
                    title = "Default playback speedup",
                    subtitle = "${state.defaultSpeedup}x",
                    onClick = { onChoiceSelected(Choice.PLAYBACK_SPEED) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.Speed,
                    title = "Speedup gesture sensitivity",
                    subtitle = "${state.speedupSensitivity}x",
                    onClick = { onChoiceSelected(Choice.SPEEDUP_SENSITIVITY) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.Subtitles,
                    title = "Subtitle font",
                    subtitle = subtitleFontLabel(state.subtitle.font),
                    onClick = { onChoiceSelected(Choice.SUBTITLE_FONT) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.TextFields,
                    title = "Subtitle size",
                    subtitle = "${state.subtitle.size} pt",
                    onClick = { onChoiceSelected(Choice.SUBTITLE_SIZE) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.VerticalAlignBottom,
                    title = "Subtitle bottom padding",
                    subtitle = "${state.subtitle.bottomPadding} dp",
                    onClick = { onChoiceSelected(Choice.SUBTITLE_PADDING) },
                )
            }
        }

        "dual" -> {
            item {
                SettingsSwitchCard(
                    icon = Icons.Filled.DisplaySettings,
                    title = "Dual screen",
                    subtitle = "Show video controls on the second screen",
                    checked = state.dualScreen,
                    onCheckedChange = { viewModel.updateGeneral("dualScreen", it) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.PlayArrow,
                    title = "Pages",
                    subtitle = dualListLabel(state.dualScreenPages, dualPageNames),
                    onClick = { onChoiceSelected(Choice.DUAL_PAGES) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.Chat,
                    title = "Video page tabs",
                    subtitle = dualListLabel(state.dualScreenVideoTabs, dualTabNames),
                    onClick = { onChoiceSelected(Choice.DUAL_TABS) },
                )
            }
            item {
                SettingsOptionCard(
                    icon = Icons.Filled.VideoLibrary,
                    title = "Library slots",
                    subtitle = slotListLabel(state.dualScreenLibrarySlots, playlists),
                    onClick = { onChoiceSelected(Choice.DUAL_SLOTS) },
                )
            }
        }
    }
}

private val dualPageNames =
    mapOf(
        "video" to "Video page",
        "library" to "Library page",
        "home" to "Home page",
    )

private val dualTabNames =
    mapOf(
        "comments" to "Comments",
        "chapters" to "Chapters",
        "recommended" to "Recommended",
        "queue" to "Queue",
    )

private val librarySectionNames =
    mapOf(
        "watch_later" to "Watch Later",
        "liked" to "Liked",
        "disliked" to "Disliked",
        "favourite" to "Favourites",
        "history" to "History",
        "downloads" to "Downloads",
        "playlists" to "Playlists",
    )

/** Display name for a second-screen slot (section id or "playlist:<id>"). */
private fun slotLabel(
    value: String,
    sectionNames: Map<String, String>,
    playlists: List<PlaylistOption>,
): String {
    if (value.startsWith("playlist:")) {
        val id = value.substringAfter(":").toLongOrNull()
        return playlists.firstOrNull { it.id == id }?.name ?: "Playlist"
    }
    return sectionNames[value] ?: "Empty"
}

/** Short subtitle for the "Library slots" settings card. */
private fun slotListLabel(
    slots: List<String>,
    playlists: List<PlaylistOption>,
): String =
    (0 until 4).joinToString(", ") {
        slotLabel(slots.getOrNull(it) ?: "", librarySectionNames, playlists)
    }

private fun dualListLabel(keys: List<String>, names: Map<String, String>): String {
    val all = names.keys.filter { it in keys }
    return when {
        all.isEmpty() -> "None"
        all.size == names.keys.size -> "All"
        else -> all.joinToString(", ") { names[it] ?: it }
    }
}

private fun sectionTitle(category: String): String =
    when (category) {
        "appearance" -> "Appearance"
        "content" -> "Content"
        "playback" -> "Playback"
        "dual" -> "Dual screen"
        else -> "Settings"
    }

private fun subtitleFontLabel(code: String): String =
    when (code) {
        "sans" -> "Sans-serif"
        "serif" -> "Serif"
        "mono" -> "Monospace"
        else -> "Default"
    }

/** Dialog for choosing a numeric value (subtitle size in pt, padding in dp). */
@Composable
private fun StepperDialog(
    title: String,
    value: Int,
    step: Int,
    min: Int,
    max: Int,
    suffix: String,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { draft = (draft - step).coerceAtLeast(min) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
                Text("$draft$suffix", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { draft = (draft + step).coerceAtMost(max) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelected(draft) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private enum class Choice {
    THEME,
    GRID_COLUMNS,
    SUBTITLE_FONT,
    SUBTITLE_SIZE,
    SUBTITLE_PADDING,
    DUAL_PAGES,
    DUAL_TABS,
    DUAL_SLOTS,
    LIBRARY_SECTION_ORDER,
    PLAYBACK_SPEED,
    SPEEDUP_SENSITIVITY,
    VIDEO_RESOLUTION,
    DOWNLOAD_RESOLUTION,
}

@Composable
private fun MultiSelectDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: List<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (label, value) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(value, value !in selected) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = value in selected,
                            onCheckedChange = { onToggle(value, it) },
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
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
            Column(Modifier.verticalScroll(rememberScrollState())) {
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

/**
 * Reorder a list of items (up/down per row). Live-updates on each move.
 * [items] is (id, label) in the current order; [onChange] receives the new
 * ordered ids.
 */
@Composable
private fun ReorderDialog(
    title: String,
    items: List<Pair<String, String>>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(items) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                draft.forEachIndexed { index, (id, label) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val d = draft.toMutableList()
                                    val t = d[index]
                                    d[index] = d[index - 1]
                                    d[index - 1] = t
                                    draft = d
                                    onChange(d.map { it.first })
                                }
                            },
                            enabled = index > 0,
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Icon(
                                Icons.Filled.ExpandLess,
                                contentDescription = "Move up",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                if (index < draft.size - 1) {
                                    val d = draft.toMutableList()
                                    val t = d[index]
                                    d[index] = d[index + 1]
                                    d[index + 1] = t
                                    draft = d
                                    onChange(d.map { it.first })
                                }
                            },
                            enabled = index < draft.size - 1,
                            modifier =
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Icon(
                                Icons.Filled.ExpandMore,
                                contentDescription = "Move down",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * Configure the four 2x2 second-screen library slots. Tapping a slot opens a
 * picker of all sections + all playlists; picking one assigns it to that slot.
 */
@Composable
private fun SlotsDialog(
    slots: List<String>,
    sectionNames: Map<String, String>,
    playlists: List<PlaylistOption>,
    onSetSlot: (Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Library slots" else "Slot ${editing!! + 1}") },
        text = {
            if (editing == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until 4).forEach { index ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Tokens.RadiusMd))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { editing = index }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Slot ${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(Tokens.SpaceSm))
                            Text(
                                text = slotLabel(slots.getOrNull(index) ?: "", sectionNames, playlists),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                        }
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    sectionNames.forEach { (id, name) ->
                        SlotPickRow(
                            name = name,
                            selected = slots.getOrNull(editing!!) == id,
                            onClick = { onSetSlot(editing!!, id); editing = null },
                        )
                    }
                    if (playlists.isNotEmpty()) {
                        Spacer(Modifier.height(Tokens.SpaceSm))
                        Text(
                            text = "Playlists",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    playlists.forEach { p ->
                        val value = "playlist:${p.id}"
                        SlotPickRow(
                            name = p.name,
                            selected = slots.getOrNull(editing!!) == value,
                            onClick = { onSetSlot(editing!!, value); editing = null },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SlotPickRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = Tokens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(Tokens.SpaceSm))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

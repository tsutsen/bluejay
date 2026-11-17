package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import com.tsutsen.platformplayer.core.designsystem.icon.DualScreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.datastore.model.ControllerBinding
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.SettingsOptionCard
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.designsystem.layout.bluejayNavItems
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.PlayerControllerActions
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
                    // 4.dp gap: every item here is a GroupPosition
                    // member, so the spacing reads as one stacked group.
                    verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
                ) {
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.BrightnessAuto,
                            title = "Appearance",
                            subtitle = "Theme, dynamic color, contrast",
                            onClick = { navigator.navigateToSettingsFragment("appearance") },
                            groupPosition = GroupPosition.First,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.VideoLibrary,
                            title = "Content",
                            subtitle = "Plugins, video page sections",
                            onClick = { navigator.navigateToSettingsFragment("content") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.PlayArrow,
                            title = "Playback",
                            subtitle = "Subtitles, quality",
                            onClick = { navigator.navigateToSettingsFragment("playback") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.Gesture,
                            title = "Gestures",
                            subtitle = "Speed, per-slot gesture actions",
                            onClick = { navigator.navigateToSettingsFragment("gestures") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.Games,
                            title = "Controller",
                            subtitle = "Gamepad / remote button mapping",
                            onClick = { navigator.navigateToSettingsFragment("controller") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = DualScreen,
                            title = "Dual screen",
                            subtitle = "Second display pages, tabs, sections",
                            onClick = { navigator.navigateToSettingsFragment("dual") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.Backup,
                            title = "Backup & restore",
                            subtitle = "Export or restore the full app state",
                            onClick = { navigator.navigateToSettingsFragment("backup") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.Info,
                            title = "About",
                            subtitle = "Version, license, support the original project",
                            onClick = { navigator.navigateToSettingsFragment("about") },
                            groupPosition = GroupPosition.Last,
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
    onHiddenContentClick: () -> Unit = {},
    onNavigateTab: (String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val enabledSources by viewModel.enabledSources.collectAsState(initial = emptyList())
    var selectedChoice by remember { mutableStateOf<Choice?>(null) }
    var bindingAction by remember { mutableStateOf<String?>(null) }
    val state = uiState as? SettingsUiState.Loaded

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(
            title = { Text(sectionTitle(category), style = MaterialTheme.typography.titleLarge) },
            leading = { BackIconButton(onBack) },
        )
        val loaded = state
        if (loaded != null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = Tokens.SpaceLg,
                            end = Tokens.SpaceLg,
                            bottom = Tokens.SpaceLg,
                        ),
            ) {
                SectionItems(
                    category = category,
                    state = loaded,
                    viewModel = viewModel,
                    playlists = playlists,
                    enabledSources = enabledSources,
                    onChoiceSelected = { selectedChoice = it },
                    onBindRequested = { bindingAction = it },
                    onPluginsClick = onPluginsClick,
                    onHiddenContentClick = onHiddenContentClick,
                    onNavigateTab = onNavigateTab,
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
                    title = "Mode",
                    options =
                        listOf(
                            "Follow system" to "AUTO",
                            "Light" to "LIGHT",
                            "Dark" to "DARK",
                        ),
                    selected = it.prefs.appearance.themeMode.name,
                    onSelected = { value ->
                        viewModel.updateAppearance(
                            it.prefs.appearance.copy(themeMode = ThemeMode.valueOf(value)),
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
                    selected = it.prefs.gridColumns.toString(),
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
                    selected = it.prefs.subtitle.font,
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
                    value = it.prefs.subtitle.size,
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
                    value = it.prefs.subtitle.bottomPadding,
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

        Choice.SUBTITLE_OUTLINE -> {
            loaded?.let {
                StepperDialog(
                    title = "Subtitle outline",
                    value = it.prefs.subtitle.outline,
                    step = 1,
                    min = 0,
                    max = 6,
                    suffix = " px",
                    onSelected = { value ->
                        viewModel.updateGeneral("subtitleOutline", value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.DUAL_PAGES -> {
            loaded?.let {
                ReorderDialog(
                    title = "Pages",
                    items =
                        (it.prefs.dualScreenPages + (dualPageNames.keys - it.prefs.dualScreenPages))
                            .map { id -> id to (dualPageNames[id] ?: id) },
                    // The saved list is membership + order; the dialog shows all
                    // known pages, so filter the new order back to the enabled set.
                    onChange = { newOrder ->
                        viewModel.setDualScreenPages(
                            newOrder.filter { id -> id in it.prefs.dualScreenPages },
                        )
                    },
                    onDismiss = { selectedChoice = null },
                    enabledIds = it.prefs.dualScreenPages,
                    onToggleEnabled = { key, checked ->
                        viewModel.setDualScreenPages(
                            if (checked) it.prefs.dualScreenPages + key else it.prefs.dualScreenPages - key,
                        )
                    },
                )
            }
        }

        Choice.DUAL_TAB_ORDER -> {
            loaded?.let {
                ReorderDialog(
                    title = "Video tabs",
                    items =
                        it.prefs.dualScreenVideoTabOrder
                            .map { id -> id to (dualTabNames[id] ?: id) },
                    onChange = { newOrder -> viewModel.setDualScreenVideoTabOrder(newOrder) },
                    onDismiss = { selectedChoice = null },
                    // One popup for both order and visibility: the checkbox
                    // toggles the tab's enabled state.
                    enabledIds = it.prefs.dualScreenVideoTabs,
                    onToggleEnabled = { key, checked ->
                        viewModel.setDualScreenVideoTabs(
                            if (checked) it.prefs.dualScreenVideoTabs + key else it.prefs.dualScreenVideoTabs - key,
                        )
                    },
                )
            }
        }

        Choice.DUAL_PAGE_ORDER -> {
            loaded?.let {
                ReorderDialog(
                    title = "Video page order",
                    items =
                        it.prefs.dualScreenPageOrder
                            .map { id -> id to (dualPageOrderNames[id] ?: id) },
                    onChange = { newOrder -> viewModel.setDualScreenPageOrder(newOrder) },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.DASH_TOP_CREATORS -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Top creators",
                    options =
                        listOf(
                            "This week" to "week",
                            "Overall" to "overall",
                        ),
                    selected = it.prefs.dualScreenTopCreatorsScope,
                    onSelected = { value ->
                        viewModel.setDualScreenTopCreatorsScope(value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.DASH_PAGE_ORDER -> {
            loaded?.let {
                ReorderDialog(
                    title = "Dash page order",
                    items =
                        it.prefs.dualScreenDashPageOrder
                            .map { id -> id to (dashPageOrderNames[id] ?: id) },
                    onChange = { newOrder -> viewModel.setDualScreenDashPageOrder(newOrder) },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.DUAL_SLOTS -> {
            loaded?.let {
                SlotsDialog(
                    slots = it.prefs.dualScreenLibrarySlots,
                    sectionNames = librarySectionNames,
                    playlists = playlists,
                    onSetSlot = { index, value ->
                        val current = it.prefs.dualScreenLibrarySlots
                        val newList = (0 until 4).map { i -> current.getOrNull(i) ?: "watch_later" }.toMutableList()
                        newList[index] = value
                        viewModel.setDualScreenLibrarySlots(newList)
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.DUAL_FEED_SOURCES -> {
            loaded?.let {
                MultiSelectDialog(
                    title = "Feed sources",
                    options = enabledSources.map { it.name to it.id },
                    selected = it.prefs.dualScreenFeedSources,
                    onToggle = { id, checked ->
                        viewModel.setDualScreenFeedSources(
                            if (checked) it.prefs.dualScreenFeedSources + id else it.prefs.dualScreenFeedSources - id,
                        )
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.LIBRARY_SECTION_ORDER -> {
            loaded?.let {
                ReorderDialog(
                    title = "Library sections",
                    items = it.prefs.librarySectionOrder.map { id -> id to (librarySectionNames[id] ?: id) },
                    onChange = { newOrder -> viewModel.setLibrarySectionOrder(newOrder) },
                    onDismiss = { selectedChoice = null },
                    enabledIds = it.prefs.librarySectionsEnabled,
                    onToggleEnabled = { key, checked ->
                        viewModel.setLibrarySectionsEnabled(
                            if (checked) it.prefs.librarySectionsEnabled + key else it.prefs.librarySectionsEnabled - key,
                        )
                    },
                )
            }
        }

        Choice.DOCK_TAB_ORDER -> {
            loaded?.let {
                val names = bluejayNavItems.associate { item -> item.key to item.label }
                ReorderDialog(
                    title = "Tab order",
                    // The saved list is the order; show all known tabs, missing keys appended.
                    items =
                        (it.prefs.dockTabOrder + (names.keys - it.prefs.dockTabOrder))
                            .map { id -> id to (names[id] ?: id) },
                    onChange = { newOrder -> viewModel.setDockTabOrder(newOrder) },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.DEFAULT_DOCK_TAB -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Default tab",
                    options = bluejayNavItems.map { it.label to it.key },
                    selected = it.prefs.defaultDockTab,
                    onSelected = { value ->
                        viewModel.setDefaultDockTab(value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.SUBSCRIPTIONS_ORDER -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Subscriptions list order",
                    options =
                        listOf(
                            "Watch time" to "watchtime",
                            "Alphabetical" to "alphabetical",
                            "Random" to "random",
                        ),
                    selected = it.prefs.subscriptionsOrder,
                    onSelected = { value ->
                        viewModel.setSubscriptionsOrder(value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.RECENCY -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Recency",                    options =
                        listOf(
                            "1 week" to "1w",
                            "1 month" to "1m",
                            "3 months" to "3m",
                            "6 months" to "6m",
                            "1 year" to "1y",
                            "3 years" to "3y",
                            "Any time" to "any",
                        ),
                    selected = it.prefs.recency,
                    onSelected = { value ->
                        viewModel.setRecency(value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.FONT -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Font",
                    options =
                        listOf(
                            "System" to "DEFAULT",
                            "Inter" to "INTER",
                            "Roboto" to "ROBOTO",
                            "Open Sans" to "OPEN_SANS",
                        ),
                    selected = it.prefs.appearance.fontFamily.name,
                    onSelected = { value ->
                        viewModel.setFontFamily(
                            com.tsutsen.platformplayer.core.datastore.model.FontFamily.valueOf(value),
                        )
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.THUMBNAIL_TINT -> {
            loaded?.let {
                ThumbnailTintDialog(
                    current = it.prefs.appearance.thumbnailTintArgb,
                    onPick = { argb ->
                        viewModel.setThumbnailTint(argb)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.THUMBNAIL_TINT_BLEND -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Tint blend mode",
                    options =
                        listOf(
                            "Wash" to com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.WASH.name,
                            "Multiply" to com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.MULTIPLY.name,
                            "Screen" to com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.SCREEN.name,
                            "Overlay" to com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.OVERLAY.name,
                            "Soft light" to com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.SOFT_LIGHT.name,
                            "Color burn" to com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.COLOR_BURN.name,
                        ),
                    selected = it.prefs.appearance.thumbnailTintBlendMode,
                    onSelected = { value ->
                        viewModel.setThumbnailTintBlendMode(value)
                        selectedChoice = null
                    },
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
                    selected = it.prefs.defaultSpeedup.toString(),
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
                    title = "Speedup gesture speed",
                    options =
                        listOf(
                            "Low" to "0.5",
                            "Normal" to "1.0",
                            "High" to "1.5",
                            "Very high" to "2.0",
                        ),
                    selected = it.prefs.speedupSensitivity.toString(),
                    onSelected = { value ->
                        viewModel.updateGeneral("speedupSensitivity", value.toFloat())
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.JUMP_STEP -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Time jump step",
                    options =
                        listOf(
                            "1s" to "1",
                            "3s" to "3",
                            "5s" to "5",
                            "10s" to "10",
                            "30s" to "30",
                        ),
                    selected = it.prefs.jumpStepSeconds.toString(),
                    onSelected = { value ->
                        viewModel.updateGeneral("jumpStepSeconds", value.toInt())
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
                    selected = it.prefs.defaultVideoResolution,
                    onSelected = { value ->
                        viewModel.updateGeneral("defaultVideoResolution", value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.CONTROLLER_SEEK_BACK -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Backwards jump step",
                    options =
                        listOf(
                            "5s" to "5",
                            "10s" to "10",
                            "15s" to "15",
                            "20s" to "20",
                            "30s" to "30",
                            "45s" to "45",
                            "60s" to "60",
                        ),
                    selected = it.prefs.controller.seekBackSeconds.toString(),
                    onSelected = { value ->
                        viewModel.setController(it.prefs.controller.copy(seekBackSeconds = value.toInt()))
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }

        Choice.CONTROLLER_SEEK_FORWARD -> {
            loaded?.let {
                ChoiceDialog(
                    title = "Forwards jump step",
                    options =
                        listOf(
                            "5s" to "5",
                            "10s" to "10",
                            "15s" to "15",
                            "20s" to "20",
                            "30s" to "30",
                            "45s" to "45",
                            "60s" to "60",
                        ),
                    selected = it.prefs.controller.seekForwardSeconds.toString(),
                    onSelected = { value ->
                        viewModel.setController(it.prefs.controller.copy(seekForwardSeconds = value.toInt()))
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
                    selected = it.prefs.defaultDownloadResolution,
                    onSelected = { value ->
                        viewModel.updateGeneral("defaultDownloadResolution", value)
                        selectedChoice = null
                    },
                    onDismiss = { selectedChoice = null },
                )
            }
        }
    }

    // Controller button binding capture (Settings > Controller)
    val binding = bindingAction?.let { id -> PlayerControllerActions.ALL.firstOrNull { it.id == id } }
    if (category == "controller" && binding != null) {
        ControllerBindingPopup(
            action = binding,
            onBound = { keyCode, deviceName ->
                (uiState as? SettingsUiState.Loaded)?.let { current ->
                    viewModel.setController(
                        current.prefs.controller.copy(
                            mappings =
                                current.prefs.controller.mappings +
                                    (binding.id to ControllerBinding(keyCode = keyCode, deviceName = deviceName)),
                        ),
                    )
                }
                bindingAction = null
            },
            onClear = {
                (uiState as? SettingsUiState.Loaded)?.let { current ->
                    viewModel.setController(
                        current.prefs.controller.copy(mappings = current.prefs.controller.mappings - binding.id),
                    )
                }
                bindingAction = null
            },
            onDismiss = { bindingAction = null },
        )
    }
}

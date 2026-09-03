package com.tsutsen.platformplayer.feature.settings.impl

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tab
import com.tsutsen.platformplayer.core.designsystem.icon.DualScreen
import com.tsutsen.platformplayer.core.designsystem.icon.SplitscreenBottom
import com.tsutsen.platformplayer.core.designsystem.icon.VideoTemplate
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ControllerBinding
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.ReorderableList
import com.tsutsen.platformplayer.core.designsystem.component.BluejayModalBottomSheet
import com.tsutsen.platformplayer.core.designsystem.component.SettingsOptionCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsSliderCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsSwitchCard
import com.tsutsen.platformplayer.core.designsystem.component.groupShape
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.PlayerControllerActions
import com.tsutsen.platformplayer.core.model.PlayerGestures
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.SourceInfo
import com.tsutsen.platformplayer.core.navigation.Navigator
import com.tsutsen.platformplayer.core.ui.GamepadKeyBus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun writeSettingsGranted(context: android.content.Context): Boolean =
    runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

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

        Choice.SUBTITLE_OUTLINE -> {
            loaded?.let {
                StepperDialog(
                    title = "Subtitle outline",
                    value = it.subtitle.outline,
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
                        (it.dualScreenPages + (dualPageNames.keys - it.dualScreenPages))
                            .map { id -> id to (dualPageNames[id] ?: id) },
                    // The saved list is membership + order; the dialog shows all
                    // known pages, so filter the new order back to the enabled set.
                    onChange = { newOrder ->
                        viewModel.setDualScreenPages(
                            newOrder.filter { id -> id in it.dualScreenPages },
                        )
                    },
                    onDismiss = { selectedChoice = null },
                    enabledIds = it.dualScreenPages,
                    onToggleEnabled = { key, checked ->
                        viewModel.setDualScreenPages(
                            if (checked) it.dualScreenPages + key else it.dualScreenPages - key,
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
                        it.dualScreenVideoTabOrder
                            .map { id -> id to (dualTabNames[id] ?: id) },
                    onChange = { newOrder -> viewModel.setDualScreenVideoTabOrder(newOrder) },
                    onDismiss = { selectedChoice = null },
                    // One popup for both order and visibility: the checkbox
                    // toggles the tab's enabled state.
                    enabledIds = it.dualScreenVideoTabs,
                    onToggleEnabled = { key, checked ->
                        viewModel.setDualScreenVideoTabs(
                            if (checked) it.dualScreenVideoTabs + key else it.dualScreenVideoTabs - key,
                        )
                    },
                )
            }
        }

        Choice.DUAL_PAGE_ORDER -> {
            loaded?.let {
                ReorderDialog(
                    title = "Main page order",
                    items =
                        it.dualScreenPageOrder
                            .map { id -> id to (dualPageOrderNames[id] ?: id) },
                    onChange = { newOrder -> viewModel.setDualScreenPageOrder(newOrder) },
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

        Choice.DUAL_FEED_SOURCES -> {
            loaded?.let {
                MultiSelectDialog(
                    title = "Feed sources",
                    options = enabledSources.map { it.name to it.id },
                    selected = it.dualScreenFeedSources,
                    onToggle = { id, checked ->
                        viewModel.setDualScreenFeedSources(
                            if (checked) it.dualScreenFeedSources + id else it.dualScreenFeedSources - id,
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
                    items = it.librarySectionOrder.map { id -> id to (librarySectionNames[id] ?: id) },
                    onChange = { newOrder -> viewModel.setLibrarySectionOrder(newOrder) },
                    onDismiss = { selectedChoice = null },
                    enabledIds = it.librarySectionsEnabled,
                    onToggleEnabled = { key, checked ->
                        viewModel.setLibrarySectionsEnabled(
                            if (checked) it.librarySectionsEnabled + key else it.librarySectionsEnabled - key,
                        )
                    },
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
                    title = "Speedup gesture speed",
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
                    selected = it.jumpStepSeconds.toString(),
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
                    selected = it.defaultVideoResolution,
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
                    selected = it.controller.seekBackSeconds.toString(),
                    onSelected = { value ->
                        viewModel.setController(it.controller.copy(seekBackSeconds = value.toInt()))
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
                    selected = it.controller.seekForwardSeconds.toString(),
                    onSelected = { value ->
                        viewModel.setController(it.controller.copy(seekForwardSeconds = value.toInt()))
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

    // Controller button binding capture (Settings > Controller)
    val binding = bindingAction?.let { id -> PlayerControllerActions.ALL.firstOrNull { it.id == id } }
    if (category == "controller" && binding != null) {
        ControllerBindingPopup(
            action = binding,
            onBound = { keyCode, deviceName ->
                (uiState as? SettingsUiState.Loaded)?.let { current ->
                    viewModel.setController(
                        current.controller.copy(
                            mappings =
                                current.controller.mappings +
                                    (binding.id to ControllerBinding(keyCode = keyCode, deviceName = deviceName)),
                        ),
                    )
                }
                bindingAction = null
            },
            onClear = {
                (uiState as? SettingsUiState.Loaded)?.let { current ->
                    viewModel.setController(
                        current.controller.copy(mappings = current.controller.mappings - binding.id),
                    )
                }
                bindingAction = null
            },
            onDismiss = { bindingAction = null },
        )
    }
}

/**
 * One controller action: shows the bound key (and the device it was bound
 * with). Tap to bind a new key; Clear removes the custom binding (back to
 * the default key).
 */
@Composable
private fun ControllerBindingRow(
    action: PlayerControllerActions.Action,
    binding: ControllerBinding?,
    onCapture: () -> Unit,
    onClear: () -> Unit,
    groupPosition: GroupPosition = GroupPosition.Single,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onCapture),
        shape = groupShape(groupPosition),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Tokens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(action.label, style = MaterialTheme.typography.titleSmall)
                val b = binding
                Text(
                    text =
                        if (b == null) {
                            "${PlayerControllerActions.labelFor(action.defaultKeyCode)} (default)"
                        } else {
                            buildString {
                                append(PlayerControllerActions.labelFor(b.keyCode))
                                b.deviceName?.let { append("  ·  ").append(it) }
                            }
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (binding != null) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
            Text("Tap to bind", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Binding capture popup: the first controller/remote input that arrives is
 * bound to the action.
 *
 * Cemu's design (InputBindingPopup): a non-focusable Compose [Popup], not a
 * [Dialog] — the activity window keeps input focus the whole time, so
 * controller keys and generic motion events keep flowing through
 * `Activity.dispatchKeyEvent` / `dispatchGenericMotionEvent` into
 * [GamepadKeyBus.events] (a [Dialog] steals focus and the first press is
 * lost in the window-focus transition). While [GamepadKeyBus.beginCapture]
 * is active the activity consumes every input (Cemu's `hasSubscribers`
 * rule): nothing leaks to the screen behind and BACK cannot dismiss this
 * mid-capture.
 */
@Composable
private fun ControllerBindingPopup(
    action: PlayerControllerActions.Action,
    onBound: (keyCode: Int, deviceName: String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) {
        GamepadKeyBus.beginCapture()
        try {
            GamepadKeyBus.events.collect { event ->
                // Bind on press edges only; a stray release from a button
                // held before the popup opened must not consume the slot.
                if (event.isPress) onBound(event.keyCode, event.deviceName)
            }
        } finally {
            GamepadKeyBus.endCapture()
        }
    }
    Popup(alignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier =
                    Modifier
                        .sizeIn(maxWidth = 560.dp, maxHeight = 560.dp)
                        .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Bind ${action.label}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Press a button, d-pad direction, or push a stick fully.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onClear) { Text("Clear") }
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }
                }
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
@Composable
private fun SectionItems(
    category: String,
    state: SettingsUiState.Loaded,
    viewModel: SettingsViewModel,
    playlists: List<PlaylistOption>,
    enabledSources: List<SourceInfo>,
    onChoiceSelected: (Choice) -> Unit,
    onBindRequested: (String) -> Unit,
    onPluginsClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceLg)) {
        when (category) {
            "about" -> {
                AboutItems()
            }

            "appearance" -> {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
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
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.Palette,
                        title = "Dynamic color",
                        subtitle = "Material You colors from wallpaper",
                        checked = state.appearance.dynamicColor,
                        onCheckedChange = { viewModel.updateGeneral("dynamicColor", it) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSliderCard(
                        icon = Icons.Filled.RoundedCorner,
                        title = "UI rounding",
                        subtitle = "Corner radius across the app",
                        value = state.appearance.uiRounding.toFloat(),
                        // Minimum 5: at 0 the radii (and the derived grid
                        // gaps) would go fully sharp, which the app no longer
                        // supports as a look.
                        valueRange = 5f..200f,
                        onValueChange = {
                            // ponytail: persists on every tick; fine for a small
                            // JSON settings file, batch if it ever chugs.
                            viewModel.updateGeneral("uiRounding", it.roundToInt())
                        },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.GridOn,
                        title = "Grid columns",
                        subtitle = "${state.gridColumns} columns",
                        onClick = { onChoiceSelected(Choice.GRID_COLUMNS) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader("Custom themes")
                    ThemesSection(state.appearance, viewModel)
                }
            }

            "content" -> {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader("Plugins")
                    SettingsOptionCard(
                        icon = Icons.Filled.Extension,
                        title = "Plugins",
                        subtitle = "Manage installed source plugins",
                        onClick = onPluginsClick,
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.SystemUpdateAlt,
                        title = "Auto-update plugins",
                        subtitle = "Check for and install plugin updates on launch",
                        checked = state.autoUpdatePlugins,
                        onCheckedChange = { viewModel.updateGeneral("autoUpdatePlugins", it) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Video page",
                        reset =
                            if (
                                state.showRecommendedVideos != defaults.showRecommendedVideos ||
                                state.showComments != defaults.showComments
                            ) {
                                {
                                    viewModel.updateGeneral(
                                        "showRecommendedVideos",
                                        defaults.showRecommendedVideos,
                                    )
                                    viewModel.updateGeneral("showComments", defaults.showComments)
                                }
                            } else {
                                null
                            },
                    )
                    SettingsSwitchCard(
                        icon = VideoTemplate,
                        title = "Show recommended videos",
                        subtitle = "Recommended tab on the video page",
                        checked = state.showRecommendedVideos,
                        onCheckedChange = { viewModel.updateGeneral("showRecommendedVideos", it) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.Chat,
                        title = "Show comments",
                        subtitle = "Comments tab on the video page",
                        checked = state.showComments,
                        onCheckedChange = { viewModel.updateGeneral("showComments", it) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Library",
                        reset =
                            if (
                                state.librarySectionOrder != defaults.librarySectionOrder ||
                                state.librarySectionsEnabled != defaults.librarySectionsEnabled
                            ) {
                                {
                                    viewModel.setLibrarySectionOrder(defaults.librarySectionOrder)
                                    viewModel.setLibrarySectionsEnabled(
                                        defaults.librarySectionsEnabled,
                                    )
                                }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.VideoLibrary,
                        title = "Library sections",
                        subtitle = "Order and visibility of sections on the library screen",
                        onClick = { onChoiceSelected(Choice.LIBRARY_SECTION_ORDER) },
                    )
                }
            }

            "playback" -> {
                val subtitlesDefault = defaults.subtitle
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Subtitles",
                        reset =
                            if (state.subtitle != subtitlesDefault) {
                                {
                                    viewModel.updateGeneral("subtitleFont", subtitlesDefault.font)
                                    viewModel.updateGeneral(
                                        "subtitleFontSize",
                                        subtitlesDefault.size,
                                    )
                                    viewModel.updateGeneral(
                                        "subtitleBottomPadding",
                                        subtitlesDefault.bottomPadding,
                                    )
                                    viewModel.updateGeneral(
                                        "subtitleOutline",
                                        subtitlesDefault.outline,
                                    )
                                }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Subtitles,
                        title = "Subtitle font",
                        subtitle = subtitleFontLabel(state.subtitle.font),
                        onClick = { onChoiceSelected(Choice.SUBTITLE_FONT) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.TextFields,
                        title = "Subtitle size",
                        subtitle = "${state.subtitle.size} pt",
                        onClick = { onChoiceSelected(Choice.SUBTITLE_SIZE) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.VerticalAlignBottom,
                        title = "Subtitle bottom padding",
                        subtitle = "${state.subtitle.bottomPadding} dp",
                        onClick = { onChoiceSelected(Choice.SUBTITLE_PADDING) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.BorderStyle,
                        title = "Subtitle outline",
                        subtitle = "${state.subtitle.outline} px",
                        onClick = { onChoiceSelected(Choice.SUBTITLE_OUTLINE) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Quality",
                        reset =
                            if (
                                state.defaultVideoResolution != defaults.defaultVideoResolution ||
                                state.defaultDownloadResolution != defaults.defaultDownloadResolution
                            ) {
                                {
                                    viewModel.updateGeneral(
                                        "defaultVideoResolution",
                                        defaults.defaultVideoResolution,
                                    )
                                    viewModel.updateGeneral(
                                        "defaultDownloadResolution",
                                        defaults.defaultDownloadResolution,
                                    )
                                }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Hd,
                        title = "Default video resolution",
                        subtitle = state.defaultVideoResolution,
                        onClick = { onChoiceSelected(Choice.VIDEO_RESOLUTION) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Download,
                        title = "Default download resolution",
                        subtitle = state.defaultDownloadResolution,
                        onClick = { onChoiceSelected(Choice.DOWNLOAD_RESOLUTION) },
                        groupPosition = GroupPosition.Last,
                    )
                }
            }

            "gestures" -> {
                var gestureSheetMode by remember { mutableStateOf<String?>(null) }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsOptionCard(
                        icon = Icons.Filled.Speed,
                        title = "Default playback speedup",
                        subtitle = "${state.defaultSpeedup}x",
                        onClick = { onChoiceSelected(Choice.PLAYBACK_SPEED) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Speed,
                        title = "Speedup gesture speed",
                        subtitle = "${state.speedupSensitivity}x",
                        onClick = { onChoiceSelected(Choice.SPEEDUP_SENSITIVITY) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Timer,
                        title = "Time jump step",
                        subtitle = "${state.jumpStepSeconds}s",
                        onClick = { onChoiceSelected(Choice.JUMP_STEP) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsOptionCard(
                        icon = Icons.Filled.Gesture,
                        title = "Fullscreen player gestures",
                        subtitle =
                            if (state.playerGestures.fullscreen.isCustomized) {
                                "Customized"
                            } else {
                                "Defaults"
                            },
                        onClick = { gestureSheetMode = PlayerGestures.MODE_FULLSCREEN },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Gesture,
                        title = "Normal player gestures",
                        subtitle =
                            if (state.playerGestures.normal.isCustomized) {
                                "Customized"
                            } else {
                                "Defaults"
                            },
                        onClick = { gestureSheetMode = PlayerGestures.MODE_NORMAL },
                        groupPosition = GroupPosition.Last,
                    )
                }

                gestureSheetMode?.let { mode ->
                    BluejayModalBottomSheet(
                        onDismiss = { gestureSheetMode = null },
                        title =
                            if (mode == PlayerGestures.MODE_FULLSCREEN) {
                                "Fullscreen player gestures"
                            } else {
                                "Normal player gestures"
                            },
                    ) {
                        // The shared sheet only pads its title; give the
                        // editor the same horizontal inset as the title.
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Tokens.SpaceLg),
                        ) {
                            Text(
                                text = "Each zone in a player can have its unique gestures.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = Tokens.SpaceSm),
                            )
                            PlayerGesturesEditor(
                            mode = mode,
                            slotSet =
                                if (mode == PlayerGestures.MODE_FULLSCREEN) {
                                    state.playerGestures.fullscreen
                                } else {
                                    state.playerGestures.normal
                                },
                                onCellChange = { slot, type, action ->
                                    viewModel.setPlayerGesturesCell(
                                        mode,
                                        slot,
                                        type,
                                        action,
                                    )
                                },
                            )
                            if (
                                (mode == PlayerGestures.MODE_FULLSCREEN &&
                                    state.playerGestures.fullscreen.isCustomized) ||
                                (mode == PlayerGestures.MODE_NORMAL &&
                                    state.playerGestures.normal.isCustomized)
                            ) {
                                TextButton(onClick = { viewModel.resetPlayerGestures(mode) }) {
                                    Text("Reset to defaults")
                                }
                            }
                        }
                    }
                }
            }

            "dual" -> {
                val settingsContext = LocalContext.current
                val writeSettingsLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult(),
                    ) { }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader("General")
                    SettingsSwitchCard(
                        icon = Icons.Filled.DisplaySettings,
                        title = "Dual screen",
                        subtitle = "Show video controls on the second screen",
                        checked = state.dualScreen,
                        onCheckedChange = { enabled ->
                            viewModel.updateGeneral("dualScreen", enabled)
                            // Device-wide brightness (both screens) needs the
                            // WRITE_SETTINGS grant — ask once when the feature
                            // is turned on.
                            if (enabled && !writeSettingsGranted(settingsContext)) {
                                writeSettingsLauncher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                        Uri.parse("package:${settingsContext.packageName}"),
                                    ),
                                )
                            }
                        },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Layers,
                        title = "Pages",
                        subtitle = dualListLabel(state.dualScreenPages, dualPageNames),
                        onClick = { onChoiceSelected(Choice.DUAL_PAGES) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Video page",
                        reset =
                            if (
                                state.dualScreenVideoTabs != defaults.dualScreenVideoTabs ||
                                state.dualScreenVideoTabOrder != defaults.dualScreenVideoTabOrder
                            ) {
                                {
                                    viewModel.setDualScreenVideoTabs(defaults.dualScreenVideoTabs)
                                    viewModel.setDualScreenVideoTabOrder(
                                        defaults.dualScreenVideoTabOrder,
                                    )
                                }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Tab,
                        title = "Video tabs",
                        subtitle = dualOrderLabel(state.dualScreenVideoTabOrder, dualTabNames),
                        onClick = { onChoiceSelected(Choice.DUAL_TAB_ORDER) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = SplitscreenBottom,
                        title = "Main page order",
                        subtitle = dualOrderLabel(state.dualScreenPageOrder, dualPageOrderNames),
                        onClick = { onChoiceSelected(Choice.DUAL_PAGE_ORDER) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Playlists page",
                        reset =
                            if (state.dualScreenLibrarySlots != defaults.dualScreenLibrarySlots) {
                                { viewModel.setDualScreenLibrarySlots(defaults.dualScreenLibrarySlots) }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.VideoLibrary,
                        title = "Library slots",
                        subtitle = slotListLabel(state.dualScreenLibrarySlots, playlists),
                        onClick = { onChoiceSelected(Choice.DUAL_SLOTS) },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Home page",
                        reset =
                            if (state.dualScreenFeedSources.isNotEmpty()) {
                                { viewModel.setDualScreenFeedSources(emptyList()) }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.RssFeed,
                        title = "Feed sources",
                        subtitle =
                            if (state.dualScreenFeedSources.isEmpty()) {
                                "All sources"
                            } else {
                                state.dualScreenFeedSources.joinToString(", ") { id ->
                                    enabledSources.firstOrNull { it.id == id }?.name ?: id
                                }
                            },
                        onClick = { onChoiceSelected(Choice.DUAL_FEED_SOURCES) },
                    )
                }
            }

            "controller" -> {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsSwitchCard(
                        icon = Icons.Filled.Games,
                        title = "Use controller",
                        subtitle = "Control playback with a gamepad or TV remote",
                        checked = state.controller.enabled,
                        onCheckedChange = { enabled ->
                            viewModel.setController(state.controller.copy(enabled = enabled))
                        },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Replay10,
                        title = "Backwards jump step",
                        subtitle = "${state.controller.seekBackSeconds}s",
                        onClick = { onChoiceSelected(Choice.CONTROLLER_SEEK_BACK) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Forward30,
                        title = "Forwards jump step",
                        subtitle = "${state.controller.seekForwardSeconds}s",
                        onClick = { onChoiceSelected(Choice.CONTROLLER_SEEK_FORWARD) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Buttons",
                        reset =
                            if (state.controller.mappings.isNotEmpty()) {
                                { viewModel.setController(state.controller.copy(mappings = emptyMap())) }
                            } else {
                                null
                            },
                    )
                    PlayerControllerActions.ALL.forEachIndexed { index, action ->
                        ControllerBindingRow(
                            action = action,
                            binding = state.controller.mappings[action.id],
                            onCapture = { onBindRequested(action.id) },
                            onClear = {
                                viewModel.setController(
                                    state.controller.copy(
                                        mappings = state.controller.mappings - action.id,
                                    ),
                                )
                            },
                            groupPosition =
                                GroupPosition.fromIndex(
                                    index,
                                    PlayerControllerActions.ALL.size,
                                ),
                        )
                    }
                }
            }
        }
    }
}

private val dualPageNames =
    mapOf(
        "video" to "Video page",
        "library" to "Library page",
        "home" to "Home page",
        "dash" to "Dash page",
    )

private val dualTabNames =
    mapOf(
        "info" to "Info",
        "controls" to "Controls",
        "comments" to "Comments",
        "chapters" to "Chapters",
        "recommended" to "Recommended",
        "queue" to "Queue",
        "dot" to "·",
    )

private val dualPageOrderNames =
    mapOf(
        "controls" to "Controls",
        "video" to "Video",
        "tabs" to "Tabs",
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

private fun dualListLabel(
    keys: List<String>,
    names: Map<String, String>,
): String {
    val all = names.keys.filter { it in keys }
    return when {
        all.isEmpty() -> "None"
        all.size == names.keys.size -> "All"
        else -> all.joinToString(", ") { names[it] ?: it }
    }
}

/** Subtitle for order settings: the actual order, comma separated. */
private fun dualOrderLabel(
    order: List<String>,
    names: Map<String, String>,
): String = order.joinToString(", ") { names[it] ?: it }

/**
 * About page: app identity + version, the support-the-original notice
 * (Bluejay is a fork of Grayjay, which builds on FUTO), and the standard
 * about links (upstream site, source repo, license).
 */
@Composable
private fun AboutItems() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // The feature module has no BuildConfig of its own (versionName is set
    // on the app module) — read it from the package manager instead.
    val version =
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull() ?: "unknown"

    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceLg)) {
        Card(
            shape = RoundedCornerShape(BluejayTokens().radius.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        ) {
            Row(
                modifier = Modifier.padding(Tokens.SpaceLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(Tokens.AvatarMd),
                    tint = scheme.primary,
                )
                Spacer(Modifier.width(Tokens.SpaceLg))
                Column {
                    Text(
                        text = "Bluejay",
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = "Version $version",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            text =
                "Bluejay is a fork of Grayjay, which builds on FUTO. " +
                    "If this app keeps you watching, consider supporting the original project.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
            SettingsOptionCard(
                icon = Icons.Filled.Launch,
                title = "Support Grayjay",
                subtitle = "grayjay.app — the original project",
                onClick = { openUrl(context, "https://grayjay.app") },
                groupPosition = GroupPosition.First,
            )
            SettingsOptionCard(
                icon = Icons.Filled.Code,
                title = "GitHub",
                subtitle = "Source code, issues, feature requests",
                onClick = {
                    openUrl(context, "https://github.com/tsutsen-org/bluejay-android")
                },
                groupPosition = GroupPosition.Middle,
            )
            SettingsOptionCard(
                icon = Icons.Filled.Copyright,
                title = "License",
                subtitle = "Source First License 1.1",
                onClick = {
                    openUrl(
                        context,
                        "https://github.com/tsutsen-org/bluejay-android/blob/master/LICENSE.md",
                    )
                },
                groupPosition = GroupPosition.Last,
            )
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun sectionTitle(category: String): String =
    when (category) {
        "appearance" -> "Appearance"
        "content" -> "Content"
        "playback" -> "Playback"
        "gestures" -> "Gestures"
        "controller" -> "Controller"
        "dual" -> "Dual screen"
        "about" -> "About"
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
    SUBTITLE_OUTLINE,
    DUAL_PAGES,
    DUAL_TAB_ORDER,
    DUAL_PAGE_ORDER,
    DUAL_SLOTS,
    DUAL_FEED_SOURCES,
    LIBRARY_SECTION_ORDER,
    PLAYBACK_SPEED,
    SPEEDUP_SENSITIVITY,
    JUMP_STEP,
    CONTROLLER_SEEK_BACK,
    CONTROLLER_SEEK_FORWARD,
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
internal fun ChoiceDialog(
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
 * Reorder (and optionally enable/disable) a list of items via a
 * drag-reorderable list. [items] is (id, label) in the current display
 * order; [onChange] receives the new ordered ids when a drag settles.
 *
 * Pass [enabledIds] + [onToggleEnabled] to also show an enable/disable
 * checkbox per row — order and visibility in one popup.
 */
@Composable
private fun ReorderDialog(
    title: String,
    items: List<Pair<String, String>>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    enabledIds: List<String>? = null,
    onToggleEnabled: ((String, Boolean) -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ReorderableList(
                items = items,
                onReordered = onChange,
                enabledIds = enabledIds,
                onToggleEnabled = onToggleEnabled,
                // ponytail: fixed cap — a LazyColumn is greedy inside an
                // AlertDialog and would expand the dialog to window height;
                // 400dp fits every current list (max 7 rows) without
                // scrolling. Replace with a token if one exists for dialog
                // max heights.
                modifier = Modifier.heightIn(max = 400.dp),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * Small subsection title above a group of settings cards. Pass [reset] to
 * show a right-aligned "Reset to defaults" action (only pass it when the
 * subsection actually deviates from the defaults).
 */
@Composable
private fun SettingsHeader(
    title: String,
    reset: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Tokens.SpaceSm, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (reset != null) {
            // Compact text action — the same line height as the title, so
            // the header row keeps its height when the button appears.
            Text(
                text = "Reset to defaults",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Tokens.SpaceXs))
                        .clickable(onClick = reset)
                        .padding(
                            horizontal = Tokens.SpaceSm,
                            vertical = Tokens.SpaceXxs,
                        ),
            )
        }
    }
}

/** Canonical defaults — used to decide which subsections deviate. */
private val defaults = AppPreferences()

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
                                    .clip(RoundedCornerShape(BluejayTokens().radius.md))
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
                            onClick = {
                                onSetSlot(editing!!, id)
                                editing = null
                            },
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
                            onClick = {
                                onSetSlot(editing!!, value)
                                editing = null
                            },
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

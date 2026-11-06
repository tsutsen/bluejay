package com.tsutsen.platformplayer.feature.settings.impl

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tab
import com.tsutsen.platformplayer.core.designsystem.icon.Download
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.data.repository.UpdateCheckState
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ControllerBinding
import com.tsutsen.platformplayer.core.datastore.model.HiddenContentEntry
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.ReorderableList
import com.tsutsen.platformplayer.core.designsystem.component.BluejayModalBottomSheet
import com.tsutsen.platformplayer.core.designsystem.component.SettingsOptionCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsSliderCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsSwitchCard
import com.tsutsen.platformplayer.core.designsystem.component.groupShape
import com.tsutsen.platformplayer.core.designsystem.layout.AppHeader
import com.tsutsen.platformplayer.core.designsystem.layout.bluejayNavItems
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.ui.AsyncImage
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

/**
 * One controller action: shows the bound key (and the device it was bound
 * with). Tap to bind a new key; Clear removes the custom binding (back to
 * the default key).
 */
/** Icon tile per controller action, matching the other settings rows. */
private fun controllerActionIcon(id: String) =
    when (id) {
        PlayerControllerActions.PLAY_PAUSE -> Icons.Filled.PlayArrow
        PlayerControllerActions.SEEK_BACK -> Icons.Filled.Replay10
        PlayerControllerActions.SEEK_FORWARD -> Icons.Filled.Forward30
        PlayerControllerActions.SPEED_UP, PlayerControllerActions.SPEED_DOWN -> Icons.Filled.Speed
        PlayerControllerActions.NEXT -> Icons.Filled.SkipNext
        PlayerControllerActions.PREVIOUS -> Icons.Filled.SkipPrevious
        PlayerControllerActions.CLOSE -> Icons.Filled.Close
        PlayerControllerActions.BRIGHTNESS_UP, PlayerControllerActions.BRIGHTNESS_DOWN -> Icons.Filled.BrightnessAuto
        PlayerControllerActions.VOLUME_UP -> Icons.Filled.VolumeUp
        PlayerControllerActions.VOLUME_DOWN -> Icons.Filled.VolumeDown
        else -> Icons.Filled.Games
    }

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
            Box(
                modifier =
                    Modifier
                        .size(Tokens.AvatarMd)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(BluejayTokens().radius.sm),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    controllerActionIcon(action.id),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(Tokens.IconMd),
                )
            }
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
/**
 * Captures the next gamepad/TV-remote key press and binds it to [action].
 * Public so the getting-started tour can reuse the same capture UX.
 */
@Composable
fun ControllerBindingPopup(
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
    onHiddenContentClick: () -> Unit,
    onNavigateTab: (String) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceLg)) {
        when (category) {
            "backup" -> {
                BackupItems(viewModel, onNavigateTab)
            }

            "about" -> {
                AboutItems(viewModel)
            }

            "appearance" -> {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsOptionCard(
                        icon = Icons.Filled.BrightnessAuto,
                        title = "Mode",
                        subtitle =
                            when (state.prefs.appearance.themeMode) {
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
                        checked = state.prefs.appearance.dynamicColor,
                        onCheckedChange = { viewModel.updateGeneral("dynamicColor", it) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSliderCard(
                        icon = Icons.Filled.RoundedCorner,
                        title = "UI rounding",
                        subtitle = "Corner radius across the app",
                        value = state.prefs.appearance.uiRounding.toFloat(),
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
                        icon = Icons.Filled.TextFields,
                        title = "Font",
                        subtitle = fontFamilyLabel(state.prefs.appearance.fontFamily),
                        onClick = { onChoiceSelected(Choice.FONT) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSliderCard(
                        icon = Icons.Filled.FormatSize,
                        title = "Font size",
                        subtitle = "Scales all text (${(state.prefs.appearance.fontSizeScale * 100).roundToInt()}%)",
                        value = state.prefs.appearance.fontSizeScale * 100f,
                        valueRange = 80f..140f,
                        onValueChange = { viewModel.setFontSizeScale(it / 100f) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.GridOn,
                        title = "Grid columns",
                        subtitle = "${state.prefs.gridColumns} columns",
                        onClick = { onChoiceSelected(Choice.GRID_COLUMNS) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader("Themes")
                    ThemesSection(state.prefs.appearance, viewModel)
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader("Thumbnails")
                    SettingsOptionCard(
                        icon = Icons.Filled.Palette,
                        title = "Tint",
                        subtitle =
                            state.prefs.appearance.thumbnailTintArgb?.let { tintPreviewLabel(it) }
                                ?: "None",
                        onClick = { onChoiceSelected(Choice.THUMBNAIL_TINT) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSliderCard(
                        icon = Icons.Filled.BorderStyle,
                        title = "Tint strength",
                        subtitle = "How much of the tint mixes into thumbnails",
                        value = state.prefs.appearance.thumbnailTintStrength * 100f,
                        valueRange = 0f..100f,
                        onValueChange = { viewModel.setThumbnailTintStrength(it / 100f) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Layers,
                        title = "Tint blend mode",
                        subtitle = tintBlendLabel(state.prefs.appearance.thumbnailTintBlendMode),
                        onClick = { onChoiceSelected(Choice.THUMBNAIL_TINT_BLEND) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSliderCard(
                        icon = Icons.Filled.BrightnessAuto,
                        title = "Desaturate",
                        subtitle = "Color intensity of every thumbnail",
                        value = (1f - state.prefs.appearance.thumbnailSaturation) * 100f,
                        valueRange = 0f..100f,
                        onValueChange = { viewModel.setThumbnailSaturation(1f - it / 100f) },
                        groupPosition = GroupPosition.Last,
                    )
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
                        checked = state.prefs.autoUpdatePlugins,
                        onCheckedChange = { viewModel.updateGeneral("autoUpdatePlugins", it) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Video page",
                        reset =
                            if (
                                state.prefs.showRecommendedVideos != defaults.showRecommendedVideos ||
                                state.prefs.showComments != defaults.showComments
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
                        icon = Icons.Filled.PlayCircle,
                        title = "Autoplay",
                        subtitle = "Play the next video in the queue when the current one ends",
                        checked = state.prefs.playback.autoPlay,
                        onCheckedChange = { viewModel.setAutoplay(it) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSwitchCard(
                        icon = VideoTemplate,
                        title = "Show recommended videos",
                        subtitle = "Recommended tab on the video page",
                        checked = state.prefs.showRecommendedVideos,
                        onCheckedChange = { viewModel.updateGeneral("showRecommendedVideos", it) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.Chat,
                        title = "Show comments",
                        subtitle = "Comments tab on the video page",
                        checked = state.prefs.showComments,
                        onCheckedChange = { viewModel.updateGeneral("showComments", it) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Library",
                        reset =
                            if (
                                state.prefs.librarySectionOrder != defaults.librarySectionOrder ||
                                state.prefs.librarySectionsEnabled != defaults.librarySectionsEnabled
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
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Home feed",
                        reset =
                            if (
                                state.prefs.showWatchedVideos != defaults.showWatchedVideos ||
                                state.prefs.showStartedVideos != defaults.showStartedVideos
                            ) {
                                {
                                    viewModel.setShowWatchedVideos(defaults.showWatchedVideos)
                                    viewModel.setShowStartedVideos(defaults.showStartedVideos)
                                }
                            } else {
                                null
                            },
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.Visibility,
                        title = "Show watched videos",
                        subtitle = "Hide fully watched videos from the home feed",
                        checked = state.prefs.showWatchedVideos,
                        onCheckedChange = { viewModel.setShowWatchedVideos(it) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.History,
                        title = "Show started videos",
                        subtitle = "Hide partially watched videos from the home feed",
                        checked = state.prefs.showStartedVideos,
                        onCheckedChange = { viewModel.setShowStartedVideos(it) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader("Recommendations")
                    SettingsSwitchCard(
                        icon = Icons.Filled.RssFeed,
                        title = "Homebrew recommendations",
                        subtitle = "Build the home feed from the sources below",
                        checked = state.prefs.homebrewRecommendations,
                        onCheckedChange = { viewModel.setHomebrewRecommendations(it) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.PlayCircle,
                        title = "Source picks",
                        subtitle = "Home feed from your sources",
                        checked = state.prefs.sourceYoutubeFeed,
                        onCheckedChange = { viewModel.setSourceYoutubeFeed(it) },
                        enabled = state.prefs.homebrewRecommendations,
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.Subscriptions,
                        title = "Subscriptions",
                        subtitle = "Recent unwatched videos from subscribed channels",
                        checked = state.prefs.sourceSubscriptions,
                        onCheckedChange = { viewModel.setSourceSubscriptions(it) },
                        enabled = state.prefs.homebrewRecommendations,
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.Groups,
                        title = "Top creators",
                        subtitle = "Recent unwatched videos from creators you watch most",
                        checked = state.prefs.sourceTopCreators,
                        onCheckedChange = { viewModel.setSourceTopCreators(it) },
                        enabled = state.prefs.homebrewRecommendations,
                        groupPosition = GroupPosition.Middle,
                    )
                    SpawnProbabilityCard(
                        enabled = state.prefs.homebrewRecommendations,
                        cut1 = state.prefs.probCut1,
                        cut2 = state.prefs.probCut2,
                        sourceYoutubeFeed = state.prefs.sourceYoutubeFeed,
                        sourceSubscriptions = state.prefs.sourceSubscriptions,
                        sourceTopCreators = state.prefs.sourceTopCreators,
                        onCutsChange = { cut1, cut2 -> viewModel.setProbCuts(cut1, cut2) },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.History,
                        title = "Recency",
                        subtitle = recencyLabel(state.prefs.recency),
                        onClick = { onChoiceSelected(Choice.RECENCY) },
                        enabled = state.prefs.homebrewRecommendations,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.VisibilityOff,
                        title = "Hidden content",
                        subtitle = "Videos and creators you hid or reduced",
                        onClick = onHiddenContentClick,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Dock",
                        reset =
                            if (
                                state.prefs.dockTabOrder != defaults.dockTabOrder ||
                                state.prefs.defaultDockTab != defaults.defaultDockTab
                            ) {
                                {
                                    viewModel.setDockTabOrder(defaults.dockTabOrder)
                                    viewModel.setDefaultDockTab(defaults.defaultDockTab)
                                }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Reorder,
                        title = "Tab order",
                        subtitle = "Drag to reorder the bottom navigation tabs",
                        onClick = { onChoiceSelected(Choice.DOCK_TAB_ORDER) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.PlayCircle,
                        title = "Default tab",
                        subtitle =
                            bluejayNavItems.firstOrNull { it.key == state.prefs.defaultDockTab }?.label
                                ?: "Home",
                        onClick = { onChoiceSelected(Choice.DEFAULT_DOCK_TAB) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Subscriptions",
                        reset =
                            if (state.prefs.subscriptionsOrder != defaults.subscriptionsOrder) {
                                { viewModel.setSubscriptionsOrder(defaults.subscriptionsOrder) }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Subscriptions,
                        title = "List order",
                        subtitle =
                            when (state.prefs.subscriptionsOrder) {
                                "random" -> "Random"
                                "alphabetical" -> "Alphabetical"
                                else -> "Watch time"
                            },
                        onClick = { onChoiceSelected(Choice.SUBSCRIPTIONS_ORDER) },
                        groupPosition = GroupPosition.First,
                    )
                }
            }

            "hiddenContent" -> {
                val hiddenCreators by viewModel.hiddenCreators.collectAsState(initial = emptyList())
                val hiddenVideos by viewModel.hiddenVideos.collectAsState(initial = emptyList())
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceLg)) {
                    Text(
                        text =
                            "How likely hidden or reduced items still appear in the home feed. " +
                                "0% never shows, 100% is fully shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                        SettingsHeader("Authors")
                        if (hiddenCreators.isEmpty()) {
                            EmptyHiddenContentHint("No creators adjusted yet")
                        } else {
                            hiddenCreators
                                .sortedBy { it.name.ifEmpty { it.url }.lowercase() }
                                .forEachIndexed { i, entry ->
                                    HiddenContentRow(
                                        entry = entry,
                                        position = groupPositionFor(i, hiddenCreators.size),
                                        onProbabilityChange = {
                                            viewModel.setHiddenCreatorProbability(entry.url, it)
                                        },
                                    )
                                }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                        SettingsHeader("Videos")
                        if (hiddenVideos.isEmpty()) {
                            EmptyHiddenContentHint("No videos adjusted yet")
                        } else {
                            hiddenVideos
                                .sortedBy { it.name.ifEmpty { it.url }.lowercase() }
                                .forEachIndexed { i, entry ->
                                    HiddenContentRow(
                                        entry = entry,
                                        position = groupPositionFor(i, hiddenVideos.size),
                                        onProbabilityChange = {
                                            viewModel.setHiddenVideoProbability(entry.url, it)
                                        },
                                    )
                                }
                        }
                    }
                }
            }

            "playback" -> {
                val subtitlesDefault = defaults.subtitle
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Subtitles",
                        reset =
                            if (state.prefs.subtitle != subtitlesDefault) {
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
                        subtitle = subtitleFontLabel(state.prefs.subtitle.font),
                        onClick = { onChoiceSelected(Choice.SUBTITLE_FONT) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.TextFields,
                        title = "Subtitle size",
                        subtitle = "${state.prefs.subtitle.size} pt",
                        onClick = { onChoiceSelected(Choice.SUBTITLE_SIZE) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.VerticalAlignBottom,
                        title = "Subtitle bottom padding",
                        subtitle = "${state.prefs.subtitle.bottomPadding} dp",
                        onClick = { onChoiceSelected(Choice.SUBTITLE_PADDING) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.BorderStyle,
                        title = "Subtitle outline",
                        subtitle = "${state.prefs.subtitle.outline} px",
                        onClick = { onChoiceSelected(Choice.SUBTITLE_OUTLINE) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Quality",
                        reset =
                            if (
                                state.prefs.defaultVideoResolution != defaults.defaultVideoResolution ||
                                state.prefs.defaultDownloadResolution != defaults.defaultDownloadResolution
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
                        subtitle = state.prefs.defaultVideoResolution,
                        onClick = { onChoiceSelected(Choice.VIDEO_RESOLUTION) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Download,
                        title = "Default download resolution",
                        subtitle = state.prefs.defaultDownloadResolution,
                        onClick = { onChoiceSelected(Choice.DOWNLOAD_RESOLUTION) },
                        groupPosition = GroupPosition.Last,
                    )
                }
            }

            "gestures" -> {
                var gestureSheetMode by remember { mutableStateOf<String?>(null) }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader("General")
                    SettingsOptionCard(
                        icon = Icons.Filled.Speed,
                        title = "Default playback speedup",
                        subtitle = "${state.prefs.defaultSpeedup}x",
                        onClick = { onChoiceSelected(Choice.PLAYBACK_SPEED) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Speed,
                        title = "Speedup gesture speed",
                        subtitle = "${state.prefs.speedupSensitivity}x",
                        onClick = { onChoiceSelected(Choice.SPEEDUP_SENSITIVITY) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Timer,
                        title = "Time jump step",
                        subtitle = "${state.prefs.jumpStepSeconds}s",
                        onClick = { onChoiceSelected(Choice.JUMP_STEP) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader("Customization")
                    SettingsOptionCard(
                        icon = Icons.Filled.Gesture,
                        title = "Fullscreen player gestures",
                        subtitle =
                            if (state.prefs.playerGestures.fullscreen.isCustomized) {
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
                            if (state.prefs.playerGestures.normal.isCustomized) {
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
                        headerAction =
                            if (
                                (mode == PlayerGestures.MODE_FULLSCREEN &&
                                    state.prefs.playerGestures.fullscreen.isCustomized) ||
                                (mode == PlayerGestures.MODE_NORMAL &&
                                    state.prefs.playerGestures.normal.isCustomized)
                            ) {
                                {
                                    TextButton(onClick = { viewModel.resetPlayerGestures(mode) }) {
                                        Text("Reset to defaults")
                                    }
                                }
                            } else {
                                null
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
                                    state.prefs.playerGestures.fullscreen
                                } else {
                                    state.prefs.playerGestures.normal
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
                        icon = DualScreen,
                        title = "Dual screen",
                        subtitle = "Show video controls on the second screen",
                        checked = state.prefs.dualScreen,
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
                        subtitle = dualListLabel(state.prefs.dualScreenPages, dualPageNames),
                        onClick = { onChoiceSelected(Choice.DUAL_PAGES) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Dash page",
                        reset =
                            if (
                                state.prefs.dualScreenTopCreatorsScope !=
                                    defaults.dualScreenTopCreatorsScope ||
                                state.prefs.dualScreenDashPageOrder != defaults.dualScreenDashPageOrder
                            ) {
                                {
                                    viewModel.setDualScreenTopCreatorsScope(
                                        defaults.dualScreenTopCreatorsScope,
                                    )
                                    viewModel.setDualScreenDashPageOrder(
                                        defaults.dualScreenDashPageOrder,
                                    )
                                }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Groups,
                        title = "Top creators",
                        subtitle =
                            if (state.prefs.dualScreenTopCreatorsScope == "overall") "Overall"
                            else "This week",
                        onClick = { onChoiceSelected(Choice.DASH_TOP_CREATORS) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Reorder,
                        title = "Dash page order",
                        subtitle = dualOrderLabel(state.prefs.dualScreenDashPageOrder, dashPageOrderNames),
                        onClick = { onChoiceSelected(Choice.DASH_PAGE_ORDER) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Video page",
                        reset =
                            if (
                                state.prefs.dualScreenVideoTabs != defaults.dualScreenVideoTabs ||
                                state.prefs.dualScreenVideoTabOrder != defaults.dualScreenVideoTabOrder
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
                        subtitle = dualOrderLabel(state.prefs.dualScreenVideoTabOrder, dualTabNames),
                        onClick = { onChoiceSelected(Choice.DUAL_TAB_ORDER) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = SplitscreenBottom,
                        title = "Video page order",
                        subtitle = dualOrderLabel(state.prefs.dualScreenPageOrder, dualPageOrderNames),
                        onClick = { onChoiceSelected(Choice.DUAL_PAGE_ORDER) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Playlists page",
                        reset =
                            if (state.prefs.dualScreenLibrarySlots != defaults.dualScreenLibrarySlots) {
                                { viewModel.setDualScreenLibrarySlots(defaults.dualScreenLibrarySlots) }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.VideoLibrary,
                        title = "Library slots",
                        subtitle = slotListLabel(state.prefs.dualScreenLibrarySlots, playlists),
                        onClick = { onChoiceSelected(Choice.DUAL_SLOTS) },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Home page",
                        reset =
                            if (state.prefs.dualScreenFeedSources.isNotEmpty()) {
                                { viewModel.setDualScreenFeedSources(emptyList()) }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.RssFeed,
                        title = "Feed sources",
                        subtitle =
                            if (state.prefs.dualScreenFeedSources.isEmpty()) {
                                "All sources"
                            } else {
                                state.prefs.dualScreenFeedSources.joinToString(", ") { id ->
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
                        checked = state.prefs.controller.enabled,
                        onCheckedChange = { enabled ->
                            viewModel.setController(state.prefs.controller.copy(enabled = enabled))
                        },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Replay10,
                        title = "Backwards jump step",
                        subtitle = "${state.prefs.controller.seekBackSeconds}s",
                        onClick = { onChoiceSelected(Choice.CONTROLLER_SEEK_BACK) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Forward30,
                        title = "Forwards jump step",
                        subtitle = "${state.prefs.controller.seekForwardSeconds}s",
                        onClick = { onChoiceSelected(Choice.CONTROLLER_SEEK_FORWARD) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = "Buttons",
                        reset =
                            if (state.prefs.controller.mappings.isNotEmpty()) {
                                { viewModel.setController(state.prefs.controller.copy(mappings = emptyMap())) }
                            } else {
                                null
                            },
                    )
                    PlayerControllerActions.ALL.forEachIndexed { index, action ->
                        ControllerBindingRow(
                            action = action,
                            binding = state.prefs.controller.mappings[action.id],
                            onCapture = { onBindRequested(action.id) },
                            onClear = {
                                viewModel.setController(
                                    state.prefs.controller.copy(
                                        mappings = state.prefs.controller.mappings - action.id,
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

private val dashPageOrderNames =
    mapOf(
        "stats" to "Stats",
        "top_creators" to "Top creators",
        "continue" to "Continue",
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
 * "Backup & restore" section: tiles with the size of the current app
 * state (loaded on demand, no background work), then export / restore.
 * Both actions run the app's existing backup engine (StateBackup), which
 * handles the file pickers, the zip format and the import UI.
 */
@Composable
private fun BackupItems(
    viewModel: SettingsViewModel,
    onNavigateTab: (String) -> Unit = {},
) {
    val summary by viewModel.backupSummary.collectAsState()
    var confirmRestore by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadBackupSummary() }

    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceLg)) {
        // State tiles: two per row, the third spans the full width.
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
                BackupTile(
                    count = summary?.subscriptions ?: 0,
                    label = "subscriptions",
                    detail = "across ${summary?.sources ?: 0} sources",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab("subscriptions") },
                )
                BackupTile(
                    count = summary?.savedVideos ?: 0,
                    label = "videos",
                    detail = "across ${summary?.savedPlaylists ?: 0} playlists",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab("library") },
                )
            }
            BackupTile(
                count = summary?.customSettings ?: 0,
                label = "custom settings",
                detail = "across ${summary?.sections ?: 0} sections",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onNavigateTab("settings") },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
            SettingsOptionCard(
                icon = Icons.Filled.Upload,
                title = "Export backup",
                subtitle = "Save settings, subscriptions, playlists, watch later, history and plugins to a file",
                onClick = { viewModel.exportBackup() },
                groupPosition = GroupPosition.First,
            )
            SettingsOptionCard(
                icon = Icons.Filled.Restore,
                title = "Restore from backup",
                subtitle = "Load a backup file and restore the app state",
                onClick = { confirmRestore = true },
                groupPosition = GroupPosition.Last,
            )
        }

        Text(
            text =
                "The backup contains your settings, subscriptions, playlists, " +
                    "watch later list, watch history and plugin settings. " +
                    "Downloaded video files are not included.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore backup") },
            text = {
                Text(
                    "This replaces your current subscriptions, playlists, watch " +
                        "later list, history and settings with the contents of " +
                        "the backup file. Continue?",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; viewModel.importBackup() }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BackupTile(
    count: Int,
    label: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(BluejayTokens().radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(Tokens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXxs),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * About page: app identity + version, the support-the-original notice
 * (Bluejay is a fork of Grayjay, which builds on FUTO), and the standard
 * about links (upstream site, source repo, license).
 */
@Composable
private fun AboutItems(viewModel: SettingsViewModel) {
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(Tokens.SpaceLg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The real launcher icon, rendered from the package
                // manager (the feature module can't reference app R
                // resources).
                val launcherIcon =
                    remember {
                        val drawable: android.graphics.drawable.Drawable =
                            context.packageManager.getApplicationIcon(context.packageName)
                        val sizePx = 96
                        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        drawable.setBounds(0, 0, sizePx, sizePx)
                        drawable.draw(canvas)
                        bitmap.asImageBitmap()
                    }
                Image(
                    bitmap = launcherIcon,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(Tokens.AvatarLg)
                            .clip(RoundedCornerShape(BluejayTokens().radius.sm)),
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
                "Bluejay is a rewrite of Grayjay media app by FUTO and it still " +
                    "relies on the plugins developped and updated by them! So if you " +
                    "love this app, consider supporting the original project and their team.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
            val updateState by viewModel.updateCheckState.collectAsState()
            SettingsOptionCard(
                icon = Download,
                title = "Check for updates",
                subtitle =
                    when (val s = updateState) {
                        UpdateCheckState.Idle -> "Check GitHub releases — current $version"
                        UpdateCheckState.Checking -> "Checking…"
                        is UpdateCheckState.UpToDate -> "Up to date (${s.versionName})"
                        is UpdateCheckState.Available -> "${s.tag} available — tap to download"
                        is UpdateCheckState.Downloading -> "${s.tag} downloading…"
                        is UpdateCheckState.Ready -> "${s.tag} downloaded — installing… (tap to retry)"
                        is UpdateCheckState.Failed -> "Check failed: ${s.message}"
                    },
                onClick = { viewModel.onUpdateCardClick() },
                groupPosition = GroupPosition.First,
            )
            SettingsOptionCard(
                icon = Icons.Filled.PlayArrow,
                title = "Launch initial setup",
                subtitle = "Show the getting started flow again",
                onClick = { viewModel.restartGettingStarted() },
                groupPosition = GroupPosition.Middle,
            )
            SettingsOptionCard(
                icon = Icons.Filled.Launch,
                title = "Support Grayjay",
                subtitle = "grayjay.app — the original project",
                onClick = { openUrl(context, "https://grayjay.app") },
                groupPosition = GroupPosition.Middle,
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

/** Recency option label shown as the Recency row's subtitle. */
private fun recencyLabel(recency: String): String =
    when (recency) {
        "1w" -> "1 week and newer"
        "1m" -> "1 month and newer"
        "3m" -> "3 months and newer"
        "6m" -> "6 months and newer"
        "1y" -> "1 year and newer"
        "3y" -> "3 years and newer"
        else -> "Any time"
    }

/** Appearance > Font option label. */
private fun fontFamilyLabel(family: com.tsutsen.platformplayer.core.datastore.model.FontFamily): String =
    when (family) {
        com.tsutsen.platformplayer.core.datastore.model.FontFamily.INTER -> "Inter"
        com.tsutsen.platformplayer.core.datastore.model.FontFamily.ROBOTO -> "Roboto"
        com.tsutsen.platformplayer.core.datastore.model.FontFamily.OPEN_SANS -> "Open Sans"
        com.tsutsen.platformplayer.core.datastore.model.FontFamily.DEFAULT -> "System"
    }

/** "Tint" row subtitle: the picked color as hex. */
private fun tintPreviewLabel(argb: Long): String {
    val hex = Integer.toHexString(((argb and 0x00FFFFFFL).toInt())).uppercase().padStart(6, '0')
    return "#$hex"
}

/** "Tint blend mode" row subtitle. */
private fun tintBlendLabel(mode: String): String =
    when (mode) {
        com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.MULTIPLY.name -> "Multiply"
        com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.SCREEN.name -> "Screen"
        com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.OVERLAY.name -> "Overlay"
        com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.SOFT_LIGHT.name -> "Soft light"
        com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.COLOR_BURN.name -> "Color burn"
        else -> "Wash"
    }

/**
 * Thumbnail tint picker: preset hues at full color (strength is its own
 * slider below the Tint row), plus None.
 */
@Composable
private fun ThumbnailTintDialog(
    current: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thumbnail tint") },
        text = {
            Column {
                Text(
                    "The color laid over every video thumbnail. Strength and blend mode are set below the Tint row.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Tokens.SpaceMd))
                PresetColors.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm)) {
                        row.forEach { argb ->
                            val opaque = argb.toLong() or (0xFFL shl 24)
                            // Legacy tints carry a baked-in 30% alpha; match
                            // by RGB so the picked swatch still highlights.
                            ColorSwatch(
                                color = Color(opaque),
                                isSelected = current != null && (current and 0x00FFFFFFL) == (opaque and 0x00FFFFFFL),
                                onClick = { onPick(opaque) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Tokens.SpaceSm))
                TextButton(onClick = { onPick(null) }) {
                    Text("None", color = if (current == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** Grouped-card rounding for a row at [index] within a [size]-item group. */
private fun groupPositionFor(
    index: Int,
    size: Int,
): GroupPosition =
    when {
        size == 1 -> GroupPosition.Single
        index == 0 -> GroupPosition.First
        index == size - 1 -> GroupPosition.Last
        else -> GroupPosition.Middle
    }

/** Subdued hint shown when a Hidden content section is empty. */
@Composable
private fun EmptyHiddenContentHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Tokens.SpaceSm),
    )
}

/** One Hidden content entry: art, name, url, and a probability tier dropdown. */
@Composable
private fun HiddenContentRow(
    entry: HiddenContentEntry,
    position: GroupPosition,
    onProbabilityChange: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = groupShape(position),
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
            // Thumbnail (video) / channel avatar (creator) captured when the
            // item was hidden; the settings-row icon tile is the fallback.
            Box(
                modifier =
                    Modifier
                        .size(Tokens.AvatarMd)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(BluejayTokens().radius.sm),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        url = entry.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(Tokens.IconMd),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name.ifEmpty { entry.url },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Text(
                        text = "${entry.probability}%",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    HiddenContentEntry.TIERS.forEach { tier ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (tier) {
                                        0 -> "0% — never"
                                        100 -> "100% — fully shown"
                                        else -> "$tier%"
                                    },
                                )
                            },
                            onClick = {
                                onProbabilityChange(tier)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun sectionTitle(category: String): String =
    when (category) {
        "appearance" -> "Appearance"
        "content" -> "Content"
        "hiddenContent" -> "Hidden content"
        "playback" -> "Playback"
        "gestures" -> "Gestures"
        "controller" -> "Controller"
        "dual" -> "Dual screen"
        "backup" -> "Backup & restore"
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
    DASH_TOP_CREATORS,
    DASH_PAGE_ORDER,
    DUAL_SLOTS,
    DUAL_FEED_SOURCES,
    LIBRARY_SECTION_ORDER,
    DOCK_TAB_ORDER,
    DEFAULT_DOCK_TAB,
    SUBSCRIPTIONS_ORDER,
    PLAYBACK_SPEED,
    SPEEDUP_SENSITIVITY,
    JUMP_STEP,
    CONTROLLER_SEEK_BACK,
    CONTROLLER_SEEK_FORWARD,
    VIDEO_RESOLUTION,
    DOWNLOAD_RESOLUTION,
    RECENCY,
    FONT,
    THUMBNAIL_TINT,
    THUMBNAIL_TINT_BLEND,
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

/**
 * "Spawn probability" card in the Recommendations section: one two-headed
 * [RangeSlider] (0..100%, 1% steps) whose heads are the two cut points. The
 * cuts split 0..100 into three segments — source picks [0, cut1],
 * subscriptions [cut1, cut2], top creators [cut2, 100] — so the spawn
 * probabilities sum to 100% by construction. Each segment's label sits under
 * its slice of the track (width proportional to the segment); a category
 * whose source toggle is off shows its label greyed, the slider still works.
 */
@Composable
private fun SpawnProbabilityCard(
    enabled: Boolean,
    cut1: Int,
    cut2: Int,
    sourceYoutubeFeed: Boolean,
    sourceSubscriptions: Boolean,
    sourceTopCreators: Boolean,
    onCutsChange: (Int, Int) -> Unit,
) {
    // Cuts split 0..100 across the ENABLED sources only (fixed order), so the
    // shown percentages always sum to 100. Disabled sources get no segment.
    val cuts = listOf(cut1.coerceIn(0, 100), cut2.coerceIn(0, 100)).sorted()
    val all =
        listOf(
            "Source picks" to sourceYoutubeFeed,
            "Subscriptions" to sourceSubscriptions,
            "Top creators" to sourceTopCreators,
        )
    val active = all.filter { it.second }
    val bounds = listOf(0) + cuts.take(active.size - 1) + listOf(100)
    val segments =
        active.mapIndexed { k, t -> Triple(t.first, bounds[k + 1] - bounds[k], true) }
    val lo = cuts[0]
    val hi = if (cuts.size > 1) cuts[1] else 100
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = groupShape(GroupPosition.Single),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(Tokens.SpaceMd)) {
            Text(
                text = "Spawn probability",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            when (active.size) {
                // 3 enabled sources -> two heads (RangeSlider).
                3 ->
                    RangeSlider(
                        value = lo.toFloat()..hi.toFloat(),
                        onValueChange = { range ->
                            val a = range.start.roundToInt().coerceIn(0, 100)
                            val b = range.endInclusive.roundToInt().coerceIn(0, 100)
                            onCutsChange(minOf(a, b), maxOf(a, b))
                        },
                        valueRange = 0f..100f,
                        // 99 steps = 1% resolution across 0..100.
                        steps = 99,
                        enabled = enabled,
                        modifier = Modifier.padding(top = Tokens.SpaceSm),
                    )
                // 2 enabled sources -> one head.
                2 ->
                    Slider(
                        value = lo.toFloat(),
                        onValueChange = { v -> onCutsChange(v.roundToInt().coerceIn(0, 100), hi) },
                        valueRange = 0f..100f,
                        steps = 99,
                        enabled = enabled,
                        modifier = Modifier.padding(top = Tokens.SpaceSm),
                    )
                // 0-1 enabled sources -> no split needed.
                else ->
                    Text(
                        text = "100% ${segments.firstOrNull()?.first ?: ""}".trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Tokens.SpaceSm),
                    )
            }
            // Segment labels under the track, each centered in its slice
            // (width proportional to the segment = positioned by weight).
            Row(modifier = Modifier.fillMaxWidth()) {
                segments.forEach { (label, pct, active) ->
                    Box(
                        modifier =
                            // weight() requires a positive weight; a zero-width
                            // segment (cut at 0/100 or cut1 == cut2) gets none.
                            if (pct > 0) Modifier.weight(pct.toFloat()) else Modifier.width(0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$label $pct%",
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(if (active) 1f else 0.5f),
                        )
                    }
                }
            }
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

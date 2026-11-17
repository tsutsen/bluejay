package com.tsutsen.platformplayer.feature.settings.impl

import com.tsutsen.platformplayer.core.ui.R
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
import androidx.compose.ui.res.stringResource
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
                    text = stringResource(R.string.settings_error_loading_settings_1_s, current.message),
                    modifier = Modifier.align(Alignment.Center).padding(Tokens.SpaceLg),
                )
            }
        }

        is SettingsUiState.Loaded -> {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(title = { Text(stringResource(R.string.settings_settings), style = MaterialTheme.typography.titleLarge) })
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
                            title = stringResource(R.string.settings_appearance),
                            subtitle = stringResource(R.string.settings_theme_dynamic_color_contrast),
                            onClick = { navigator.navigateToSettingsFragment("appearance") },
                            groupPosition = GroupPosition.First,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.VideoLibrary,
                            title = stringResource(R.string.settings_content),
                            subtitle = stringResource(R.string.settings_plugins_video_page_sections),
                            onClick = { navigator.navigateToSettingsFragment("content") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.PlayArrow,
                            title = stringResource(R.string.settings_playback),
                            subtitle = stringResource(R.string.settings_subtitles_quality),
                            onClick = { navigator.navigateToSettingsFragment("playback") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.Gesture,
                            title = stringResource(R.string.settings_gestures),
                            subtitle = stringResource(R.string.settings_speed_per_slot_gesture_actions),
                            onClick = { navigator.navigateToSettingsFragment("gestures") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.Games,
                            title = stringResource(R.string.settings_controller),
                            subtitle = stringResource(R.string.settings_gamepad_remote_button_mapping),
                            onClick = { navigator.navigateToSettingsFragment("controller") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = DualScreen,
                            title = stringResource(R.string.settings_dual_screen),
                            subtitle = stringResource(R.string.settings_second_display_pages_tabs_sections),
                            onClick = { navigator.navigateToSettingsFragment("dual") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.Backup,
                            title = stringResource(R.string.settings_backup_restore),
                            subtitle = stringResource(R.string.settings_export_or_restore_the_full_app_state),
                            onClick = { navigator.navigateToSettingsFragment("backup") },
                            groupPosition = GroupPosition.Middle,
                        )
                    }
                    item {
                        SettingsOptionCard(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.settings_about),
                            subtitle = stringResource(R.string.settings_version_license_support_the_original_project),
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
                    title = stringResource(R.string.settings_mode),
                    options =
                        listOf(
                            stringResource(R.string.settings_follow_system) to "AUTO",
                            stringResource(R.string.settings_light) to "LIGHT",
                            stringResource(R.string.settings_dark) to "DARK",
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
                    title = stringResource(R.string.settings_grid_columns),
                    options =
                        listOf(
                            stringResource(R.string.settings_2_columns) to "2",
                            stringResource(R.string.settings_3_columns) to "3",
                            stringResource(R.string.settings_4_columns) to "4",
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
                    title = stringResource(R.string.settings_subtitle_font),
                    options =
                        listOf(
                            stringResource(R.string.settings_default) to "default",
                            stringResource(R.string.settings_sans_serif) to "sans",
                            stringResource(R.string.settings_serif) to "serif",
                            stringResource(R.string.settings_monospace) to "mono",
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
                    title = stringResource(R.string.settings_subtitle_size),
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
                    title = stringResource(R.string.settings_subtitle_bottom_padding),
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
                    title = stringResource(R.string.settings_subtitle_outline),
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
                    title = stringResource(R.string.settings_pages),
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
                    title = stringResource(R.string.settings_video_tabs),
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
                    title = stringResource(R.string.settings_video_page_order),
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
                    title = stringResource(R.string.settings_top_creators),
                    options =
                        listOf(
                            stringResource(R.string.settings_this_week) to "week",
                            stringResource(R.string.settings_overall) to "overall",
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
                    title = stringResource(R.string.settings_dash_page_order),
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
                    title = stringResource(R.string.settings_feed_sources),
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
                    title = stringResource(R.string.settings_library_sections),
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
                    title = stringResource(R.string.settings_tab_order),
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
                    title = stringResource(R.string.settings_default_tab),
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
                    title = stringResource(R.string.settings_subscriptions_list_order),
                    options =
                        listOf(
                            stringResource(R.string.settings_watch_time) to "watchtime",
                            stringResource(R.string.settings_alphabetical) to "alphabetical",
                            stringResource(R.string.settings_random) to "random",
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
                    title = stringResource(R.string.settings_recency),                    options =
                        listOf(
                            stringResource(R.string.settings_1_week) to "1w",
                            stringResource(R.string.settings_1_month) to "1m",
                            stringResource(R.string.settings_3_months) to "3m",
                            stringResource(R.string.settings_6_months) to "6m",
                            stringResource(R.string.settings_1_year) to "1y",
                            stringResource(R.string.settings_3_years) to "3y",
                            stringResource(R.string.settings_any_time) to "any",
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
                    title = stringResource(R.string.settings_font),
                    options =
                        listOf(
                            stringResource(R.string.settings_system) to "DEFAULT",
                            stringResource(R.string.settings_inter) to "INTER",
                            stringResource(R.string.settings_roboto) to "ROBOTO",
                            stringResource(R.string.settings_open_sans) to "OPEN_SANS",
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
                    title = stringResource(R.string.settings_tint_blend_mode),
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
                    title = stringResource(R.string.settings_default_playback_speedup),
                    options =
                        listOf(
                            stringResource(R.string.settings_1_5x) to "1.5",
                            stringResource(R.string.settings_2x) to "2.0",
                            stringResource(R.string.settings_3x) to "3.0",
                            stringResource(R.string.settings_4x) to "4.0",
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
                    title = stringResource(R.string.settings_speedup_gesture_speed),
                    options =
                        listOf(
                            stringResource(R.string.settings_low) to "0.5",
                            stringResource(R.string.settings_normal) to "1.0",
                            stringResource(R.string.settings_high) to "1.5",
                            stringResource(R.string.settings_very_high) to "2.0",
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
                    title = stringResource(R.string.settings_time_jump_step),
                    options =
                        listOf(
                            stringResource(R.string.settings_1s) to "1",
                            stringResource(R.string.settings_3s) to "3",
                            stringResource(R.string.settings_5s) to "5",
                            stringResource(R.string.settings_10s) to "10",
                            stringResource(R.string.settings_30s) to "30",
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
                    title = stringResource(R.string.settings_default_video_resolution),
                    options =
                        listOf(
                            stringResource(R.string.settings_auto) to "Auto",
                            stringResource(R.string.settings_144p) to "144p",
                            stringResource(R.string.settings_360p) to "360p",
                            stringResource(R.string.settings_480p) to "480p",
                            stringResource(R.string.settings_720p) to "720p",
                            stringResource(R.string.settings_1080p) to "1080p",
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
                    title = stringResource(R.string.settings_backwards_jump_step),
                    options =
                        listOf(
                            stringResource(R.string.settings_5s) to "5",
                            stringResource(R.string.settings_10s) to "10",
                            stringResource(R.string.settings_15s) to "15",
                            stringResource(R.string.settings_20s) to "20",
                            stringResource(R.string.settings_30s) to "30",
                            stringResource(R.string.settings_45s) to "45",
                            stringResource(R.string.settings_60s) to "60",
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
                    title = stringResource(R.string.settings_forwards_jump_step),
                    options =
                        listOf(
                            stringResource(R.string.settings_5s) to "5",
                            stringResource(R.string.settings_10s) to "10",
                            stringResource(R.string.settings_15s) to "15",
                            stringResource(R.string.settings_20s) to "20",
                            stringResource(R.string.settings_30s) to "30",
                            stringResource(R.string.settings_45s) to "45",
                            stringResource(R.string.settings_60s) to "60",
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
                    title = stringResource(R.string.settings_default_download_resolution),
                    options =
                        listOf(
                            stringResource(R.string.settings_1080p) to "1080p",
                            stringResource(R.string.settings_720p) to "720p",
                            stringResource(R.string.settings_480p) to "480p",
                            stringResource(R.string.settings_360p) to "360p",
                            stringResource(R.string.settings_144p) to "144p",
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

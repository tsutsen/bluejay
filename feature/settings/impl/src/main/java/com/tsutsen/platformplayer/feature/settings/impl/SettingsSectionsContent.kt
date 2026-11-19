package com.tsutsen.platformplayer.feature.settings.impl

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.tsutsen.platformplayer.core.ui.R
import com.tsutsen.platformplayer.feature.settings.impl.ThumbnailPreviewRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.tsutsen.platformplayer.core.designsystem.icon.Download
import com.tsutsen.platformplayer.core.designsystem.icon.DualScreen
import com.tsutsen.platformplayer.core.designsystem.icon.SplitscreenBottom
import com.tsutsen.platformplayer.core.designsystem.icon.VideoTemplate
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.datastore.model.HiddenContentEntry
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.component.BluejayModalBottomSheet
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.SettingsOptionCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsSliderCard
import com.tsutsen.platformplayer.core.designsystem.component.SettingsSwitchCard
import com.tsutsen.platformplayer.core.designsystem.component.groupShape
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.layout.bluejayNavItems
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.PlayerControllerActions
import com.tsutsen.platformplayer.core.model.PlayerGestures
import com.tsutsen.platformplayer.core.model.PlaylistOption
import com.tsutsen.platformplayer.core.model.SourceInfo
import com.tsutsen.platformplayer.core.ui.AsyncImage
import kotlin.math.roundToInt

/** The cards belonging to one section. */
@Composable
internal fun SectionItems(
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
                        title = stringResource(R.string.settings_mode),
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
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_material_you_colors_from_wallpaper),
                        checked = state.prefs.appearance.dynamicColor,
                        onCheckedChange = { viewModel.updateGeneral("dynamicColor", it) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSliderCard(
                        icon = Icons.Filled.RoundedCorner,
                        title = stringResource(R.string.settings_ui_rounding),
                        subtitle = stringResource(R.string.settings_corner_radius_across_the_app),
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
                        title = stringResource(R.string.settings_font),
                        subtitle = fontFamilyLabel(state.prefs.appearance.fontFamily),
                        onClick = { onChoiceSelected(Choice.FONT) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSliderCard(
                        icon = Icons.Filled.FormatSize,
                        title = stringResource(R.string.settings_font_size),
                        subtitle = stringResource(R.string.settings_scales_all_text_1_s, (state.prefs.appearance.fontSizeScale * 100).roundToInt()),
                        value = state.prefs.appearance.fontSizeScale * 100f,
                        valueRange = 80f..140f,
                        onValueChange = { viewModel.setFontSizeScale(it / 100f) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.GridOn,
                        title = stringResource(R.string.settings_grid_columns),
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
                    // Live sample row: the thumbnail pipeline (tint, strength,
                    // blend, desaturate, contrast) applies to its image via the
                    // composition locals, so every control below updates it.
                    ThumbnailPreviewRow(groupPosition = GroupPosition.First)
                    SettingsOptionCard(
                        icon = Icons.Filled.Palette,
                        title = stringResource(R.string.settings_tint),
                        subtitle =
                            state.prefs.appearance.thumbnailTintArgb?.let { tintPreviewLabel(it) }
                                ?: "None",
                        onClick = { onChoiceSelected(Choice.THUMBNAIL_TINT) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSliderCard(
                        icon = Icons.Filled.BorderStyle,
                        title = stringResource(R.string.settings_tint_strength),
                        subtitle = stringResource(R.string.settings_how_much_of_the_tint_mixes_into_thumbnails),
                        value = state.prefs.appearance.thumbnailTintStrength * 100f,
                        valueRange = 0f..100f,
                        onValueChange = { viewModel.setThumbnailTintStrength(it / 100f) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Layers,
                        title = stringResource(R.string.settings_tint_blend_mode),
                        subtitle = tintBlendLabel(state.prefs.appearance.thumbnailTintBlendMode),
                        onClick = { onChoiceSelected(Choice.THUMBNAIL_TINT_BLEND) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSliderCard(
                        icon = Icons.Filled.BrightnessAuto,
                        title = stringResource(R.string.settings_desaturate),
                        subtitle = stringResource(R.string.settings_color_intensity_of_every_thumbnail),
                        value = (1f - state.prefs.appearance.thumbnailSaturation) * 100f,
                        valueRange = 0f..100f,
                        onValueChange = { viewModel.setThumbnailSaturation(1f - it / 100f) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSliderCard(
                        icon = Icons.Filled.Contrast,
                        title = stringResource(R.string.settings_thumbnail_contrast),
                        subtitle = stringResource(R.string.settings_thumbnail_contrast_desc),
                        value = state.prefs.appearance.thumbnailContrast * 100f,
                        valueRange = 0f..200f,
                        onValueChange = { viewModel.setThumbnailContrast(it / 100f) },
                        groupPosition = GroupPosition.Last,
                    )
                }
            }

            "content" -> {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader("Plugins")
                    SettingsOptionCard(
                        icon = Icons.Filled.Extension,
                        title = stringResource(R.string.settings_plugins),
                        subtitle = stringResource(R.string.settings_manage_installed_source_plugins),
                        onClick = onPluginsClick,
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.SystemUpdateAlt,
                        title = stringResource(R.string.settings_auto_update_plugins),
                        subtitle = stringResource(R.string.settings_check_for_and_install_plugin_updates_on_launch),
                        checked = state.prefs.autoUpdatePlugins,
                        onCheckedChange = { viewModel.updateGeneral("autoUpdatePlugins", it) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = stringResource(R.string.settings_video_page),
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
                        title = stringResource(R.string.settings_autoplay),
                        subtitle = stringResource(R.string.settings_play_the_next_video_in_the_queue_when_the_current_one_ends),
                        checked = state.prefs.playback.autoPlay,
                        onCheckedChange = { viewModel.setAutoplay(it) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSwitchCard(
                        icon = VideoTemplate,
                        title = stringResource(R.string.settings_show_recommended_videos),
                        subtitle = stringResource(R.string.settings_recommended_tab_on_the_video_page),
                        checked = state.prefs.showRecommendedVideos,
                        onCheckedChange = { viewModel.updateGeneral("showRecommendedVideos", it) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.Chat,
                        title = stringResource(R.string.settings_show_comments),
                        subtitle = stringResource(R.string.settings_comments_tab_on_the_video_page),
                        checked = state.prefs.showComments,
                        onCheckedChange = { viewModel.updateGeneral("showComments", it) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = stringResource(R.string.settings_library),
                        reset =
                            if (
                                state.prefs.librarySectionOrder != defaults.librarySectionOrder ||
                                state.prefs.librarySectionsEnabled != defaults.librarySectionsEnabled ||
                                state.prefs.other.watchLaterAutoCleanup != defaults.other.watchLaterAutoCleanup
                            ) {
                                {
                                    viewModel.setLibrarySectionOrder(defaults.librarySectionOrder)
                                    viewModel.setLibrarySectionsEnabled(
                                        defaults.librarySectionsEnabled,
                                    )
                                    viewModel.setWatchLaterAutoCleanup(defaults.other.watchLaterAutoCleanup)
                                }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.VideoLibrary,
                        title = stringResource(R.string.settings_library_sections),
                        subtitle = stringResource(R.string.settings_order_and_visibility_of_sections_on_the_library_screen),
                        onClick = { onChoiceSelected(Choice.LIBRARY_SECTION_ORDER) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.AutoDelete,
                        title = stringResource(R.string.settings_watch_later_cleanup),
                        subtitle = stringResource(R.string.settings_watch_later_cleanup_desc),
                        checked = state.prefs.other.watchLaterAutoCleanup,
                        onCheckedChange = { viewModel.setWatchLaterAutoCleanup(it) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = stringResource(R.string.settings_home_feed),
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
                        title = stringResource(R.string.settings_show_watched_videos),
                        subtitle = stringResource(R.string.settings_hide_fully_watched_videos_from_the_home_feed),
                        checked = state.prefs.showWatchedVideos,
                        onCheckedChange = { viewModel.setShowWatchedVideos(it) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.History,
                        title = stringResource(R.string.settings_show_started_videos),
                        subtitle = stringResource(R.string.settings_hide_partially_watched_videos_from_the_home_feed),
                        checked = state.prefs.showStartedVideos,
                        onCheckedChange = { viewModel.setShowStartedVideos(it) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader("Recommendations")
                    SettingsSwitchCard(
                        icon = Icons.Filled.RssFeed,
                        title = stringResource(R.string.settings_homebrew_recommendations),
                        subtitle = stringResource(R.string.settings_build_the_home_feed_from_the_sources_below),
                        checked = state.prefs.homebrewRecommendations,
                        onCheckedChange = { viewModel.setHomebrewRecommendations(it) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.PlayCircle,
                        title = stringResource(R.string.settings_source_picks),
                        subtitle = stringResource(R.string.settings_home_feed_from_your_sources),
                        checked = state.prefs.sourceYoutubeFeed,
                        onCheckedChange = { viewModel.setSourceYoutubeFeed(it) },
                        enabled = state.prefs.homebrewRecommendations,
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.Subscriptions,
                        title = stringResource(R.string.settings_subscriptions),
                        subtitle = stringResource(R.string.settings_recent_unwatched_videos_from_subscribed_channels),
                        checked = state.prefs.sourceSubscriptions,
                        onCheckedChange = { viewModel.setSourceSubscriptions(it) },
                        enabled = state.prefs.homebrewRecommendations,
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsSwitchCard(
                        icon = Icons.Filled.Groups,
                        title = stringResource(R.string.settings_top_creators),
                        subtitle = stringResource(R.string.settings_recent_unwatched_videos_from_creators_you_watch_most),
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
                        title = stringResource(R.string.settings_recency),
                        subtitle = recencyLabel(state.prefs.recency),
                        onClick = { onChoiceSelected(Choice.RECENCY) },
                        enabled = state.prefs.homebrewRecommendations,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.VisibilityOff,
                        title = stringResource(R.string.settings_hidden_content),
                        subtitle = stringResource(R.string.settings_videos_and_creators_you_hid_or_reduced),
                        onClick = onHiddenContentClick,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = stringResource(R.string.settings_dock),
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
                        title = stringResource(R.string.settings_tab_order),
                        subtitle = stringResource(R.string.settings_drag_to_reorder_the_bottom_navigation_tabs),
                        onClick = { onChoiceSelected(Choice.DOCK_TAB_ORDER) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.PlayCircle,
                        title = stringResource(R.string.settings_default_tab),
                        subtitle =
                            bluejayNavItems.firstOrNull { it.key == state.prefs.defaultDockTab }?.label
                                ?: "Home",
                        onClick = { onChoiceSelected(Choice.DEFAULT_DOCK_TAB) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = stringResource(R.string.settings_subscriptions),
                        reset =
                            if (state.prefs.subscriptionsOrder != defaults.subscriptionsOrder) {
                                { viewModel.setSubscriptionsOrder(defaults.subscriptionsOrder) }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Subscriptions,
                        title = stringResource(R.string.settings_list_order),
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
                        title = stringResource(R.string.settings_subtitles),
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
                        title = stringResource(R.string.settings_subtitle_font),
                        subtitle = subtitleFontLabel(state.prefs.subtitle.font),
                        onClick = { onChoiceSelected(Choice.SUBTITLE_FONT) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.TextFields,
                        title = stringResource(R.string.settings_subtitle_size),
                        subtitle = "${state.prefs.subtitle.size} pt",
                        onClick = { onChoiceSelected(Choice.SUBTITLE_SIZE) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.VerticalAlignBottom,
                        title = stringResource(R.string.settings_subtitle_bottom_padding),
                        subtitle = "${state.prefs.subtitle.bottomPadding} dp",
                        onClick = { onChoiceSelected(Choice.SUBTITLE_PADDING) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.BorderStyle,
                        title = stringResource(R.string.settings_subtitle_outline),
                        subtitle = "${state.prefs.subtitle.outline} px",
                        onClick = { onChoiceSelected(Choice.SUBTITLE_OUTLINE) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = stringResource(R.string.settings_quality),
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
                        title = stringResource(R.string.settings_default_video_resolution),
                        subtitle = state.prefs.defaultVideoResolution,
                        onClick = { onChoiceSelected(Choice.VIDEO_RESOLUTION) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Download,
                        title = stringResource(R.string.settings_default_download_resolution),
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
                        title = stringResource(R.string.settings_default_playback_speedup),
                        subtitle = "${state.prefs.defaultSpeedup}x",
                        onClick = { onChoiceSelected(Choice.PLAYBACK_SPEED) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Speed,
                        title = stringResource(R.string.settings_speedup_gesture_speed),
                        subtitle = "${state.prefs.speedupSensitivity}x",
                        onClick = { onChoiceSelected(Choice.SPEEDUP_SENSITIVITY) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Timer,
                        title = stringResource(R.string.settings_time_jump_step),
                        subtitle = "${state.prefs.jumpStepSeconds}s",
                        onClick = { onChoiceSelected(Choice.JUMP_STEP) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader("Customization")
                    SettingsOptionCard(
                        icon = Icons.Filled.Gesture,
                        title = stringResource(R.string.settings_fullscreen_player_gestures),
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
                        title = stringResource(R.string.settings_normal_player_gestures),
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
                                        Text(stringResource(R.string.settings_reset_to_defaults))
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
                                text = stringResource(R.string.settings_each_zone_in_a_player_can_have_its_unique_gestures),
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
                        title = stringResource(R.string.settings_dual_screen),
                        subtitle = stringResource(R.string.settings_show_video_controls_on_the_second_screen),
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
                    SettingsSwitchCard(
                        icon = Icons.Filled.SwapVert,
                        title = stringResource(R.string.settings_dual_swap),
                        subtitle = stringResource(R.string.settings_dual_swap_desc),
                        checked = state.prefs.dualScreenSwapDisplays,
                        onCheckedChange = { viewModel.updateGeneral("dualScreenSwapDisplays", it) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Layers,
                        title = stringResource(R.string.settings_pages),
                        subtitle = dualListLabel(state.prefs.dualScreenPages, dualPageNames),
                        onClick = { onChoiceSelected(Choice.DUAL_PAGES) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = stringResource(R.string.settings_dash_page),
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
                        title = stringResource(R.string.settings_top_creators),
                        subtitle =
                            if (state.prefs.dualScreenTopCreatorsScope == "overall") "Overall"
                            else "This week",
                        onClick = { onChoiceSelected(Choice.DASH_TOP_CREATORS) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Reorder,
                        title = stringResource(R.string.settings_dash_page_order),
                        subtitle = dualOrderLabel(state.prefs.dualScreenDashPageOrder, dashPageOrderNames),
                        onClick = { onChoiceSelected(Choice.DASH_PAGE_ORDER) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = stringResource(R.string.settings_video_page),
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
                        title = stringResource(R.string.settings_video_tabs),
                        subtitle = dualOrderLabel(state.prefs.dualScreenVideoTabOrder, dualTabNames),
                        onClick = { onChoiceSelected(Choice.DUAL_TAB_ORDER) },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = SplitscreenBottom,
                        title = stringResource(R.string.settings_video_page_order),
                        subtitle = dualOrderLabel(state.prefs.dualScreenPageOrder, dualPageOrderNames),
                        onClick = { onChoiceSelected(Choice.DUAL_PAGE_ORDER) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = stringResource(R.string.settings_playlists_page),
                        reset =
                            if (state.prefs.dualScreenLibrarySlots != defaults.dualScreenLibrarySlots) {
                                { viewModel.setDualScreenLibrarySlots(defaults.dualScreenLibrarySlots) }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.VideoLibrary,
                        title = stringResource(R.string.settings_library_slots),
                        subtitle = slotListLabel(state.prefs.dualScreenLibrarySlots, playlists),
                        onClick = { onChoiceSelected(Choice.DUAL_SLOTS) },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = stringResource(R.string.settings_home_page),
                        reset =
                            if (state.prefs.dualScreenFeedSources.isNotEmpty()) {
                                { viewModel.setDualScreenFeedSources(emptyList()) }
                            } else {
                                null
                            },
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.RssFeed,
                        title = stringResource(R.string.settings_feed_sources),
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
                        title = stringResource(R.string.settings_use_controller),
                        subtitle = stringResource(R.string.settings_control_playback_with_a_gamepad_or_tv_remote),
                        checked = state.prefs.controller.enabled,
                        onCheckedChange = { enabled ->
                            viewModel.setController(state.prefs.controller.copy(enabled = enabled))
                        },
                        groupPosition = GroupPosition.First,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Replay10,
                        title = stringResource(R.string.settings_backwards_jump_step),
                        subtitle = "${state.prefs.controller.seekBackSeconds}s",
                        onClick = { onChoiceSelected(Choice.CONTROLLER_SEEK_BACK) },
                        groupPosition = GroupPosition.Middle,
                    )
                    SettingsOptionCard(
                        icon = Icons.Filled.Forward30,
                        title = stringResource(R.string.settings_forwards_jump_step),
                        subtitle = "${state.prefs.controller.seekForwardSeconds}s",
                        onClick = { onChoiceSelected(Choice.CONTROLLER_SEEK_FORWARD) },
                        groupPosition = GroupPosition.Last,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                    SettingsHeader(
                        title = stringResource(R.string.settings_buttons),
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
internal fun SpawnProbabilityCard(
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
                text = stringResource(R.string.settings_spawn_probability),
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

/** Subdued hint shown when a Hidden content section is empty. */
@Composable
internal fun EmptyHiddenContentHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Tokens.SpaceSm),
    )
}

/** One Hidden content entry: art, name, url, and a probability tier dropdown. */
@Composable
internal fun HiddenContentRow(
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

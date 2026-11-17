package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.PlaylistOption

internal fun sectionTitle(category: String): String =
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

/**
 * Small subsection title above a group of settings cards. Pass [reset] to
 * show a right-aligned "Reset to defaults" action (only pass it when the
 * subsection actually deviates from the defaults).
 */
@Composable
internal fun SettingsHeader(
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

/** Grouped-card rounding for a row at [index] within a [size]-item group. */
internal fun groupPositionFor(
    index: Int,
    size: Int,
): GroupPosition =
    when {
        size == 1 -> GroupPosition.Single
        index == 0 -> GroupPosition.First
        index == size - 1 -> GroupPosition.Last
        else -> GroupPosition.Middle
    }

internal enum class Choice {
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

/** Canonical defaults — used to decide which subsections deviate. */
internal val defaults = AppPreferences()

/** Recency option label shown as the Recency row's subtitle. */
internal fun recencyLabel(recency: String): String =
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
internal fun fontFamilyLabel(family: com.tsutsen.platformplayer.core.datastore.model.FontFamily): String =
    when (family) {
        com.tsutsen.platformplayer.core.datastore.model.FontFamily.INTER -> "Inter"
        com.tsutsen.platformplayer.core.datastore.model.FontFamily.ROBOTO -> "Roboto"
        com.tsutsen.platformplayer.core.datastore.model.FontFamily.OPEN_SANS -> "Open Sans"
        com.tsutsen.platformplayer.core.datastore.model.FontFamily.DEFAULT -> "System"
    }

/** "Tint" row subtitle: the picked color as hex. */
internal fun tintPreviewLabel(argb: Long): String {
    val hex = Integer.toHexString(((argb and 0x00FFFFFFL).toInt())).uppercase().padStart(6, '0')
    return "#$hex"
}

/** "Tint blend mode" row subtitle. */
internal fun tintBlendLabel(mode: String): String =
    when (mode) {
        com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.MULTIPLY.name -> "Multiply"
        com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.SCREEN.name -> "Screen"
        com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.OVERLAY.name -> "Overlay"
        com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.SOFT_LIGHT.name -> "Soft light"
        com.tsutsen.platformplayer.core.datastore.model.TintBlendMode.COLOR_BURN.name -> "Color burn"
        else -> "Wash"
    }

internal fun subtitleFontLabel(code: String): String =
    when (code) {
        "sans" -> "Sans-serif"
        "serif" -> "Serif"
        "mono" -> "Monospace"
        else -> "Default"
    }

internal val dualPageNames =
    mapOf(
        "video" to "Video page",
        "library" to "Library page",
        "home" to "Home page",
        "dash" to "Dash page",
    )

internal val dualTabNames =
    mapOf(
        "info" to "Info",
        "controls" to "Controls",
        "comments" to "Comments",
        "chapters" to "Chapters",
        "recommended" to "Recommended",
        "queue" to "Queue",
        "dot" to "·",
    )

internal val dualPageOrderNames =
    mapOf(
        "controls" to "Controls",
        "video" to "Video",
        "tabs" to "Tabs",
    )

internal val dashPageOrderNames =
    mapOf(
        "stats" to "Stats",
        "top_creators" to "Top creators",
        "continue" to "Continue",
    )

internal val librarySectionNames =
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
internal fun slotLabel(
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
internal fun slotListLabel(
    slots: List<String>,
    playlists: List<PlaylistOption>,
): String =
    (0 until 4).joinToString(", ") {
        slotLabel(slots.getOrNull(it) ?: "", librarySectionNames, playlists)
    }

internal fun dualListLabel(
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
internal fun dualOrderLabel(
    order: List<String>,
    names: Map<String, String>,
): String = order.joinToString(", ") { names[it] ?: it }

@Composable
internal fun BackIconButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
        )
    }
}

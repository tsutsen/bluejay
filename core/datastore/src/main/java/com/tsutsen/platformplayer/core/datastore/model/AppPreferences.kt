package com.tsutsen.platformplayer.core.datastore.model

data class AppPreferences(
    val appearance: AppearancePreferences = AppearancePreferences(),
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val subtitle: SubtitlePreferences = SubtitlePreferences(),
    val defaultPlaybackSpeed: Float = 1f,
    val enableDeveloperOptions: Boolean = false,
    val dualScreen: Boolean = false,
    val gridColumns: Int = 3,
    val searchHistory: List<String> = emptyList(),
    val showRecommendedVideos: Boolean = true,
    val showComments: Boolean = true,
)

/**
 * Subtitle appearance for the in-player caption overlay.
 * Values are simple strings so the settings UI can offer fixed choices:
 *  - font: "default" | "sans" | "serif" | "mono"
 *  - size: "small" | "standard" | "large"
 *  - bottomPadding: "tight" | "standard" | "wide"
 */
data class SubtitlePreferences(
    val font: String = "default",
    val size: String = "standard",
    val bottomPadding: String = "standard",
)

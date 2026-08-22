package com.tsutsen.platformplayer.core.datastore.model

data class AppPreferences(
    val appearance: AppearancePreferences = AppearancePreferences(),
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val subtitle: SubtitlePreferences = SubtitlePreferences(),
    val defaultPlaybackSpeed: Float = 1f,
    val enableDeveloperOptions: Boolean = false,
    val librarySectionOrder: List<String> =
        listOf("watch_later", "liked", "disliked", "favourite", "history", "downloads", "playlists"),
    val dualScreen: Boolean = false,
    val dualScreenPages: List<String> = listOf("video", "library", "home"),
    val dualScreenVideoTabs: List<String> = listOf("comments", "chapters", "recommended", "queue"),
    val dualScreenLibrarySlots: List<String> =
        listOf("watch_later", "liked", "favourite", "history"),
    val gridColumns: Int = 3,
    val searchHistory: List<String> = emptyList(),
    val showRecommendedVideos: Boolean = true,
    val showComments: Boolean = true,
)

/**
 * Subtitle appearance for the in-player caption overlay.
 *  - font: "default" | "sans" | "serif" | "mono"
 *  - size: font size in pt
 *  - bottomPadding: gap below the video's bottom edge, in dp
 */
data class SubtitlePreferences(
    val font: String = "default",
    val size: Int = 16,
    val bottomPadding: Int = 20,
)

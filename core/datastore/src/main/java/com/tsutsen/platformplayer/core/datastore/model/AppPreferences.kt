package com.tsutsen.platformplayer.core.datastore.model

data class AppPreferences(
    val appearance: AppearancePreferences = AppearancePreferences(),
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val subtitle: SubtitlePreferences = SubtitlePreferences(),
    val defaultPlaybackSpeed: Float = 1f,
    val defaultSpeedup: Float = 2f,
    val speedupSensitivity: Float = 1f,
    val playerGestures: PlayerGesturePreferences = PlayerGesturePreferences(),
    val defaultVideoResolution: String = "Auto",
    val defaultDownloadResolution: String = "480p",
    val enableDeveloperOptions: Boolean = false,
    val librarySectionOrder: List<String> =
        listOf("watch_later", "liked", "disliked", "favourite", "history", "downloads", "playlists"),
    val dualScreen: Boolean = false,
    val dualScreenPages: List<String> = listOf("video", "library", "home"),
    val dualScreenVideoTabs: List<String> =
        listOf("info", "controls", "comments", "chapters", "recommended", "queue", "dot"),
    /** Display order of the enabled video-page tabs (Settings > Dual screen). */
    val dualScreenVideoTabOrder: List<String> =
        listOf("info", "controls", "comments", "chapters", "recommended", "queue", "dot"),
    /** Order of the elements on the second screen's video page. */
    val dualScreenPageOrder: List<String> = listOf("controls", "video", "tabs"),
    val dualScreenFeedSources: List<String> = emptyList(),
    val dualScreenLibrarySlots: List<String> =
        listOf("watch_later", "liked", "favourite", "history"),
    val gridColumns: Int = 3,
    val homeHiddenSources: List<String> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val showRecommendedVideos: Boolean = true,
    val showComments: Boolean = true,
    val autoUpdatePlugins: Boolean = true,
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

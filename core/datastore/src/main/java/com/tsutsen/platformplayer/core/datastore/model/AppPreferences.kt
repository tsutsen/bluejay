package com.tsutsen.platformplayer.core.datastore.model

data class AppPreferences(
    val appearance: AppearancePreferences = AppearancePreferences(),
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val subtitle: SubtitlePreferences = SubtitlePreferences(),
    val defaultPlaybackSpeed: Float = 1f,
    val defaultSpeedup: Float = 2f,
    val speedupSensitivity: Float = 1f,
    /** Jump step (seconds) for the back/forward seek gestures. */
    val jumpStepSeconds: Int = 5,
    val playerGestures: PlayerGesturePreferences = PlayerGesturePreferences(),
    val controller: ControllerPreferences = ControllerPreferences(),
    val defaultVideoResolution: String = "Auto",
    val defaultDownloadResolution: String = "480p",
    val enableDeveloperOptions: Boolean = false,
    val librarySectionOrder: List<String> =
        listOf("watch_later", "liked", "disliked", "favourite", "history", "downloads", "playlists"),
    val librarySectionsEnabled: List<String> =
        listOf("watch_later", "liked", "disliked", "favourite", "history", "downloads", "playlists"),
    val dualScreen: Boolean = false,
    // Dash first: the companion lands on the dash page and flips to the
    // video page when a video opens.
    val dualScreenPages: List<String> = listOf("dash", "video", "library", "home"),
    val dualScreenVideoTabs: List<String> =
        listOf("info", "controls", "comments", "chapters", "recommended", "queue", "dot"),
    /** Display order of the enabled video-page tabs (Settings > Dual screen). */
    val dualScreenVideoTabOrder: List<String> =
        listOf("info", "controls", "comments", "chapters", "recommended", "queue", "dot"),
    /** Order of the elements on the second screen's video page. */
    val dualScreenPageOrder: List<String> = listOf("controls", "video", "tabs"),
    /** Dash page: "Top creators" scope — "week" (this week) or "overall". */
    val dualScreenTopCreatorsScope: String = "week",
    /** Dash page: display order of the widgets. */
    val dualScreenDashPageOrder: List<String> =
        listOf("stats", "top_creators", "continue"),
    val dualScreenFeedSources: List<String> = emptyList(),
    val dualScreenLibrarySlots: List<String> =
        listOf("watch_later", "liked", "favourite", "history"),
    val gridColumns: Int = 3,
    val homeHiddenSources: List<String> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val showRecommendedVideos: Boolean = true,
    val showComments: Boolean = true,
    /** Home feed: hide fully watched videos (history position >= 95% of duration). */
    val showWatchedVideos: Boolean = true,
    /** Home feed: hide started-but-unfinished videos (history position > 0). */
    val showStartedVideos: Boolean = true,
    /** Homebrew recommendations master toggle (Settings > Content > Recommendations). */
    val homebrewRecommendations: Boolean = false,
    /** Recommendation source: logged-in platform home feed. */
    val sourceYoutubeFeed: Boolean = true,
    /** Recommendation source: recent unwatched videos from subscriptions. */
    val sourceSubscriptions: Boolean = true,
    /** Recommendation source: recent unwatched videos from top creators. */
    val sourceTopCreators: Boolean = true,
    /** Spawn-probability cut point 1 (percent 0..100): segment boundary between the platform feed source and subscriptions. */
    val probCut1: Int = 33,
    /** Spawn-probability cut point 2 (percent 0..100, >= [probCut1]): segment boundary between subscriptions and top creators. */
    val probCut2: Int = 66,
    val autoUpdatePlugins: Boolean = true,
    val gettingStartedCompleted: Boolean = false,
    /** Source ids whose home-feed login notice was declined. */
    val homeLoginPromptsDismissed: List<String> = emptyList(),
    /** Dock tab keys in display order (empty/partial = default order, missing keys appended). */
    val dockTabOrder: List<String> =
        listOf("home", "search", "subscriptions", "library", "notifications", "settings"),
    /** Dock tab key the app starts on ("home" by default). */
    val defaultDockTab: String = "home",
    /** Subscriptions tab list order: "random" | "watchtime" | "alphabetical". */
    val subscriptionsOrder: String = "watchtime",
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
    val outline: Int = 3,
)

package com.tsutsen.platformplayer.core.model

/** Player display mode — replaces isFullscreen + isMinimized booleans. */
enum class PlayerMode {
    /** Full embedded player — tall enough for normal controls */
    NORMAL,
    /** Collapsed embedded player — only compact controls fit */
    COMPACT,
    /** Video fills the container, system bars hidden */
    FULLSCREEN,
    /** Mini floating player anchored to corner */
    FLOATING,
}

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 1.0f,
    val brightness: Float = 1.0f,
    val playbackSpeed: Float = 1.0f,
    val mode: PlayerMode = PlayerMode.NORMAL,
    val currentVideo: ContentItem? = null,
    val queue: List<ContentItem> = emptyList(),
    val selectedIndex: Int = 0,
    val error: String? = null,
    val isLoading: Boolean = false,
    val isCompleted: Boolean = false,
    val comments: List<CommentItem> = emptyList()
)

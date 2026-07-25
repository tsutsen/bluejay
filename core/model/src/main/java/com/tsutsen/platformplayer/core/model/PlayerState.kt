package com.tsutsen.platformplayer.core.model

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 1.0f,
    val brightness: Float = 1.0f,
    val playbackSpeed: Float = 1.0f,
    val isFullscreen: Boolean = false,
    val isMinimized: Boolean = false,
    val currentVideo: ContentItem? = null,
    val queue: List<ContentItem> = emptyList(),
    val selectedIndex: Int = 0,
    val error: String? = null
)

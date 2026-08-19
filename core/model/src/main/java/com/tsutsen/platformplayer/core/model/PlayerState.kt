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
    val error: String? = null,
    val isLoading: Boolean = false,
    val isCompleted: Boolean = false,
    val comments: List<CommentItem> = emptyList(),
    /** Recommended videos for the current video, fetched by PlayerRepository.play(). */
    val recommendations: List<Card> = emptyList(),
    /** Chapter list for the current video, fetched by PlayerRepository.play(). */
    val chapters: List<VideoChapter> = emptyList(),
    /** Available video track heights in pixels, descending (e.g. [2160, 1080, 720]). */
    val videoQualities: List<Int> = emptyList(),
    /** Available subtitle track language codes, in manifest order. */
    val subtitleLanguages: List<String> = emptyList(),
    /** UI label of the selected quality ("Auto" or "NNNp"). */
    val selectedQuality: String = "Auto",
    /** UI label of the selected subtitle ("Auto", "Off" or a language code). */
    val selectedSubtitle: String = "Auto",
    /** Text of the currently active subtitle cues (empty when none). */
    val subtitleText: String = "",
)

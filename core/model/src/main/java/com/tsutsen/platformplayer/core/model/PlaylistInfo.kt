package com.tsutsen.platformplayer.core.model

/**
 * Engine-agnostic playlist description.
 */
data class PlaylistInfo(
    val url: String,
    val name: String,
    val thumbnail: String? = null,
    val videoCount: Int? = null,
    val author: String? = null,
)

/** Video count + total duration of a local playlist (options sheet). */
data class PlaylistStats(
    val videoCount: Int,
    val totalDurationMs: Long,
)

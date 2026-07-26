package com.tsutsen.platformplayer.core.data.repository

/**
 * Lightweight video details for populating the player UI.
 * Decoupled from IPlatformVideoDetails to keep core:data free of app-module dependencies.
 */
data class VideoDetails(
    val id: String,
    val url: String,
    val title: String,
    val authorName: String?,
    val authorUrl: String?,
    val authorThumbnailUrl: String?,
    val thumbnailUrl: String?,
    val description: String?,
    val durationMs: Long?,
    val viewCount: Long?,
    val publishedAtMs: Long?
)

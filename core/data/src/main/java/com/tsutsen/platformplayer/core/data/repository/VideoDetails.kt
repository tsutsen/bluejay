package com.tsutsen.platformplayer.core.data.repository

import android.net.Uri

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
    val authorSubscriberCount: Long? = null,
    val thumbnailUrl: String?,
    val description: String?,
    val durationMs: Long?,
    val viewCount: Long?,
    val publishedAtMs: Long?,
    val likeCount: Long?,
    val dislikeCount: Long?,
    val isLive: Boolean = false,
    /** Plugin icon (file URI) for the channel badge — null with a single enabled source. */
    val sourceIconUrl: String? = null,
    val subtitles: List<SubtitleSource> = emptyList(),
    /** Selectable audio tracks (unmuxed streams: video + audio URLs). */
    val audioTracks: List<VideoAudioTrack> = emptyList(),
    /** Label of the audio track the resolver picked for initial playback. */
    val activeAudioTrack: String? = null,
    /** Video stream URL of the picked video source (re-merge on audio swap). */
    val videoStreamUrl: String? = null,
    /** Selectable resolutions (unmuxed streams: quality = which URL loads). */
    val videoStreams: List<VideoStreamOption> = emptyList(),
)

/**
 * A selectable audio track of an unmuxed (separate video + audio) stream.
 * Picking one rebuilds the merged media source around [url], so ExoPlayer
 * track selection does not apply to these.
 */
data class VideoAudioTrack(
    val id: String,
    val label: String,
    val language: String?,
    val url: String,
    val original: Boolean,
)

/**
 * One selectable resolution of an unmuxed (separate video + audio URL)
 * stream: quality there is not ABR — it is which video URL gets loaded.
 */
data class VideoStreamOption(
    val height: Int,
    val width: Int,
    val url: String,
)

/**
 * A subtitle track offered by the engine for a video.
 *
 * [contentUri] resolves the placeable subtitle location: either the
 * subtitle's direct URL or a local file the engine fetched on demand.
 */
data class SubtitleSource(
    val name: String,
    val format: String?,
    val contentUri: suspend () -> Uri?
)

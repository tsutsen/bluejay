package com.tsutsen.platformplayer.core.datastore.model

data class PlaybackPreferences(
    val autoPlay: Boolean = true,
    val defaultQuality: VideoQuality = VideoQuality.AUTO,
    val enableHardwareAcceleration: Boolean = true,
    val enableSubtitles: Boolean = true,
    val subtitleFontSize: Float = 1.0f,
    val skipSilence: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    /** Prefer the video's original audio track over a translated one. */
    val preferOriginalAudio: Boolean = false,
    /** Resume playback after audio-focus loss: 0 = off, 1 = if loss < 10s, 2 = if loss < 30s, 3 = always. */
    val restartPlaybackAfterLoss: Int = 0,
)

enum class VideoQuality { AUTO, LOW, MEDIUM, HIGH, ULTRA }

package com.futo.platformplayer.core.datastore.model

data class PlaybackPreferences(
    val autoPlay: Boolean = true,
    val defaultQuality: VideoQuality = VideoQuality.AUTO,
    val enableHardwareAcceleration: Boolean = true,
    val enableSubtitles: Boolean = true,
    val subtitleFontSize: Float = 1.0f,
    val skipSilence: Boolean = false,
    val playbackSpeed: Float = 1.0f
)

enum class VideoQuality { AUTO, LOW, MEDIUM, HIGH, ULTRA }

package com.tsutsen.platformplayer.core.datastore.model

data class AppPreferences(
    val appearance: AppearancePreferences = AppearancePreferences(),
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val language: String = "en",
    val enableNotifications: Boolean = true,
    val enableBackgroundPlayback: Boolean = true,
    val enablePictureInPicture: Boolean = true,
    val confirmExit: Boolean = false,
    val enableDeveloperOptions: Boolean = false,
    val gridColumns: Int = 3,
    val searchHistory: List<String> = emptyList(),
)

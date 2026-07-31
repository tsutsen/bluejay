package com.tsutsen.platformplayer.core.designsystem.component

import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.PlaybackPreferences

/**
 * Bundles all settings preferences into a single value for category screens.
 */
data class SettingsData(
    val appearance: AppearancePreferences,
    val playback: PlaybackPreferences,
    val language: String,
    val enableNotifications: Boolean,
    val enableBackgroundPlayback: Boolean,
    val enablePictureInPicture: Boolean,
    val confirmExit: Boolean,
    val enableDeveloperOptions: Boolean
)

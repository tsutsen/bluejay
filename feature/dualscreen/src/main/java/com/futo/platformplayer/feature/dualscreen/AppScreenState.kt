package com.futo.platformplayer.feature.dualscreen

/**
 * Sealed class representing the possible states of the app across both displays.
 * Used for cross-activity state coordination between MainActivity and CompanionActivity.
 */
sealed class AppState {
    data object Browsing : AppState()
    data class VideoOpen(val videoId: String, val isFullscreen: Boolean = false) : AppState()
    data class VideoMinimized(val videoId: String, val positionX: Float = 0f, val positionY: Float = 0f) : AppState()
}

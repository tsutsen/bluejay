package com.tsutsen.platformplayer.core.data.repository

import kotlinx.coroutines.flow.StateFlow

/** State of a manual "Check for updates" run (Settings > About). */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpToDate(val versionName: String) : UpdateCheckState
    /** A newer release was found — offered, download NOT started yet. */
    data class Available(val tag: String, val versionCode: Int) : UpdateCheckState
    /** User confirmed; the APK is downloading. */
    data class Downloading(val tag: String, val versionCode: Int) : UpdateCheckState
    /** Download finished; install via the notification. */
    data class Ready(val tag: String) : UpdateCheckState
    data class Failed(val message: String) : UpdateCheckState
}

/**
 * App self-update via GitHub releases. Implemented in the app module
 * (wraps StateUpdate / UpdateDownloadService), bound via Hilt.
 */
interface UpdateRepository {
    val checkState: StateFlow<UpdateCheckState>

    /** Installed versionName (e.g. v0.6.1-78-g…), for display. */
    val currentVersionName: String

    /** Checks the latest GitHub release against the installed versionCode. */
    fun checkForUpdates()

    /** Starts the APK download for the currently offered [UpdateCheckState.Available]. */
    fun downloadUpdate()

    /** Re-attempts the install of a downloaded update ([UpdateCheckState.Ready]). */
    fun installUpdate()
}

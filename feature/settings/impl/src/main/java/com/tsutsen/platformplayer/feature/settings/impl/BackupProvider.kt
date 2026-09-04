package com.tsutsen.platformplayer.feature.settings.impl

/**
 * Counts for the "Backup & restore" section tiles.
 */
data class BackupSummary(
    val subscriptions: Int = 0,
    val sources: Int = 0,
    val savedVideos: Int = 0,
    val savedPlaylists: Int = 0,
    val customSettings: Int = 0,
    val sections: Int = 0,
)

/**
 * Seam for the "Backup & restore" section. [summary] reports on demand what
 * a backup will contain (nothing runs in the background), [exportBackup] and
 * [importBackup] drive the file pickers. The app module implements this on
 * top of the app's backup engine (StateBackup), which the feature module
 * can't reference directly.
 */
interface BackupProvider {
    suspend fun summary(): BackupSummary

    fun exportBackup()

    fun importBackup()
}

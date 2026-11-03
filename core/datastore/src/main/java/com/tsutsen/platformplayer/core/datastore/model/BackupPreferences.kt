package com.tsutsen.platformplayer.core.datastore.model

import java.time.OffsetDateTime

data class BackupPreferences(
    /** Legacy stub getter returned a constant false (auto-backup toggle was removed with the old settings UI). */
    val shouldAutomaticBackup: Boolean = false,
    /** Whether the "set up auto-backup" prompt was answered (never shown again). */
    val didAskAutoBackup: Boolean = false,
    /** Time of the last automatic backup. */
    val lastAutoBackupTime: OffsetDateTime = OffsetDateTime.now(),
)

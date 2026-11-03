package com.tsutsen.platformplayer.core.datastore.model

data class AutoUpdatePreferences(
    /** Legacy stub getter returned a constant true (auto-update can no longer be disabled). */
    val isAutoUpdateEnabled: Boolean = true,
    /** Download app updates in the background. */
    val shouldBackgroundDownload: Boolean = true,
)

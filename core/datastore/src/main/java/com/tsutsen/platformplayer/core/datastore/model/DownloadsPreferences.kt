package com.tsutsen.platformplayer.core.datastore.model

data class DownloadsPreferences(
    /** Download progressive sources via parallel byte ranges when the server supports them. */
    val byteRangeDownload: Boolean = true,
    /** Legacy stub getter returned a constant true (network gating was removed with the old settings UI). */
    val shouldDownload: Boolean = true,
    /** Legacy stub getter returned a constant 4. */
    val byteRangeThreadCount: Int = 4,
)

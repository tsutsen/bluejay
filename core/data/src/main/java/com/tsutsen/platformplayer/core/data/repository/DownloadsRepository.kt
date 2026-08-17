package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.DownloadInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Video downloads, surfaced as a Library section ("Downloads").
 * Implementations bridge to the engine-level download machinery
 * (StateDownloads + DownloadService in the app module).
 */
interface DownloadsRepository {
    /** Completed + in-progress downloads. Emits on every download state change. */
    val downloads: StateFlow<List<DownloadInfo>>

    /**
     * Enqueue a download for [videoUrl].
     * @return an error message, or null if the download was started.
     */
    suspend fun startDownload(videoUrl: String): String?
}

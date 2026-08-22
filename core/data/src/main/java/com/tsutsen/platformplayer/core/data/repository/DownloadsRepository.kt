package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.DownloadInfo
import com.tsutsen.platformplayer.core.model.DownloadQuality
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
     * @param videoQuality the quality to download at; null = the default
     *   mobile quality (144p / 64kbps).
     * @return an error message, or null if the download was started.
     */
    suspend fun startDownload(videoUrl: String, videoQuality: DownloadQuality? = null): String?

    /**
     * Cancel a queued/in-progress download for [videoUrl] and stop the
     * download service if the queue becomes empty.
     * @return an error message, or null if the download was cancelled.
     */
    suspend fun cancelDownload(videoUrl: String): String?

    /**
     * Delete the downloaded copy of [videoUrl] (record + media files).
     * @return an error message, or null if the deletion succeeded.
     */
    suspend fun deleteDownload(videoUrl: String): String?
}

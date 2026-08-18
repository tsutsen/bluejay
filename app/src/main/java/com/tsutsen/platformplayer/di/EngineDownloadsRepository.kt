package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.core.data.repository.DownloadsRepository
import com.tsutsen.platformplayer.core.model.DownloadInfo
import com.tsutsen.platformplayer.downloads.VideoDownload
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.services.DownloadService
import com.tsutsen.platformplayer.states.StateDownloads
import com.tsutsen.platformplayer.states.StatePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the engine download machinery ([StateDownloads] +
 * [DownloadService]) into the [DownloadsRepository] contract the
 * Library UI consumes. Refreshes the flow on every download event.
 */
@Singleton
class EngineDownloadsRepository
    @Inject
    constructor() : DownloadsRepository {
        private val _downloads = MutableStateFlow(emptyList<DownloadInfo>())
        override val downloads: kotlinx.coroutines.flow.StateFlow<List<DownloadInfo>> =
            _downloads.asStateFlow()

        /** Per-download progress subscriptions (tag = download, removed when it leaves the queue). */
        private val progressSubs = linkedMapOf<VideoDownload, Any>()

        init {
            refresh()
            StateDownloads.instance.onDownloadedChanged.subscribe { refresh() }
            StateDownloads.instance.onDownloadsChanged.subscribe { refresh() }
        }

        /**
         * Live progress: each queued [VideoDownload] emits onProgressChanged
         * ~every 200ms while its bytes are moving; follow them so the UI
         * (and the flow's consumers) see progress without waiting for a
         * state-change event.
         */
        private fun trackProgress() {
            val active = StateDownloads.instance.getDownloading().toSet()
            progressSubs.keys.toList().forEach { d ->
                if (d !in active) {
                    d.onProgressChanged.remove(progressSubs.remove(d)!!)
                }
            }
            active.forEach { d ->
                if (d !in progressSubs) {
                    val tag = Any()
                    d.onProgressChanged.subscribe(tag) { refresh() }
                    progressSubs[d] = tag
                }
            }
        }

        override suspend fun startDownload(videoUrl: String): String? =
            withContext(Dispatchers.IO) {
                try {
                    // Duplicate-queue guard: the engine's download() has
                    // no de-dupe of its own — repeated taps would queue
                    // the same video multiple times (each 0→100%).
                    if (StateDownloads.instance.getDownloading().any { it.video?.url == videoUrl }) {
                        return@withContext null
                    }
                    // Cache hits return the plain feed video — fine: the
                    // engine's download() prepares details itself when
                    // they are missing.
                    val video =
                        StatePlatform.instance.getContentDetails(videoUrl).await() as? IPlatformVideo
                    if (video == null) {
                        return@withContext "Could not resolve video"
                    }
                    if (StateDownloads.instance.isDownloaded(video.id)) {
                        refresh()
                        return@withContext null
                    }
                    // The engine's own entry point: validates (rejects
                    // already-queued URLs), persists, toasts, and starts
                    // the DownloadService. 144p / 64kbps for mobile.
                    StateDownloads.instance.download(video, 256L * 144L, 64_000L)
                    trackProgress()
                    refresh()
                    null
                } catch (e: Exception) {
                    Logger.e(TAG, "startDownload failed", e)
                    e.message ?: "Download failed"
                }
            }

        override suspend fun deleteDownload(videoUrl: String): String? =
            withContext(Dispatchers.IO) {
                val video =
                    StateDownloads.instance.getDownloadedVideos().firstOrNull { it.url == videoUrl }
                if (video == null) {
                    "Not downloaded"
                } else {
                    // deleteCachedVideo -> store delete -> VideoLocal.onDelete(),
                    // which removes the actual video/audio/subtitle files.
                    StateDownloads.instance.deleteCachedVideo(video.id)
                    refresh()
                    null
                }
            }

        override suspend fun cancelDownload(videoUrl: String): String? =
            withContext(Dispatchers.IO) {
                val download =
                    StateDownloads.instance.getDownloading().firstOrNull { it.video?.url == videoUrl }
                if (download == null) {
                    "Not downloading"
                } else {
                    StateDownloads.instance.removeDownload(download)
                    if (StateDownloads.instance.getDownloading().isEmpty()) {
                        DownloadService.getService()?.closeDownloadSession()
                    }
                    refresh()
                    null
                }
            }

        private fun refresh() {
            val sd = StateDownloads.instance
            val inProgress =
                sd.getDownloading().map { d ->
                    DownloadInfo(
                        url = d.video?.url.orEmpty(),
                        title = d.name,
                        author = null,
                        authorUrl =
                            d.video
                                ?.author
                                ?.url
                                ?.takeIf { it.isNotEmpty() },
                        thumbnailUrl = d.thumbnail,
                        durationMs = null,
                        progress = d.progress.toFloat(),
                        done = false,
                    )
                }
            val done =
                sd.getDownloadedVideos().map { v ->
                    DownloadInfo(
                        url = v.url,
                        title = v.name,
                        author = v.author?.name,
                        authorUrl = v.author?.url?.takeIf { it.isNotEmpty() },
                        thumbnailUrl = v.thumbnails?.getHQThumbnail(),
                        durationMs = if (v.duration > 0) v.duration * 1000L else null,
                        progress = 1f,
                        done = true,
                    )
                }
            trackProgress()
            // Active downloads first — they are what the user is watching.
            _downloads.value = inProgress + done
        }

        private companion object {
            const val TAG = "EngineDownloads"
        }
    }

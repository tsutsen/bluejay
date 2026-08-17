package com.tsutsen.platformplayer.di

import android.content.Context
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.tsutsen.platformplayer.core.data.repository.DownloadsRepository
import com.tsutsen.platformplayer.core.model.DownloadInfo
import com.tsutsen.platformplayer.downloads.VideoDownload
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.services.DownloadService
import com.tsutsen.platformplayer.states.StateDownloads
import com.tsutsen.platformplayer.states.StatePlatform
import dagger.hilt.android.qualifiers.ApplicationContext
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
    constructor(
        @ApplicationContext private val context: Context,
    ) : DownloadsRepository {
        private val _downloads = MutableStateFlow(emptyList<DownloadInfo>())
        override val downloads: kotlinx.coroutines.flow.StateFlow<List<DownloadInfo>> =
            _downloads.asStateFlow()

        init {
            refresh()
            StateDownloads.instance.onDownloadedChanged.subscribe { refresh() }
            StateDownloads.instance.onDownloadsChanged.subscribe { refresh() }
        }

        override suspend fun startDownload(videoUrl: String): String? =
            withContext(Dispatchers.IO) {
                try {
                    val details =
                        StatePlatform.instance.getContentDetails(videoUrl).await() as? IPlatformVideoDetails
                    if (details == null) {
                        return@withContext "Could not resolve video details"
                    }
                    if (StateDownloads.instance.isDownloaded(details.id)) {
                        refresh()
                        return@withContext null
                    }
                    // 720p target; null sources let prepare() pick the best.
                    val download =
                        VideoDownload(
                            video = details,
                            targetPixelCount = 1280L * 720L,
                            targetBitrate = 128_000L,
                        )
                    StateDownloads.instance.updateDownloading(download)
                    DownloadService.getOrCreateService(context)
                    refresh()
                    null
                } catch (e: Exception) {
                    Logger.e(TAG, "startDownload failed", e)
                    e.message ?: "Download failed"
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
                        thumbnailUrl = v.thumbnails?.getHQThumbnail(),
                        durationMs = if (v.duration > 0) v.duration * 1000L else null,
                        progress = 1f,
                        done = true,
                    )
                }
            _downloads.value = done + inProgress
        }

        private companion object {
            const val TAG = "EngineDownloads"
        }
    }

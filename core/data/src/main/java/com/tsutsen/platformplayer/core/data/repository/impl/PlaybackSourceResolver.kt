package com.tsutsen.platformplayer.core.data.repository.impl

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.tsutsen.platformplayer.core.data.repository.ResolutionResult
import com.tsutsen.platformplayer.core.data.repository.SubtitleSource
import com.tsutsen.platformplayer.core.data.repository.VideoDetails
import com.tsutsen.platformplayer.core.data.repository.VideoUrlResolver
import com.tsutsen.platformplayer.core.model.Author
import com.tsutsen.platformplayer.core.model.ContentItem

/**
 * Constructs the [MediaSource]s the player plays and resolves content URLs
 * to streaming sources + video details via the engine plugin
 * ([VideoUrlResolver]). Extracted from [PlayerRepositoryImpl], which stays
 * the orchestrator.
 *
 * The resolver reference arrives late (wired by the DI module after
 * construction), so it is read through [urlResolverProvider] on every use.
 */
internal class PlaybackSourceResolver(
    private val context: Context,
    private val urlResolverProvider: () -> VideoUrlResolver?,
    private val logTag: String,
) {
    fun isStreamingUrl(url: String): Boolean =
        url.contains(".mpd") || url.contains(".m3u8") || url.contains("dash") || url.contains("hls")

    suspend fun resolveWithDetails(
        contentUrl: String,
        resumePositionMs: Long = 0,
    ): ResolutionResult =
        try {
            Log.i(logTag, "Resolving MediaSource + details for content URL: $contentUrl")
            Log.i(logTag, "urlResolver is null: ${urlResolverProvider() == null}")
            val resolver = urlResolverProvider()
            if (resolver != null) {
                Log.i(logTag, "Calling resolver.resolve()...")
                val resolution = resolver.resolve(contentUrl, resumePositionMs)
                Log.i(logTag, "Resolved MediaSource: ${resolution.mediaSource?.javaClass?.simpleName}")
                Log.i(logTag, "Video details available: ${resolution.videoDetails != null}")
                if (resolution.videoDetails != null) {
                    Log.i(logTag, "VideoDetails title: ${resolution.videoDetails.title}")
                }
                resolution
            } else {
                Log.w(logTag, "WARNING: VideoUrlResolver not provided, creating MediaSource from URL")
                ResolutionResult(createMediaSourceFromUrl(contentUrl), null)
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to resolve, creating from URL: $contentUrl", e)
            ResolutionResult(createMediaSourceFromUrl(contentUrl), null)
        }

    /**
     * ALL supported subtitle tracks of the given video, merged once when
     * the media source is assembled (each tagged with id "sub_<name>").
     * Selecting/toggling them is then a track-selection change — instant,
     * no source rebuild — instead of a re-prepare that blanks the surface.
     * Null when the video has no supported subtitle.
     */
    suspend fun buildAllSubtitleMediaSource(subtitles: List<SubtitleSource>): MediaSource? {
        val configs =
            subtitles.mapNotNull { subtitle ->
                val format = subtitle.format?.lowercase() ?: return@mapNotNull null
                if (format !in SUPPORTED_SUBTITLE_FORMATS) return@mapNotNull null
                val uri = runCatching { subtitle.contentUri() }.getOrNull() ?: return@mapNotNull null
                MediaItem.SubtitleConfiguration
                    .Builder(uri)
                    .setId("sub_${subtitle.name}")
                    .setMimeType(subtitle.format)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
        if (configs.isEmpty()) return null
        val factory = SingleSampleMediaSource.Factory(DefaultDataSource.Factory(context, createHttpDataSourceFactory()))
        val sources = configs.map { factory.createMediaSource(it, C.TIME_UNSET) }
        return if (sources.size == 1) sources[0] else MergingMediaSource(true, *sources.toTypedArray())
    }

    /**
     * Map VideoDetails to ContentItem for display in the player UI.
     */
    fun mapVideoDetailsToContentItem(details: VideoDetails): ContentItem =
        ContentItem(
            id = details.id,
            url = details.url,
            title = details.title,
            author =
                details.authorName?.let {
                    Author(
                        id = details.id,
                        name = it,
                        url = details.authorUrl,
                        thumbnailUrl = details.authorThumbnailUrl,
                        subscriberCount = details.authorSubscriberCount,
                    )
                },
            thumbnailUrl = details.thumbnailUrl,
            contentType =
                if (details.isLive) com.tsutsen.platformplayer.core.model.ContentType.LIVE
                else com.tsutsen.platformplayer.core.model.ContentType.VIDEO,
            publishedAt = details.publishedAtMs,
            durationMs = details.durationMs,
            viewCount = details.viewCount,
            description = details.description,
            likeCount = details.likeCount,
            dislikeCount = details.dislikeCount,
            sourceIconUrl = details.sourceIconUrl,
        )

    fun createHttpDataSourceFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource
            .Factory()
            .setUserAgent("Bluejay/1.0")
            .setAllowCrossProtocolRedirects(true)

    fun createMediaSourceFromUrl(url: String): MediaSource {
        Log.i(logTag, "createMediaSourceFromUrl() called with URL: $url")
        Log.i(logTag, "URL contains .mpd: ${url.contains(".mpd")}")
        Log.i(logTag, "URL contains .m3u8: ${url.contains(".m3u8")}")
        Log.i(logTag, "URL contains 'dash': ${url.contains("dash")}")
        Log.i(logTag, "URL contains 'hls': ${url.contains("hls")}")

        val httpDataSourceFactory = createHttpDataSourceFactory()

        return when {
            url.contains(".mpd") || url.contains("dash") -> {
                Log.i(logTag, "Creating DASH MediaSource for URL: $url")
                DashMediaSource
                    .Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }

            url.contains(".m3u8") || url.contains("hls") -> {
                Log.i(logTag, "Creating HLS MediaSource for URL: $url")
                HlsMediaSource
                    .Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }

            else -> {
                // Check if this is a progressive streaming URL that needs DASH conversion
                Log.i(logTag, "Checking if URL is progressive streaming...")
                // For now, try progressive media source
                Log.i(logTag, "Creating Progressive MediaSource for URL: $url")
                ProgressiveMediaSource
                    .Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
        }
    }

    private companion object {
        // Formats ExoPlayer's text renderer can display (mirrors Grayjay).
        val SUPPORTED_SUBTITLE_FORMATS = setOf("text/vtt", "application/x-subrip")
    }
}

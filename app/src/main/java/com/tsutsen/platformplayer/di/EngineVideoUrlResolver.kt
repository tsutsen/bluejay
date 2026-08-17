package com.tsutsen.platformplayer.di

import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.tsutsen.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.tsutsen.platformplayer.api.media.models.ratings.RatingLikes
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSAudioUrlSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawAudioSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSDashManifestSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSHLSManifestAudioSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSHLSManifestSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSUMPSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSUnMuxVideoSourceDescriptor
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSVideoSourceDescriptor
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSVideoUrlSource
import com.tsutsen.platformplayer.core.data.repository.ResolutionResult
import com.tsutsen.platformplayer.core.data.repository.SubtitleSource
import com.tsutsen.platformplayer.core.data.repository.VideoDetails
import com.tsutsen.platformplayer.core.data.repository.VideoUrlResolver
import com.tsutsen.platformplayer.sabr.media3.SabrMediaSource
import com.tsutsen.platformplayer.states.StatePlatform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine-based video URL resolver.
 * Uses StatePlatform to resolve content URLs to MediaSources via engine plugins.
 * Handles all source types: UMP/Sabr, DASH, HLS, VideoUrl, AudioUrl.
 */
@Singleton
class EngineVideoUrlResolver @Inject constructor() : VideoUrlResolver {

    private val TAG = "EngineVideoUrlResolver"

    override suspend fun resolve(contentUrl: String): ResolutionResult {
        return try {
            Log.i(TAG, "========================================")
            Log.i(TAG, "Resolving content URL via engine: $contentUrl")
            Log.i(TAG, "========================================")
            Log.i(TAG, "StatePlatform.instance is null: ${StatePlatform.instance == null}")

            val details = StatePlatform.instance.getContentDetails(contentUrl).await()

            if (details == null) {
                Log.w(TAG, "Engine returned null details for URL: $contentUrl")
                Log.i(TAG, "========================================")
                return ResolutionResult(null, null)
            }

            val videoDetails = details as? IPlatformVideoDetails
            if (videoDetails == null) {
                Log.w(TAG, "Details is not IPlatformVideoDetails: ${details.javaClass.simpleName}")
                Log.i(TAG, "========================================")
                return ResolutionResult(null, null)
            }

            Log.i(TAG, "Video details found: title=${videoDetails.name}, author=${videoDetails.author?.name}, description length=${videoDetails.description.length}")

            // Map IPlatformVideoDetails to lightweight VideoDetails for the data layer
            val mappedDetails = mapToVideoDetails(videoDetails, contentUrl)
            Log.i(TAG, "Mapped details: title=${mappedDetails.title}, authorName=${mappedDetails.authorName}, thumbnail=${mappedDetails.thumbnailUrl}")

            Log.i(TAG, "Engine details type: ${details.javaClass.simpleName}")

            // Create HTTP data source factory
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Bluejay/1.0")
                .setAllowCrossProtocolRedirects(true)

            // Try to resolve video source
            val videoMediaSource = resolveVideoSource(videoDetails, httpDataSourceFactory, contentUrl)

            if (videoMediaSource != null) {
                Log.i(TAG, "Resolved video MediaSource: ${videoMediaSource.javaClass.simpleName}")
                Log.i(TAG, "========================================")
                return ResolutionResult(videoMediaSource, mappedDetails)
            }

            // Fallback: no MediaSource but we still have the video details
            Log.w(TAG, "No video MediaSource found, returning details only")
            Log.i(TAG, "========================================")
            ResolutionResult(null, mappedDetails)
        } catch (e: Exception) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "Failed to resolve URL via engine: $contentUrl", e)
            Log.e(TAG, "========================================")
            ResolutionResult(null, null)
        }
    }

    /**
     * Map IPlatformVideoDetails to lightweight VideoDetails for the data layer.
     */
    private fun mapToVideoDetails(details: IPlatformVideoDetails, fallbackUrl: String): VideoDetails {
        val author = details.author
        // Extract likes/dislikes from rating
        val (likeCount, dislikeCount) = extractLikesDislikes(details.rating)
        Log.i(TAG, "Rating: likes=${likeCount}, dislikes=${dislikeCount}, type=${details.rating?.type}")
        return VideoDetails(
            id = details.id.value ?: details.id.platform,
            url = details.url.ifEmpty { fallbackUrl },
            title = details.name,
            authorName = author?.name,
            authorUrl = author?.url?.takeIf { it.isNotEmpty() },
            authorThumbnailUrl = author?.thumbnail,
            thumbnailUrl = details.thumbnails?.getHQThumbnail(),
            description = details.description,
            durationMs = if (details.duration > 0) details.duration else null,
            viewCount = if (details.viewCount > 0) details.viewCount else null,
            publishedAtMs = details.datetime?.toInstant()?.toEpochMilli(),
            likeCount = likeCount,
            dislikeCount = dislikeCount,
            subtitles = details.subtitles.map { source ->
                SubtitleSource(
                    name = source.name,
                    format = source.format,
                    contentUri = { source.getSubtitlesURI() }
                )
            }
        )
    }

    /**
     * Extract likes and dislikes from IRating.
     */
    private fun extractLikesDislikes(rating: com.tsutsen.platformplayer.api.media.models.ratings.IRating?): Pair<Long?, Long?> {
        if (rating == null) return Pair(null, null)
        return when (rating) {
            is RatingLikes -> Pair(rating.likes, null)
            is RatingLikeDislikes -> Pair(rating.likes, rating.dislikes)
            else -> Pair(null, null)
        }
    }

    private fun resolveVideoSource(
        videoDetails: IPlatformVideoDetails,
        httpDataSourceFactory: DefaultHttpDataSource.Factory,
        contentUrl: String
    ): MediaSource? {
        // Priority 1: Live stream
        val live = videoDetails.live
        if (live != null) {
            Log.i(TAG, "Using live stream source")
            return createMediaSourceFromSource(live, httpDataSourceFactory, contentUrl)
        }

        // Priority 2: DASH manifest
        val dash = videoDetails.dash
        if (dash != null) {
            Log.i(TAG, "Using DASH manifest source")
            return createMediaSourceFromSource(dash, httpDataSourceFactory, contentUrl)
        }

        // Priority 3: HLS manifest
        val hls = videoDetails.hls
        if (hls != null) {
            Log.i(TAG, "Using HLS manifest source")
            return createMediaSourceFromSource(hls, httpDataSourceFactory, contentUrl)
        }

        // Priority 4: Muxed video source descriptor
        val video = videoDetails.video
        if (video is JSVideoSourceDescriptor) {
            Log.i(TAG, "Using muxed video source descriptor")
            return resolveMuxedVideoSource(video, httpDataSourceFactory, contentUrl)
        }

        // Priority 5: Unmuxed video source descriptor (separate video + audio)
        if (video is JSUnMuxVideoSourceDescriptor) {
            Log.i(TAG, "Using unmuxed video source descriptor")
            return resolveUnmuxedVideoSource(video, httpDataSourceFactory, contentUrl)
        }

        Log.w(TAG, "No suitable video source found")
        return null
    }

    private fun resolveMuxedVideoSource(
        video: JSVideoSourceDescriptor,
        httpDataSourceFactory: DefaultHttpDataSource.Factory,
        contentUrl: String
    ): MediaSource? {
        val sources = video.videoSources
        if (sources.isEmpty()) {
            Log.w(TAG, "No video sources in muxed descriptor")
            return null
        }

        // Try each source until we find one that works
        for (source in sources) {
            Log.i(TAG, "Trying source: ${source.javaClass.simpleName}")
            val mediaSource = createMediaSourceFromSource(source, httpDataSourceFactory, contentUrl)
            if (mediaSource != null) {
                Log.i(TAG, "Successfully created MediaSource from: ${source.javaClass.simpleName}")
                return mediaSource
            }
        }

        Log.w(TAG, "Failed to create MediaSource from any muxed source")
        return null
    }

    private fun resolveUnmuxedVideoSource(
        video: JSUnMuxVideoSourceDescriptor,
        httpDataSourceFactory: DefaultHttpDataSource.Factory,
        contentUrl: String
    ): MediaSource? {
        val videoSources = video.videoSources
        val audioSources = video.audioSources

        Log.i(TAG, "Unmuxed: ${videoSources.size} video sources, ${audioSources.size} audio sources")

        // Find a working video-only MediaSource
        val videoMediaSource = videoSources.firstNotNullOfOrNull { videoSource ->
            Log.i(TAG, "Trying video source: ${videoSource.javaClass.simpleName}")
            createMediaSourceFromSource(videoSource, httpDataSourceFactory, contentUrl)
        }

        if (videoMediaSource == null) {
            Log.w(TAG, "Failed to create MediaSource from any unmuxed video source")
            return null
        }

        // Try to merge video + audio, but fall back to video-only if audio fails
        val audioMediaSource = audioSources.firstNotNullOfOrNull { audioSource ->
            Log.i(TAG, "Trying audio source: ${audioSource.javaClass.simpleName}")
            try {
                val source = createMediaSourceFromSource(audioSource, httpDataSourceFactory)
                if (source != null) {
                    Log.i(TAG, "Successfully created audio MediaSource: ${audioSource.javaClass.simpleName}")
                    source
                } else {
                    Log.w(TAG, "Failed to create audio MediaSource from: ${audioSource.javaClass.simpleName}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating audio MediaSource", e)
                null
            }
        }

        if (audioMediaSource != null) {
            Log.i(TAG, "Merging video + audio MediaSources for unmuxed playback")
            // Use adjustPeriodTimeOffsets to handle timing mismatches between video and audio
            return MergingMediaSource(
                true, // adjustPeriodTimeOffsets
                videoMediaSource,
                audioMediaSource
            )
        }

        Log.w(TAG, "No audio source resolved, falling back to video-only playback (silent)")
        return videoMediaSource
    }

    private fun createMediaSourceFromSource(
        source: com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoSource,
        httpDataSourceFactory: DefaultHttpDataSource.Factory,
        contentUrl: String
    ): MediaSource? {
        return when (source) {
            // UMP/Sabr protocol
            is JSUMPSource -> {
                Log.i(TAG, "Creating SabrMediaSource for UMP source")
                try {
                    val streamSpec = source.toStreamSpec(
                        httpClientFactory = {
                            com.tsutsen.platformplayer.api.http.ManagedHttpClient()
                        },
                        ownsClient = false
                    )

                    val mediaItem = MediaItem.fromUri(contentUrl)
                    val sabrMediaSource = SabrMediaSource.Factory(streamSpec)
                        .setViewport(source.width, source.height)
                        .createMediaSource(mediaItem)

                    Log.i(TAG, "Created SabrMediaSource successfully")
                    sabrMediaSource
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create SabrMediaSource", e)
                    null
                }
            }

            // DASH manifest
            is JSDashManifestSource,
            is JSDashManifestRawSource,
            is IDashManifestSource -> {
                val dashUrl = when (source) {
                    is JSDashManifestSource -> source.url
                    is JSDashManifestRawSource -> source.url
                    is IDashManifestSource -> source.url
                    else -> null
                }

                if (dashUrl != null) {
                    Log.i(TAG, "Creating DASH MediaSource for URL: $dashUrl")
                    DashMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(dashUrl)))
                } else {
                    Log.w(TAG, "DASH source has no URL")
                    null
                }
            }

            // HLS manifest
            is JSHLSManifestSource,
            is IHLSManifestSource -> {
                val hlsUrl = when (source) {
                    is JSHLSManifestSource -> source.url
                    is IHLSManifestSource -> source.url
                    else -> null
                }

                if (hlsUrl != null) {
                    Log.i(TAG, "Creating HLS MediaSource for URL: $hlsUrl")
                    HlsMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(hlsUrl)))
                } else {
                    Log.w(TAG, "HLS source has no URL")
                    null
                }
            }

            // Direct video URL
            is JSVideoUrlSource,
            is IVideoUrlSource -> {
                val videoUrl = when (source) {
                    is JSVideoUrlSource -> source.getVideoUrl()
                    is IVideoUrlSource -> source.getVideoUrl()
                    else -> null
                }

                if (videoUrl != null) {
                    Log.i(TAG, "Creating Progressive MediaSource for video URL: $videoUrl")
                    ProgressiveMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)))
                } else {
                    Log.w(TAG, "VideoUrl source has no URL")
                    null
                }
            }

            else -> {
                Log.w(TAG, "Unsupported source type: ${source.javaClass.simpleName}")
                null
            }
        }
    }

    private fun createMediaSourceFromSource(
        source: com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioSource,
        httpDataSourceFactory: DefaultHttpDataSource.Factory
    ): MediaSource? {
        return when (source) {
            // Audio URL source
            is JSAudioUrlSource,
            is com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioUrlSource -> {
                val audioUrl = when (source) {
                    is JSAudioUrlSource -> source.getAudioUrl()
                    is com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioUrlSource -> source.getAudioUrl()
                    else -> null
                }

                if (audioUrl != null) {
                    Log.i(TAG, "Creating Progressive MediaSource for audio URL: $audioUrl")
                    ProgressiveMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(audioUrl)))
                } else {
                    Log.w(TAG, "AudioUrl source has no URL")
                    null
                }
            }

            // HLS audio
            is JSHLSManifestAudioSource,
            is com.tsutsen.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource -> {
                val hlsUrl = when (source) {
                    is JSHLSManifestAudioSource -> source.url
                    is com.tsutsen.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource -> source.url
                    else -> null
                }

                if (hlsUrl != null) {
                    Log.i(TAG, "Creating HLS MediaSource for audio URL: $hlsUrl")
                    HlsMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(hlsUrl)))
                } else {
                    Log.w(TAG, "HLS audio source has no URL")
                    null
                }
            }

            // DASH audio
            is JSDashManifestRawAudioSource -> {
                Log.i(TAG, "Creating DASH MediaSource for audio: ${source.url}")
                DashMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(source.url)))
            }

            else -> {
                Log.w(TAG, "Unsupported audio source type: ${source.javaClass.simpleName}")
                null
            }
        }
    }
}

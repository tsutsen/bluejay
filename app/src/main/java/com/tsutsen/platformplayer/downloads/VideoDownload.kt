package com.tsutsen.platformplayer.downloads

import android.content.Context
import android.util.Log
import com.tsutsen.platformplayer.UIDialogs
import com.tsutsen.platformplayer.api.http.ManagedHttpClient
import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.models.modifier.IRequestModifier
import com.tsutsen.platformplayer.api.media.models.streams.VideoUnMuxedSourceDescriptor
import com.tsutsen.platformplayer.api.media.models.streams.sources.AudioUrlSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.LocalAudioSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.LocalSubtitleSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.LocalVideoSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.SubtitleRawSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.VideoUrlSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideoDetails
import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformVideoDetails
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.IJSDashManifestRawSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawAudioSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSDashManifestRawSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSUMPSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSUMPAudioSource
import com.tsutsen.platformplayer.api.media.platforms.js.models.sources.JSSource
import com.tsutsen.platformplayer.constructs.Event1
import com.tsutsen.platformplayer.exceptions.DownloadException
import com.tsutsen.platformplayer.helpers.FileHelper.Companion.sanitizeFileName
import com.tsutsen.platformplayer.helpers.VideoHelper
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.parsers.HLS
import com.tsutsen.platformplayer.serializers.OffsetDateTimeNullableSerializer
import com.tsutsen.platformplayer.states.StateDownloads
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.toHumanBitrate
import com.tsutsen.platformplayer.downloads.hasAnySource
import com.tsutsen.platformplayer.downloads.isDownloadable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Contextual
import kotlinx.serialization.Transient
import java.io.File
import java.io.FileOutputStream
import java.time.OffsetDateTime

@kotlinx.serialization.Serializable
class VideoDownload {
    var state: State = State.QUEUED;

    @Contextual
    @Transient
    var plugin: IPlatformClient? = null;
    var video: SerializedPlatformVideo? = null;
    var videoDetails: SerializedPlatformVideoDetails? = null;

    val videoEither: IPlatformVideo get() = videoDetails ?: video ?: throw IllegalStateException("Missing video?");
    val id: PlatformID get() = videoEither.id
    val name: String get() = videoEither.name;
    val thumbnail: String? get() = videoDetails?.thumbnails?.getHQThumbnail();

    var targetPixelCount: Long? = null;
    var targetBitrate: Long? = null;
    var targetVideoName: String? = null;
    var targetAudioName: String? = null;

    var videoSource: VideoUrlSource?;
    var audioSource: AudioUrlSource?;
    var overrideResultAudioSource: IAudioSource? = null;
    @Contextual
    @Transient
    val videoSourceToUse: IVideoSource? get () = if(requiresLiveVideoSource) videoSourceLive as IVideoSource? else videoSource as IVideoSource?;
    @Contextual
    @Transient
    val audioSourceToUse: IAudioSource? get () = if(requiresLiveAudioSource) audioSourceLive as IAudioSource? else audioSource as IAudioSource?;

    var requireVideoSource: Boolean = false;
    var requireAudioSource: Boolean = false;
    var requiredCheck: Boolean = false;

    @Contextual
    @Transient
    val isVideoDownloadReady: Boolean get() = !requireVideoSource ||
            ((requiresLiveVideoSource && isLiveVideoSourceValid) || (!requiresLiveVideoSource && videoSource != null));
    @Contextual
    @Transient
    val isAudioDownloadReady: Boolean get() = !requireAudioSource ||
            ((requiresLiveAudioSource && isLiveAudioSourceValid) || (!requiresLiveAudioSource && audioSource != null));


    var subtitleSource: SubtitleRawSource?;
    @kotlinx.serialization.Serializable(with = OffsetDateTimeNullableSerializer::class)
    var prepareTime: OffsetDateTime? = null;

    var requiresLiveVideoSource: Boolean = false;
    @Contextual
    @kotlinx.serialization.Transient
    var videoSourceLive: JSSource? = null;
    val isLiveVideoSourceValid get() = videoSourceLive?.getUnderlyingPlugin()?.busy {
        videoSourceLive?.getUnderlyingObject()?.isClosed?.let { !it } ?: false;
    } ?: false;

    var requiresLiveAudioSource: Boolean = false;
    @Contextual
    @kotlinx.serialization.Transient
    var audioSourceLive: JSSource? = null;
    val isLiveAudioSourceValid get() = audioSourceLive?.getUnderlyingPlugin()?.busy {
        audioSourceLive?.getUnderlyingObject()?.isClosed?.let { !it } ?: false;
    } ?: false;

    var hasVideoRequestExecutor: Boolean = false;
    var hasAudioRequestExecutor: Boolean = false;
    var hasVideoRequestModifier: Boolean = false;
    var hasAudioRequestModifier: Boolean = false;

    // Transient: IRequestModifier is a runtime object from the JS plugin engine and cannot be
    // serialized. After deserialization these are null - DownloadService must re-prepare to
    // recapture them from the live plugin source (see needsReprepareForAuth).
    @kotlinx.serialization.Transient
    private var preparedVideoRequestModifier: IRequestModifier? = null;
    @kotlinx.serialization.Transient
    private var preparedAudioRequestModifier: IRequestModifier? = null;

    val needsReprepareForAuth: Boolean get() =
        (hasVideoRequestModifier && preparedVideoRequestModifier == null && videoSourceLive == null) ||
        (hasAudioRequestModifier && preparedAudioRequestModifier == null && audioSourceLive == null);

    var progress: Double = 0.0;
    var isCancelled = false;

    var downloadSpeedVideo: Long = 0;
    var downloadSpeedAudio: Long = 0;
    val downloadSpeed: Long get() = downloadSpeedVideo + downloadSpeedAudio;

    var error: String? = null;

    var videoFilePath: String? = null;
    var videoFileNameBase: String? = null;
    var videoFileNameExt: String? = null;
    val videoFileName: String? get() = if(videoFileNameBase.isNullOrEmpty()) null else videoFileNameBase + (if(!videoFileNameExt.isNullOrEmpty()) "." + videoFileNameExt else "");
    var videoOverrideContainer: String? = null;
    var videoFileSize: Long? = null;

    var audioFilePath: String? = null;
    var audioFileNameBase: String? = null;
    var audioFileNameExt: String? = null;
    val audioFileName: String? get() = if(audioFileNameBase.isNullOrEmpty()) null else audioFileNameBase + (if(!audioFileNameExt.isNullOrEmpty()) "." + audioFileNameExt else "");
    var audioOverrideContainer: String? = null;
    var audioFileSize: Long? = null;

    var subtitleFilePath: String? = null;
    var subtitleFileName: String? = null;

    var groupType: String? = null;
    var groupID: String? = null;

    @kotlinx.serialization.Transient
    val onStateChanged = Event1<State>();
    @kotlinx.serialization.Transient
    val onProgressChanged = Event1<Double>();


    fun changeState(newState: State) {
        state = newState;
        onStateChanged.emit(newState);
    }

    constructor(video: IPlatformVideo, targetPixelCount: Long? = null, targetBitrate: Long? = null, optionalSources: Boolean = false) {
        this.video = SerializedPlatformVideo.fromVideo(video);
        this.videoSource = null;
        this.audioSource = null;
        this.subtitleSource = null;
        this.targetPixelCount = targetPixelCount;
        this.targetBitrate = targetBitrate;
        this.hasVideoRequestExecutor = video is JSSource && video.hasRequestExecutor;
        this.requiresLiveVideoSource = false;
        this.requiresLiveAudioSource = false;
        this.targetVideoName = videoSource?.name;
        this.requireVideoSource = targetPixelCount != null;
        this.requireAudioSource = targetBitrate != null; //TODO: May not be a valid check.. can only be determined after live fetch?
        this.requiredCheck = optionalSources;
    }
    constructor(video: IPlatformVideoDetails, videoSource: IVideoSource?, audioSource: IAudioSource?, subtitleSource: SubtitleRawSource?, videoModifier: IRequestModifier? = null, audioModifier: IRequestModifier? = null) {
        this.video = SerializedPlatformVideo.fromVideo(video);
        this.videoDetails = SerializedPlatformVideoDetails.fromVideo(video, if (subtitleSource != null) listOf(subtitleSource) else listOf());
        this.videoSource = if(videoSource is IVideoUrlSource) VideoUrlSource.fromUrlSource(videoSource) else null;
        this.audioSource = if(audioSource is IAudioUrlSource) AudioUrlSource.fromUrlSource(audioSource) else null;
        this.videoSourceLive = if(videoSource is JSSource) videoSource else null;
        this.audioSourceLive = if(audioSource is JSSource) audioSource else null;
        this.subtitleSource = subtitleSource;
        this.prepareTime = OffsetDateTime.now();
        this.preparedVideoRequestModifier = videoModifier ?: (if (videoSource is JSSource && videoSource.hasRequestModifier) videoSource.getRequestModifier() else null);
        this.preparedAudioRequestModifier = audioModifier ?: (if (audioSource is JSSource && audioSource.hasRequestModifier) audioSource.getRequestModifier() else null);
        this.hasVideoRequestExecutor = videoSource is JSSource && videoSource.hasRequestExecutor;
        this.hasAudioRequestExecutor = audioSource is JSSource && audioSource.hasRequestExecutor;
        // Set modifier flags from either the source or an explicitly provided modifier
        // (e.g. from the HLS picker, where the source is an HLSVariant, not JSSource).
        // These flags are serialized and used by needsReprepareForAuth after restore.
        this.hasVideoRequestModifier = preparedVideoRequestModifier != null;
        this.hasAudioRequestModifier = preparedAudioRequestModifier != null;
        // requiresLiveVideoSource means a live JSSource is needed at download time (for executors
        // or DASH generation). Modifiers alone don't require a live source - they're already
        // captured in preparedVideoRequestModifier and recaptured via needsReprepareForAuth.
        val sourceHasVideoModifier = videoSource is JSSource && videoSource.hasRequestModifier;
        val sourceHasAudioModifier = audioSource is JSSource && audioSource.hasRequestModifier;
        this.requiresLiveVideoSource = sourceHasVideoModifier || this.hasVideoRequestExecutor || (videoSource is JSDashManifestRawSource && videoSource.hasGenerate) || videoSource is JSUMPSource;
        this.requiresLiveAudioSource = sourceHasAudioModifier || this.hasAudioRequestExecutor || (audioSource is JSDashManifestRawAudioSource && audioSource.hasGenerate) || audioSource is JSUMPAudioSource;
        this.targetVideoName = videoSource?.name;
        this.targetAudioName = audioSource?.name;
        this.targetPixelCount = if(videoSource != null) (videoSource.width * videoSource.height).toLong() else null;
        this.targetBitrate = if(audioSource != null) audioSource.bitrate.toLong() else null;
        this.requireVideoSource = videoSource != null;
        this.requireAudioSource = audioSource != null;
    }

    fun withGroup(groupType: String, groupID: String): VideoDownload {
        this.groupType = groupType;
        this.groupID = groupID;
        return this;
    }

    fun getDownloadInfo() : String {
        val videoInfo = if(videoSource != null)
            "${videoSource!!.width}x${videoSource!!.height} (${videoSource!!.container})"
        else if(targetPixelCount != null && targetPixelCount!! > 0) {
            val guessWidth = ((4 * Math.sqrt(targetPixelCount!!.toDouble())) / 3).toInt();
            val guessHeight = ((3 * Math.sqrt(targetPixelCount!!.toDouble())) / 4).toInt();
            "${guessWidth}x${guessHeight}"
        }
        else null;
        val audioInfo = if(audioSource != null)
            audioSource!!.bitrate.toHumanBitrate();
        else if(targetBitrate != null && targetBitrate!! > 0)
            targetBitrate!!.toHumanBitrate();
        else null;

        val items = arrayOf(videoInfo, audioInfo).filter { it != null };

        return items.joinToString(" • ");
    }

    suspend fun prepare(client: ManagedHttpClient) {
        Logger.i(TAG, "VideoDownload Prepare [${name}]");

        //If live sources are required, ensure a live object is present
        if(requiresLiveVideoSource && !isLiveVideoSourceValid) {
            videoDetails = null;
            videoSource = null;
            videoSourceLive = null;
            videoOverrideContainer = null;
        }
        if(requiresLiveAudioSource && !isLiveAudioSourceValid) {
            videoDetails = null;
            audioSource = null;
            videoSourceLive = null;
            audioOverrideContainer = null;
        }
        if(video == null && videoDetails == null)
            throw IllegalStateException("Missing information for download to complete");
        if(targetPixelCount == null && targetBitrate == null && videoSource == null && audioSource == null && targetVideoName == null && targetAudioName == null)
            throw IllegalStateException("No sources or query values set");

        //Fetch full video object and determine source
        if(video != null && videoDetails == null) {
            val original = if (plugin != null) plugin!!.getContentDetails(video!!.url) else StatePlatform.instance.getContentDetails(video!!.url)?.await();
            if(original !is IPlatformVideoDetails)
                throw IllegalStateException("Original content is not media?");

            if(requiredCheck) {
                if(original.video is VideoUnMuxedSourceDescriptor) {
                    if(requireVideoSource) {
                        if((original.video as VideoUnMuxedSourceDescriptor).audioSources.any() && !original.video.videoSources.any()) {
                            requireVideoSource = false;
                            targetPixelCount = null;
                        }
                    }
                    if(requireAudioSource) {
                        if(!(original.video as VideoUnMuxedSourceDescriptor).audioSources.any() && original.video.videoSources.any()) {
                            requireAudioSource = false;
                            targetBitrate = null;
                        }
                    }
                }
                else {
                    if(requireAudioSource) {
                        requireAudioSource = false;
                        targetBitrate = null;
                    }
                }
                requiredCheck = false;
            }

            if(original.video.hasAnySource() && !original.isDownloadable()) {
                Logger.i(TAG, "Attempted to download unsupported video [${original.name}]:${original.url}");
                throw DownloadException("Unsupported video for downloading", false);
            }

            videoDetails = SerializedPlatformVideoDetails.fromVideo(original, if (subtitleSource != null) listOf(subtitleSource!!) else listOf());
            if(videoSource == null && targetPixelCount != null) {
                val videoSources = arrayListOf<IVideoSource>()
                for (source in original.video.videoSources) {
                    if (source is IHLSManifestSource) {
                        val sourceModifier = if (source is JSSource && source.hasRequestModifier) source.getRequestModifier() else null
                        if (sourceModifier != null) preparedVideoRequestModifier = sourceModifier
                        try {
                            val playlistResponse = client.get(source.url, HashMap(), sourceModifier)
                            if (playlistResponse.isOk) {
                                val resolvedPlaylistUrl = playlistResponse.url
                                val playlistContent = playlistResponse.body?.string()
                                if (playlistContent != null) {
                                    videoSources.addAll(HLS.parseAndGetVideoSources(source, playlistContent, resolvedPlaylistUrl))
                                }
                            }
                            playlistResponse.close()
                        } catch (e: Throwable) {
                            Log.i(TAG, "Failed to get HLS video sources", e)
                        }
                    } else if (source is JSUMPSource && !source.isLive) {
                        videoSources.addAll(source.downloadQualitySources())
                    } else {
                        videoSources.add(source)
                    }
                }
                var vsource: IVideoSource? = null;

                if(targetVideoName != null)
                    vsource = videoSources.find { x -> x.isDownloadable() && x.name == targetVideoName };
                if(vsource == null && targetPixelCount == null)
                    throw IllegalStateException("Could not find comparable downloadable video stream (No target pixel count)");
                if(vsource == null)
                    vsource = VideoHelper.selectBestVideoSource(videoSources, targetPixelCount!!.toInt(), arrayOf())
                //    ?: throw IllegalStateException("Could not find a valid video source for video");
                if(vsource is JSSource) {
                    this.hasVideoRequestExecutor = this.hasVideoRequestExecutor || vsource.hasRequestExecutor;
                    this.requiresLiveVideoSource = this.hasVideoRequestExecutor || (vsource is JSDashManifestRawSource && vsource.hasGenerate) || vsource is JSUMPSource;
                    if (vsource.hasRequestModifier && preparedVideoRequestModifier == null)
                        preparedVideoRequestModifier = vsource.getRequestModifier()
                }

                if(vsource == null) {
                    videoSource = null;
                    if(original.video.videoSources.size == 0)
                        requireVideoSource = false;
                }
                else if(vsource is IVideoUrlSource)
                    videoSource = VideoUrlSource.fromUrlSource(vsource)
                else if(vsource is JSSource && requiresLiveVideoSource)
                    videoSourceLive = vsource;
                else
                    throw DownloadException("Video source is not supported for downloading (yet) [" + vsource?.javaClass?.name + "]", false);
            }

            if(audioSource == null && targetBitrate != null) {
                var audioSources = mutableListOf<IAudioSource>()
                val video = original.video
                if (video is VideoUnMuxedSourceDescriptor) {
                    for (source in video.audioSources) {
                        if (source is IHLSManifestAudioSource) {
                            val sourceModifier = if (source is JSSource && source.hasRequestModifier) source.getRequestModifier() else null
                            if (sourceModifier != null) preparedAudioRequestModifier = sourceModifier
                            try {
                                val playlistResponse = client.get(source.url, HashMap(), sourceModifier)
                                if (playlistResponse.isOk) {
                                    val resolvedPlaylistUrl = playlistResponse.url
                                    val playlistContent = playlistResponse.body?.string()
                                    if (playlistContent != null) {
                                        audioSources.addAll(HLS.parseAndGetAudioSources(source, playlistContent, resolvedPlaylistUrl))
                                    }
                                }
                                playlistResponse.close()
                            } catch (e: Throwable) {
                                Log.i(TAG, "Failed to get HLS audio sources", e)
                            }
                        } else {
                            audioSources.add(source)
                        }
                    }
                }
                for (source in original.video.videoSources) {
                    if (source is JSUMPSource && !source.isLive)
                        audioSources.addAll(source.downloadAudioQualitySources())
                }

                var asource: IAudioSource? = null;
                if(targetAudioName != null) {
                    val filteredAudioSources = audioSources.filter { x -> x.isDownloadable() && x.name == targetAudioName }.toTypedArray();
                    if(filteredAudioSources.size == 1)
                        asource = filteredAudioSources.first();
                    else if(filteredAudioSources.size > 1)
                        audioSources = filteredAudioSources.toMutableList();
                }
                if(asource == null && targetBitrate == null)
                    throw IllegalStateException("Could not find comparable downloadable video stream (No target bitrate)");
                if(asource == null)
                    asource = VideoHelper.selectBestAudioSource(audioSources, arrayOf(), null, targetBitrate)
                        ?: if(videoSource != null ) null
                        else throw DownloadException("Could not find a valid video or audio source for download")

                if(asource is JSSource) {
                    this.hasAudioRequestExecutor = this.hasAudioRequestExecutor || asource.hasRequestExecutor;
                    this.requiresLiveAudioSource = this.hasAudioRequestExecutor || (asource is JSDashManifestRawSource && asource.hasGenerate) || asource is JSUMPAudioSource;
                    if (asource.hasRequestModifier && preparedAudioRequestModifier == null)
                        preparedAudioRequestModifier = asource.getRequestModifier()
                }

                if(asource == null) {
                    audioSource = null;
                    if(!original.video.isUnMuxed || original.video.videoSources.size == 0)
                        requireVideoSource = false;
                }
                else if(asource is IAudioUrlSource)
                    audioSource = AudioUrlSource.fromUrlSource(asource)
                else if(asource is JSSource && requiresLiveAudioSource)
                    audioSourceLive = asource;
                else
                    throw DownloadException("Audio source is not supported for downloading (yet) [" + asource?.javaClass?.name + "]", false);
            }

            if(!isVideoDownloadReady)
                throw DownloadException("No valid sources found for video");
            if(!isAudioDownloadReady)
                throw DownloadException("No valid sources found for audio");
        }
    }

    suspend fun download(context: Context, client: ManagedHttpClient, onProgress: ((Double) -> Unit)? = null) = coroutineScope {
        Logger.i(TAG, "VideoDownload Download [${name}]");
        if(videoDetails == null || (videoSourceToUse == null && audioSourceToUse == null))
            throw IllegalStateException("Missing information for download to complete");
        val downloadDir = StateDownloads.instance.getDownloadsDirectory();

        if(videoDetails!!.id.value == null)
            throw IllegalStateException("Video has no id");

        if(isCancelled) throw CancellationException("Download got cancelled");

        val actualVideoSource = if(requiresLiveVideoSource && videoSourceLive is IVideoSource)
            videoSourceLive as IVideoSource?;
        else videoSource;
        val actualAudioSource = if(requiresLiveAudioSource && audioSourceLive is IAudioSource)
            audioSourceLive as IAudioSource?;
        else audioSource;

        if(actualVideoSource != null) {
            videoFileNameBase = "${videoDetails!!.id.value!!} [${actualVideoSource!!.width}x${actualVideoSource!!.height}]".sanitizeFileName();
            videoFileNameExt = videoContainerToExtension(actualVideoSource!!.container);
            videoFilePath = File(downloadDir, videoFileName!!).absolutePath;
            if(actualVideoSource is JSDashManifestRawSource && actualAudioSource == null) {
                audioFileNameBase = "${videoDetails!!.id.value!!}-[unknown]".sanitizeFileName();
                audioFileNameExt = videoAudioContainerToExtension(actualVideoSource!!.container);
                audioFilePath = File(downloadDir, audioFileName!!).absolutePath;
            }
            if(actualVideoSource is JSUMPSource) {
                val bestVideo = selectBestUMPVideoFormat(actualVideoSource, targetPixelCount);
                videoFileNameExt = umpContainerToExtension(bestVideo?.containerMimeType);
                videoFilePath = File(downloadDir, videoFileName!!).absolutePath;
                val bestAudio = if(actualAudioSource == null) selectBestUMPAudioFormat(actualVideoSource) else null;
                if(bestAudio != null) {
                    audioFileNameBase = "${videoDetails!!.id.value!!} [${bestAudio.language ?: "unknown"}-${bestAudio.bitrate}]".sanitizeFileName();
                    audioFileNameExt = umpContainerToExtension(bestAudio.containerMimeType);
                    audioFilePath = File(downloadDir, audioFileName!!).absolutePath;
                }
            }
        }
        if(actualAudioSource != null) {
            audioFileNameBase = "${videoDetails!!.id.value!!} [${actualAudioSource!!.language}-${actualAudioSource!!.bitrate}]".sanitizeFileName();
            audioFileNameExt = audioContainerToExtension(actualAudioSource!!.container);
            audioFilePath = File(downloadDir, audioFileName!!).absolutePath;
        }
        if(subtitleSource != null) {
            subtitleFileName = "${videoDetails!!.id.value!!} [${subtitleSource!!.name}].${subtitleContainerToExtension(subtitleSource!!.format)}".sanitizeFileName();
            subtitleFilePath = File(downloadDir, subtitleFileName!!).absolutePath;
        }
        val progressLock = Object();
        val sourcesToDownload = mutableListOf<Deferred<Unit>>();

        var lastVideoLength: Long = 0;
        var lastVideoRead: Long = 0;
        var lastAudioLength: Long = 0;
        var lastAudioRead: Long = 0;

        if(actualVideoSource != null) {
            sourcesToDownload.add(async {
                Logger.i(TAG, "Started downloading video");

                var lastEmit = 0L;
                val progressCallback = { length: Long, totalRead: Long, speed: Long ->
                    synchronized(progressLock) {
                        lastVideoLength = length;
                        lastVideoRead = totalRead;
                        downloadSpeedVideo = speed;
                        if(videoFileSize == null)
                            videoFileSize = lastVideoLength;

                        val totalLength = lastVideoLength + lastAudioLength;
                        val total = lastVideoRead + lastAudioRead;
                        if(totalLength > 0) {
                            val percentage = (total / totalLength.toDouble());
                            progress = percentage;

                            val now = System.currentTimeMillis();
                            if(now - lastEmit > 200) {
                                lastEmit = System.currentTimeMillis();
                                onProgress?.invoke(percentage);
                                onProgressChanged.emit(percentage);
                            }
                        }
                    }
                }

                val videoModifier = preparedVideoRequestModifier
                val ctx = SegmentDownloadContext(context, client, videoModifier) { isCancelled }
                if(actualVideoSource is IVideoUrlSource) {
                    val videoTarget = File(downloadDir, videoFileName!!)
                    videoFileSize = when (videoSource!!.container) {
                        "application/vnd.apple.mpegurl" -> {
                            // HLS segments are concatenated into an MP4 file during download,
                            // so override the container for local playback/casting
                            videoOverrideContainer = "video/mp4";
                            HlsSegmentDownloader(ctx, "Video", videoSource!!.getVideoUrl()).download(videoTarget, progressCallback)
                        }
                        else -> ProgressiveSegmentDownloader(this@VideoDownload, ctx, "Video", videoSource!!.getVideoUrl()).download(videoTarget, progressCallback)
                    }
                }
                else if(actualVideoSource is JSDashManifestRawSource) {
                    val dashAudioTarget = if(actualAudioSource == null) File(downloadDir, audioFileName!!) else null
                    val downloader = DashSegmentDownloader(this@VideoDownload, ctx, "Video", actualVideoSource,
                        if(actualAudioSource == null) 3 else 1, dashAudioTarget)
                    videoFileSize = downloader.download(File(downloadDir, videoFileName!!), progressCallback)
                }
                else if(actualVideoSource is JSUMPSource) {
                    val umpAudioFile = if(actualAudioSource == null && audioFileName != null) File(downloadDir, audioFileName!!) else null;
                    val downloader = UmpSegmentDownloader(this@VideoDownload, ctx, source = actualVideoSource, audioFile = umpAudioFile)
                    videoFileSize = downloader.download(File(downloadDir, videoFileName!!), progressCallback)
                    if(downloader.audioSize > 0) audioFileSize = downloader.audioSize;
                }
                else throw NotImplementedError("NotImplemented video download: " + actualVideoSource.javaClass.name);
            });
        }
        if(actualAudioSource != null) {
            sourcesToDownload.add(async {
                Logger.i(TAG, "Started downloading audio");

                var lastEmit = 0L;
                val progressCallback = { length: Long, totalRead: Long, speed: Long ->
                    synchronized(progressLock) {
                        lastAudioLength = length;
                        lastAudioRead = totalRead;
                        downloadSpeedAudio = speed;
                        if(audioFileSize == null)
                            audioFileSize = lastAudioLength;

                        val totalLength = lastVideoLength + lastAudioLength;
                        val total = lastVideoRead + lastAudioRead;
                        if(totalLength > 0) {
                            val percentage = (total / totalLength.toDouble());
                            progress = percentage;

                            val now = System.currentTimeMillis();
                            if(now - lastEmit > 200) {
                                lastEmit = System.currentTimeMillis();
                                onProgress?.invoke(percentage);
                                onProgressChanged.emit(percentage);
                            }
                        }
                    }
                }

                val audioModifier = preparedAudioRequestModifier
                val ctx = SegmentDownloadContext(context, client, audioModifier) { isCancelled }
                val audioTarget = File(downloadDir, audioFileName!!)
                if(actualAudioSource is IAudioUrlSource)
                    audioFileSize = when (audioSource!!.container) {
                        "application/vnd.apple.mpegurl" -> {
                            // HLS segments are concatenated into an MP4 file during download,
                            // so override the container for local playback/casting
                            audioOverrideContainer = "audio/mp4";
                            HlsSegmentDownloader(ctx, "Audio", audioSource!!.getAudioUrl()).download(audioTarget, progressCallback)
                        }
                        else -> ProgressiveSegmentDownloader(this@VideoDownload, ctx, "Audio", audioSource!!.getAudioUrl()).download(audioTarget, progressCallback)
                    }
                else if(actualAudioSource is JSDashManifestRawAudioSource) {
                    audioFileSize = DashSegmentDownloader(this@VideoDownload, ctx, "Audio", actualAudioSource, 2, null)
                        .download(audioTarget, progressCallback);
                }
                else if(actualAudioSource is JSUMPAudioSource) {
                    audioFileSize = UmpSegmentDownloader(this@VideoDownload, ctx, audioSource = actualAudioSource)
                        .download(audioTarget, progressCallback);
                }
                else throw NotImplementedError("NotImplemented audio download: " + actualAudioSource.javaClass.name);
            });
        }
        if (subtitleSource != null) {
            sourcesToDownload.add(async {
                File(downloadDir, subtitleFileName!!).writeText(subtitleSource!!._subtitles)
            });
        }

        var wasSuccesful = false;
        try {
            awaitAll(*sourcesToDownload.toTypedArray());
            wasSuccesful = true;
        }
        catch(runtimeEx: RuntimeException) {
            if(runtimeEx.cause != null)
                throw runtimeEx.cause!!;
            else
                throw runtimeEx;
        }
        catch(ex: Throwable) {
            throw ex;
        }
        finally {
            if(!wasSuccesful) {
                try {
                    if(videoFilePath != null) {
                        val remainingVideo = File(videoFilePath!!);
                        if (remainingVideo.exists()) {
                            Logger.i(TAG, "Deleting remaining video file");
                            remainingVideo.delete();
                        }
                    }
                    if(audioFilePath != null) {
                        val remainingAudio = File(audioFilePath!!);
                        if (remainingAudio.exists()) {
                            Logger.i(TAG, "Deleting remaining audio file");
                            remainingAudio.delete();
                        }
                    }
                }
                catch(iex: Throwable) {
                    Logger.e(TAG, "Failed to delete files after failure:\n${iex.message}", iex);
                }
            }
        }
    }

    private fun umpContainerToExtension(mime: String?): String = when {
        mime == null -> "mp4";
        mime.contains("webm") -> "webm";
        mime.contains("mp4") -> "mp4";
        else -> "mp4";
    }

    fun createNewPluginClient() {
        UIDialogs.appToast("Download creating new client at request of plugin");
        cleanupPluginClient();
        plugin = (video?.url ?: videoDetails?.url)?.let { StatePlatform.instance.getContentClient(it) }?.let { if(it is JSClient) it else null }?.getCopy(false, true);
        plugin?.initialize();
    }
    fun cleanupPluginClient() {
        val oldPlugin = plugin;
        plugin = null;
        try {
            oldPlugin?.disable();
        }
        catch(ex: Throwable) {
            Logger.e(TAG, "Failed to dispose download client: ${ex.message}" , ex);
        }
    }

    data class DecryptionInfo(
        val key: ByteArray,
        val iv: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DecryptionInfo

            if (!key.contentEquals(other.key)) return false
            if (!iv.contentEquals(other.iv)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = key.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }

    /*private fun downloadSource_Sequential(client: ManagedHttpClient, fileStream: FileOutputStream, url: String, onProgress: (Long, Long, Long) -> Unit): Long {
        val progressRate: Int = 4096 * 25
        var lastProgressCount: Int = 0
        val speedRate: Int = 4096 * 25
        var readSinceLastSpeedTest: Long = 0
        var timeSinceLastSpeedTest: Long = System.currentTimeMillis()

        var lastSpeed: Long = 0

        var totalRead: Long = 0
        var sourceLength: Long
        val buffer = ByteArray(4096)

        var isPartialDownload = false
        var result: ManagedHttpClient.Response? = null
        do {
            result = client.get(url, if (isPartialDownload) hashMapOf("Range" to "bytes=$totalRead-") else hashMapOf())
            if (isPartialDownload) {
                if (result.code != 206)
                    throw IllegalStateException("Failed to download source, byte range fallback failed. Web[${result.code}] Error")
            } else {
                if (!result.isOk)
                    throw IllegalStateException("Failed to download source. Web[${result.code}] Error")
            }
            if (result.body == null)
                throw IllegalStateException("Failed to download source. Web[${result.code}] No response")

            isPartialDownload = true
            sourceLength = result.body!!.contentLength()
            val sourceStream = result.body!!.byteStream()

            try {
                while (true) {
                    val read = sourceStream.read(buffer)
                    if (read <= 0) {
                        break
                    }

                    fileStream.write(buffer, 0, read)

                    totalRead += read
                    readSinceLastSpeedTest += read

                    if (totalRead / progressRate > lastProgressCount) {
                        onProgress(sourceLength, totalRead, lastSpeed)
                        lastProgressCount++
                    }
                    if (readSinceLastSpeedTest > speedRate) {
                        val lastSpeedTime = timeSinceLastSpeedTest
                        timeSinceLastSpeedTest = System.currentTimeMillis()
                        val timeSince = timeSinceLastSpeedTest - lastSpeedTime
                        if (timeSince > 0)
                            lastSpeed = (readSinceLastSpeedTest / (timeSince / 1000.0)).toLong()
                        readSinceLastSpeedTest = 0
                    }

                    if (isCancelled)
                        throw CancellationException("Cancelled")
                }
            } catch (e: Throwable) {
                Logger.w(TAG, "Sequential download was interrupted, trying to fallback to byte ranges", e)
            } finally {
                sourceStream.close()
                result.body?.close()
            }
        } while (totalRead < sourceLength)

        onProgress(sourceLength, totalRead, 0)
        return sourceLength
    }*/
    fun validate() {
        Logger.i(TAG, "VideoDownload Validate [${name}]");
        if(videoSourceToUse != null) {
            if(videoFilePath == null)
                throw IllegalStateException("Missing video file name after download");
            val expectedFile = File(videoFilePath!!);
            if(!expectedFile.exists())
                throw IllegalStateException("Video file missing after download");
            if (videoSource?.container != "application/vnd.apple.mpegurl") {
                // A missing/unknown Content-Length (-1) cannot be validated.
                val expectedSize = videoFileSize
                if (expectedSize != null && expectedSize > 0 && expectedFile.length() != expectedSize)
                    throw IllegalStateException("Expected size [${expectedSize}], but found ${expectedFile.length()}");
            }
        }
        if(audioSourceToUse != null || (videoSourceToUse is IJSDashManifestRawSource)) {
            if(audioFilePath == null)
                throw IllegalStateException("Missing audio file name after download");
            val expectedFile = File(audioFilePath!!);
            if(!expectedFile.exists())
                throw IllegalStateException("Audio file missing after download");
            if (audioSource?.container != "application/vnd.apple.mpegurl") {
                val expectedSize = audioFileSize
                if (expectedSize != null && expectedSize > 0 && expectedFile.length() != expectedSize)
                    throw IllegalStateException("Expected size [${expectedSize}], but found ${expectedFile.length()}");
            }
        }
        if(subtitleSource != null) {
            if(subtitleFilePath == null)
                throw IllegalStateException("Missing subtitle file name after download");
            val expectedFile = File(subtitleFilePath!!);
            if(!expectedFile.exists())
                throw IllegalStateException("Subtitle file missing after download");
        }
    }
    fun complete() {
        Logger.i(TAG, "VideoDownload Complete [${name}]");
        val existing = StateDownloads.instance.getCachedVideo(id);
        val localVideoSource = videoFilePath?.let { LocalVideoSource.fromSource(videoSourceToUse!!, it, videoFileSize ?: 0, videoOverrideContainer) };
        val localAudioSource = audioFilePath?.let { LocalAudioSource.fromSource(overrideResultAudioSource ?: audioSourceToUse!!, it, audioFileSize ?: 0, audioOverrideContainer) };
        val localSubtitleSource = subtitleFilePath?.let { LocalSubtitleSource.fromSource(subtitleSource!!, it) };

        if(localVideoSource != null && videoSourceToUse != null && videoSourceToUse is IStreamMetaDataSource)
            localVideoSource.streamMetaData = (videoSourceToUse as IStreamMetaDataSource).streamMetaData;

        if(localAudioSource != null && audioSourceToUse != null && audioSourceToUse is IStreamMetaDataSource)
            localAudioSource.streamMetaData = (audioSourceToUse as IStreamMetaDataSource).streamMetaData;

        if(localAudioSource != null && localAudioSource.streamMetaData == null && videoSourceToUse is JSDashManifestRawSource)
            localAudioSource.streamMetaData = (videoSourceToUse as JSDashManifestRawSource).audioStreamMetaData;

        if(existing != null) {
            existing.videoSerialized = videoDetails!!;
            if(localVideoSource != null) {
                val newVideos = ArrayList(existing.videoSource);
                newVideos.add(localVideoSource);
                existing.videoSource = newVideos;
            }
            if(localAudioSource != null) {
                val newAudios = ArrayList(existing.audioSource);
                newAudios.add(localAudioSource);
                existing.audioSource = newAudios;
            }
            if (localSubtitleSource != null) {
                val newSubtitles = ArrayList(existing.subtitlesSources);
                newSubtitles.add(localSubtitleSource);
                existing.subtitlesSources = newSubtitles;
            }
            StateDownloads.instance.updateCachedVideo(existing);
        }
        else {
            val newVideo = VideoLocal(videoDetails!!, OffsetDateTime.now());
            if(localVideoSource != null)
                newVideo.videoSource.add(localVideoSource);
            if(localAudioSource != null)
                newVideo.audioSource.add(localAudioSource);
            if (localSubtitleSource != null)
                newVideo.subtitlesSources.add(localSubtitleSource);
            newVideo.groupID = groupID;
            newVideo.groupType = groupType;
            StateDownloads.instance.updateCachedVideo(newVideo);
        }
    }

    fun cleanup(){
        cleanupPluginClient()
    }

    enum class State {
        QUEUED,
        PREPARING,
        DOWNLOADING,
        VALIDATING,
        FINALIZING,
        COMPLETED,
        ERROR;

        override fun toString(): String {
            val lowercase = super.toString().lowercase();
            if(lowercase.length == 0)
                return lowercase;
            return lowercase[0].uppercase() + lowercase.substring(1);
        }
    }

    internal fun executeOrGet(client: ManagedHttpClient, executor: JSRequestExecutor?, modifier: IRequestModifier?, url: String, headers: Map<String, String> = mapOf()): ByteArray {
        if (executor != null) {
            val modified = modifier?.modifyRequest(url, headers)
            return executor.executeRequest("GET", modified?.url ?: url, null, modified?.headers ?: headers)
        } else {
            val resp = client.get(url, headers.toMutableMap(), modifier)
            if (!resp.isOk) {
                resp.body?.close()
                throw IllegalStateException("Request failed for ($url) with code: ${resp.code}")
            }
            return resp.body!!.bytes()
        }
    }

    companion object {
        const val TAG = "VideoDownload";
        const val GROUP_PLAYLIST = "Playlist";
        const val GROUP_WATCHLATER= "WatchLater";


        val REGEX_DASH_TEMPLATE = Regex("<SegmentTemplate .*?media=\"(.*?)\".*?>(.*?)<\\/SegmentTemplate>", RegexOption.DOT_MATCHES_ALL);
        val REGEX_DASH_TEMPLATE_WITH_MIME = Regex("<Representation.*?mimeType=\\\"(.*?)\\\".*?>.*?<SegmentTemplate .*?media=\\\"(.*?)\\\".*?>(.*?)<\\/SegmentTemplate>", RegexOption.DOT_MATCHES_ALL);
        val REGEX_CODECS = Regex("codecs=\\\"(.*?)\\\"")
        val REGEX_DASH_CUE = Regex("<S .*?t=\"([0-9]*?)\".*?d=\"([0-9]*?)\".*?\\/>", RegexOption.DOT_MATCHES_ALL);

        fun videoContainerToExtension(container: String): String? {
            if (container.contains("video/mp4") || container == "application/vnd.apple.mpegurl")
                return "mp4";
            else if (container.contains("application/x-mpegURL"))
                return "m3u8";
            else if (container.contains("video/3gpp"))
                return "3gp";
            else if (container.contains("video/quicktime"))
                return "mov";
            else if (container.contains("video/webm"))
                return "webm";
            else if (container.contains("video/x-matroska"))
                return "mkv";
            else
                return "video";//throw IllegalStateException("Unknown container: " + container)
        }

        //TODO: Change usages of this to an accurate container instead of infering it.
        fun videoAudioContainerToExtension(container: String): String? {
            if (container.contains("video/mp4") || container == "application/vnd.apple.mpegurl")
                return "mp4a";
            else if (container.contains("video/webm"))
                return "webm";
            else
                return "mp4a";//throw IllegalStateException("Unknown container: " + container)
        }

        fun audioContainerToExtension(container: String): String {
            if (container.contains("audio/mp4"))
                return "mp4a";
            else if (container.contains("video/mp4"))
                return "mp4";
            else if (container.contains("audio/mpeg"))
                return "mpga";
            else if (container.contains("audio/mp3"))
                return "mp3";
            else if (container.contains("audio/webm"))
                return "webm";
            else if (container == "application/vnd.apple.mpegurl")
                return "m4a";
            else
                return "audio";// throw IllegalStateException("Unknown container: " + container)
        }

        fun subtitleContainerToExtension(container: String?): String {
            if (container == null)
                return "subtitle";

            if (container.contains("text/vtt"))
                return "vtt";
            else if (container.contains("text/plain"))
                return "srt";
            else if (container.contains("application/x-subrip"))
                return "srt";
            else
                return "subtitle";
        }
    }

    class SpeedTracker {
        private val segmentStart: Long;
        private val intervalMs: Long;
        private var workDone: Long;
        var lastSpeed: Long;
        constructor(intervalMs: Long) {
            segmentStart = System.currentTimeMillis();
            this.intervalMs = intervalMs;
            this.workDone = 0;
            this.lastSpeed = 0;
        }
        fun addWork(work: Long) {
            val now = System.currentTimeMillis();
            if((now - segmentStart) > intervalMs)
            {
                lastSpeed = workDone;
                workDone = 0;
            }
            workDone += work;
        }

    }
}

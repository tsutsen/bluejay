package com.tsutsen.platformplayer.core.data.repository.impl

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.tsutsen.platformplayer.core.data.repository.SubtitleSource
import com.tsutsen.platformplayer.core.data.repository.VideoAudioTrack
import com.tsutsen.platformplayer.core.data.repository.VideoStreamOption
import com.tsutsen.platformplayer.core.model.AudioTrackInfo
import com.tsutsen.platformplayer.core.model.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The resolver-provided tracks of the currently loaded video. The player
 * events read these to decide what to surface: unmuxed (separate video +
 * audio URL) streams expose only one merged ExoPlayer track group, so the
 * options were seeded from the resolver instead.
 */
internal class CurrentTrackInfo(
    val audioTracks: List<VideoAudioTrack>,
    val subtitles: List<SubtitleSource>,
    val videoStreams: List<VideoStreamOption>,
)

/**
 * [Player.Listener] that mirrors player events into the shared
 * [PlayerState]. Extracted from [PlayerRepositoryImpl], which stays the
 * orchestrator.
 */
internal class PlayerEventListener(
    private val logTag: String,
    private val state: MutableStateFlow<PlayerState>,
    private val playerProvider: () -> ExoPlayer?,
    private val currentTracks: () -> CurrentTrackInfo?,
    private val replayOnceIfArmed: () -> Unit,
    private val sourceRecovery: (androidx.media3.common.PlaybackException) -> Boolean,
    private val resumeCheck: () -> Unit,
) : Player.Listener {
    /**
     * Wall clock of the last observed playing moment (main thread only —
     * the events arrive there). The resume refresh uses the gap to decide
     * whether playback was suspended long enough to bother re-resolving.
     */
    var lastPlayingAtMs: Long = 0L
        private set

    override fun onPlaybackStateChanged(playbackState: Int) {
        Log.i(logTag, "Playback state changed: $playbackState")
        val duration = playerProvider()?.duration ?: 0L
        state.update {
            it.copy(
                isLoading = playbackState == Player.STATE_BUFFERING,
                isCompleted = playbackState == Player.STATE_ENDED,
                durationMs = duration,
            )
        }
        // LOOP_ONCE: replay the video exactly one more time, then stop.
        if (playbackState == Player.STATE_ENDED) {
            replayOnceIfArmed()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Log.i(logTag, "isPlaying changed: $isPlaying")
        if (isPlaying) lastPlayingAtMs = System.currentTimeMillis()
        state.update { it.copy(isPlaying = isPlaying) }
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        // Resume attempt: if the current source's stream URL is near
        // expiry after a long suspension, refresh it BEFORE the buffer
        // drains (avoids the visible recovery flash on wake-from-sleep).
        // All gating (gap, expiry margin, budget) lives in the manager;
        // this is a cheap no-op on ordinary resumes.
        if (playWhenReady) resumeCheck()
    }

    // media3 1.x routes decoded cues here (the player's internal
    // TextOutput forwards to listeners) - no TextOutput plumbing needed.
    override fun onCues(cueGroup: CueGroup) {
        val text =
            cueGroup.cues
                .mapNotNull { cue -> cue.text?.toString() }
                .joinToString("\n")
        state.update {
            if (it.subtitleText == text) it else it.copy(subtitleText = text)
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        // Don't update position here - let the position ticker handle it
        // This avoids race conditions with seekTo() and scrubbing
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        state.update { it.copy(playbackSpeed = playbackParameters.speed) }
    }

    override fun onTracksChanged(tracks: Tracks) {
        // Unmuxed streams expose exactly one merged audio group; the
        // options were seeded from the resolver instead.
        val trackInfo = currentTracks()
        val unmuxedAudio = !trackInfo?.audioTracks.isNullOrEmpty()
        val heights = mutableListOf<Int>()
        val languages = mutableListOf<String>()
        val audioTracks: MutableList<AudioTrackInfo> =
            if (unmuxedAudio) state.value.audioTracks.toMutableList()
            else mutableListOf()
        var selectedAudio: String? = null
        for (group in tracks.groups) {
            when (group.mediaTrackGroup.type) {
                C.TRACK_TYPE_VIDEO -> {
                    for (i in 0 until group.length) {
                        val height = group.getTrackFormat(i).height
                        if (height > 0) heights.add(height)
                    }
                }

                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val language = group.getTrackFormat(i).language
                        if (!language.isNullOrBlank()) languages.add(language)
                    }
                }

                C.TRACK_TYPE_AUDIO -> {
                    if (unmuxedAudio) {
                        selectedAudio = state.value.selectedAudioTrack
                    } else {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            // "und" (undetermined) is not a language: it
                            // labels the chip "und" and selection by it is
                            // a no-op.
                            val language =
                                format.language
                                    ?.takeIf { it != "und" && it != "undetermined" }
                            // Chip key = language, else label. Tracks
                            // sharing a key are the same choice at
                            // different bitrates (SAB groups them in one
                            // TrackGroup) — one chip per key. Tracks with
                            // neither cannot be targeted via
                            // TrackSelectionParameters — skip them.
                            val key = language ?: format.label
                            if (key.isNullOrBlank()) continue
                            if (group.isTrackSelected(i) && selectedAudio == null) {
                                selectedAudio = key
                            }
                            if (audioTracks.none { it.label == key }) {
                                audioTracks.add(
                                    AudioTrackInfo(
                                        id = "${group.mediaTrackGroup.id}:$i",
                                        label = key,
                                        language = language,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        // Unmuxed streams report only the one loaded file; the real
        // options were seeded from the resolver.
        val qualities =
            if (!trackInfo?.videoStreams.isNullOrEmpty()) state.value.videoQualities
            else heights.distinct().sortedDescending()
        // Engine-provided subtitle tracks first (SAB streams carry
        // no text tracks), then player-reported text tracks (DASH).
        val subtitles = (trackInfo?.subtitles.orEmpty().map { it.name } + languages).distinct()
        state.update {
            if (it.videoQualities == qualities &&
                it.subtitleLanguages == subtitles &&
                it.audioTracks == audioTracks &&
                it.selectedAudioTrack == selectedAudio.orEmpty()
            ) {
                it
            } else {
                it.copy(
                    videoQualities = qualities,
                    subtitleLanguages = subtitles,
                    audioTracks = audioTracks,
                    selectedAudioTrack = selectedAudio.orEmpty(),
                )
            }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        Log.e(logTag, "Player error: ${error.errorCodeName}, message: ${error.message}", error)
        if (sourceRecovery(error)) return
        state.update { it.copy(error = error.message ?: "Unknown error") }
    }

    override fun onLoadingChanged(isLoading: Boolean) {
        Log.i(logTag, "Loading changed: $isLoading")
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        Log.i(logTag, "Playback suppression reason changed: $playbackSuppressionReason")
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        Log.i(logTag, "isLoading changed: $isLoading")
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        Log.i(logTag, "MediaItem transitioned: ${mediaItem?.mediaId}, reason: $reason")
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        // Gated: this fires on every ExoPlayer event batch on the main
        // thread, and the string interpolation alone is not free.
        if (com.tsutsen.platformplayer.core.data.BuildConfig.DEBUG) {
            Log.i(logTag, "Player events: $events")
        }
    }
}

/**
 * Analytics listener that logs load events and dropped frames. Extracted
 * from [PlayerRepositoryImpl].
 */
internal class LoadAnalyticsListener(
    private val logTag: String,
) : AnalyticsListener {
    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
        mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
        error: java.io.IOException,
        wasCanceled: Boolean,
    ) {
        val trackType =
            when (mediaLoadData.trackType) {
                C.TRACK_TYPE_VIDEO -> "VIDEO"
                C.TRACK_TYPE_AUDIO -> "AUDIO"
                C.TRACK_TYPE_TEXT -> "TEXT"
                C.TRACK_TYPE_METADATA -> "METADATA"
                C.TRACK_TYPE_UNKNOWN -> "UNKNOWN"
                else -> "OTHER"
            }
        Log.e(logTag, "LoadError track=$trackType uri=${loadEventInfo.uri} wasCanceled=$wasCanceled", error)
    }

    override fun onLoadCompleted(
        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
        loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
        mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
    ) {
        Log.i(logTag, "LoadCompleted track=${mediaLoadData.trackType} bytes=${loadEventInfo.bytesLoaded}")
    }

    override fun onDroppedVideoFrames(
        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long,
    ) {
        Log.w(logTag, "Dropped $droppedFrames video frames")
    }
}

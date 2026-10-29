package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.PlayerState
import kotlinx.coroutines.flow.StateFlow

interface PlayerRepository {

    val playerState: StateFlow<PlayerState>

    /** Current playback position in ms (0 when no player exists). */
    val positionMs: Long

    /** Current media duration in ms (0 when unknown / no player). */
    val durationMs: Long

    /** Current video frame size, null when no video is loaded. */
    val videoSize: PlaybackVideoSize?

    /**
     * Engine-neutral playback port: state, controls and surface binding
     * without Media3 types on the interface.
     */
    val engine: PlaybackEngine

    /**
     * Play [videoId]. Pass [initial] (known details from the tapped card) so
     * the UI can show title/author/thumbnail instantly instead of a
     * "Loading..." placeholder while the media resolves.
     */
    suspend fun play(videoId: String, initial: ContentItem? = null)
    suspend fun pause()
    suspend fun resume()
    suspend fun seekTo(positionMs: Long)
    suspend fun setVolume(volume: Float)
    suspend fun setBrightness(brightness: Float)
    suspend fun setPlaybackSpeed(speed: Float)
    suspend fun setVideoQuality(quality: String)
    suspend fun setSubtitle(selection: String)

    /**
     * Select the audio track with UI label [selection] (as listed in
     * [PlayerState.audioTracks]); the engine re-selects on the next
     * track change.
     */
    suspend fun setAudioTrack(selection: String)

    /**
     * Toggles subtitles for the current video: turns them off when a concrete
     * track is active, otherwise activates the last explicitly selected track
     * (or the first available one).
     */
    suspend fun toggleSubtitles()

    /**
     * Set the loop mode: [LOOP_OFF] advances/stops at the end, [LOOP_ONCE]
     * replays the current video exactly one more time, [LOOP_INFINITE]
     * replays it forever.
     */
    fun setLoopMode(mode: Int)

    /** Current loop mode (see [setLoopMode]). Survives ViewModel restarts. */
    val loopMode: kotlinx.coroutines.flow.StateFlow<Int>
    suspend fun toggleFullscreen()
    suspend fun minimize()
    suspend fun exitFullscreen()
    suspend fun exitMiniPlayer()
    suspend fun close()

    /**
     * Push the fetched extras for the current video into [playerState] so
     * other surfaces (e.g. the companion display) can read the same data
     * without re-fetching. The owning screen fetches once and pushes here.
     */
    fun setVideoExtras(comments: List<CommentItem>, recommendations: List<Card>)

    val mediaSessionToken: android.media.session.MediaSession.Token?
        get() = null

    companion object {
        const val LOOP_OFF = 0
        const val LOOP_ONCE = 1
        const val LOOP_INFINITE = 2
    }
}

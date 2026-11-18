package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.ControllerPreferences
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.PlayerControllerActions
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.core.model.toContentItem
import com.tsutsen.platformplayer.core.ui.GamepadKeyBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Speed clamp for controller speed-up/down (same range the gesture steps use). */
private const val MIN_SPEED = 0.25f
private const val MAX_SPEED = 4f

/** Action ids that exist now — bindings saved for removed/renamed actions
 * (e.g. the old combined "speed" action) must not shadow their keycode. */
private val KNOWN_ACTION_IDS = PlayerControllerActions.ALL.mapTo(HashSet()) { it.id }

/**
 * Transport and playback controls for the player: play/seek/volume/brightness/
 * speed/quality/subtitle/audio-track, fullscreen/minimize, queue navigation,
 * close, plus the gamepad/remote key dispatcher. Extracted from
 * [PlayerViewModel]; the ViewModel keeps thin delegating methods.
 */
internal class PlayerPlaybackController(
    private val playerRepository: PlayerRepository,
    private val playbackQueueRepository: PlaybackQueueRepository,
    private val settingsRepository: SettingsRepository,
    private val historyTracker: HistoryTracker,
    private val scope: CoroutineScope,
    private val uiState: StateFlow<PlayerUiState>,
    private val controllerPrefs: StateFlow<ControllerPreferences>,
) {
    /** Speed in effect before a controller speed-hold started (restored
     *  on release). Class-level: the speed action handler is local to
     *  [handleControllerKey] but [close] must be able to reset a stuck hold. */
    private var speedBeforeHold: Float? = null

    fun play(
        videoId: String,
        initial: ContentItem? = null,
    ) {
        scope.launch {
            // PlayerRepository.play() clears the previous video's extras and
            // fetches the new one's (comments/recs/chapters) — the single
            // orchestration point shared with the companion screen.
            playerRepository.play(videoId, initial)
            // A new video starts at the user's default speed (manual
            // changes during playback still override it).
            playerRepository.setPlaybackSpeed(
                settingsRepository.preferences.value.defaultPlaybackSpeed,
            )
            // Track in history
            historyTracker.trackPlayback(
                contentUrl = videoId,
                title = initial?.title ?: videoId,
                author = initial?.author?.name,
                thumbnailUrl = initial?.thumbnailUrl,
                postedAt = initial?.publishedAt,
            )
        }
    }

    /** Play a card whose details are already known (instant title/thumb). */
    fun play(card: VideoCard) {
        play(card.url, card.toContentItem())
    }

    fun pause() {
        PlayerEventBus.emit(PlayerEvent.PlayPauseToggled(isPlaying = false))
        scope.launch {
            playerRepository.pause()
        }
    }

    fun resume() {
        PlayerEventBus.emit(PlayerEvent.PlayPauseToggled(isPlaying = true))
        scope.launch {
            playerRepository.resume()
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            playerRepository.seekTo(positionMs)
        }
    }

    /** Seek to [positionMs], clamped to the current video's duration. */
    fun seekToClamped(positionMs: Long) {
        val duration = playerRepository.playerState.value.durationMs
        val target = if (duration > 0) positionMs.coerceIn(0, duration - 500) else positionMs.coerceAtLeast(0)
        seekTo(target)
    }

    /** Seek relative to the engine's live position (avoids stale UI state). */
    fun seekBy(deltaMs: Long) {
        val current = playerRepository.positionMs
        PlayerEventBus.emit(PlayerEvent.Seek(deltaMs))
        scope.launch {
            playerRepository.seekTo((current + deltaMs).coerceIn(0, playerRepository.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE))
        }
    }

    /** Current engine position in ms (0 when no player). */
    fun currentPositionMs(): Long = playerRepository.positionMs

    fun setVolume(volume: Float) {
        PlayerEventBus.emit(PlayerEvent.VolumeChanged(volume))
        scope.launch {
            playerRepository.setVolume(volume)
        }
    }

    fun setBrightness(brightness: Float) {
        PlayerEventBus.emit(PlayerEvent.BrightnessChanged(brightness))
        scope.launch {
            playerRepository.setBrightness(brightness)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        PlayerEventBus.emit(PlayerEvent.PlaybackSpeedChanged(speed))
        scope.launch {
            playerRepository.setPlaybackSpeed(speed)
        }
    }

    fun setVideoQuality(quality: String) {
        scope.launch {
            playerRepository.setVideoQuality(quality)
        }
    }

    fun setSubtitle(selection: String) {
        scope.launch {
            playerRepository.setSubtitle(selection)
        }
    }

    fun setAudioTrack(selection: String) {
        scope.launch {
            playerRepository.setAudioTrack(selection)
        }
    }

    fun toggleSubtitles() {
        scope.launch {
            playerRepository.toggleSubtitles()
        }
    }

    /**
     * Handle a gamepad/remote press or release edge. Looks up the bound
     * action in the user's mapping (falling back to each action's
     * default key) and performs it. Bindings saved for removed actions
     * (e.g. the old combined "speed") are ignored so they don't shadow
     * their keycode.
     *
     * Speed actions are *hold* actions: while held, playback runs at
     * the *current* speed multiplied by 2 (up) / divided by 2 (down),
     * clamped to [MIN_SPEED]..[MAX_SPEED]; releasing restores the speed
     * from before the hold. All other actions fire on press only.
     *
     * @return true when the key is mapped (press *and* release are
     *   consumed so they don't leak to system handling).
     */
    fun handleControllerKey(event: GamepadKeyBus.GamepadEvent): Boolean {
        val prefs = controllerPrefs.value
        if (!prefs.enabled) return false
        val action =
            prefs.mappings.entries.firstOrNull {
                it.key in KNOWN_ACTION_IDS && it.value.keyCode == event.keyCode
            }?.key
                ?: PlayerControllerActions.ALL.firstOrNull { it.defaultKeyCode == event.keyCode }?.id
                ?: return false
        val state = uiState.value as? PlayerUiState.Loaded ?: return false
        when (action) {
            PlayerControllerActions.SPEED_UP ->
                if (event.isPress) {
                    if (speedBeforeHold == null) speedBeforeHold = state.playbackSpeed
                    setPlaybackSpeed((state.playbackSpeed * 2f).coerceIn(MIN_SPEED, MAX_SPEED))
                } else {
                    speedBeforeHold?.let { setPlaybackSpeed(it) }
                    speedBeforeHold = null
                }

            PlayerControllerActions.SPEED_DOWN ->
                if (event.isPress) {
                    if (speedBeforeHold == null) speedBeforeHold = state.playbackSpeed
                    setPlaybackSpeed((state.playbackSpeed / 2f).coerceIn(MIN_SPEED, MAX_SPEED))
                } else {
                    speedBeforeHold?.let { setPlaybackSpeed(it) }
                    speedBeforeHold = null
                }

            else ->
                if (event.isPress) {
                    when (action) {
                        PlayerControllerActions.PLAY_PAUSE ->
                            if (state.isPlaying) pause() else resume()

                        PlayerControllerActions.SEEK_BACK ->
                            seekBy(-prefs.seekBackSeconds * 1000L)

                        PlayerControllerActions.SEEK_FORWARD ->
                            seekBy(prefs.seekForwardSeconds * 1000L)

                        PlayerControllerActions.NEXT -> skipNext()
                        PlayerControllerActions.PREVIOUS -> skipPrevious()

                        // Back-style key in fullscreen exits fullscreen
                        // first (same as the app's back handler);
                        // otherwise it closes the player.
                        PlayerControllerActions.CLOSE ->
                            if (state.isFullscreen) exitFullscreen() else close()

                        PlayerControllerActions.BRIGHTNESS_UP ->
                            setBrightness((state.brightness + 0.1f).coerceIn(0f, 1f))

                        PlayerControllerActions.BRIGHTNESS_DOWN ->
                            setBrightness((state.brightness - 0.1f).coerceIn(0f, 1f))

                        PlayerControllerActions.VOLUME_UP ->
                            setVolume((state.volume + 0.1f).coerceIn(0f, 1f))

                        PlayerControllerActions.VOLUME_DOWN ->
                            setVolume((state.volume - 0.1f).coerceIn(0f, 1f))

                        else -> {}
                    }
                }
        }
        return true
    }

    fun toggleFullscreen() {
        scope.launch {
            playerRepository.toggleFullscreen()
        }
    }

    fun minimize() {
        scope.launch {
            playerRepository.minimize()
        }
    }

    fun exitFullscreen() {
        scope.launch {
            playerRepository.exitFullscreen()
        }
    }

    fun exitMiniPlayer() {
        scope.launch {
            playerRepository.exitMiniPlayer()
        }
    }

    fun close() {
        speedBeforeHold = null
        PlayerEventBus.emit(PlayerEvent.Closed)
        // A closed player session consumes the current video — drop it
        // from the queue so it doesn't linger at the head afterwards.
        // (playerRepository.close() nulls currentVideo, so the queue's
        // "current stays first" collector won't re-add it.)
        val current = playerRepository.playerState.value.currentVideo
        if (current != null) {
            playbackQueueRepository.remove(current.url)
        }
        scope.launch {
            playerRepository.close()
        }
    }

    fun skipNext() {
        PlayerEventBus.emit(PlayerEvent.NextRequested)
        playbackQueueRepository.playNext()
    }

    fun skipPrevious() {
        PlayerEventBus.emit(PlayerEvent.PreviousRequested)
        scope.launch {
            // TODO: Implement queue navigation
        }
    }

    fun cycleLoopMode() {
        val next = (playerRepository.loopMode.value + 1) % 3
        playerRepository.setLoopMode(next)
    }
}

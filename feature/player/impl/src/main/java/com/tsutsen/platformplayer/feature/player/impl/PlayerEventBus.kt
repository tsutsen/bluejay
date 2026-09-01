package com.tsutsen.platformplayer.feature.player.impl

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Player action events — every transport action is published here
 * regardless of origin (touch gestures, controller, companion screen, ...),
 * so any consumer sees the full action stream.
 *
 * Current consumer: the on-screen action badges in [PlayerView] (seek,
 * speed, volume, brightness). Some events have no badge yet
 * (play/pause, next/previous, closed) — they still pass through so
 * badges or other feedback can be added later without rewiring the
 * action origins.
 */
sealed interface PlayerEvent {
    /** Relative seek; [deltaMs] < 0 = backwards. */
    data class Seek(val deltaMs: Long) : PlayerEvent

    data class PlaybackSpeedChanged(val speed: Float) : PlayerEvent

    data class VolumeChanged(val level: Float) : PlayerEvent

    data class BrightnessChanged(val level: Float) : PlayerEvent

    data class PlayPauseToggled(val isPlaying: Boolean) : PlayerEvent

    data object NextRequested : PlayerEvent

    data object PreviousRequested : PlayerEvent

    data object Closed : PlayerEvent
}

object PlayerEventBus {
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)

    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    fun emit(event: PlayerEvent) {
        _events.tryEmit(event)
    }
}

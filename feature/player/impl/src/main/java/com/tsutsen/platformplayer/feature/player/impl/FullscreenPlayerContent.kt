package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.ExoPlayer

/**
 * FULLSCREEN mode: video fills the whole screen, no detail page underneath (unlike
 * NORMAL/COMPACT, which keep the scrollable video-details LazyColumn around). Reuses the same
 * [PlayerControlsScaffold] as [WindowedPlayerContent] for the gesture layer / indicators /
 * top+bottom bars, since those behave identically here - only the video area is full-size
 * instead of the scroll-driven `playerHeightPx`.
 */
@Composable
fun FullscreenPlayerContent(
    player: ExoPlayer?,
    state: PlayerUiState.Loaded,
    isLoading: Boolean,
    brightnessValue: Float,
    volumeValue: Float,
    showBrightnessIndicator: Boolean,
    showVolumeIndicator: Boolean,
    showTopOverlay: Boolean,
    showBottomOverlay: Boolean,
    gestureCallbacks: PlayerGestureCallbacks,
    onMinimize: () -> Unit,
    onReplayToggle: () -> Unit,
    onWatchLater: () -> Unit,
    onOptions: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    isScrubbing: Boolean,
    scrubPositionMs: Long
) {
    Box(modifier = Modifier.fillMaxSize()) {
        PlayerVideoSurface(player = player)

        // Gesture layer (vertical drag enabled in FULLSCREEN for brightness/volume)
        PlayerGestureLayer(
            modifier = Modifier.fillMaxSize(),
            callbacks = gestureCallbacks,
            mode = PlayerMode.FULLSCREEN
        )

        // Brightness/Volume indicators
        if (showBrightnessIndicator) {
            BrightnessIndicator(brightness = brightnessValue, modifier = Modifier.align(Alignment.CenterStart))
        }
        if (showVolumeIndicator) {
            VolumeIndicator(volume = volumeValue, modifier = Modifier.align(Alignment.CenterEnd))
        }

        // Top bar
        if (showTopOverlay) {
            TopOverlay(
                title = state.currentVideo?.title ?: "Unknown",
                channelName = state.currentVideo?.author?.name ?: "Unknown",
                onMinimize = onMinimize,
                onReplayToggle = onReplayToggle,
                onWatchLater = onWatchLater,
                onOptions = onOptions
            )
        }

        // Bottom bar
        if (showBottomOverlay) {
            BottomOverlay(
                player = player,
                currentPositionMs = state.currentPositionMs,
                durationMs = state.durationMs,
                isPlaying = state.isPlaying,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onChapters = onChapters,
                onFullscreen = onFullscreenToggle,
                onSeek = onSeek,
                isScrubbing = isScrubbing,
                scrubPositionMs = scrubPositionMs
            )
        }
    }
}

package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * ==================== Action badges (PlayerEventBus) ====================
 * Badges are driven by the player event bus, not the gesture
 * handler, so the same badges show for actions from the
 * controller, the companion screen, etc. — not just gestures.
 */
@Composable
internal fun PlayerEventBadgeEffects(
    viewModel: PlayerViewModel,
    state: PlayerUiState.Loaded,
    badgeState: MutableState<GestureBadgeState>,
    badgeKeepAliveCounter: MutableState<Int>,
    seekPreviewMs: MutableState<Long?>,
    pendingSeekMs: MutableState<Long?>,
) {
    LaunchedEffect(Unit) {
        // All badges (seek, speed, volume, brightness, hold-seek
        // preview) share ONE element: every event emits a
        // [GestureBadgeState]; the overlay's session keep-alive resets
        // the hide timer on each emission, so no per-badge timer job.
        val seekAccumulator = SeekBadgeAccumulator()

        fun formatTimestamp(ms: Long): String {
            val totalSec = (ms / 1000).toInt()
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }

        fun showBadge(key: String, icon: ImageVector, label: String = "", progress: Float? = null) {
            badgeKeepAliveCounter.value++
            badgeState.value =
                GestureBadgeState(
                    key = key,
                    label = label,
                    icon = icon,
                    progress = progress,
                    visible = true,
                    keepAlive = badgeKeepAliveCounter.value,
                )
        }

        PlayerEventBus.events.collect { event ->
            when (event) {
                is PlayerEvent.Seek -> {
                    val totalMs = seekAccumulator.accumulate(event.deltaMs, System.currentTimeMillis())
                    val seconds = totalMs / 1000
                    showBadge(
                        key = if (event.deltaMs < 0) "rewind_back" else "rewind_forward",
                        label = if (seconds > 0) "+${seconds}s" else "${seconds}s",
                        icon = if (event.deltaMs < 0) Icons.Default.Replay10 else Icons.Default.Forward10,
                    )
                }

                is PlayerEvent.PlaybackSpeedChanged ->
                    showBadge(
                        key = "speed",
                        // "2.00" -> "2.0", "0.25" -> "0.25", + the × glyph.
                        label =
                            run {
                                var t = "%.2f".format(event.speed).trimEnd('0')
                                if (t.endsWith(".")) t += "0"
                                "$t×"
                            },
                        icon = Icons.Outlined.Speed,
                    )

                is PlayerEvent.SeekPreview -> {
                    seekPreviewMs.value = event.targetMs
                    showBadge(
                        key = "seek_preview",
                        label = formatTimestamp(event.targetMs),
                        icon =
                            if (event.targetMs >= viewModel.positionMs.value)
                                Icons.Default.FastForward
                            else Icons.Default.FastRewind,
                        progress =
                            (event.targetMs.toFloat() /
                                state.durationMs.coerceAtLeast(1L))
                                .coerceIn(0f, 1f),
                    )
                }

                is PlayerEvent.SeekCommitted -> {
                    // The scrub settled. Hand the target to the
                    // pending holder (the playhead keeps showing it
                    // until the live position catches up — nulling
                    // the preview outright flashes the old position
                    // for a frame while ExoPlayer applies the seek).
                    pendingSeekMs.value = event.targetMs
                    seekPreviewMs.value = null
                }

                is PlayerEvent.BrightnessChanged ->
                    showBadge("brightness", Icons.Default.BrightnessHigh, label = "${(event.level * 100).toInt()}%", progress = event.level)

                is PlayerEvent.VolumeChanged ->
                    showBadge("volume", Icons.Default.VolumeUp, label = "${(event.level * 100).toInt()}%", progress = event.level)

                // PlayPauseToggled / NextRequested / PreviousRequested /
                // Closed: no badge yet — they still pass through the bus
                // for future consumers.
                else -> Unit
            }
        }
    }
}

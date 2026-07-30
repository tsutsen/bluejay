package com.tsutsen.platformplayer.feature.player.impl.gesture

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Declarative indicator spec emitted by gesture handlers.
 *
 * Each action optionally produces an indicator. Actions like MORPH produce [None].
 * The UI layer resolves the spec into a composable overlay.
 *
 * @property key unique identifier for dedup (e.g. "brightness", "volume")
 */
sealed interface GestureIndicator {
    /** No indicator for this action (e.g. morph, seek). */
    data object None : GestureIndicator {
        override val key: String = ""
    }

    /** Progress bar indicator: icon + filled bar + percentage text. */
    data class Progress(
        override val key: String,
        val value: Float,
        val icon: ImageVector,
        val format: (Float) -> String = { "${(it * 100).toInt()}%" },
    ) : GestureIndicator

    /** Badge indicator: shows a value without a progress bar (e.g. speed). */
    data class Badge(
        override val key: String,
        val value: Float,
        val icon: ImageVector,
        val format: (Float) -> String = { "%.2fx".format(it) },
    ) : GestureIndicator

    /** Text badge: icon + pre-formatted label (e.g. "+5s", "-5s"). */
    data class TextBadge(
        override val key: String,
        val label: String,
        val icon: ImageVector,
    ) : GestureIndicator

    val key: String
}

/**
 * Resolve a [GestureAction] to its default indicator spec.
 * Override with [resolveIndicator] callback for custom behaviour.
 */
fun GestureAction.defaultIndicator(value: Float = 0f): GestureIndicator =
    when (this) {
        GestureAction.BRIGHTNESS -> GestureIndicator.Progress(
            key = "brightness",
            value = value,
            icon = Icons.Default.BrightnessHigh,
        )
        GestureAction.VOLUME -> GestureIndicator.Progress(
            key = "volume",
            value = value,
            icon = Icons.Default.VolumeUp,
        )
        GestureAction.SPEEDUP, GestureAction.SPEEDDOWN -> GestureIndicator.Badge(
            key = "speed",
            value = value,
            icon = Icons.Outlined.Speed,
        )
        else -> GestureIndicator.None
    }

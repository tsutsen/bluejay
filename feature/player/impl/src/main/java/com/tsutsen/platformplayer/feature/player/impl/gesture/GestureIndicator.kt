package com.tsutsen.platformplayer.feature.player.impl.gesture

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Declarative indicator spec for the player's on-screen overlays.
 * The UI layer resolves the spec into a composable overlay.
 *
 * @property key unique identifier for dedup (e.g. "brightness", "volume")
 */
sealed interface GestureIndicator {
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

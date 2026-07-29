package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized configuration for all player morph transition constants, thresholds,
 * and layout parameters. Mirrors the pure-function pattern of `PlayerGeometry.kt` —
 * everything that was previously scattered as `const val` across individual files
 * lives here as default-valued data-class properties.
 *
 * All defaults match the pre-refactor values exactly (zero behavior change).
 */
data class PlayerMorphConfig(
    // — Morph transition ranges —
    val morphTransitionStart: Float = 0.3f,
    val morphTransitionEnd: Float = 0.7f,
    val detailsFadeStart: Float = 0.1f,
    val detailsFadeEnd: Float = 0.4f,
    val detailsTranslateFraction: Float = 0.3f,

    // — Settled state thresholds —
    val miniDragThreshold: Float = 0.98f,
    val miniSettledThreshold: Float = 0.01f,
    val fullscreenSettledThreshold: Float = 0.01f,

    // — Gesture parameters —
    val dragTravelFraction: Float = 0.9f,
    val deadzoneProgress: Float = 0.05f,
    val doubleTapIntervalMs: Long = 300,

    // — Seek & media control —
    val seekAmountSeconds: Int = 10,
    val brightnessStepSize: Float = 0.02f,
    val volumeStepSize: Float = 0.02f,

    // — Animation timing —
    val transitionDurationMs: Int = 250,
    val animationSpeedMultiplier: Float = 1.0f,
    val autoHideMs: Long = 3000,

    // — Layout —
    val miniPlayerWidthDp: Dp = 280.dp,
    val miniPlayerAspectRatio: Float = 9f / 16f,
    val miniPlayerPaddingDp: Dp = 16.dp,

    // — Additional thresholds (formerly scattered const vals) —
    val touchSlop: Float = 12f,
    val controlsSlideDistanceDp: Int = 24,
    val collapsedControlsThreshold: Float = 0.45f,
    val morphSettleThreshold: Float = 0.4f,
    val controlsHideAtProgress: Float = 0.8f,
    val morphDragTravelFraction: Float = 0.45f,
) {
    companion object {
        val Default = PlayerMorphConfig()
    }

    fun effectiveDuration(baseMs: Int): Int =
        (baseMs / animationSpeedMultiplier).toInt().coerceIn(50, baseMs * 4)
}

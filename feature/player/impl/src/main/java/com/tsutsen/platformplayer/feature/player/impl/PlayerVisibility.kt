package com.tsutsen.platformplayer.feature.player.impl

import android.util.Log

private const val TAG = "PlayerVisibility"

/**
 * All visibility/alpha values for the player UI, computed once from a single set of inputs.
 * Mirrors the pure-function pattern of `computeVideoLayout()` — no Compose state, no side effects,
 * fully unit-testable.
 *
 * `barAlpha` is the single alpha for top+bottom overlay bars — covers both NORMAL and FULLSCREEN
 * modes. Consumers should use this, not the per-mode alphas below (kept for debugging/animation).
 */
data class ControlsVisibility(
    /** Combined alpha for top+bottom bars — use this in 99% of cases */
    val barAlpha: Float,
    val normalBarAlpha: Float,
    val fullscreenBarAlpha: Float,
    val miniControlsAlpha: Float,
    val floatingAlpha: Float,
    val detailsAlpha: Float,
    val detailsTranslateY: Float,
    /** True when top+bottom bars should be drawn (NORMAL, COMPACT, or FULLSCREEN) */
    val showBars: Boolean,
    val showMiniControls: Boolean,
    val showFloatingOverlay: Boolean,
    val showDetails: Boolean,
    /** Derived mode — the single source of truth for mode-specific logic */
    val mode: PlayerMode,
)

fun computeControlsVisibility(
    miniProgress: Float,
    fullscreenProgress: Float,
    playerHeightRatio: Float,
    controlsVisible: Boolean,
    config: PlayerMorphConfig = PlayerMorphConfig.Default,
): ControlsVisibility {
    val controlsVisibleFactor = if (controlsVisible) 1f else 0f

    // Continuous crossfade replaces isCollapsedControls boolean.
    // playerHeightRatio 0.4+ → fully normal. 0.2- → fully compact. Between → crossfade.
    val collapseAlpha = progressAlpha(
        p = playerHeightRatio,
        start = 0.2f,
        end = 0.4f,
        reversed = true
    )

    val normalMorphFade = progressAlpha(
        miniProgress, config.morphTransitionStart, config.morphTransitionEnd, reversed = true
    )
    val fullscreenMorphFade = progressAlpha(fullscreenProgress, 0f, 1f, reversed = true)

    val normalBarAlpha = normalMorphFade * fullscreenMorphFade * (1f - collapseAlpha) * controlsVisibleFactor
    val fullscreenBarAlpha = progressAlpha(fullscreenProgress, 0f, 1f) * controlsVisibleFactor
    // Single alpha that covers both NORMAL and FULLSCREEN — consumers use this
    val barAlpha = maxOf(normalBarAlpha, fullscreenBarAlpha)

    val miniControlsAlpha = progressAlpha(miniProgress, config.morphTransitionStart, config.morphTransitionEnd)
    val floatingAlpha = progressAlpha(miniProgress, config.morphTransitionStart, config.morphTransitionEnd)
    val detailsAlpha = progressAlpha(miniProgress, config.detailsFadeStart, config.detailsFadeEnd, reversed = true)
    val detailsTranslateY = miniProgress * config.detailsTranslateFraction

    Log.d(TAG, "computeControlsVisibility: barAlpha=$barAlpha normalBarAlpha=$normalBarAlpha fullscreenBarAlpha=$fullscreenBarAlpha miniControlsAlpha=$miniControlsAlpha floatingAlpha=$floatingAlpha controlsVisibleFactor=$controlsVisibleFactor")

    val mode = computePlayerMode(miniProgress, fullscreenProgress, playerHeightRatio, config)

    return ControlsVisibility(
        barAlpha = barAlpha,
        normalBarAlpha = normalBarAlpha,
        fullscreenBarAlpha = fullscreenBarAlpha,
        miniControlsAlpha = miniControlsAlpha,
        floatingAlpha = floatingAlpha,
        detailsAlpha = detailsAlpha,
        detailsTranslateY = detailsTranslateY,
        showBars = barAlpha > 0.01f,
        showMiniControls = miniControlsAlpha > 0.01f,
        showFloatingOverlay = floatingAlpha > 0.01f,
        showDetails = detailsAlpha > 0.01f,
        mode = mode,
    )
}

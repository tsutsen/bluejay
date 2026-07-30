package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.model.PlayerMode

/**
 * All visibility/alpha values for the player UI, computed once from a single set of inputs.
 * Mirrors the pure-function pattern of `computeVideoLayout()` — no Compose state, no side effects,
 * fully unit-testable.
 */
data class ControlsVisibility(
    val barAlpha: Float,
    val floatingAlpha: Float,
    val detailsAlpha: Float,
    val detailsTranslateY: Float,
    /** True when top+bottom bars should be drawn (NORMAL, COMPACT, or FULLSCREEN) */
    val showBars: Boolean,
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
    // Note: controlsVisible is no longer used here — visibility animation
    // is handled by controlsVisibleAlpha in PlayerControls.kt via animateFloatAsState.
    // This function only computes morph/fullscreen/collapse alpha.

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

    val normalBarAlpha = normalMorphFade * fullscreenMorphFade * (1f - collapseAlpha)
    val fullscreenBarAlpha = progressAlpha(fullscreenProgress, 0f, 1f)
    val barAlpha = maxOf(normalBarAlpha, fullscreenBarAlpha)

    val floatingAlpha = progressAlpha(miniProgress, config.morphTransitionStart, config.morphTransitionEnd)
    val detailsAlpha = progressAlpha(miniProgress, config.detailsFadeStart, config.detailsFadeEnd, reversed = true)
    val detailsTranslateY = miniProgress * config.detailsTranslateFraction

    val mode = computePlayerMode(miniProgress, fullscreenProgress, playerHeightRatio, config)

    return ControlsVisibility(
        barAlpha = barAlpha,
        floatingAlpha = floatingAlpha,
        detailsAlpha = detailsAlpha,
        detailsTranslateY = detailsTranslateY,
        showBars = barAlpha > 0.01f,
        showFloatingOverlay = floatingAlpha > 0.01f,
        showDetails = detailsAlpha > 0.01f,
        mode = mode,
    )
}

package com.tsutsen.platformplayer.feature.player.impl

import android.util.Log

private const val TAG = "PlayerVisibility"

/**
 * All visibility/alpha values for the player UI, computed once from a single set of inputs.
 * Mirrors the pure-function pattern of `computeVideoLayout()` — no Compose state, no side effects,
 * fully unit-testable.
 *
 * The `collapseAlpha` replaces the `isCollapsedControls` boolean with a continuous crossfade
 * driven by `playerHeightRatio`. This fixes §1B (alpha snap during scroll collapse).
 */
data class ControlsVisibility(
    val normalBarAlpha: Float,
    val compactBarAlpha: Float,
    val fullscreenBarAlpha: Float,
    val miniControlsAlpha: Float,
    val floatingAlpha: Float,
    val detailsAlpha: Float,
    val detailsTranslateY: Float,
    val showNormalTopBar: Boolean,
    val showNormalBottomBar: Boolean,
    val showCompactBar: Boolean,
    val showFullscreenBar: Boolean,
    val showMiniControls: Boolean,
    val showFloatingOverlay: Boolean,
    val showDetails: Boolean,
    /** Derived mode — the single source of truth for mode-specific logic */
    val mode: PlayerMode,
)

/**
 * Combined bar alpha that works across all modes.
 * Use this instead of normalBarAlpha or fullscreenBarAlpha individually
 * to avoid "forgot to account for fullscreen" bugs.
 */
val ControlsVisibility.combinedBarAlpha: Float
    get() = maxOf(normalBarAlpha, fullscreenBarAlpha)

/** True when top bar should be drawn (normal OR fullscreen mode) */
val ControlsVisibility.showTopBar: Boolean
    get() = showNormalTopBar || showFullscreenBar

/** True when bottom bar should be drawn (normal OR fullscreen mode) */
val ControlsVisibility.showBottomBar: Boolean
    get() = showNormalBottomBar || showFullscreenBar

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
    val compactBarAlpha = normalMorphFade * fullscreenMorphFade * collapseAlpha * controlsVisibleFactor
    val fullscreenBarAlpha = progressAlpha(fullscreenProgress, 0f, 1f) * controlsVisibleFactor
    val miniControlsAlpha = progressAlpha(miniProgress, config.morphTransitionStart, config.morphTransitionEnd)
    val floatingAlpha = progressAlpha(miniProgress, config.morphTransitionStart, config.morphTransitionEnd)
    val detailsAlpha = progressAlpha(miniProgress, config.detailsFadeStart, config.detailsFadeEnd, reversed = true)
    val detailsTranslateY = miniProgress * config.detailsTranslateFraction

    Log.d(TAG, "computeControlsVisibility: normalBarAlpha=$normalBarAlpha compactBarAlpha=$compactBarAlpha fullscreenBarAlpha=$fullscreenBarAlpha miniControlsAlpha=$miniControlsAlpha floatingAlpha=$floatingAlpha controlsVisibleFactor=$controlsVisibleFactor")

    val mode = computePlayerMode(miniProgress, fullscreenProgress, config)

    return ControlsVisibility(
        normalBarAlpha = normalBarAlpha,
        compactBarAlpha = compactBarAlpha,
        fullscreenBarAlpha = fullscreenBarAlpha,
        miniControlsAlpha = miniControlsAlpha,
        floatingAlpha = floatingAlpha,
        detailsAlpha = detailsAlpha,
        detailsTranslateY = detailsTranslateY,
        showNormalTopBar = normalBarAlpha > 0.01f,
        showNormalBottomBar = normalBarAlpha > 0.01f,
        showCompactBar = compactBarAlpha > 0.01f,
        showFullscreenBar = fullscreenBarAlpha > 0.01f,
        showMiniControls = miniControlsAlpha > 0.01f,
        showFloatingOverlay = floatingAlpha > 0.01f,
        showDetails = detailsAlpha > 0.01f,
        mode = mode,
    )
}

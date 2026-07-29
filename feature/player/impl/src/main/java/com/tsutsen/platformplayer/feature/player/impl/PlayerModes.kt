package com.tsutsen.platformplayer.feature.player.impl

import com.tsutsen.platformplayer.core.model.PlayerMode

// ==================== Overlay Mode (visual rendering states) ====================

/**
 * The four visually-distinguishable states the player overlay can be in.
 *
 * - [FLOATING]   small 16:9 draggable "mini player" window
 * - [NORMAL]     default player: video + timeline/playback controls + scrollable video details
 * - [COMPACT]    NORMAL's video box scrolled down far enough that only a slim control row fits
 * - [FULLSCREEN] video fills the entire screen, no detail page underneath
 *
 * IMPORTANT: [COMPACT] is not a separate video-rendering path. It shares NORMAL's video box
 * and detail list one-for-one - it only swaps which control row is drawn over the video.
 * Everything else (FLOATING, FULLSCREEN) is a fully separate render path.
 */
enum class PlayerOverlayMode {
    FLOATING,
    NORMAL,
    COMPACT,
    FULLSCREEN
}

/**
 * Single source of truth for which mode is active right now.
 */
fun computePlayerOverlayMode(
    isMinimized: Boolean,
    isFullscreen: Boolean,
    isCollapsedControls: Boolean
): PlayerOverlayMode = when {
    isMinimized -> PlayerOverlayMode.FLOATING
    isFullscreen -> PlayerOverlayMode.FULLSCREEN
    isCollapsedControls -> PlayerOverlayMode.COMPACT
    else -> PlayerOverlayMode.NORMAL
}

// ==================== Player Mode (logic/behavior states) ====================

/**
 * Derive discrete mode from continuous progress values.
 * Evaluated in a fixed priority order to avoid ambiguity during transitions.
 */
fun computePlayerMode(
    miniProgress: Float,
    fullscreenProgress: Float,
    playerHeightRatio: Float = 1f,
    config: PlayerMorphConfig = PlayerMorphConfig.Default,
): PlayerMode = when {
    miniProgress >= config.miniSettledThreshold -> PlayerMode.FLOATING
    fullscreenProgress >= 0.5f -> PlayerMode.FULLSCREEN
    playerHeightRatio < 0.3f -> PlayerMode.COMPACT
    else -> PlayerMode.NORMAL
}

/**
 * Smooth alpha for transitioning INTO this mode (0→1 as mode becomes active).
 * Used for fade-in animations during mode transitions.
 */
fun PlayerMode.enterAlpha(
    miniProgress: Float,
    fullscreenProgress: Float,
    config: PlayerMorphConfig = PlayerMorphConfig.Default,
): Float = when (this) {
    PlayerMode.NORMAL, PlayerMode.COMPACT -> {
        val miniFade = progressAlpha(miniProgress, 0f, config.morphTransitionStart, reversed = true)
        val fsFade = progressAlpha(fullscreenProgress, 0f, 0.5f, reversed = true)
        miniFade * fsFade
    }
    PlayerMode.FULLSCREEN -> progressAlpha(fullscreenProgress, 0f, 1f)
    PlayerMode.FLOATING -> progressAlpha(miniProgress, config.morphTransitionStart, config.morphTransitionEnd)
}

/**
 * Smooth alpha for transitioning OUT OF this mode (1→0 as mode deactivates).
 * Inverse of enterAlpha — useful for crossfade scenarios.
 */
fun PlayerMode.exitAlpha(
    miniProgress: Float,
    fullscreenProgress: Float,
    config: PlayerMorphConfig = PlayerMorphConfig.Default,
): Float = 1f - enterAlpha(miniProgress, fullscreenProgress, config)

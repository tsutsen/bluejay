package com.tsutsen.platformplayer.feature.player.impl

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

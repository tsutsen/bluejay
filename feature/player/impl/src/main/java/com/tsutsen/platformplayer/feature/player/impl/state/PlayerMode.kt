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
 * and detail list one-for-one - it only swaps which control row is drawn over the video
 * (see WindowedPlayerContent.kt). Everything else (FLOATING, FULLSCREEN) is a fully separate
 * render path with its own file.
 */
enum class PlayerMode {
    FLOATING,
    NORMAL,
    COMPACT,
    FULLSCREEN
}

/**
 * Single source of truth for which mode is active right now. All mode-branching in
 * PlayerScreen.kt should read this value rather than re-deriving the underlying booleans
 * ad hoc - that duplication (isMinimizedAnim / isFullscreenAnim / isCollapsedControls all
 * checked independently in different places) is what made the old file hard to follow.
 */
fun computePlayerMode(
    isMinimized: Boolean,
    isFullscreen: Boolean,
    isCollapsedControls: Boolean
): PlayerMode = when {
    isMinimized -> PlayerMode.FLOATING
    isFullscreen -> PlayerMode.FULLSCREEN
    isCollapsedControls -> PlayerMode.COMPACT
    else -> PlayerMode.NORMAL
}

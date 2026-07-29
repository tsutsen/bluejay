package com.tsutsen.platformplayer.feature.player.impl

/**
 * Discrete player mode derived from continuous progress values.
 *
 * Computed ONCE from miniProgress + fullscreenProgress + playerHeightRatio,
 * then passed everywhere. Consumers should NEVER derive mode themselves — use this enum.
 *
 * Transition graph:
 *   NORMAL ↔ COMPACT       (playerHeightRatio crosses midpoint)
 *   NORMAL ↔ FULLSCREEN    (fullscreenProgress crosses 0.5)
 *   NORMAL ↔ FLOATING      (miniProgress crosses settleThreshold)
 *   COMPACT ↔ FULLSCREEN   (enter fullscreen from compact)
 *   COMPACT ↔ FLOATING     (minimize from compact)
 */
enum class PlayerMode {
    /** Full embedded player — tall enough for normal controls */
    NORMAL,

    /** Collapsed embedded player — only compact controls fit */
    COMPACT,

    /** Video fills the container, system bars hidden */
    FULLSCREEN,

    /** Mini floating player anchored to corner */
    FLOATING,
}

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

package com.tsutsen.platformplayer.feature.player.impl.gesture

/**
 * Preset actions a gesture slot can hold.
 *
 * Swipe actions — strength driven by [GestureFrame.instantDelta] each frame.
 * Hold actions  — base effect on START, modulated by [GestureFrame.totalDelta]
 *                while finger drifts, restored on END.
 * Instant actions — fired once on [GestureFrame.Phase.START] (double-tap).
 */
enum class GestureAction {
    // No-op
    NONE,

    // Swipe (continuous)
    VOLUME,
    BRIGHTNESS,

    // Hold (base + optional swipe modulation)
    SPEEDUP,
    SPEEDDOWN,

    // Instant (double-tap)
    REWIND_FORWARD,
    REWIND_BACK,
    CONTEXT_MENU,

    // Morph transitions (swipe or instant depending on config)
    MORPH_TO_FLOATING,
    MORPH_TO_FULLSCREEN
}

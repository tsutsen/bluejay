package com.tsutsen.platformplayer.feature.player.impl.gesture

/**
 * The four gesture types each sector slot can recognise.
 *
 * Swipe types carry direction in the [GestureFrame.totalDelta] sign:
 *   SWIPE_VERTICAL   — negative y = up, positive y = down
 *   SWIPE_HORIZONTAL — negative x = left, positive x = right
 */
enum class GestureType {
    SWIPE_VERTICAL,
    SWIPE_HORIZONTAL,
    DOUBLE_TAP,
    HOLD
}

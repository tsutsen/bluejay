package com.tsutsen.platformplayer.feature.player.impl.gesture

/**
 * 9 equal rectangular sectors forming a 3×3 grid over the player surface.
 * Row 0 = top, Row 2 = bottom. Col 0 = left, Col 2 = right.
 */
enum class GestureSector(val row: Int, val col: Int) {
    TOP_LEFT(0, 0), TOP_CENTER(0, 1), TOP_RIGHT(0, 2),
    MIDDLE_LEFT(1, 0), MIDDLE_CENTER(1, 1), MIDDLE_RIGHT(1, 2),
    BOTTOM_LEFT(2, 0), BOTTOM_CENTER(2, 1), BOTTOM_RIGHT(2, 2);

    companion object {
        /** Resolve a touch position (in px) to the sector it falls into. */
        fun fromPosition(x: Float, y: Float, width: Float, height: Float): GestureSector {
            val col = (x / width * 3).toInt().coerceIn(0, 2)
            val row = (y / height * 3).toInt().coerceIn(0, 2)
            return entries.find { it.row == row && it.col == col } ?: MIDDLE_CENTER
        }
    }
}

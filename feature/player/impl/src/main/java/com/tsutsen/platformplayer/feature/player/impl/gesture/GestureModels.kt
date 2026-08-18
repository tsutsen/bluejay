package com.tsutsen.platformplayer.feature.player.impl.gesture

import androidx.compose.ui.geometry.Offset

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
    MORPH_TO_FULLSCREEN,

    // Direction-aware vertical morph: swipe up → FULLSCREEN, swipe down → FLOATING.
    // Direction is resolved from the sign of GestureFrame.totalDelta.y in the handler.
    MORPH_VERTICAL
}

/**
 * Lifecycle phase of a continuous gesture (swipe / hold).
 */
enum class GesturePhase { START, ACTIVE, END }

/**
 * One frame emitted during a continuous gesture (swipe or hold).
 *
 * A complete gesture produces: START → zero or more ACTIVE → END.
 *
 * @property sector       the 3×3 grid sector the gesture originated in
 * @property gestureType  SWIPE_VERTICAL, SWIPE_HORIZONTAL, or HOLD
 * @property action       the resolved [GestureAction] for this sector + type
 * @property phase        START / ACTIVE / END
 * @property instantDelta movement since the last frame (pixels)
 * @property totalDelta   cumulative movement since gesture START (pixels)
 *                         — for HOLD this is the modulation offset from
 *                           the initial hold position
 * @property elapsedMs    milliseconds since gesture START
 * @property fingerPosition current absolute finger position (pixels)
 */
data class GestureFrame(
    val sector: GestureSector,
    val gestureType: GestureType,
    val action: GestureAction,
    val phase: GesturePhase,
    val instantDelta: Offset = Offset.Zero,
    val totalDelta: Offset = Offset.Zero,
    val elapsedMs: Long = 0,
    val fingerPosition: Offset
)

/**
 * Single-shot event for instant gestures (currently DOUBLE_TAP only).
 */
data class InstantActionEvent(
    val sector: GestureSector,
    val action: GestureAction,
    val position: Offset
)

// =====================================================================
// Config models
// =====================================================================

/**
 * One sector's gesture → action mapping (4 slots).
 */
data class GestureSlotConfig(
    val swipeVertical: GestureAction = GestureAction.NONE,
    val swipeHorizontal: GestureAction = GestureAction.NONE,
    val doubleTap: GestureAction = GestureAction.NONE,
    val hold: GestureAction = GestureAction.NONE
) {
    fun resolve(type: GestureType): GestureAction = when (type) {
        GestureType.SWIPE_VERTICAL -> swipeVertical
        GestureType.SWIPE_HORIZONTAL -> swipeHorizontal
        GestureType.DOUBLE_TAP -> doubleTap
        GestureType.HOLD -> hold
    }
}

/**
 * Gesture config for one overlay mode — maps each of the 9 sectors to its slot config.
 */
data class GestureConfig(
    val sectors: Map<GestureSector, GestureSlotConfig> =
        GestureSector.entries.associateWith { GestureSlotConfig() }
) {
    fun resolve(sector: GestureSector, type: GestureType): GestureAction =
        sectors[sector]?.resolve(type) ?: GestureAction.NONE

    /** Builder-style copy with one sector updated. */
    fun withSector(sector: GestureSector, slot: GestureSlotConfig) =
        copy(sectors = sectors + (sector to slot))

    /** Builder-style copy with all sectors from a map. */
    fun withSectors(entries: Map<GestureSector, GestureSlotConfig>) =
        copy(sectors = this.sectors + entries)
}

/**
 * Gesture configs for all four overlay modes.
 */
data class GestureConfigs(
    val fullscreen: GestureConfig = GestureConfig(),
    val normal: GestureConfig = GestureConfig(),
    val compact: GestureConfig = GestureConfig(),
    val floating: GestureConfig = GestureConfig()
) {
    fun forMode(mode: com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode): GestureConfig =
        when (mode) {
            com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.FULLSCREEN -> fullscreen
            com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.NORMAL -> normal
            com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.COMPACT -> compact
            com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.FLOATING -> floating
        }
}

package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.ui.geometry.Offset

/**
 * Default gesture bindings — zone-based, mode-specific.
 *
 * Each zone has its own independent set of gestures. No precedence, no fallback.
 *
 * Fullscreen:
 * - TOP row: morph (swipe-down to exit) + speed x2 (long press)
 * - TOP_LEFT: + brightness (swipe-up/down) + rewind 5s (double-tap)
 * - TOP_RIGHT: + forward 5s (double-tap)
 * - MIDDLE_LEFT: volume (swipe-up/down) + rewind 5s (double-tap)
 * - MIDDLE_RIGHT: brightness (swipe-up/down) + forward 5s (double-tap)
 * - BOTTOM row: mini drag (swipe-up/down) + speed x2 (long press)
 *
 * Normal mode:
 * - All zones: morph (swipe-down to exit) + same double-tap/long-press as fullscreen
 *
 * Controls (seek bar, buttons) take precedence over gestures via requireUnconsumed=true
 * in the gesture recognizer — if a child consumes the touch, no gesture fires.
 */
fun defaultPlayerBindings(
    isFullscreen: Boolean,
    onMorphDrag: (dragAmount: Float) -> Unit,
    onMiniDrag: (dragAmount: Float) -> Unit,
    onBrightnessDrag: (dragAmount: Float) -> Unit,
    onVolumeDrag: (dragAmount: Float) -> Unit,
    onDoubleTapSeekLeft: () -> Unit,
    onDoubleTapSeekRight: () -> Unit,
    onDoubleTapFullscreen: () -> Unit,
    onTap: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
): GestureBindings {
    val longPressStart = DiscreteAction { _, _ -> onLongPressStart() }
    val longPressEnd = DiscreteAction { _, _ -> onLongPressEnd() }
    val doubleTapLeft = DiscreteAction { _, _ -> onDoubleTapSeekLeft() }
    val doubleTapRight = DiscreteAction { _, _ -> onDoubleTapSeekRight() }
    val doubleTapFullscreen = DiscreteAction { _, _ -> onDoubleTapFullscreen() }
    val tap = DiscreteAction { _, _ -> onTap() }

    val morphDrag = object : ContinuousAction {
        override fun onStart(zone: GestureZone, position: Offset) {}
        override fun onDelta(deltaPx: Float) { onMorphDrag(deltaPx) }
        override fun onEnd() {}
    }
    val miniDrag = object : ContinuousAction {
        override fun onStart(zone: GestureZone, position: Offset) {}
        override fun onDelta(deltaPx: Float) { onMiniDrag(deltaPx) }
        override fun onEnd() {}
    }
    val brightnessDrag = object : ContinuousAction {
        override fun onStart(zone: GestureZone, position: Offset) {}
        override fun onDelta(deltaPx: Float) { onBrightnessDrag(deltaPx) }
        override fun onEnd() {}
    }
    val volumeDrag = object : ContinuousAction {
        override fun onStart(zone: GestureZone, position: Offset) {}
        override fun onDelta(deltaPx: Float) { onVolumeDrag(deltaPx) }
        override fun onEnd() {}
    }

    // Build zone-based bindings for the current mode
    val bindings = mutableMapOf<GestureZone, ZoneBindings>()

    // Helper to add a zone with all its gestures
    fun addZone(zone: GestureZone, discrete: Map<DiscreteGesture, DiscreteAction> = emptyMap(), continuous: Map<ContinuousGesture, ContinuousAction> = emptyMap()) {
        bindings[zone] = ZoneBindings(discrete = discrete, continuous = continuous)
    }

    // TOP row: morph + speed x2 (long press) + tap (show/hide controls)
    val topDiscrete = mapOf(
        DiscreteGesture.TAP to tap,
        DiscreteGesture.LONG_PRESS_START to longPressStart,
        DiscreteGesture.LONG_PRESS_END to longPressEnd
    )
    val topContinuous = mapOf(ContinuousGesture.VERTICAL_DRAG to morphDrag)

    // TOP_LEFT: morph + brightness + rewind 5s + tap + speed x2
    addZone(
        GestureZone(GestureRow.TOP, GestureColumn.LEFT),
        discrete = topDiscrete + mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapLeft),
        continuous = topContinuous + mapOf(ContinuousGesture.VERTICAL_DRAG to brightnessDrag)
    )

    // TOP_CENTER: morph + tap + speed x2
    addZone(
        GestureZone(GestureRow.TOP, GestureColumn.CENTER),
        discrete = topDiscrete,
        continuous = topContinuous
    )

    // TOP_RIGHT: morph + forward 5s + tap + speed x2
    addZone(
        GestureZone(GestureRow.TOP, GestureColumn.RIGHT),
        discrete = topDiscrete + mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapRight),
        continuous = topContinuous + mapOf(ContinuousGesture.VERTICAL_DRAG to volumeDrag)
    )

    // MIDDLE_LEFT: volume + rewind 5s + tap + speed x2
    addZone(
        GestureZone(GestureRow.MIDDLE, GestureColumn.LEFT),
        discrete = topDiscrete + mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapLeft),
        continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to volumeDrag)
    )

    // MIDDLE_CENTER: tap + speed x2
    addZone(
        GestureZone(GestureRow.MIDDLE, GestureColumn.CENTER),
        discrete = topDiscrete
    )

    // MIDDLE_RIGHT: brightness + forward 5s + tap + speed x2
    addZone(
        GestureZone(GestureRow.MIDDLE, GestureColumn.RIGHT),
        discrete = topDiscrete + mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapRight),
        continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to brightnessDrag)
    )

    // BOTTOM row: mini drag + tap + speed x2
    val bottomDiscrete = mapOf(
        DiscreteGesture.TAP to tap,
        DiscreteGesture.LONG_PRESS_START to longPressStart,
        DiscreteGesture.LONG_PRESS_END to longPressEnd
    )
    val bottomContinuous = mapOf(ContinuousGesture.VERTICAL_DRAG to miniDrag)

    // BOTTOM_LEFT: mini drag + rewind 5s + tap + speed x2
    addZone(
        GestureZone(GestureRow.BOTTOM, GestureColumn.LEFT),
        discrete = bottomDiscrete + mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapLeft),
        continuous = bottomContinuous
    )

    // BOTTOM_CENTER: mini drag + tap + speed x2
    addZone(
        GestureZone(GestureRow.BOTTOM, GestureColumn.CENTER),
        discrete = bottomDiscrete,
        continuous = bottomContinuous
    )

    // BOTTOM_RIGHT: mini drag + forward 5s + tap + speed x2
    addZone(
        GestureZone(GestureRow.BOTTOM, GestureColumn.RIGHT),
        discrete = bottomDiscrete + mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapRight),
        continuous = bottomContinuous
    )

    // Normal mode: all zones handle morph instead of brightness/volume
    if (!isFullscreen) {
        // Override MIDDLE zones with morph + tap + same double-tap/long-press
        addZone(
            GestureZone(GestureRow.MIDDLE, GestureColumn.LEFT),
            discrete = topDiscrete + mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapLeft),
            continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to morphDrag)
        )
        addZone(
            GestureZone(GestureRow.MIDDLE, GestureColumn.CENTER),
            discrete = topDiscrete,
            continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to morphDrag)
        )
        addZone(
            GestureZone(GestureRow.MIDDLE, GestureColumn.RIGHT),
            discrete = topDiscrete + mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapRight),
            continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to morphDrag)
        )
    }

    return GestureBindings(byZone = bindings)
}

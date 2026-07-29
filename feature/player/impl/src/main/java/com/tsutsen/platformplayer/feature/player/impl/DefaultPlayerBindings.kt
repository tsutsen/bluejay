package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.ui.geometry.Offset

/**
 * Default gesture bindings matching current app behavior.
 *
 * Row precedence: TOP row handles fullscreen drag + 2x hold, BOTTOM row handles minimize drag + 2x hold.
 * Column precedence: LEFT/RIGHT columns handle brightness/volume (MIDDLE row only, since rows win for drag),
 *                     double-tap seek (all rows), fullscreen toggle (CENTER column).
 * Global: TAP toggles controls visibility.
 */
fun defaultPlayerBindings(
    onFullscreenDrag: (dragAmount: Float) -> Unit,
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

    val fullscreenDrag = object : ContinuousAction {
        override fun onStart(zone: GestureZone, position: Offset) {}
        override fun onDelta(deltaPx: Float) { onFullscreenDrag(deltaPx) }
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

    return GestureBindings(
        byRow = mapOf(
            GestureRow.TOP to ZoneBindings(
                discrete = mapOf(
                    DiscreteGesture.LONG_PRESS_START to longPressStart,
                    DiscreteGesture.LONG_PRESS_END to longPressEnd,
                ),
                continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to fullscreenDrag)
            ),
            GestureRow.BOTTOM to ZoneBindings(
                discrete = mapOf(
                    DiscreteGesture.LONG_PRESS_START to longPressStart,
                    DiscreteGesture.LONG_PRESS_END to longPressEnd,
                ),
                continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to miniDrag)
            )
        ),
        byColumn = mapOf(
            GestureColumn.LEFT to ZoneBindings(
                discrete = mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapLeft),
                continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to brightnessDrag)
            ),
            GestureColumn.RIGHT to ZoneBindings(
                discrete = mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapRight),
                continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to volumeDrag)
            ),
            GestureColumn.CENTER to ZoneBindings(
                discrete = mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapFullscreen)
            )
        ),
        global = ZoneBindings(
            discrete = mapOf(DiscreteGesture.TAP to tap)
        )
    )
}

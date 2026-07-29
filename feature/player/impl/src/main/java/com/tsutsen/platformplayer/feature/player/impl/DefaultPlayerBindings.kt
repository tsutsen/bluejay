package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.ui.geometry.Offset

/**
 * Default gesture bindings matching current app behavior.
 *
 * Row precedence: TOP row handles brightness (left) / volume (right) + 2x hold,
 *                  BOTTOM row handles minimize drag + 2x hold.
 * Column precedence: double-tap seek (LEFT/RIGHT columns, all rows),
 *                     fullscreen toggle (CENTER column).
 * Global: TAP toggles controls visibility.
 *
 * Brightness/volume are fullscreen-only (TOP row zone bindings). In normal mode (MIDDLE row)
 * there is no vertical drag binding — only tap/double-tap actions.
 */
fun defaultPlayerBindings(
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
            GestureRow.BOTTOM to ZoneBindings(
                discrete = mapOf(
                    DiscreteGesture.LONG_PRESS_START to longPressStart,
                    DiscreteGesture.LONG_PRESS_END to longPressEnd,
                ),
                continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to miniDrag)
            )
        ),
        byZone = mapOf(
            GestureZone(GestureRow.TOP, GestureColumn.LEFT) to ZoneBindings(
                discrete = mapOf(
                    DiscreteGesture.LONG_PRESS_START to longPressStart,
                    DiscreteGesture.LONG_PRESS_END to longPressEnd,
                ),
                continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to brightnessDrag)
            ),
            GestureZone(GestureRow.TOP, GestureColumn.RIGHT) to ZoneBindings(
                discrete = mapOf(
                    DiscreteGesture.LONG_PRESS_START to longPressStart,
                    DiscreteGesture.LONG_PRESS_END to longPressEnd,
                ),
                continuous = mapOf(ContinuousGesture.VERTICAL_DRAG to volumeDrag)
            )
        ),
        byColumn = mapOf(
            GestureColumn.LEFT to ZoneBindings(
                discrete = mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapLeft)
            ),
            GestureColumn.RIGHT to ZoneBindings(
                discrete = mapOf(DiscreteGesture.DOUBLE_TAP to doubleTapRight)
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

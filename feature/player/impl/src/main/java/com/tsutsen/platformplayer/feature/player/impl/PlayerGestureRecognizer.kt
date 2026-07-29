package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Single gesture recognizer that classifies and dispatches gestures via a binding table.
 * Replaces hand-rolled gesture handlers in PlayerControls.kt and PlayerGestures.kt.
 *
 * Behavior:
 * 1. On down: `requireUnconsumed = true` — a touch already consumed by a child button
 *    never enters this loop (fixes the play/pause blink bug).
 * 2. Resolve zone once from the down position — not re-evaluated mid-drag.
 * 3. Track movement; classify dominant axis + direction once past touch-slop;
 *    disambiguate tap vs. double-tap (timeout + distance) vs. long-press (drag).
 * 4. Defensively re-check `change.isConsumed` before claiming the gesture.
 * 5. Look up the resolved action via `bindings.resolveDiscrete(...)` /
 *    `resolveContinuous(...)` and drive it. No business logic lives here.
 */
fun Modifier.playerGesture(
    bindings: GestureBindings,
    areaWidth: Float,
    areaHeight: Float,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val zone = resolveGestureZone(down.position, areaWidth, areaHeight)

        var totalDragY = 0f
        var pastSlop = false
        var pointerId = down.id
        var lastTapTime = 0L
        var lastTapX = 0f
        var lastTapY = 0f
        var isLongPress = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break

            // Defensively re-check consumption before claiming
            if (!pastSlop && change.isConsumed) break

            if (change.previousPressed && !change.pressed) {
                // Gesture ended
                if (!pastSlop) {
                    // Tap or double-tap
                    val now = System.currentTimeMillis()
                    val dx = change.position.x - lastTapX
                    val dy = change.position.y - lastTapY
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    val doubleTapTimeout = PlayerMorphConfig.Default.doubleTapIntervalMs

                    if (now - lastTapTime < doubleTapTimeout && dist < PlayerMorphConfig.Default.touchSlop) {
                        // Double tap
                        bindings.resolveDiscrete(zone, DiscreteGesture.DOUBLE_TAP)?.invoke(zone, change.position)
                        lastTapTime = 0L
                    } else {
                        // Single tap
                        bindings.resolveDiscrete(zone, DiscreteGesture.TAP)?.invoke(zone, change.position)
                        lastTapTime = now
                        lastTapX = change.position.x
                        lastTapY = change.position.y
                    }
                } else {
                    // Drag ended
                    if (isLongPress) {
                        bindings.resolveDiscrete(zone, DiscreteGesture.LONG_PRESS_END)?.invoke(zone, change.position)
                    }
                }
                break
            }

            val dy = change.position.y - change.previousPosition.y
            if (!pastSlop) {
                totalDragY += dy
                if (abs(totalDragY) > PlayerMorphConfig.Default.touchSlop) {
                    pastSlop = true
                    val continuousAction = bindings.resolveContinuous(zone, ContinuousGesture.VERTICAL_DRAG)
                    if (continuousAction != null) {
                        continuousAction.onStart(zone, change.position)
                        isLongPress = true
                        bindings.resolveDiscrete(zone, DiscreteGesture.LONG_PRESS_START)?.invoke(zone, change.position)
                        change.consume()
                    }
                }
            } else {
                val continuousAction = bindings.resolveContinuous(zone, ContinuousGesture.VERTICAL_DRAG)
                if (continuousAction != null) {
                    continuousAction.onDelta(dy)
                    change.consume()
                }
            }
        }
    }
}

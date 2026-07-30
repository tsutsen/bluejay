package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Single gesture recognizer that classifies and dispatches gestures via a zone-based binding table.
 * Replaces hand-rolled gesture handlers in PlayerControls.kt and PlayerGestures.kt.
 *
 * Behavior:
 * 1. On down: `requireUnconsumed = true` — a touch already consumed by a child button
 *    never enters this loop (controls take precedence over gestures).
 * 2. Resolve zone once from the down position — not re-evaluated mid-drag.
 * 3. Track movement; classify dominant axis + direction once past touch-slop;
 *    disambiguate tap vs. double-tap (timeout + distance) vs. long-press (drag).
 * 4. Defensively re-check `change.isConsumed` before claiming the gesture.
 * 5. Look up the resolved action via `bindings.value.resolveDiscrete(...)` /
 *    `resolveContinuous(...)` and drive it. No business logic lives here.
 *
 * Zone-based: each zone has its own independent set of gestures. No precedence,
 * no fallback. If a zone has no binding for a gesture, that gesture does nothing.
 *
 * IMPORTANT: `bindings` and `areaSize` are [State] objects because `pointerInput(Unit)`
 * starts its coroutine exactly once and never restarts. Without State, the coroutine
 * would capture stale values from the first composition (e.g., Size.Zero before layout).
 * Using [State] ensures every access inside the long-lived coroutine reads the current value.
 */
fun Modifier.playerGesture(
    bindings: State<GestureBindings>,
    areaSize: State<Pair<Float, Float>>,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val (areaWidth, areaHeight) = areaSize.value
        val zone = resolveGestureZone(down.position, areaWidth, areaHeight)

        var totalDragY = 0f
        var pastSlop = false
        var pointerId = down.id
        var lastTapTime = 0L
        var lastTapX = 0f
        var lastTapY = 0f
        var isLongPress = false
        var lastPosition = down.position
        var endedCleanly = false

        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                lastPosition = change.position

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
                            bindings.value.resolveDiscrete(zone, DiscreteGesture.DOUBLE_TAP)?.invoke(zone, change.position)
                            lastTapTime = 0L
                        } else {
                            // Single tap
                            bindings.value.resolveDiscrete(zone, DiscreteGesture.TAP)?.invoke(zone, change.position)
                            lastTapTime = now
                            lastTapX = change.position.x
                            lastTapY = change.position.y
                        }
                    } else {
                        // Drag ended
                        if (isLongPress) {
                            bindings.value.resolveContinuous(zone, ContinuousGesture.VERTICAL_DRAG)?.onEnd()
                            bindings.value.resolveDiscrete(zone, DiscreteGesture.LONG_PRESS_END)?.invoke(zone, change.position)
                        }
                    }
                    endedCleanly = true
                    break
                }

                val dy = change.position.y - change.previousPosition.y
                if (!pastSlop) {
                    totalDragY += dy
                    if (abs(totalDragY) > PlayerMorphConfig.Default.touchSlop) {
                        pastSlop = true
                        val continuousAction = bindings.value.resolveContinuous(zone, ContinuousGesture.VERTICAL_DRAG)
                        if (continuousAction != null) {
                            continuousAction.onStart(zone, change.position)
                            isLongPress = true
                            bindings.value.resolveDiscrete(zone, DiscreteGesture.LONG_PRESS_START)?.invoke(zone, change.position)
                            change.consume()
                        }
                    }
                } else {
                    val continuousAction = bindings.value.resolveContinuous(zone, ContinuousGesture.VERTICAL_DRAG)
                    if (continuousAction != null) {
                        continuousAction.onDelta(dy)
                        change.consume()
                    }
                }
            }
        } finally {
            // If this coroutine gets cancelled mid-drag (composable removed from the tree,
            // parent takes over the gesture, etc.), the loop above never reaches its normal
            // "drag ended" branch, so the bound action never learns the drag stopped. That
            // leaves external gesture state (e.g. drag-driven morph progress) stuck mid-gesture,
            // which is exactly the state that produced the swipe-to-morph stutter. Make sure
            // onEnd() / LONG_PRESS_END always fire exactly once, however the gesture concludes.
            if (isLongPress && !endedCleanly) {
                bindings.value.resolveContinuous(zone, ContinuousGesture.VERTICAL_DRAG)?.onEnd()
                bindings.value.resolveDiscrete(zone, DiscreteGesture.LONG_PRESS_END)?.invoke(zone, lastPosition)
            }
        }
    }
}

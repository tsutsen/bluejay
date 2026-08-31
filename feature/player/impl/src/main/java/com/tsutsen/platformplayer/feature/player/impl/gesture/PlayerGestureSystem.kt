package com.tsutsen.platformplayer.feature.player.impl.gesture

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---- gesture recognition thresholds ----
private const val SWIPE_THRESHOLD = 30f          // px to recognise slide vs jitter
private const val HOLD_TIMEOUT_MS = 500L         // ms to trigger hold
private const val DOUBLE_TAP_TIMEOUT_MS = 300L   // max gap between taps for double-tap
private const val DOUBLE_TAP_SLOP_DP = 36f       // tap drift tolerance between the two taps (dp)

/** Outcome of the decision phase. */
private enum class Decision { TAP, SLIDE }

/**
 * Unified gesture detection.
 *
 * The player surface is divided into a 3x3 grid of [GestureSector]s; each
 * sector maps the four gestures (double tap, vertical slide, horizontal
 * slide, hold) to a [GestureAction] through [GestureConfigs].
 *
 * A single [awaitEachGesture] loop owns every pointer on the surface:
 *
 *   1. **Double tap** — recognised synchronously on DOWN against the
 *      previous tap; fires [GestureActionHandler.handleInstantAction]
 *      immediately.
 *   2. **Decision phase** — the gesture is waited on until it becomes
 *      unambiguous: the finger moves [SWIPE_THRESHOLD] px → slide; the
 *      [HOLD_TIMEOUT_MS] deadline elapses → hold (a perfectly still
 *      finger emits no events, so the [withTimeout] race against the
 *      event stream is the decision itself); the finger lifts → single tap.
 *   3. **Execution phase** — the recognised gesture runs to pointer-up:
 *      continuous gestures emit START → ACTIVE… → END frames through
 *      [handler]. END is dispatched exactly once (try/finally), even if
 *      the player leaves composition mid-gesture, so indicators and
 *      playback speed can never stick.
 *
 * The pointerInput is never restarted on mode / scrub changes — those
 * are read per gesture through [rememberUpdatedState] — so a
 * recomposition cannot kill an in-flight gesture.
 *
 * Floating-mode geometry (video rect, rest position, mini size) is read from
 * [surface] inside frame-level modifier lambdas and inside the drag handlers
 * themselves (which run outside composition), so no per-frame values cross
 * this boundary as parameters.
 */
@Composable
fun PlayerGestureSystem(
    modifier: Modifier,
    surface: com.tsutsen.platformplayer.feature.player.impl.PlayerSurface,
    overlayMode: com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode,
    isLandscape: Boolean,
    gestureConfigs: GestureConfigs,
    handler: GestureActionHandler,
    isScrubbing: Boolean,
    onTap: () -> Unit,
    onMorphDragStart: () -> Unit,
    onMorphDrag: (dragY: Float) -> Unit,
    onMorphDragEnd: (dragY: Float) -> Unit,
    // Floating-mode drag params (only used when overlayMode == FLOATING)
    onOffsetChanged: (Float, Float) -> Unit = { _, _ -> },
    onExpand: () -> Unit = {},
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Survives recomposition / pointerInput restarts so an in-flight
    // deferred single-tap can still be cancelled when a second tap arrives.
    var pendingTapJob by remember { mutableStateOf<Job?>(null) }
    // The pure decision state machine; the pointerInput block below is a
    // thin adapter that feeds it events and dispatches its output.
    val recognizer = remember { PlayerGestureRecognizer() }
    val currentHandler by rememberUpdatedState(handler)
    val currentConfigs by rememberUpdatedState(gestureConfigs)
    val currentOverlayMode by rememberUpdatedState(overlayMode)
    val currentIsScrubbing by rememberUpdatedState(isScrubbing)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnMorphDragStart by rememberUpdatedState(onMorphDragStart)
    val currentOnMorphDrag by rememberUpdatedState(onMorphDrag)
    val currentOnMorphDragEnd by rememberUpdatedState(onMorphDragEnd)
    val currentOnExpand by rememberUpdatedState(onExpand)
    val currentOnOffsetChanged by rememberUpdatedState(onOffsetChanged)
    val currentDoubleTapSlopPx by rememberUpdatedState(with(density) { DOUBLE_TAP_SLOP_DP.dp.toPx() })

    Box(modifier = modifier) {
        // ---- floating mode: drag + tap ----
        if (overlayMode == com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.FLOATING) {
            Box(
                modifier = Modifier
                    // Frame-level lambdas: the hit box tracks the morphing /
                    // springing video rect without recomposition.
                    .offset {
                        val layout = surface.videoLayout(isLandscape, density)
                        IntOffset(
                            x = layout.offsetX.toInt(),
                            y = layout.offsetY.toInt()
                        )
                    }.layout { measurable, constraints ->
                        // Frame-safe sizing: re-layout only, no recomposition.
                        val layout = surface.videoLayout(isLandscape, density)
                        val placeable =
                            measurable.measure(
                                Constraints(layout.widthPx.roundToInt(), layout.widthPx.roundToInt(), layout.heightPx.roundToInt(), layout.heightPx.roundToInt())
                            )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { currentOnExpand() }
                        )
                    }
                    .pointerInput(Unit) {
                        var localOffsetX = 0f
                        var localOffsetY = 0f

                        detectDragGestures(
                            onDragStart = {
                                // Re-seeds the raw offset from the eased copy
                                // (mid-glide grabs must not pop) and flags
                                // dragging — so miniOffsetNow() below returns
                                // the exact current position.
                                surface.startMiniDrag()
                                // Read at call time (outside composition).
                                val start = surface.miniOffsetNow()
                                localOffsetX = start.x
                                localOffsetY = start.y
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                localOffsetX += dragAmount.x
                                localOffsetY += dragAmount.y
                                currentOnOffsetChanged(localOffsetX, localOffsetY)
                            },
                            onDragEnd = {
                                val edgeThreshold = 100f
                                val container = surface.containerSize.value
                                val mini = surface.miniSizePx(density)
                                val initialX = surface.floatingRestPx(density).x
                                val initialY = surface.floatingRestPx(density).y
                                val actualX = initialX + localOffsetX
                                val actualY = initialY + localOffsetY

                                var snappedX = localOffsetX
                                if (actualX < edgeThreshold) snappedX = -initialX
                                else if (actualX > container.width - mini.width - edgeThreshold)
                                    snappedX = (container.width - mini.width) - initialX

                                var snappedY = localOffsetY
                                if (actualY < edgeThreshold) snappedY = -initialY
                                else if (actualY > container.height - mini.height - edgeThreshold)
                                    snappedY = (container.height - mini.height) - initialY

                                // One atomic step: snapped target published,
                                // drag flag cleared.
                                surface.endMiniDrag(snappedX, snappedY)
                            },
                            onDragCancel = { surface.cancelMiniDrag() }
                        )
                    }
            )
        }

        // ---- non-floating: unified gesture detection ----
        if (overlayMode != com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.FLOATING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // A recognised continuous gesture ends in exactly one
                            // END frame — on release, or from the finally / catch
                            // below when the player leaves composition
                            // mid-gesture. The recognizer guards the dispatch.
                            try {
                                val down = awaitFirstDown()
                                val downPos = down.position
                                val downTime = System.currentTimeMillis()
                                val pointerId = down.id
                                val downY = downPos.y

                                // A new down cancels any pending deferred single-tap.
                                pendingTapJob?.cancel()
                                pendingTapJob = null

                                // Mode + config are read once per gesture.
                                val cfg = currentConfigs.forMode(currentOverlayMode)

                                // ---- 1. Double tap: synchronous on down ----
                                val downResult = recognizer.onDown(
                                    downPos.x, downPos.y, downTime,
                                    size.width.toFloat(), size.height.toFloat(),
                                    currentOverlayMode, cfg, currentDoubleTapSlopPx
                                )
                                if (downResult.doubleTap) {
                                    Log.d("GESTURE", "DOUBLE-TAP sector=${downResult.instantAction?.sector}")
                                    downResult.instantAction?.let {
                                        currentHandler.handleInstantAction(it)
                                    }
                                    // Drain the pointer: the second tap may still be
                                    // held down, and leftover moves must not spawn a
                                    // phantom gesture on the next iteration.
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change =
                                            event.changes.lastOrNull { it.id == pointerId } ?: continue
                                        if (!change.pressed) break
                                        change.consume()
                                    }
                                    return@awaitEachGesture
                                }

                                // While the timeline is being scrubbed, swallow the gesture.
                                if (currentIsScrubbing) {
                                    Log.d("GESTURE", "swallowed: isScrubbing=true")
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change =
                                            event.changes.lastOrNull { it.id == pointerId } ?: continue
                                        if (!change.pressed) break
                                        change.consume()
                                    }
                                    return@awaitEachGesture
                                }

                                // ---- 2. Decision phase ----
                                //
                                // Hold is decided by an external watchdog: a plain
                                // coroutine on the composable scope with a real
                                // timer. A perfectly still finger emits NO pointer
                                // events, and this restricted scope may only suspend
                                // on bare awaitPointerEvent() calls (withTimeout
                                // around it cancels the whole gesture coroutine at
                                // the deadline instead of just the wait) — so the
                                // 500ms deadline can never be detected from inside
                                // the loop. The watchdog fires the hold once via
                                // recognizer.onHoldTimeout; the loop below wakes on
                                // every move/release, drives hold modulation, ends
                                // the hold (onUp / cancel), and defers the tap when
                                // nothing fired.
                                val holdWatchdog = scope.launch {
                                    delay(PlayerGestureRecognizer.HOLD_TIMEOUT_MS)
                                    recognizer.onHoldTimeout(System.currentTimeMillis())
                                        ?.let {
                                            Log.d("GESTURE", "HOLD fired: sector=${it.sector} action=${it.action}")
                                            currentHandler.handleGestureFrame(it)
                                        }
                                }
                                try {
                                    var slide: PlayerGestureRecognizer.MoveResult.SlideStart? = null
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.lastOrNull { it.id == pointerId } ?: continue
                                        if (!change.pressed) {
                                            when (val up = recognizer.onUp(System.currentTimeMillis())) {
                                                is PlayerGestureRecognizer.UpResult.TapDeferred -> {
                                                    // Defer: the first tap of a double tap must not fire.
                                                    pendingTapJob = scope.launch {
                                                        delay(PlayerGestureRecognizer.DOUBLE_TAP_TIMEOUT_MS)
                                                        currentOnTap()
                                                    }
                                                }
                                                is PlayerGestureRecognizer.UpResult.End ->
                                                    up.frame?.let { currentHandler.handleGestureFrame(it) }
                                            }
                                            break
                                        }
                                        change.consume()
                                        when (val move = recognizer.onMove(
                                            change.position.x, change.position.y,
                                            System.currentTimeMillis()
                                        )) {
                                            is PlayerGestureRecognizer.MoveResult.Idle ->
                                                move.frames.forEach { currentHandler.handleGestureFrame(it) }
                                            is PlayerGestureRecognizer.MoveResult.SlideStart -> {
                                                move.frames.forEach { currentHandler.handleGestureFrame(it) }
                                                slide = move
                                                break
                                            }
                                        }
                                    }

                                    // ---- 3. Execution phase ----
                                    if (slide != null) {
                                        if (slide.morphDrag) {
                                            // Live morph-to-floating drag: callbacks, no frames.
                                            currentOnMorphDragStart()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.lastOrNull { it.id == pointerId } ?: continue
                                                if (!change.pressed) {
                                                    currentOnMorphDragEnd(
                                                        (change.position.y - downY).coerceAtLeast(0f)
                                                    )
                                                    recognizer.onSlideEnd(System.currentTimeMillis())
                                                        ?.let { currentHandler.handleGestureFrame(it) }
                                                    break
                                                }
                                                change.consume()
                                                currentOnMorphDrag(
                                                    (change.position.y - downY).coerceAtLeast(0f)
                                                )
                                            }
                                        } else if (slide.startFrame != null) {
                                            currentHandler.handleGestureFrame(slide.startFrame)
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.lastOrNull { it.id == pointerId } ?: continue
                                                if (!change.pressed) {
                                                    recognizer.onSlideEnd(System.currentTimeMillis())
                                                        ?.let { currentHandler.handleGestureFrame(it) }
                                                    break
                                                }
                                                change.consume()
                                                recognizer.onSlideMove(
                                                    change.position.x, change.position.y,
                                                    System.currentTimeMillis()
                                                )?.let { currentHandler.handleGestureFrame(it) }
                                            }
                                        } else {
                                            // No action: just wait for release.
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change =
                                                    event.changes.lastOrNull { it.id == pointerId } ?: continue
                                                if (!change.pressed) break
                                                change.consume()
                                            }
                                        }
                                    }
                                } finally {
                                    holdWatchdog.cancel()
                                    recognizer.cancel(System.currentTimeMillis())
                                        ?.let { currentHandler.handleGestureFrame(it) }
                                }
                            } catch (e: CancellationException) {
                                // Ensure an in-flight hold/slide still gets its END
                                // frame (speed reset, keep-alive cancel) when the
                                // player leaves composition mid-gesture.
                                recognizer.cancel(System.currentTimeMillis())
                                    ?.let { currentHandler.handleGestureFrame(it) }
                                Log.d("GESTURE", "gesture iteration CANCELLED")
                                throw e
                            }
                        }
                    }
            )
        }
    }
}

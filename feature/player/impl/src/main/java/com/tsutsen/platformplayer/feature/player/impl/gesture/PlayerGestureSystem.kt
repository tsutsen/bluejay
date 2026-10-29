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
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
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
 *      previous tap; [GesturePipeline] fires the instant action
 *      immediately.
 *   2. **Decision phase** — the gesture is waited on until it becomes
 *      unambiguous: the finger moves [SWIPE_THRESHOLD] px → slide; the
 *      [HOLD_TIMEOUT_MS] deadline elapses → hold (a perfectly still
 *      finger emits no events, so the [withTimeout] race against the
 *      event stream is the decision itself); the finger lifts → single tap.
 *   3. **Execution phase** — the recognised gesture runs to pointer-up:
 *      [GesturePipeline] emits START → ACTIVE… → END frames internally
 *      (morph slides included — their frames reach the drag callbacks).
 *      END is dispatched exactly once (try/finally), even if the player
 *      leaves composition mid-gesture, so indicators and playback speed
 *      can never stick.
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
    pipeline: GesturePipeline,
    isScrubbing: Boolean,
    onTap: () -> Unit,
    // Pinch-to-zoom (non-floating mode only): called with an incremental
    // scale factor (1.0 = no change) on every pinch move while two
    // pointers are down. The caller owns the accumulated scale.
    onPinchScale: (Float) -> Unit = {},
    // Zoom-pan (non-floating mode only): while the video is zoomed in, single-finger
    // drags pan the zoom window instead of the sector action. Called with per-move
    // SCREEN-px deltas; the caller accumulates and clamps the offset.
    onZoomPan: (Float, Float) -> Unit = { _, _ -> },
    // Read at gesture start: true while the video is zoomed in.
    zoomActive: () -> Boolean = { false },
    // Floating-mode drag params (only used when overlayMode == FLOATING)
    onOffsetChanged: (Float, Float) -> Unit = { _, _ -> },
    onExpand: () -> Unit = {},
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Survives recomposition / pointerInput restarts so an in-flight
    // deferred single-tap can still be cancelled when a second tap arrives.
    var pendingTapJob by remember { mutableStateOf<Job?>(null) }
    // The deep seam (recognizer + action dispatch); the pointerInput block
    // below is a thin adapter that feeds it events and reacts to its two
    // Compose-visible outcomes (double tap, deferred tap).
    val currentPipeline by rememberUpdatedState(pipeline)
    val currentConfigs by rememberUpdatedState(gestureConfigs)
    val currentOverlayMode by rememberUpdatedState(overlayMode)
    val currentIsScrubbing by rememberUpdatedState(isScrubbing)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnPinchScale by rememberUpdatedState(onPinchScale)
    val currentOnZoomPan by rememberUpdatedState(onZoomPan)
    val currentZoomActive by rememberUpdatedState(zoomActive)
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
                            // mid-gesture. The pipeline guards the dispatch.
                            try {
                                val down = awaitFirstDown()
                                val downPos = down.position
                                val downTime = System.currentTimeMillis()
                                val pointerId = down.id

                                // A new down cancels any pending deferred single-tap.
                                pendingTapJob?.cancel()
                                pendingTapJob = null

                                // Mode + config are read once per gesture.
                                val cfg = currentConfigs.forMode(currentOverlayMode)

                                // ---- 1. Double tap: synchronous on down ----
                                val doubleTap = currentPipeline.onDown(
                                    downPos.x, downPos.y, downTime,
                                    size.width.toFloat(), size.height.toFloat(),
                                    currentOverlayMode, cfg, currentDoubleTapSlopPx
                                )
                                if (doubleTap) {
                                    Log.d("GESTURE", "DOUBLE-TAP")
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
                                // pipeline.onHoldTimeout; the loop below wakes on
                                // every move/release, drives hold modulation, ends
                                // the hold (onUp / cancel), and defers the tap when
                                // nothing fired.
                                // Zoom-pan mode: while the video is zoomed in, a
                                // single-finger drag past the swipe threshold pans the
                                // zoom window instead of the sector action, and the
                                // hold is suppressed (the finger is positioning the
                                // view, not scrubbing or changing speed). A tap with
                                // no movement still falls through to the recognizer's
                                // deferred-tap path below.
                                val zoomPanMode = currentZoomActive()
                                val holdWatchdog: Job? =
                                    if (zoomPanMode) {
                                        null
                                    } else {
                                        scope.launch {
                                            delay(PlayerGestureRecognizer.HOLD_TIMEOUT_MS)
                                            currentPipeline.onHoldTimeout(System.currentTimeMillis())
                                        }
                                    }
                                try {
                                    var slide: GesturePipeline.MoveResult.SlideStarted? = null
                                    // Pinch state: set when a second pointer lands
                                    // while the first is down. The gesture becomes
                                    // a pinch-to-zoom from that moment on.
                                    var pinchSecondId: PointerId? = null
                                    var pinchPrevDist = 0f
                                    // Zoom-pan state (used when zoomPanMode): the last
                                    // position deltas were measured from, the
                                    // accumulated drag distance (slop detection), and
                                    // whether the pan has been activated.
                                    var panLastX = downPos.x
                                    var panLastY = downPos.y
                                    var panTotalX = 0f
                                    var panTotalY = 0f
                                    var panStarted = false
                                    // Two-finger centroid for zoom-panning during a
                                    // pinch (only tracked while zoomed in).
                                    var pinchPrevCx = 0f
                                    var pinchPrevCy = 0f
                                    var pinchHasPrevCentroid = false
                                    while (true) {
                                        val event = awaitPointerEvent()

                                        // ---- 2a. Pinch: a second pointer has landed ----
                                        //
                                        // The pinch lives in this loop instead of a
                                        // separate detectTransformGestures layer
                                        // above it: that detector (foundation
                                        // 1.12.0-beta01) tracks the centroid of
                                        // EVERY pressed pointer as pan and, once a
                                        // single finger drifts past touch slop,
                                        // consumes all later events — starving
                                        // every single-finger gesture below it.
                                        // Here a second pointer is only ever
                                        // interesting while the first is down, and
                                        // it consumes nothing before that moment.
                                        if (pinchSecondId == null) {
                                            val second =
                                                event.changes.firstOrNull { it.id != pointerId && it.pressed }
                                            if (second != null) {
                                                pinchSecondId = second.id
                                                // The single-finger decision is
                                                // over: kill the hold watchdog and
                                                // flush the hold/slide END frame
                                                // (if a hold already fired) so
                                                // nothing sticks.
                                                holdWatchdog?.cancel()
                                                currentPipeline.cancel(System.currentTimeMillis())
                                                val first = event.changes.firstOrNull { it.id == pointerId }
                                                if (first != null && first.pressed) {
                                                    pinchPrevDist = pointerDistance(first, second)
                                                }
                                            }
                                        }
                                        if (pinchSecondId != null) {
                                            // The pinch owns every pointer of this
                                            // gesture from here on — consume all
                                            // changes (including the first
                                            // finger's up, so it can never be
                                            // mistaken for a tap).
                                            event.changes.forEach { it.consume() }
                                            val first = event.changes.firstOrNull { it.id == pointerId }
                                            val second = event.changes.firstOrNull { it.id == pinchSecondId }
                                            if (first != null && second != null &&
                                                first.pressed && second.pressed
                                            ) {
                                                val dist = pointerDistance(first, second)
                                                if (dist > 0f && pinchPrevDist > 0f) {
                                                    currentOnPinchScale(dist / pinchPrevDist)
                                                }
                                                pinchPrevDist = dist
                                                // While zoomed in, two-finger drags also pan the
                                                // window: track the centroid motion alongside
                                                // the pinch scale.
                                                if (currentZoomActive()) {
                                                    val cx = (first.position.x + second.position.x) / 2f
                                                    val cy = (first.position.y + second.position.y) / 2f
                                                    if (pinchHasPrevCentroid) {
                                                        currentOnZoomPan(cx - pinchPrevCx, cy - pinchPrevCy)
                                                    }
                                                    pinchPrevCx = cx
                                                    pinchPrevCy = cy
                                                    pinchHasPrevCentroid = true
                                                } else {
                                                    pinchHasPrevCentroid = false
                                                }
                                            }
                                            // Both fingers up: pinch done. End the
                                            // gesture with no tap/hold/slide —
                                            // slide is null, so the execution
                                            // phase below is skipped, and the
                                            // finally flush is a no-op (the END
                                            // frame was already sent on entry).
                                            if (first?.pressed != true && second?.pressed != true) break
                                            continue
                                        }

                                        // ---- 2b. Zoom-pan: the video is zoomed in ----
                                        //
                                        // The single-finger recognizer path below is bypassed: a drag past
                                        // the swipe threshold reports raw screen-px deltas as a pan, the
                                        // hold was already disabled (see zoomPanMode), and a release without
                                        // a pan falls through to the recognizer's onUp — which has only
                                        // seen down + up, so it defers exactly like a tap.
                                        if (zoomPanMode) {
                                            val panChange = event.changes.lastOrNull { it.id == pointerId } ?: continue
                                            if (!panChange.pressed) {
                                                if (!panStarted &&
                                                    currentPipeline.onUp(System.currentTimeMillis())
                                                ) {
                                                    pendingTapJob = scope.launch {
                                                        delay(PlayerGestureRecognizer.DOUBLE_TAP_TIMEOUT_MS)
                                                        currentOnTap()
                                                    }
                                                }
                                                break
                                            }
                                            panChange.consume()
                                            val panDx = panChange.position.x - panLastX
                                            val panDy = panChange.position.y - panLastY
                                            panLastX = panChange.position.x
                                            panLastY = panChange.position.y
                                            panTotalX += panDx
                                            panTotalY += panDy
                                            if (!panStarted && hypot(panTotalX, panTotalY) > SWIPE_THRESHOLD) {
                                                panStarted = true
                                            }
                                            if (panStarted) currentOnZoomPan(panDx, panDy)
                                            continue
                                        }
                                        val change = event.changes.lastOrNull { it.id == pointerId } ?: continue
                                        if (!change.pressed) {
                                            if (currentPipeline.onUp(System.currentTimeMillis())) {
                                                // Defer: the first tap of a double tap must not fire.
                                                pendingTapJob = scope.launch {
                                                    delay(PlayerGestureRecognizer.DOUBLE_TAP_TIMEOUT_MS)
                                                    currentOnTap()
                                                }
                                            }
                                            break
                                        }
                                        change.consume()
                                        when (
                                            val move = currentPipeline.onMove(
                                                change.position.x, change.position.y,
                                                System.currentTimeMillis()
                                            )
                                        ) {
                                            GesturePipeline.MoveResult.Idle -> {}
                                            is GesturePipeline.MoveResult.SlideStarted -> {
                                                slide = move
                                                break
                                            }
                                        }
                                    }

                                    // ---- 3. Execution phase ----
                                    // Morph slides use the same frame path as every
                                    // other slide: the START/ACTIVE/END frames carry
                                    // the resolved action, and the pipeline routes
                                    // morph frames to the onMorphDrag*/onShrinkDrag*/
                                    // onFullscreenDrag* callbacks.
                                    // Only slides reach here with the finger still
                                    // down (the decision loop breaks on release for
                                    // taps and hold-ends), so only slides drain.
                                    // A tap or hold-end must end the block
                                    // immediately — waiting for more events from an
                                    // already released pointer hangs this block
                                    // forever and deafens the whole layer until the
                                    // Box is recomposed (e.g. a floating round trip).
                                    if (slide != null) {
                                        if (slide.bound) {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.lastOrNull { it.id == pointerId } ?: continue
                                                if (!change.pressed) {
                                                    currentPipeline.onSlideEnd(System.currentTimeMillis())
                                                    break
                                                }
                                                change.consume()
                                                currentPipeline.onSlideMove(
                                                    change.position.x, change.position.y,
                                                    System.currentTimeMillis()
                                                )
                                            }
                                        } else {
                                            // Unbound slide: no action — drain until
                                            // release so the next gesture starts clean.
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
                                    holdWatchdog?.cancel()
                                    currentPipeline.cancel(System.currentTimeMillis())
                                }
                            } catch (e: CancellationException) {
                                // Ensure an in-flight hold/slide still gets its END
                                // frame (speed reset, keep-alive cancel) when the
                                // player leaves composition mid-gesture.
                                currentPipeline.cancel(System.currentTimeMillis())
                                Log.d("GESTURE", "gesture iteration CANCELLED")
                                throw e
                            } catch (e: Throwable) {
                                // Never let a pipeline exception deafen the layer:
                                // log it, flush the END frame, and
                                // keep listening for the next gesture.
                                Log.w("GESTURE", "gesture iteration failed (recovering)", e)
                                runCatching { currentPipeline.cancel(System.currentTimeMillis()) }
                                    .onFailure { err ->
                                        Log.w("GESTURE", "end-frame dispatch failed", err)
                                    }
                            }
                        }
                    }
            )
        }
    }
}

/**
 * Euclidean distance between two pressed pointers in px — the pinch's size
 * measurement. The per-move scale handed to [PlayerGestureSystem.onPinchScale]
 * is the ratio of successive distances (1.0 while the fingers hold still).
 */
private fun pointerDistance(a: PointerInputChange, b: PointerInputChange): Float {
    val dx = a.position.x - b.position.x
    val dy = a.position.y - b.position.y
    return sqrt(dx * dx + dy * dy)
}

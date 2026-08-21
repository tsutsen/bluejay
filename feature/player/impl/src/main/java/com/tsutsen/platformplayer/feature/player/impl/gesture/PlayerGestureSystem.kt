package com.tsutsen.platformplayer.feature.player.impl.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.math.sqrt

// ---- gesture recognition thresholds ----
private const val SWIPE_THRESHOLD = 30f          // px to recognise slide vs jitter
private const val HOLD_TIMEOUT_MS = 500L         // ms to trigger hold
private const val DOUBLE_TAP_TIMEOUT_MS = 300L   // max gap between taps for double-tap
private const val DOUBLE_TAP_SLOP_DP = 36f       // tap drift tolerance between the two taps (dp)

/** Outcome of the decision phase. */
private enum class Decision { TAP, HOLD, SLIDE }

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
 */
@Composable
fun PlayerGestureSystem(
    modifier: Modifier,
    overlayMode: com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode,
    gestureConfigs: GestureConfigs,
    handler: GestureActionHandler,
    isScrubbing: Boolean,
    containerWidth: Float,
    containerHeight: Float,
    onTap: () -> Unit,
    onMorphDragStart: () -> Unit,
    onMorphDrag: (dragY: Float) -> Unit,
    onMorphDragEnd: (dragY: Float) -> Unit,
    // Floating-mode drag params (only used when overlayMode == FLOATING)
    floatingVideoLayout: com.tsutsen.platformplayer.feature.player.impl.VideoLayout? = null,
    isDraggingMiniPlayer: Boolean = false,
    onDragStateChanged: (Boolean) -> Unit = {},
    onOffsetChanged: (Float, Float) -> Unit = { _, _ -> },
    onExpand: () -> Unit = {},
    floatingRestX: Float = 0f,
    floatingRestY: Float = 0f,
    currentOffsetX: Float = 0f,
    currentOffsetY: Float = 0f,
    miniWidthPx: Float = 0f,
    miniHeightPx: Float = 0f,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Survives recomposition / pointerInput restarts so an in-flight
    // deferred single-tap can still be cancelled when a second tap arrives.
    var pendingTapJob by remember { mutableStateOf<Job?>(null) }
    val currentHandler by rememberUpdatedState(handler)
    val currentConfigs by rememberUpdatedState(gestureConfigs)
    val currentOverlayMode by rememberUpdatedState(overlayMode)
    val currentIsScrubbing by rememberUpdatedState(isScrubbing)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnMorphDragStart by rememberUpdatedState(onMorphDragStart)
    val currentOnMorphDrag by rememberUpdatedState(onMorphDrag)
    val currentOnMorphDragEnd by rememberUpdatedState(onMorphDragEnd)
    val currentOnExpand by rememberUpdatedState(onExpand)
    val currentOnDragStateChanged by rememberUpdatedState(onDragStateChanged)
    val currentOnOffsetChanged by rememberUpdatedState(onOffsetChanged)
    val currentContainerWidth by rememberUpdatedState(containerWidth)
    val currentContainerHeight by rememberUpdatedState(containerHeight)
    val currentDoubleTapSlopPx by rememberUpdatedState(with(density) { DOUBLE_TAP_SLOP_DP.dp.toPx() })

    Box(modifier = modifier) {
        // ---- floating mode: drag + tap ----
        if (overlayMode == com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.FLOATING
            && floatingVideoLayout != null
        ) {
            val latestOffsetX by rememberUpdatedState(currentOffsetX)
            val latestOffsetY by rememberUpdatedState(currentOffsetY)
            val latestRestX by rememberUpdatedState(floatingRestX)
            val latestRestY by rememberUpdatedState(floatingRestY)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = floatingVideoLayout.offsetX.toInt(),
                            y = floatingVideoLayout.offsetY.toInt()
                        )
                    }
                    .size(
                        width = with(density) { floatingVideoLayout.widthPx.toDp() },
                        height = with(density) { floatingVideoLayout.heightPx.toDp() }
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { currentOnExpand() }
                        )
                    }
                    .pointerInput(Unit) {
                        var localOffsetX = latestOffsetX
                        var localOffsetY = latestOffsetY

                        detectDragGestures(
                            onDragStart = {
                                currentOnDragStateChanged(true)
                                localOffsetX = latestOffsetX
                                localOffsetY = latestOffsetY
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                localOffsetX += dragAmount.x
                                localOffsetY += dragAmount.y
                                currentOnOffsetChanged(localOffsetX, localOffsetY)
                            },
                            onDragEnd = {
                                currentOnDragStateChanged(false)
                                val edgeThreshold = 100f
                                val initialX = latestRestX
                                val initialY = latestRestY
                                val actualX = initialX + localOffsetX
                                val actualY = initialY + localOffsetY

                                var snappedX = localOffsetX
                                if (actualX < edgeThreshold) snappedX = -initialX
                                else if (actualX > currentContainerWidth - miniWidthPx - edgeThreshold)
                                    snappedX = (currentContainerWidth - miniWidthPx) - initialX

                                var snappedY = localOffsetY
                                if (actualY < edgeThreshold) snappedY = -initialY
                                else if (actualY > currentContainerHeight - miniHeightPx - edgeThreshold)
                                    snappedY = (currentContainerHeight - miniHeightPx) - initialY

                                currentOnOffsetChanged(snappedX, snappedY)
                            },
                            onDragCancel = { currentOnDragStateChanged(false) }
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
                        // Tap tracking persists across gestures (double-tap detection).
                        var lastTapTime = 0L
                        var lastTapPos = Offset.Zero

                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val downPos = down.position
                            val downTime = System.currentTimeMillis()
                            val pointerId = down.id
                            val downX = downPos.x
                            val downY = downPos.y

                            // A new down cancels any pending deferred single-tap.
                            pendingTapJob?.cancel()
                            pendingTapJob = null

                            // Mode + config are read once per gesture.
                            val cfg = currentConfigs.forMode(currentOverlayMode)
                            val sector = GestureSector.fromPosition(
                                downX, downY,
                                size.width.toFloat(), size.height.toFloat()
                            )

                            // A recognised continuous gesture ends in exactly one
                            // END frame — on release, or from finally when the
                            // player leaves composition mid-gesture.
                            var activeAction: GestureAction? = null
                            var activeType: GestureType? = null
                            var endSent = false

                            fun dispatchEnd() {
                                if (endSent) return
                                endSent = true
                                val action = activeAction ?: return
                                val type = activeType ?: return
                                if (action == GestureAction.NONE) return
                                currentHandler.handleGestureFrame(
                                    GestureFrame(
                                        sector = sector,
                                        gestureType = type,
                                        action = action,
                                        phase = GesturePhase.END,
                                        elapsedMs = System.currentTimeMillis() - downTime,
                                        fingerPosition = downPos
                                    )
                                )
                            }

                            // ---- 1. Double tap: synchronous on down ----
                            val now = System.currentTimeMillis()
                            val dxFromLast = downX - lastTapPos.x
                            val dyFromLast = downY - lastTapPos.y
                            val distFromLastTap = sqrt(dxFromLast * dxFromLast + dyFromLast * dyFromLast)

                            if (now - lastTapTime < DOUBLE_TAP_TIMEOUT_MS &&
                                distFromLastTap < currentDoubleTapSlopPx
                            ) {
                                lastTapTime = now
                                lastTapPos = downPos
                                val action = cfg.resolve(sector, GestureType.DOUBLE_TAP)
                                if (action != GestureAction.NONE) {
                                    currentHandler.handleInstantAction(
                                        InstantActionEvent(sector, action, downPos)
                                    )
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
                            var decision = Decision.TAP
                            var slideType = GestureType.SWIPE_VERTICAL
                            var slideDownward = false

                            while (true) {
                                val remaining = downTime + HOLD_TIMEOUT_MS - System.currentTimeMillis()
                                val event =
                                    if (remaining > 0) {
                                        try {
                                            withTimeout(remaining) { awaitPointerEvent() }
                                        } catch (_: TimeoutCancellationException) {
                                            // The hold deadline elapsed — a perfectly
                                            // still finger emits no events, so the
                                            // timeout itself is the decision.
                                            decision = Decision.HOLD
                                            break
                                        }
                                    } else {
                                        decision = Decision.HOLD
                                        break
                                    }

                                val change = event.changes.lastOrNull { it.id == pointerId } ?: continue
                                if (!change.pressed) {
                                    decision = Decision.TAP
                                    break
                                }
                                change.consume()

                                val totalDx = change.position.x - downX
                                val totalDy = change.position.y - downY
                                val totalDist = sqrt(totalDx * totalDx + totalDy * totalDy)
                                if (totalDist > SWIPE_THRESHOLD) {
                                    decision = Decision.SLIDE
                                    val isHorizontal = abs(totalDx) > abs(totalDy)
                                    slideType =
                                        if (isHorizontal) GestureType.SWIPE_HORIZONTAL
                                        else GestureType.SWIPE_VERTICAL
                                    slideDownward = !isHorizontal && totalDy > 0
                                    break
                                }
                            }

                            // ---- 3. Execution phase ----
                            try {
                                when (decision) {
                                    Decision.TAP -> {
                                        lastTapTime = now
                                        lastTapPos = downPos
                                        // Defer: the first tap of a double tap must not fire.
                                        pendingTapJob = scope.launch {
                                            delay(DOUBLE_TAP_TIMEOUT_MS)
                                            currentOnTap()
                                        }
                                    }

                                    Decision.HOLD -> {
                                        val action = cfg.resolve(sector, GestureType.HOLD)
                                        if (action != GestureAction.NONE) {
                                            activeAction = action
                                            activeType = GestureType.HOLD
                                            currentHandler.handleGestureFrame(
                                                GestureFrame(
                                                    sector = sector,
                                                    gestureType = GestureType.HOLD,
                                                    action = action,
                                                    phase = GesturePhase.START,
                                                    elapsedMs = System.currentTimeMillis() - downTime,
                                                    fingerPosition = downPos
                                                )
                                            )
                                            // Hold to the end, modulating with finger drift.
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change =
                                                    event.changes.lastOrNull { it.id == pointerId } ?: continue
                                                if (!change.pressed) break
                                                change.consume()
                                                currentHandler.handleGestureFrame(
                                                    GestureFrame(
                                                        sector = sector,
                                                        gestureType = GestureType.HOLD,
                                                        action = action,
                                                        phase = GesturePhase.ACTIVE,
                                                        totalDelta = Offset(
                                                            change.position.x - downX,
                                                            change.position.y - downY
                                                        ),
                                                        elapsedMs = System.currentTimeMillis() - downTime,
                                                        fingerPosition = change.position
                                                    )
                                                )
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

                                    Decision.SLIDE -> {
                                        val isTopRow = sector.row == 0
                                        val isMorphDrag =
                                            (currentOverlayMode ==
                                                com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.NORMAL ||
                                                currentOverlayMode ==
                                                com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.COMPACT ||
                                                (currentOverlayMode ==
                                                    com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.FULLSCREEN &&
                                                    isTopRow)) &&
                                            slideType == GestureType.SWIPE_VERTICAL &&
                                            slideDownward

                                        if (isMorphDrag) {
                                            // Live morph-to-floating drag: callbacks, no frames.
                                            currentOnMorphDragStart()
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change =
                                                    event.changes.lastOrNull { it.id == pointerId } ?: continue
                                                if (!change.pressed) {
                                                    currentOnMorphDragEnd(
                                                        (change.position.y - downY).coerceAtLeast(0f)
                                                    )
                                                    break
                                                }
                                                change.consume()
                                                currentOnMorphDrag(
                                                    (change.position.y - downY).coerceAtLeast(0f)
                                                )
                                            }
                                        } else {
                                            val action = cfg.resolve(sector, slideType)
                                            if (action != GestureAction.NONE) {
                                                activeAction = action
                                                activeType = slideType
                                                var lastPos = downPos
                                                currentHandler.handleGestureFrame(
                                                    GestureFrame(
                                                        sector = sector,
                                                        gestureType = slideType,
                                                        action = action,
                                                        phase = GesturePhase.START,
                                                        totalDelta = Offset.Zero,
                                                        elapsedMs = System.currentTimeMillis() - downTime,
                                                        fingerPosition = downPos
                                                    )
                                                )
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change =
                                                        event.changes.lastOrNull { it.id == pointerId } ?: continue
                                                    if (!change.pressed) break
                                                    change.consume()
                                                    currentHandler.handleGestureFrame(
                                                        GestureFrame(
                                                            sector = sector,
                                                            gestureType = slideType,
                                                            action = action,
                                                            phase = GesturePhase.ACTIVE,
                                                            instantDelta = Offset(
                                                                change.position.x - lastPos.x,
                                                                change.position.y - lastPos.y
                                                            ),
                                                            totalDelta = Offset(
                                                                change.position.x - downX,
                                                                change.position.y - downY
                                                            ),
                                                            elapsedMs = System.currentTimeMillis() - downTime,
                                                            fingerPosition = change.position
                                                        )
                                                    )
                                                    lastPos = change.position
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
                                    }
                                }
                            } finally {
                                dispatchEnd()
                            }
                        }
                    }
            )
        }
    }
}

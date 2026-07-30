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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.sqrt

// ---- gesture recognition thresholds ----
private const val SWIPE_THRESHOLD = 30f          // px to recognise swipe vs jitter
private const val HOLD_JITTER_THRESHOLD = 15f    // max drift before hold still activates
private const val HOLD_TIMEOUT_MS = 500L         // ms to trigger hold
private const val DOUBLE_TAP_TIMEOUT_MS = 300L   // max gap between taps for double-tap
private const val TOUCH_SLOP = 12f               // tap drift tolerance

// ---- morph drag thresholds (from existing code) ----
private const val MORPH_DRAG_MIN_VELLOCITY = 0.3f // fraction of container height

/**
 * Unified gesture detection composable that replaces the old stacked-box [PlayerGestures].
 *
 * A single [awaitEachGesture] loop owns all pointer events on the player surface:
 *  1. Resolves the 3×3 sector from touch position
 *  2. Recognises gesture type (swipe / hold / double-tap)
 *  3. Emits [GestureFrame] stream to [handler]
 *  4. Morph drag takes precedence in NORMAL / COMPACT modes
 *
 * [isScrubbing] blocks all gesture recognition when the timeline is being interacted with.
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

    Box(modifier = modifier) {
        // ---- Floating mode: drag + tap ----
        if (overlayMode == com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.FLOATING
            && floatingVideoLayout != null) {
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

        // ---- Non-floating: unified gesture detection ----
        if (overlayMode != com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.FLOATING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentOverlayMode, currentIsScrubbing) {
                        val cfg = currentConfigs.forMode(currentOverlayMode)

                        // Tap tracking persists across awaitEachGesture iterations
                        var lastTapTime = 0L
                        var lastTapPos = Offset.Zero

                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val downPos = down.position
                            val downTime = System.currentTimeMillis()

                            val sector = GestureSector.fromPosition(
                                downPos.x, downPos.y,
                                size.width.toFloat(), size.height.toFloat()
                            )

                            // ---- Track pointer movement ----
                            var lastPos = downPos
                            var totalDeltaX = 0f
                            var totalDeltaY = 0f
                            var gestureRecognized = false
                            var gestureType: GestureType? = null
                            var holdTriggered = false
                            var isSwipeDownward = false

                            // Check for double-tap first (compare with previous tap)
                            val now = System.currentTimeMillis()
                            val timeSinceLastTap = now - lastTapTime
                            val distFromLastTap = sqrt(
                                (downPos.x - lastTapPos.x) * (downPos.x - lastTapPos.x) +
                                (downPos.y - lastTapPos.y) * (downPos.y - lastTapPos.y)
                            )

                            if (timeSinceLastTap < DOUBLE_TAP_TIMEOUT_MS && distFromLastTap < TOUCH_SLOP) {
                                // Double-tap detected
                                gestureRecognized = true
                                gestureType = GestureType.DOUBLE_TAP
                                val action = cfg.resolve(sector, GestureType.DOUBLE_TAP)
                                if (action != GestureAction.NONE) {
                                    currentHandler.handleInstantAction(
                                        InstantActionEvent(sector, action, downPos)
                                    )
                                }
                                // Update last tap tracking
                                lastTapTime = now
                                lastTapPos = downPos
                            }

                            // Wait for more events to distinguish swipe vs hold vs single tap
                            if (!gestureRecognized) {
                                lastTapTime = now
                                lastTapPos = downPos

                                while (true) {
                                    val event = awaitPointerEvent()

                                    // ---- Pointer up — check first before anything else ----
                                    if (event.changes.all { !it.pressed }) {
                                        if (gestureRecognized && gestureType != null && gestureType != GestureType.DOUBLE_TAP) {
                                            val action = cfg.resolve(sector, gestureType!!)
                                            if (action != GestureAction.NONE) {
                                                currentHandler.handleGestureFrame(
                                                    GestureFrame(
                                                        sector = sector,
                                                        gestureType = gestureType!!,
                                                        action = action,
                                                        phase = GesturePhase.END,
                                                        totalDelta = Offset(totalDeltaX, totalDeltaY),
                                                        elapsedMs = System.currentTimeMillis() - downTime,
                                                        fingerPosition = Offset(totalDeltaX + downPos.x, totalDeltaY + downPos.y)
                                                    )
                                                )
                                            }
                                        } else if (!gestureRecognized) {
                                            // Single tap — toggle controls
                                            currentOnTap()
                                        }
                                        break
                                    }

                                    val change = event.changes.firstOrNull { it.pressed } ?: continue

                                    val pos = change.position
                                    totalDeltaX = pos.x - downPos.x
                                    totalDeltaY = pos.y - downPos.y
                                    val totalDist = sqrt(totalDeltaX * totalDeltaX + totalDeltaY * totalDeltaY)
                                    val elapsed = System.currentTimeMillis() - downTime

                                    // ---- Swipe detection ----
                                    if (!gestureRecognized && totalDist > SWIPE_THRESHOLD) {
                                        gestureRecognized = true
                                        val isHorizontal = kotlin.math.abs(totalDeltaX) > kotlin.math.abs(totalDeltaY)
                                        gestureType = if (isHorizontal) {
                                            GestureType.SWIPE_HORIZONTAL
                                        } else {
                                            isSwipeDownward = totalDeltaY > 0
                                            GestureType.SWIPE_VERTICAL
                                        }

                                        // ---- Morph drag precedence check ----
                                        // NORMAL/COMPACT: any downward swipe → morph to floating
                                        // FULLSCREEN: only top-row downward swipe → morph (middle/bottom do brightness/volume)
                                        val isTopRow = sector == GestureSector.TOP_LEFT ||
                                            sector == GestureSector.TOP_CENTER ||
                                            sector == GestureSector.TOP_RIGHT
                                        val isMorphCandidate = (
                                            ((currentOverlayMode == com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.NORMAL ||
                                              currentOverlayMode == com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.COMPACT) ||
                                             (currentOverlayMode == com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode.FULLSCREEN && isTopRow)) &&
                                            gestureType == GestureType.SWIPE_VERTICAL &&
                                            isSwipeDownward
                                        )

                                        if (isMorphCandidate) {
                                            // Morph drag takes precedence — fire morph callbacks
                                            currentOnMorphDragStart()
                                            currentOnMorphDrag(totalDeltaY.coerceAtLeast(0f))
                                            change.consume()

                                            // Continue tracking for morph drag
                                            while (true) {
                                                val morphEvent = awaitPointerEvent()
                                                val morphChange = morphEvent.changes.firstOrNull() ?: break
                                                if (!morphChange.pressed) {
                                                    val finalDragY = totalDeltaY.coerceAtLeast(0f)
                                                    currentOnMorphDragEnd(finalDragY)
                                                    break
                                                }
                                                val morphPos = morphChange.position
                                                totalDeltaY = morphPos.y - downPos.y
                                                currentOnMorphDrag(totalDeltaY.coerceAtLeast(0f))
                                                morphChange.consume()
                                            }
                                            break
                                        }

                                        // Regular swipe — emit START frame
                                        val action = cfg.resolve(sector, gestureType!!)
                                        if (action != GestureAction.NONE) {
                                            currentHandler.handleGestureFrame(
                                                GestureFrame(
                                                    sector = sector,
                                                    gestureType = gestureType!!,
                                                    action = action,
                                                    phase = GesturePhase.START,
                                                    instantDelta = Offset(totalDeltaX, totalDeltaY),
                                                    totalDelta = Offset(totalDeltaX, totalDeltaY),
                                                    elapsedMs = elapsed,
                                                    fingerPosition = pos
                                                )
                                            )
                                        }
                                        change.consume()
                                    }

                                    // ---- Hold detection ----
                                    if (!gestureRecognized && !holdTriggered &&
                                        elapsed > HOLD_TIMEOUT_MS && totalDist < HOLD_JITTER_THRESHOLD) {
                                        holdTriggered = true
                                        gestureRecognized = true
                                        gestureType = GestureType.HOLD

                                        val action = cfg.resolve(sector, GestureType.HOLD)
                                        if (action != GestureAction.NONE) {
                                            currentHandler.handleGestureFrame(
                                                GestureFrame(
                                                    sector = sector,
                                                    gestureType = GestureType.HOLD,
                                                    action = action,
                                                    phase = GesturePhase.START,
                                                    totalDelta = Offset.Zero, // modulation starts at 0
                                                    elapsedMs = elapsed,
                                                    fingerPosition = pos
                                                )
                                            )
                                        }
                                    }

                                    // ---- Continuous frames for recognised gestures ----
                                    if (gestureRecognized && gestureType != GestureType.DOUBLE_TAP) {
                                        val instantDx = pos.x - lastPos.x
                                        val instantDy = pos.y - lastPos.y
                                        val newElapsed = System.currentTimeMillis() - downTime

                                        val action = cfg.resolve(sector, gestureType!!)
                                        if (action != GestureAction.NONE) {
                                            currentHandler.handleGestureFrame(
                                                GestureFrame(
                                                    sector = sector,
                                                    gestureType = gestureType!!,
                                                    action = action,
                                                    phase = GesturePhase.ACTIVE,
                                                    instantDelta = Offset(instantDx, instantDy),
                                                    totalDelta = Offset(totalDeltaX, totalDeltaY),
                                                    elapsedMs = newElapsed,
                                                    fingerPosition = pos
                                                )
                                            )
                                        }
                                        lastPos = pos
                                    }
                                }
                            }
                        }
                    }
            )
        }
    }
}

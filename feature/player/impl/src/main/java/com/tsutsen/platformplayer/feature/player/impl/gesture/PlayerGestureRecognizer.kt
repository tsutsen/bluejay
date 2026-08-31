package com.tsutsen.platformplayer.feature.player.impl.gesture

import androidx.compose.ui.geometry.Offset
import com.tsutsen.platformplayer.feature.player.impl.PlayerOverlayMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pure decision state machine for player-surface gestures.
 *
 * No Compose, no coroutines, no wall clock: the adapter (the composable
 * [PlayerGestureSystem]) feeds it pointer events with timestamps and
 * receives decisions. That keeps the recognition logic — double-tap
 * window, hold race, swipe threshold, axis lock, morph-drag resolution,
 * END-frame dispatch — unit-testable by replaying event sequences.
 *
 * The adapter still owns the two things that need a real timer and a
 * coroutine scope: the hold watchdog ([onHoldTimeout] is fired by a
 * `scope.launch { delay(HOLD_TIMEOUT_MS) }` — a perfectly still finger
 * emits no pointer events) and the deferred single-tap
 * ([UpResult.TapDeferred] → `scope.launch { delay; onTap() }`, so the
 * first tap of a double tap does not fire).
 *
 * A complete gesture produces at most: START → zero or more ACTIVE →
 * END. The END frame is dispatched exactly once even if the gesture is
 * cancelled mid-flight ([cancel]), so indicators and playback speed
 * can never stick.
 */
class PlayerGestureRecognizer(
    private val swipeThreshold: Float = SWIPE_THRESHOLD,
    private val holdTimeoutMs: Long = HOLD_TIMEOUT_MS,
    private val doubleTapTimeoutMs: Long = DOUBLE_TAP_TIMEOUT_MS,
) {
    companion object {
        const val SWIPE_THRESHOLD = 30f          // px to recognise slide vs jitter
        const val HOLD_TIMEOUT_MS = 500L         // ms to trigger hold
        const val DOUBLE_TAP_TIMEOUT_MS = 300L   // max gap between taps for double-tap
    }

    /**
     * Result of [onDown]. [doubleTap] is true when this down was
     * recognised as the second tap of a double tap — the adapter must
     * drain the pointer to release in that case (the tap is consumed
     * even when the sector's slot is NONE). [instantAction] carries the
     * action to fire, null when the slot is NONE.
     */
    data class DownResult(
        val doubleTap: Boolean,
        val instantAction: InstantActionEvent?
    )

    /**
     * Result of [onMove] during the decision phase.
     * [frames] are hold frames to emit (an END frame when a hold hands
     * over to a slide, ACTIVE frames while a hold modulates).
     * [SlideStart] means the decision phase resolved: take over with
     * the frame-driven execution loop ([startFrame] != null) or a
     * plain drain (unbound slot). Morph slides ride the same frame
     * path — the handler routes their frames to the drag callbacks.
     */
    sealed interface MoveResult {
        data class Idle(val frames: List<GestureFrame> = emptyList()) : MoveResult
        data class SlideStart(
            val startFrame: GestureFrame?,
            val frames: List<GestureFrame> = emptyList()
        ) : MoveResult
    }

    /**
     * Result of [onUp] at the end of the decision phase.
     * [TapDeferred] — the adapter defers the tap behind a
     * DOUBLE_TAP_TIMEOUT_MS window. [End] — the gesture had an active
     * action; [frame] is its END frame (null when the action was NONE).
     */
    sealed interface UpResult {
        data object TapDeferred : UpResult
        data class End(val frame: GestureFrame?) : UpResult
    }

    // ---- cross-gesture tap memory (survives between gestures) ----
    private var lastTapTime = 0L
    private var lastTapPos = Offset.Zero

    // ---- per-gesture state ----
    private var downTime = 0L
    private var downPos = Offset.Zero
    private var sector = GestureSector.MIDDLE_CENTER
    private var cfg: GestureConfig? = null

    private var decisionIsSlide = false
    private var slideType = GestureType.SWIPE_VERTICAL
    private var holdFired = false
    // Once a speed-hold's drift goes horizontal, that decision is
    // locked — a later vertical drift must not intercept it.
    private var holdHorizontalLocked = false
    private var maxDist = 0f
    private var released = false
    private var activeAction: GestureAction? = null
    private var activeType: GestureType? = null
    private var endSent = false
    private var lastSlidePos = Offset.Zero

    /**
     * First down of a new gesture. Resets per-gesture state and runs the
     * synchronous double-tap check against the previous tap.
     *
     * @param timeMs wall-clock time of the down
     * @param widthPx / heightPx current surface size, for sector resolution
     * @param overlayMode current overlay mode
     * @param cfg gesture config already resolved for [overlayMode]
     * @param doubleTapSlopPx max distance between the two taps (px)
     */
    fun onDown(
        x: Float, y: Float, timeMs: Long,
        widthPx: Float, heightPx: Float,
        overlayMode: PlayerOverlayMode, cfg: GestureConfig,
        doubleTapSlopPx: Float,
    ): DownResult {
        resetGesture()
        downTime = timeMs
        downPos = Offset(x, y)
        sector = GestureSector.fromPosition(x, y, widthPx, heightPx)
        this.cfg = cfg

        val dx = x - lastTapPos.x
        val dy = y - lastTapPos.y
        val distFromLastTap = sqrt(dx * dx + dy * dy)

        if (timeMs - lastTapTime < doubleTapTimeoutMs && distFromLastTap < doubleTapSlopPx) {
            lastTapTime = timeMs
            lastTapPos = downPos
            val action = cfg.resolve(sector, GestureType.DOUBLE_TAP)
            return DownResult(
                doubleTap = true,
                instantAction = if (action != GestureAction.NONE) {
                    InstantActionEvent(sector, action, downPos)
                } else {
                    null
                }
            )
        }
        return DownResult(doubleTap = false, instantAction = null)
    }

    /**
     * Move event while the decision phase is running.
     */
    fun onMove(x: Float, y: Float, timeMs: Long): MoveResult {
        val dx = x - downPos.x
        val dy = y - downPos.y
        val dist = sqrt(dx * dx + dy * dy)
        maxDist = max(maxDist, dist)

        val frames = mutableListOf<GestureFrame>()

        if (dist > swipeThreshold) {
            val isHorizontal = abs(dx) > abs(dy)
            // Horizontal drift during a speed hold is fine tuning — the hold
            // stays alive and keeps modulating instead of handing off. The
            // first dominant axis locks the decision; once locked horizontal,
            // a vertical drift can never intercept it.
            if (holdFired && (isHorizontal || holdHorizontalLocked)) {
                if (isHorizontal) holdHorizontalLocked = true
            } else {
                if (holdFired) {
                    // Slide wins — end the hold now; re-arm the END dispatch
                    // for the slide that follows.
                    holdFired = false
                    buildEndFrame(timeMs)?.let(frames::add)
                    endSent = false
                    activeAction = null
                    activeType = null
                }
                decisionIsSlide = true
                slideType = if (isHorizontal) GestureType.SWIPE_HORIZONTAL
                else GestureType.SWIPE_VERTICAL
                return MoveResult.SlideStart(
                    startFrame = beginSlide(timeMs),
                    frames = frames
                )
            }
        }

        val holdAction = activeAction
        if (holdFired && holdAction != null) {
            // Hold is active — modulate with finger drift; a still finger is
            // covered by the handler's keep-alive job.
            frames.add(
                GestureFrame(
                    sector = sector,
                    gestureType = GestureType.HOLD,
                    action = holdAction,
                    phase = GesturePhase.ACTIVE,
                    totalDelta = Offset(dx, dy),
                    elapsedMs = timeMs - downTime,
                    fingerPosition = Offset(x, y)
                )
            )
        }
        return MoveResult.Idle(frames = frames)
    }

    /**
     * Fired by the adapter's hold watchdog after [HOLD_TIMEOUT_MS].
     * Returns the hold START frame, or null when the gesture is already
     * gone, already a hold, or has moved past the swipe threshold.
     *
     * Note: a hold in a NONE slot still swallows the tap — [holdFired]
     * is set even when no frame is returned.
     */
    fun onHoldTimeout(timeMs: Long): GestureFrame? {
        val cfg = cfg ?: return null
        if (released || holdFired || maxDist > swipeThreshold) return null
        holdFired = true
        val action = cfg.resolve(sector, GestureType.HOLD)
        if (action == GestureAction.NONE) return null
        activeAction = action
        activeType = GestureType.HOLD
        return GestureFrame(
            sector = sector,
            gestureType = GestureType.HOLD,
            action = action,
            phase = GesturePhase.START,
            elapsedMs = timeMs - downTime,
            fingerPosition = downPos
        )
    }

    /**
     * Finger lifted at the end of the decision phase (tap or hold path).
     */
    fun onUp(timeMs: Long): UpResult {
        released = true
        return when {
            holdFired -> UpResult.End(buildEndFrame(timeMs))
            else -> {
                // Remember this tap for double-tap detection; the adapter
                // defers the actual tap behind the double-tap window.
                lastTapTime = downTime
                lastTapPos = downPos
                UpResult.TapDeferred
            }
        }
    }

    /**
     * Move event while a slide is executing. Returns the ACTIVE frame,
     * or null for an unbound slot (the adapter drains to release).
     */
    fun onSlideMove(x: Float, y: Float, timeMs: Long): GestureFrame? {
        val action = activeAction ?: return null
        val type = activeType ?: return null
        val frame = GestureFrame(
            sector = sector,
            gestureType = type,
            action = action,
            phase = GesturePhase.ACTIVE,
            instantDelta = Offset(x - lastSlidePos.x, y - lastSlidePos.y),
            totalDelta = Offset(x - downPos.x, y - downPos.y),
            elapsedMs = timeMs - downTime,
            fingerPosition = Offset(x, y)
        )
        lastSlidePos = Offset(x, y)
        return frame
    }

    /**
     * Finger lifted while a slide (or morph drag) is executing.
     * Returns the slide's END frame, or null for an unbound slot /
     * morph drag.
     */
    fun onSlideEnd(timeMs: Long): GestureFrame? = buildEndFrame(timeMs)

    /**
     * Cancel the in-flight gesture (e.g. the player left composition
     * mid-gesture). Returns a pending END frame so continuous gestures
     * still terminate exactly once. Idempotent.
     */
    fun cancel(timeMs: Long): GestureFrame? {
        released = true
        return buildEndFrame(timeMs)
    }

    // ---- internals ----

    private fun resetGesture() {
        downTime = 0L
        downPos = Offset.Zero
        sector = GestureSector.MIDDLE_CENTER
        cfg = null
        decisionIsSlide = false
        slideType = GestureType.SWIPE_VERTICAL
        holdFired = false
        holdHorizontalLocked = false
        maxDist = 0f
        released = false
        activeAction = null
        activeType = null
        endSent = false
        lastSlidePos = Offset.Zero
    }

    /**
     * END frame for the currently active action, dispatched at most once
     * per gesture (re-armed when a hold hands over to a slide).
     */
    private fun buildEndFrame(timeMs: Long): GestureFrame? {
        if (endSent) return null
        endSent = true
        val action = activeAction ?: return null
        val type = activeType ?: return null
        return GestureFrame(
            sector = sector,
            gestureType = type,
            action = action,
            phase = GesturePhase.END,
            elapsedMs = timeMs - downTime,
            fingerPosition = downPos
        )
    }

    /**
     * Resolve the slide's action and emit its START frame. Returns null
     * for unbound slots. Morph actions emit frames like any other — the
     * handler routes them to the drag callbacks, so the user's per-slot
     * assignment always decides what a slide does.
     */
    private fun beginSlide(timeMs: Long): GestureFrame? {
        val cfg = cfg ?: return null
        val action = cfg.resolve(sector, slideType)
        if (action == GestureAction.NONE) return null
        activeAction = action
        activeType = slideType
        lastSlidePos = downPos
        return GestureFrame(
            sector = sector,
            gestureType = slideType,
            action = action,
            phase = GesturePhase.START,
            totalDelta = Offset.Zero,
            elapsedMs = timeMs - downTime,
            fingerPosition = downPos
        )
    }
}

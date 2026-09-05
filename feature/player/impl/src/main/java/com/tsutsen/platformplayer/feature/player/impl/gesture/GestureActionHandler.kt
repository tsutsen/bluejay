package com.tsutsen.platformplayer.feature.player.impl.gesture

import android.app.Activity
import android.content.Context
import com.tsutsen.platformplayer.feature.player.impl.PlayerEvent
import com.tsutsen.platformplayer.feature.player.impl.PlayerEventBus
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import com.tsutsen.platformplayer.feature.player.impl.SystemControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Frame-based handler that dispatches gesture frames to the appropriate action.
 *
 * Continuous actions (swipe / hold) receive START → ACTIVE… → END frames.
 * Instant actions (double-tap) receive a single [InstantActionEvent].
 */
interface GestureActionHandler {
    fun handleGestureFrame(frame: GestureFrame)
    fun handleInstantAction(event: InstantActionEvent)
}

/**
 * Concrete handler wired to a [PlayerViewModel] and player callbacks.
 *
 * Action feedback (badges) is not handled here: the actions flow through
 * [PlayerEventBus] (the seek/speed calls go via the view model, brightness
 * and volume are emitted directly), so badges show for every origin, not
 * just gestures.
 *
 * @property viewModel       for seek, speed, fullscreen, minimize
 * @property screenHeight    screen height in px (for normalising brightness delta)
 * @property onMorphDragStart  called when a morph-to-floating swipe begins
 * @property onMorphDrag       called with cumulative downward drag px during morph swipe
 * @property onMorphDragEnd    called when morph swipe ends; velocity = release speed px/ms toward the target
 * @property onFullscreenDragStart  called when a morph-to-fullscreen (swipe up) begins
 * @property onFullscreenDrag       called with cumulative upward drag px during fullscreen morph
 * @property onFullscreenDragEnd    called when the fullscreen morph swipe ends; velocity = release speed px/ms toward fullscreen
 * @property onShrinkDragStart      called when a morph-to-normal (swipe down in fullscreen) begins
 * @property onShrinkDrag           called with cumulative downward drag px during shrink
 * @property onShrinkDragEnd        called when the shrink swipe ends; velocity = release speed px/ms toward normal
 */
class PlayerGestureActionHandler(
    private val viewModel: PlayerViewModel,
    private val screenHeight: () -> Float,
    private val context: Context,
    private val activity: Activity? = null,
    private val onMorphDragStart: () -> Unit = {},
    private val onMorphDrag: (dragY: Float) -> Unit = {},
    private val onMorphDragEnd: (dragY: Float, velocityPxPerMs: Float) -> Unit = { _, _ -> },
    private val onFullscreenDragStart: () -> Unit = {},
    private val onFullscreenDrag: (dragY: Float) -> Unit = {},
    private val onFullscreenDragEnd: (dragY: Float, velocityPxPerMs: Float) -> Unit = { _, _ -> },
    private val onShrinkDragStart: () -> Unit = {},
    private val onShrinkDrag: (dragY: Float) -> Unit = {},
    private val onShrinkDragEnd: (dragY: Float, velocityPxPerMs: Float) -> Unit = { _, _ -> },
) : GestureActionHandler {

    // --- brightness state (device-wide via SystemControls, window-local fallback) ---
    private var currentBrightness = 1f

    // --- volume state ---
    private var currentVolume = 1f

    // --- speed state ---
    private var originalSpeed = 1f

    // --- morph drag state ---
    private var morphStartDelta = 0f

    // --- drag velocity sampling (release speed seeds the settle springs) ---
    // Last sample of the active drag (elapsedMs, dragPx toward target);
    // the frame-pair delta is the release velocity estimate (1-frame lag).
    private var dragSampleT = 0L
    private var dragSamplePx = 0f
    private var dragSampleV = 0f
    private var hasDragSample = false

    private fun sampleDragVelocity(t: Long, px: Float) {
        if (hasDragSample && t > dragSampleT) {
            dragSampleV = (px - dragSamplePx) / (t - dragSampleT).toFloat()
        }
        dragSampleT = t
        dragSamplePx = px
        hasDragSample = true
    }

    private fun releaseDragVelocity(): Float {
        val v = if (hasDragSample) dragSampleV else 0f
        hasDragSample = false
        dragSampleV = 0f
        return v
    }

    // --- vertical morph state (MORPH_VERTICAL) ---
    // -1 = swipe up (fullscreen), +1 = swipe down (floating);
    // null until the first frame that decisively leaves the axis.
    private var morphVerticalDir: Int? = null
    private var morphVerticalDrag = 0f

    // --- speed hold keep-alive ---
    private var speedHoldJob: Job? = null

    // --- speed hold state ---
    // Written on the pointer thread (ACTIVE), read by the keep-alive coroutine.
    @Volatile
    private var lastSpeedHoldReported = 0f

    fun snapshotBrightness() {
        // Shared across screens: last user value, else device-wide setting.
        currentBrightness = SystemControls.readBrightness(context)
    }

    fun snapshotVolume() {
        currentVolume = SystemControls.getVolume(context)
    }

    fun snapshotSpeed() {
        originalSpeed = (viewModel.uiState.value as? com.tsutsen.platformplayer.feature.player.impl.PlayerUiState.Loaded)?.playbackSpeed ?: 1f
    }

    override fun handleGestureFrame(frame: GestureFrame) {
        when (frame.action) {
            GestureAction.NONE -> {}
            GestureAction.BRIGHTNESS -> handleBrightness(frame)
            GestureAction.VOLUME -> handleVolume(frame)
            GestureAction.SPEEDUP ->
                handleSpeedHold(frame, baseMultiplier = viewModel.defaultSpeedup)
            GestureAction.SPEEDDOWN -> handleSpeedHold(frame, baseMultiplier = 0.5f)
            GestureAction.MORPH_TO_FLOATING -> handleMorphToFloating(frame)
            GestureAction.MORPH_TO_FULLSCREEN -> handleMorphToFullscreen(frame)
            GestureAction.MORPH_TO_NORMAL -> handleMorphToNormal(frame)
            GestureAction.MORPH_VERTICAL -> handleMorphVertical(frame)
            // Jumps assigned to a swipe or hold slot fire once at gesture
            // start (double-tap jumps go through handleInstantAction).
            GestureAction.REWIND_BACK,
            GestureAction.REWIND_FORWARD ->
                if (frame.phase == GesturePhase.START) handleAccumulatedSeek(frame.action)

            // Anything else: no-op.
            else -> {}
        }
    }

    override fun handleInstantAction(event: InstantActionEvent) {
        when (event.action) {
            GestureAction.REWIND_BACK,
            GestureAction.REWIND_FORWARD -> handleAccumulatedSeek(event.action)
            GestureAction.MORPH_TO_FLOATING -> viewModel.minimize()
            GestureAction.MORPH_TO_FULLSCREEN -> viewModel.toggleFullscreen()
            GestureAction.MORPH_TO_NORMAL -> viewModel.exitFullscreen()
            GestureAction.CONTEXT_MENU -> {} // stub
            else -> {}
        }
    }

    /**
     * Applies a ±[jumpStepMs] seek. The badge (including consecutive
     * double-tap accumulation) is derived from the [PlayerEvent.Seek]
     * events on the bus, not here — so seeks from any origin badge identically.
     */
    private fun handleAccumulatedSeek(action: GestureAction) {
        viewModel.seekBy(if (action == GestureAction.REWIND_BACK) -jumpStepMs() else jumpStepMs())
    }

    // ---- Brightness (swipe vertical, continuous) ----
    private fun handleBrightness(frame: GestureFrame) {
        when (frame.phase) {
            GesturePhase.START -> {
                snapshotBrightness()
            }
            GesturePhase.ACTIVE -> {
                // instantDelta.y: negative = swipe up (brighter), positive = down (darker)
                val delta = -frame.instantDelta.y / screenHeight()
                currentBrightness = (currentBrightness + delta).coerceIn(0f, 1f)
                // All screens: device-wide when granted, plus this window
                // (the companion window follows through SystemControls.brightness).
                SystemControls.applyBrightness(context, currentBrightness, activity?.window)
                PlayerEventBus.emit(PlayerEvent.BrightnessChanged(currentBrightness))
            }
            GesturePhase.END -> {
                PlayerEventBus.emit(PlayerEvent.BrightnessChanged(currentBrightness))
            }
        }
    }

    // ---- Volume (swipe vertical, continuous) ----
    private fun handleVolume(frame: GestureFrame) {
        when (frame.phase) {
            GesturePhase.START -> {
                snapshotVolume()
            }
            GesturePhase.ACTIVE -> {
                // instantDelta.y: negative = swipe up (louder), positive = down (quieter)
                val delta = -frame.instantDelta.y / screenHeight()
                currentVolume = (currentVolume + delta).coerceIn(0f, 1f)
                SystemControls.setVolume(context, currentVolume)
                PlayerEventBus.emit(PlayerEvent.VolumeChanged(currentVolume))
            }
            GesturePhase.END -> {
                PlayerEventBus.emit(PlayerEvent.VolumeChanged(currentVolume))
            }
        }
    }

    /** Jump step from the settings (Settings > Gestures > Time jump step). */
    private fun jumpStepMs(): Long = viewModel.jumpStepSeconds * 1000L

    companion object {
        /** Horizontal px to travel for one ±0.1x speed step. */
        private const val SPEED_SWIPE_STEP_PX = 200f
        /** Below this much horizontal movement a speed gesture counts as a still hold. */
        private const val SPEED_HOLD_DEADZONE_PX = 48f
        private const val SPEED_STEP = 0.1f

        /** Keep-alive interval for speed hold — keeps badge visible during still holds. */
        private const val KEEP_ALIVE_INTERVAL_MS = 100L
    }

    // ---- Speed hold with optional swipe modulation ----
    //    Horizontal swipe changes speed in 0.1x steps.
    private fun handleSpeedHold(frame: GestureFrame, baseMultiplier: Float) {
        when (frame.phase) {
            GesturePhase.START -> {
                snapshotSpeed()
                lastSpeedHoldReported = baseMultiplier
                viewModel.setPlaybackSpeed(baseMultiplier)
                // Start keep-alive coroutine — re-emits the *current* speed periodically so
                // the badge stays visible during still holds. Re-emitting the base here would
                // snap the badge back to x2 while the finger is held after a movement step.
                speedHoldJob?.cancel()
                speedHoldJob = CoroutineScope(Dispatchers.Default).launch {
                    while (true) {
                        delay(KEEP_ALIVE_INTERVAL_MS)
                        val current = lastSpeedHoldReported
                        if (current > 0f) {
                            PlayerEventBus.emit(PlayerEvent.PlaybackSpeedChanged(current))
                        }
                    }
                }
            }
            GesturePhase.ACTIVE -> {
                // totalDelta.x: positive = swipe right (faster), negative = left (slower).
                // Deadzone: a still hold (tap/long-press slot) must never
                // modulate speed, no matter how high the swipe speed is set —
                // otherwise hold jitter steps the speed at high sensitivity.
                val dx = frame.totalDelta.x
                val steps =
                    if (abs(dx) < SPEED_HOLD_DEADZONE_PX) 0
                    else (dx / (SPEED_SWIPE_STEP_PX / viewModel.speedupSensitivity)).toInt()
                val speed = (baseMultiplier + steps * SPEED_STEP).coerceIn(0.25f, 4f)
                // Round to nearest 0.1 to avoid floating-point drift
                val snapped = (speed * 10).toInt() / 10f
                if (snapped != lastSpeedHoldReported) {
                    lastSpeedHoldReported = snapped
                    viewModel.setPlaybackSpeed(snapped)
                }
            }
            GesturePhase.END -> {
                speedHoldJob?.cancel()
                speedHoldJob = null
                viewModel.setPlaybackSpeed(originalSpeed)
            }
        }
    }

    // ---- Morph to floating (swipe vertical downward) ----
    //    No indicator — morph is visual through the player animation itself.
    private fun handleMorphToFloating(frame: GestureFrame) {
        when (frame.phase) {
            GesturePhase.START -> {
                morphStartDelta = 0f
                hasDragSample = false
                onMorphDragStart()
            }
            GesturePhase.ACTIVE -> {
                val dragY = frame.totalDelta.y.coerceAtLeast(0f)
                if (dragY > 0f) {
                    morphStartDelta = dragY
                    sampleDragVelocity(frame.elapsedMs, dragY)
                    onMorphDrag(dragY)
                }
            }
            GesturePhase.END -> {
                onMorphDragEnd(morphStartDelta, releaseDragVelocity())
            }
        }
    }

    // ---- Morph to normal (swipe vertical downward, fullscreen only) ----
    //    Shrink the surface back toward normal; the surface drives the
    //    animation through the shrink-progress callbacks.
    private fun handleMorphToNormal(frame: GestureFrame) {
        when (frame.phase) {
            GesturePhase.START -> {
                morphStartDelta = 0f
                hasDragSample = false
                onShrinkDragStart()
            }
            GesturePhase.ACTIVE -> {
                val dragY = frame.totalDelta.y.coerceAtLeast(0f)
                if (dragY > 0f) {
                    morphStartDelta = dragY
                    sampleDragVelocity(frame.elapsedMs, dragY)
                    onShrinkDrag(dragY)
                }
            }
            GesturePhase.END -> {
                onShrinkDragEnd(morphStartDelta, releaseDragVelocity())
            }
        }
    }

    // ---- Morph to fullscreen (swipe vertical upward — stub) ----
    private fun handleMorphToFullscreen(frame: GestureFrame) {
        // Currently handled as instant on double-tap via handleInstantAction
        // Swipe-up variant is a stub for now
    }

    // ---- Morph vertical (swipe up → fullscreen, swipe down → floating) ----
    //    Direction is locked on the first decisive frame and drives which
    //    drag callback set receives the frames.
    private fun handleMorphVertical(frame: GestureFrame) {
        when (frame.phase) {
            GesturePhase.START -> {
                morphVerticalDir = null
                morphVerticalDrag = 0f
                hasDragSample = false
            }

            GesturePhase.ACTIVE -> {
                val y = frame.totalDelta.y
                if (y == 0f) return
                if (morphVerticalDir == null) {
                    morphVerticalDir = if (y < 0f) -1 else 1
                    if (morphVerticalDir == -1) onFullscreenDragStart() else onMorphDragStart()
                }
                morphVerticalDrag = if (morphVerticalDir == -1) -y else y
                sampleDragVelocity(frame.elapsedMs, morphVerticalDrag)
                if (morphVerticalDir == -1) onFullscreenDrag(morphVerticalDrag) else onMorphDrag(morphVerticalDrag)
            }

            GesturePhase.END -> {
                when (morphVerticalDir) {
                    -1 -> onFullscreenDragEnd(morphVerticalDrag, releaseDragVelocity())
                    1 -> onMorphDragEnd(morphVerticalDrag, releaseDragVelocity())
                    else -> {}
                }
                morphVerticalDir = null
            }
        }
    }
}

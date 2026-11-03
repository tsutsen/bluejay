package com.tsutsen.platformplayer.feature.player.impl.gesture

import com.tsutsen.platformplayer.core.model.GestureAction
import com.tsutsen.platformplayer.feature.player.impl.PlayerEvent
import com.tsutsen.platformplayer.feature.player.impl.PlayerEventBus
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
 *
 * Action feedback (badges) is not handled here: the actions flow through
 * [PlayerEventBus] (the seek/speed calls go via [PlaybackEffects], brightness
 * and volume are emitted directly), so badges show for every origin, not
 * just gestures.
 */
internal class PlayerGestureActionHandler(
    private val playback: PlaybackEffects,
    private val layout: LayoutEffects,
) {

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

    // --- hold-seek (SEEK_HOLD) state: pause + scrub, commit on release ---
    private var holdSeekWasPlaying = false
    private var holdSeekStartMs = 0L
    private var holdSeekDurationMs = 0L
    private var holdSeekTargetMs = 0L

    fun handleGestureFrame(frame: GestureFrame) {
        when (frame.action) {
            GestureAction.NONE -> {}
            GestureAction.BRIGHTNESS -> handleBrightness(frame)
            GestureAction.VOLUME -> handleVolume(frame)
            GestureAction.SPEEDUP ->
                handleSpeedHold(frame, baseMultiplier = playback.defaultSpeedup())
            GestureAction.SPEEDDOWN -> handleSpeedHold(frame, baseMultiplier = 0.5f)
            GestureAction.SEEK_HOLD -> handleHoldSeek(frame)
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

    fun handleInstantAction(event: InstantActionEvent) {
        when (event.action) {
            GestureAction.REWIND_BACK,
            GestureAction.REWIND_FORWARD -> handleAccumulatedSeek(event.action)
            GestureAction.MORPH_TO_FLOATING -> layout.minimize()
            GestureAction.MORPH_TO_FULLSCREEN -> layout.toggleFullscreen()
            GestureAction.MORPH_TO_NORMAL -> layout.exitFullscreen()
            GestureAction.CONTEXT_MENU -> {} // stub
            GestureAction.PLAY_PAUSE -> {
                if (playback.isPlaying()) playback.pause() else playback.resume()
            }
            GestureAction.PIP -> layout.enterPip()
            else -> {}
        }
    }

    /**
     * Applies a ±[jumpStepMs] seek. The badge (including consecutive
     * double-tap accumulation) is derived from the [PlayerEvent.Seek]
     * events on the bus, not here — so seeks from any origin badge identically.
     */
    private fun handleAccumulatedSeek(action: GestureAction) {
        playback.seekBy(if (action == GestureAction.REWIND_BACK) -playback.jumpStepMs() else playback.jumpStepMs())
    }

    // ---- Brightness (swipe vertical, continuous) ----
    private fun handleBrightness(frame: GestureFrame) {
        when (frame.phase) {
            GesturePhase.START -> {
                currentBrightness = playback.brightness()
            }
            GesturePhase.ACTIVE -> {
                // instantDelta.y: negative = swipe up (brighter), positive = down (darker)
                // Re-read the live value each frame as the base: the cached START value
                // goes stale if the system level changed mid-gesture (hardware keys etc.)
                val delta = -frame.instantDelta.y / playback.screenHeightPx()
                currentBrightness = (playback.brightness() + delta).coerceIn(0f, 1f)
                // All screens: device-wide when granted, plus this window
                // (the companion window follows through SystemControls.brightness).
                playback.applyBrightness(currentBrightness)
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
                currentVolume = playback.volume()
            }
            GesturePhase.ACTIVE -> {
                // instantDelta.y: negative = swipe up (louder), positive = down (quieter)
                // Re-read the live system volume each frame as the base (see brightness)
                val delta = -frame.instantDelta.y / playback.screenHeightPx()
                currentVolume = (playback.volume() + delta).coerceIn(0f, 1f)
                playback.setVolume(currentVolume)
                PlayerEventBus.emit(PlayerEvent.VolumeChanged(currentVolume))
            }
            GesturePhase.END -> {
                PlayerEventBus.emit(PlayerEvent.VolumeChanged(currentVolume))
            }
        }
    }

    companion object {
        /** Horizontal px to travel for one ±0.1x speed step. */
        private const val SPEED_SWIPE_STEP_PX = 200f
        /** Below this much horizontal movement a speed gesture counts as a still hold. */
        private const val SPEED_HOLD_DEADZONE_PX = 48f
        private const val SPEED_STEP = 0.1f

        /**
         * Hold-seek scrub: ms of video per horizontal px of finger drift
         * (100px ≈ 12s; a full-width swipe ≈ a 20-minute video).
         */
        private const val SEEK_HOLD_PX_TO_MS = 120f

        /** Keep-alive interval for speed hold — keeps badge visible during still holds. */
        private const val KEEP_ALIVE_INTERVAL_MS = 100L
    }

    // ---- Speed hold with optional swipe modulation ----
    //    Horizontal swipe changes speed in 0.1x steps.
    private fun handleSpeedHold(frame: GestureFrame, baseMultiplier: Float) {
        // base = current speed * multiplier (multiplicative, like the controller
        // speed buttons): holding at 2x goes to 4x, not to a fixed 2x.
        when (frame.phase) {
            GesturePhase.START -> {
                originalSpeed = playback.playbackSpeed()
                val base = (originalSpeed * baseMultiplier).coerceIn(0.25f, 4f)
                lastSpeedHoldReported = base
                playback.setPlaybackSpeed(base)
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
                val base = (originalSpeed * baseMultiplier).coerceIn(0.25f, 4f)
                // totalDelta.x: positive = swipe right (faster), negative = left (slower).
                // Deadzone: a still hold (tap/long-press slot) must never
                // modulate speed, no matter how high the swipe speed is set —
                // otherwise hold jitter steps the speed at high sensitivity.
                val dx = frame.totalDelta.x
                val steps =
                    if (abs(dx) < SPEED_HOLD_DEADZONE_PX) 0
                    else (dx / (SPEED_SWIPE_STEP_PX / playback.speedupSensitivity())).toInt()
                val speed = (base + steps * SPEED_STEP).coerceIn(0.25f, 4f)
                // Round to nearest 0.1 to avoid floating-point drift
                val snapped = (speed * 10).toInt() / 10f
                if (snapped != lastSpeedHoldReported) {
                    lastSpeedHoldReported = snapped
                    playback.setPlaybackSpeed(snapped)
                }
            }
            GesturePhase.END -> {
                speedHoldJob?.cancel()
                speedHoldJob = null
                playback.setPlaybackSpeed(originalSpeed)
            }
        }
    }

    // ---- Hold-seek (SEEK_HOLD): pause, scrub with horizontal drift, commit on release ----
    private fun handleHoldSeek(frame: GestureFrame) {
        when (frame.phase) {
            GesturePhase.START -> {
                val durationMs = playback.durationMs()
                if (durationMs <= 0) return
                holdSeekWasPlaying = playback.isPlaying()
                holdSeekDurationMs = durationMs
                holdSeekStartMs = playback.positionMs()
                holdSeekTargetMs = holdSeekStartMs
                if (holdSeekWasPlaying) playback.pause()
            }
            GesturePhase.ACTIVE -> {
                val dx = frame.totalDelta.x
                val target =
                    (holdSeekStartMs + dx * SEEK_HOLD_PX_TO_MS).toLong().coerceIn(0L, holdSeekDurationMs)
                if (target != holdSeekTargetMs) {
                    holdSeekTargetMs = target
                    PlayerEventBus.emit(PlayerEvent.SeekPreview(target))
                }
            }
            GesturePhase.END -> {
                playback.seekTo(holdSeekTargetMs)
                // The preview position becomes the committed one — consumers
                // (the badge, the timeline playhead) can hand over to the live
                // position flow from here.
                PlayerEventBus.emit(PlayerEvent.SeekCommitted(holdSeekTargetMs))
                if (holdSeekWasPlaying) playback.resume()
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
                layout.morphDragStart()
            }
            GesturePhase.ACTIVE -> {
                val dragY = frame.totalDelta.y.coerceAtLeast(0f)
                if (dragY > 0f) {
                    morphStartDelta = dragY
                    sampleDragVelocity(frame.elapsedMs, dragY)
                    layout.morphDrag(dragY)
                }
            }
            GesturePhase.END -> {
                layout.morphDragEnd(morphStartDelta, releaseDragVelocity())
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
                layout.shrinkDragStart()
            }
            GesturePhase.ACTIVE -> {
                val dragY = frame.totalDelta.y.coerceAtLeast(0f)
                if (dragY > 0f) {
                    morphStartDelta = dragY
                    sampleDragVelocity(frame.elapsedMs, dragY)
                    layout.shrinkDrag(dragY)
                }
            }
            GesturePhase.END -> {
                layout.shrinkDragEnd(morphStartDelta, releaseDragVelocity())
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
                    if (morphVerticalDir == -1) layout.fullscreenDragStart() else layout.morphDragStart()
                }
                morphVerticalDrag = if (morphVerticalDir == -1) -y else y
                sampleDragVelocity(frame.elapsedMs, morphVerticalDrag)
                if (morphVerticalDir == -1) layout.fullscreenDrag(morphVerticalDrag) else layout.morphDrag(morphVerticalDrag)
            }

            GesturePhase.END -> {
                when (morphVerticalDir) {
                    -1 -> layout.fullscreenDragEnd(morphVerticalDrag, releaseDragVelocity())
                    1 -> layout.morphDragEnd(morphVerticalDrag, releaseDragVelocity())
                    else -> {}
                }
                morphVerticalDir = null
            }
        }
    }
}

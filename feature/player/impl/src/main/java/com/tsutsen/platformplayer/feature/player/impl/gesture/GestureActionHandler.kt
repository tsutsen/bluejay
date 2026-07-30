package com.tsutsen.platformplayer.feature.player.impl.gesture

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel

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
 * @property viewModel       for seek, speed, fullscreen, minimize
 * @property currentPositionMs supplier of current playback position
 * @property screenHeight    screen height in px (for normalising brightness delta)
 * @property onIndicator     called with a [GestureIndicator] spec on each ACTIVE/END frame.
 *                           emits [GestureIndicator.None] for actions without indicators.
 * @property onIndicatorEnd  called once when the gesture ends — UI should start hide timer here.
 * @property onMorphDragStart  called when a morph-to-floating swipe begins
 * @property onMorphDrag       called with cumulative downward drag px during morph swipe
 * @property onMorphDragEnd    called when morph swipe ends (decides commit or cancel)
 */
class PlayerGestureActionHandler(
    private val viewModel: PlayerViewModel,
    private val currentPositionMs: () -> Long,
    private val screenHeight: () -> Float,
    private val context: Context,
    private val activity: Activity? = null,
    private val onIndicator: (GestureIndicator) -> Unit = {},
    private val onIndicatorEnd: () -> Unit = {},
    private val onMorphDragStart: () -> Unit = {},
    private val onMorphDrag: (dragY: Float) -> Unit = {},
    private val onMorphDragEnd: (dragY: Float) -> Unit = {},
) : GestureActionHandler {

    // --- brightness state ---
    private var startBrightness = 1f
    private var currentBrightness = 1f

    // --- volume state ---
    private var audioManager: AudioManager? = null
    private var currentVolume = 1f
    private var maxVolume = 15

    // --- speed state ---
    private var originalSpeed = 1f

    // --- morph drag state ---
    private var morphStartDelta = 0f

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
    }

    fun snapshotBrightness() {
        currentBrightness = activity?.window?.attributes?.screenBrightness ?: 1f
        if (currentBrightness < 0f) currentBrightness = 1f // auto mode fallback
        startBrightness = currentBrightness
    }

    fun snapshotVolume() {
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        currentVolume = current.toFloat() / maxVolume
    }

    fun snapshotSpeed() {
        // We read speed from the ViewModel uiState when available;
        // for now default to 1f — the ViewModel will provide actual value.
        originalSpeed = 1f
    }

    override fun handleGestureFrame(frame: GestureFrame) {
        when (frame.action) {
            GestureAction.NONE -> {}
            GestureAction.BRIGHTNESS -> handleBrightness(frame)
            GestureAction.VOLUME -> handleVolume(frame)
            GestureAction.SPEEDUP -> handleSpeedHold(frame, baseMultiplier = 2f)
            GestureAction.SPEEDDOWN -> handleSpeedHold(frame, baseMultiplier = 0.5f)
            GestureAction.MORPH_TO_FLOATING -> handleMorphToFloating(frame)
            GestureAction.MORPH_TO_FULLSCREEN -> handleMorphToFullscreen(frame)
            // Instant actions handled via handleInstantAction
            else -> {}
        }
    }

    override fun handleInstantAction(event: InstantActionEvent) {
        when (event.action) {
            GestureAction.REWIND_BACK -> viewModel.seekTo(currentPositionMs() - 5000)
            GestureAction.REWIND_FORWARD -> viewModel.seekTo(currentPositionMs() + 5000)
            GestureAction.MORPH_TO_FLOATING -> viewModel.minimize()
            GestureAction.MORPH_TO_FULLSCREEN -> viewModel.toggleFullscreen()
            GestureAction.CONTEXT_MENU -> {} // stub
            else -> {}
        }
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
                activity?.window?.attributes = (activity.window.attributes).apply {
                    screenBrightness = currentBrightness
                }
                onIndicator(GestureAction.BRIGHTNESS.defaultIndicator(currentBrightness))
            }
            GesturePhase.END -> {
                onIndicator(GestureAction.BRIGHTNESS.defaultIndicator(currentBrightness))
                onIndicatorEnd()
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
                val index = (currentVolume * maxVolume).toInt().coerceIn(0, maxVolume)
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
                onIndicator(GestureAction.VOLUME.defaultIndicator(currentVolume))
            }
            GesturePhase.END -> {
                onIndicator(GestureAction.VOLUME.defaultIndicator(currentVolume))
                onIndicatorEnd()
            }
        }
    }

    // ---- Speed hold with optional swipe modulation ----
    private fun handleSpeedHold(frame: GestureFrame, baseMultiplier: Float) {
        val sensitivity = 150f // px per 1x speed change

        when (frame.phase) {
            GesturePhase.START -> {
                snapshotSpeed()
                // Apply base multiplier on hold activation
                viewModel.setPlaybackSpeed(baseMultiplier)
                onIndicator(GestureAction.SPEEDUP.defaultIndicator(baseMultiplier))
            }
            GesturePhase.ACTIVE -> {
                // totalDelta.x: positive = swipe right (faster), negative = left (slower)
                val modulation = frame.totalDelta.x / sensitivity
                val speed = (baseMultiplier + modulation).coerceIn(0.25f, 4f)
                viewModel.setPlaybackSpeed(speed)
                onIndicator(GestureAction.SPEEDUP.defaultIndicator(speed))
            }
            GesturePhase.END -> {
                viewModel.setPlaybackSpeed(originalSpeed)
                onIndicatorEnd()
            }
        }
    }

    // ---- Morph to floating (swipe vertical downward) ----
    //    No indicator — morph is visual through the player animation itself.
    private fun handleMorphToFloating(frame: GestureFrame) {
        when (frame.phase) {
            GesturePhase.START -> {
                morphStartDelta = 0f
                onMorphDragStart()
            }
            GesturePhase.ACTIVE -> {
                val dragY = frame.totalDelta.y.coerceAtLeast(0f)
                if (dragY > 0f) {
                    morphStartDelta = dragY
                    onMorphDrag(dragY)
                }
            }
            GesturePhase.END -> {
                onMorphDragEnd(morphStartDelta)
            }
        }
    }

    // ---- Morph to fullscreen (swipe vertical upward — stub) ----
    private fun handleMorphToFullscreen(frame: GestureFrame) {
        // Currently handled as instant on double-tap via handleInstantAction
        // Swipe-up variant is a stub for now
    }
}

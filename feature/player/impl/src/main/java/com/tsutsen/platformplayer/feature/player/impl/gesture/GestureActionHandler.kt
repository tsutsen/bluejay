package com.tsutsen.platformplayer.feature.player.impl.gesture

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import com.tsutsen.platformplayer.feature.player.impl.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // --- speed hold keep-alive ---
    private var speedHoldJob: Job? = null

    // --- speed hold state ---
    private var lastSpeedHoldReported = 0f

    // --- consecutive double-tap seek accumulation ---
    private var lastSeekAction: GestureAction? = null
    private var lastSeekTimeMs: Long = 0L
    private var accumulatedSeekMs: Long = 0L

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
            GestureAction.REWIND_BACK,
            GestureAction.REWIND_FORWARD -> handleAccumulatedSeek(event.action)
            GestureAction.MORPH_TO_FLOATING -> viewModel.minimize()
            GestureAction.MORPH_TO_FULLSCREEN -> viewModel.toggleFullscreen()
            GestureAction.CONTEXT_MENU -> {} // stub
            else -> {}
        }
    }

    /**
     * Applies a ±5s seek and, if the same direction was tapped again within
     * [SEEK_ACCUMULATE_WINDOW_MS], accumulates the total so the badge shows
     * e.g. -15s / +20s instead of always -5s / +5s.
     */
    private fun handleAccumulatedSeek(action: GestureAction) {
        val now = System.currentTimeMillis()
        val stepMs = if (action == GestureAction.REWIND_BACK) -SEEK_STEP_MS else SEEK_STEP_MS

        accumulatedSeekMs = if (
            action == lastSeekAction &&
            now - lastSeekTimeMs < SEEK_ACCUMULATE_WINDOW_MS
        ) {
            accumulatedSeekMs + stepMs
        } else {
            stepMs
        }
        lastSeekAction = action
        lastSeekTimeMs = now

        viewModel.seekBy(stepMs)

        val seconds = accumulatedSeekMs / 1000
        val label = if (seconds > 0) "+${seconds}s" else "${seconds}s"
        val isBack = action == GestureAction.REWIND_BACK
        onIndicator(
            GestureIndicator.TextBadge(
                key = if (isBack) "rewind_back" else "rewind_forward",
                label = label,
                icon = if (isBack) Icons.Default.Replay10 else Icons.Default.Forward10,
            )
        )
        // Skip onIndicatorEnd — badge auto-hides via overlay
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

    companion object {
        private const val SEEK_STEP_MS = 5000L
        /** Window in which same-direction double-taps accumulate into one running total. */
        private const val SEEK_ACCUMULATE_WINDOW_MS = 800L

        /** Horizontal px to travel for one ±0.1x speed step. */
        private const val SPEED_SWIPE_STEP_PX = 200f
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
                lastSpeedHoldReported = 0f
                viewModel.setPlaybackSpeed(baseMultiplier)
                onIndicator(GestureAction.SPEEDUP.defaultIndicator(baseMultiplier))
                // Start keep-alive coroutine — emits onIndicator periodically so badge stays visible
                speedHoldJob?.cancel()
                speedHoldJob = CoroutineScope(Dispatchers.Default).launch {
                    while (true) {
                        delay(KEEP_ALIVE_INTERVAL_MS)
                        val steps = 0
                        val speed = (baseMultiplier + steps * SPEED_STEP).coerceIn(0.25f, 4f)
                        val snapped = (speed * 10).toInt() / 10f
                        onIndicator(GestureAction.SPEEDUP.defaultIndicator(snapped))
                    }
                }
            }
            GesturePhase.ACTIVE -> {
                // totalDelta.x: positive = swipe right (faster), negative = left (slower)
                val steps = (frame.totalDelta.x / SPEED_SWIPE_STEP_PX).toInt()
                val speed = (baseMultiplier + steps * SPEED_STEP).coerceIn(0.25f, 4f)
                // Round to nearest 0.1 to avoid floating-point drift
                val snapped = (speed * 10).toInt() / 10f
                if (snapped != lastSpeedHoldReported) {
                    lastSpeedHoldReported = snapped
                    viewModel.setPlaybackSpeed(snapped)
                }
                // Emit on finger movement — keep-alive coroutine handles the rest
                onIndicator(GestureAction.SPEEDUP.defaultIndicator(snapped))
            }
            GesturePhase.END -> {
                speedHoldJob?.cancel()
                speedHoldJob = null
                viewModel.setPlaybackSpeed(originalSpeed)
                onIndicator(GestureAction.SPEEDUP.defaultIndicator(originalSpeed))
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

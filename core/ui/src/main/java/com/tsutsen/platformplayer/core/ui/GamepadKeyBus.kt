package com.tsutsen.platformplayer.core.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.tsutsen.platformplayer.core.model.PlayerControllerActions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs

/**
 * Gamepad/controller input bus — Cemu's `GamepadInputSource` design.
 *
 * Android delivers controller input on two channels (the same split Cemu's
 * InputMapper uses):
 *  - [KeyEvent]s for every *digital* button (A/B/X/Y, shoulders L1/R1,
 *    start, select) and the left stick (the driver auto-maps stick
 *    deflection to DPAD keys);
 *  - generic [MotionEvent]s (gamepad/joystick source) for the *analog*
 *    parts that never become keys: triggers (AXIS_LTRIGGER/RTRIGGER →
 *    L2/R2) and the right stick (AXIS_RX/RY/RZ, which has no keycodes at
 *    all). Controller motion arrives via `dispatchGenericMotionEvent`,
 *    NOT `dispatchTouchEvent`.
 *
 * The activity feeds both channels into [events] (press-edges only).
 * Two kinds of consumers:
 *  - the **player** registers a [setPlayerHandler] while composed; the
 *    activity consumes a key only when that handler handles it, so
 *    unbound keys keep working with normal UI navigation;
 *  - the **settings binding popup** is a non-focusable Compose `Popup`,
 *    so the activity keeps input focus; it calls [beginCapture] and
 *    collects [events] — the activity then consumes *everything*
 *    (Cemu's `hasSubscribers` rule), which is why the very first press
 *    binds and BACK cannot dismiss the popup mid-capture.
 */
object GamepadKeyBus {

    data class GamepadEvent(val keyCode: Int, val deviceName: String?)

    private val _events = MutableSharedFlow<GamepadEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GamepadEvent> = _events.asSharedFlow()

    fun emit(event: GamepadEvent) {
        _events.tryEmit(event)
    }

    // ----- capture (settings binding popup) -----

    @Volatile
    private var capturing = false

    val isCapturing: Boolean
        get() = capturing

    fun beginCapture() {
        capturing = true
    }

    fun endCapture() {
        capturing = false
    }

    // ----- player handler -----

    private var playerHandler: ((GamepadEvent) -> Boolean)? = null

    fun setPlayerHandler(handler: ((GamepadEvent) -> Boolean)?) {
        playerHandler = handler
    }

    fun consumeIfPlayer(event: GamepadEvent): Boolean =
        playerHandler?.invoke(event) ?: false

    // -----------------------------------------------------------------
    // Analog axes (Cemu InputMapper pattern: threshold + press edges)
    // -----------------------------------------------------------------

    /** Cemu's MIN_ABS_AXIS_VALUE — stick center dead zone. */
    private const val MIN_AXIS = 0.33f
    /** Below this an axis is considered fully released (spring-back). */
    private const val RELEASE_AXIS = 0.05f

    private val pressedAxes = HashSet<Int>()

    fun isControllerMotion(event: MotionEvent): Boolean =
        (event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)) != 0

    /**
     * Axis press-edges in [event] (rising edges only: an axis crossing
     * [MIN_AXIS] yields one event; it clears when the axis returns to
     * rest).
     */
    fun motionEdges(event: MotionEvent): List<GamepadEvent> {
        if (!isControllerMotion(event)) return emptyList()
        val deviceId = event.deviceId
        val name = InputDevice.getDevice(deviceId)?.name
        val edges = mutableListOf<GamepadEvent>()

        // Triggers: one-directional (0..1)
        for ((axis, keyCode) in listOf(MotionEvent.AXIS_LTRIGGER to KeyEvent.KEYCODE_BUTTON_L2, MotionEvent.AXIS_RTRIGGER to KeyEvent.KEYCODE_BUTTON_R2)) {
            val value = event.getAxisValue(axis)
            if (value > MIN_AXIS) {
                if (markPressed(deviceId, axis, 1)) edges += GamepadEvent(keyCode, name)
            } else if (value < RELEASE_AXIS) {
                unpress(deviceId, axis, 1)
            }
        }

        // Right stick: two-directional (-1..1), each direction its own key
        for (axis in AXIS_STICK_RIGHT) {
            val value = event.getAxisValue(axis)
            if (value > MIN_AXIS) {
                if (markPressed(deviceId, axis, 1)) edges += GamepadEvent(rightStickKey(axis, positive = true), name)
            } else if (value < -MIN_AXIS) {
                if (markPressed(deviceId, axis, 0)) edges += GamepadEvent(rightStickKey(axis, positive = false), name)
            } else if (abs(value) < RELEASE_AXIS) {
                unpress(deviceId, axis, 0)
                unpress(deviceId, axis, 1)
            }
        }
        return edges
    }

    private fun rightStickKey(axis: Int, positive: Boolean): Int =
        when (axis) {
            MotionEvent.AXIS_RX, MotionEvent.AXIS_RZ ->
                if (positive) PlayerControllerActions.KEY_RIGHT_STICK_RIGHT else PlayerControllerActions.KEY_RIGHT_STICK_LEFT
            MotionEvent.AXIS_RY ->
                if (positive) PlayerControllerActions.KEY_RIGHT_STICK_DOWN else PlayerControllerActions.KEY_RIGHT_STICK_UP
            else -> -1
        }

    private fun stateKey(deviceId: Int, axis: Int, sign: Int) = deviceId * 10000 + axis * 2 + sign

    private fun markPressed(deviceId: Int, axis: Int, sign: Int): Boolean =
        pressedAxes.add(stateKey(deviceId, axis, sign))

    private fun unpress(deviceId: Int, axis: Int, sign: Int) {
        pressedAxes.remove(stateKey(deviceId, axis, sign))
    }
}

private val AXIS_STICK_RIGHT = listOf(MotionEvent.AXIS_RX, MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ)

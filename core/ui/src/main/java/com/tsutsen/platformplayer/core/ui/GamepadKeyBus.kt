package com.tsutsen.platformplayer.core.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.tsutsen.platformplayer.core.model.PlayerControllerActions
import kotlin.math.abs

/**
 * Gamepad/controller input bus.
 *
 * Android delivers controller input on two channels (the same split Cemu's
 * InputMapper uses):
 *  - [KeyEvent]s for every *digital* button (A/B/X/Y, shoulders L1/R1,
 *    start, select) and the left stick (the driver auto-maps stick
 *    deflection to DPAD keys);
 *  - [MotionEvent]s (gamepad/joystick source) for the *analog* parts that
 *    never become keys: triggers (AXIS_LTRIGGER/RTRIGGER → L2/R2) and the
 *    right stick (AXIS_RX/RY/RZ, which has no keycodes at all).
 *
 * Both channels funnel press-edges into [dispatch] as [GamepadEvent].
 * Handlers must only be registered while the input is meant for them (e.g.
 * the player screen while composed, the settings binding dialog via a
 * view-level OnKeyListener/OnTouchListener — see SettingsScreen).
 *
 * Events are handled by whichever window has focus: the activity
 * (dispatchKeyEvent / dispatchTouchEvent) normally, or the dialog's own
 * window while a dialog is open — a dialog's key/motion events never
 * reach the activity.
 */
object GamepadKeyBus {

    data class GamepadEvent(val keyCode: Int, val deviceName: String?)

    private val handlers = mutableListOf<(GamepadEvent) -> Boolean>()

    fun addHandler(handler: (GamepadEvent) -> Boolean) {
        handlers += handler
    }

    fun removeHandler(handler: (GamepadEvent) -> Boolean) {
        handlers -= handler
    }

    private fun dispatch(event: GamepadEvent): Boolean {
        if (handlers.isEmpty()) return false
        return handlers.any { it(event) }
    }

    /** Feed from `Activity.dispatchKeyEvent`. */
    fun dispatchKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        return dispatch(GamepadEvent(event.keyCode, event.device?.name))
    }

    /**
     * Feed from `Activity.dispatchTouchEvent`. Returns true when the event
     * is controller button/axis motion and should be consumed (so it never
     * reaches the UI as a phantom touch).
     */
    fun dispatchMotion(event: MotionEvent): Boolean {
        if (!isControllerMotion(event)) return false
        if (event.action == MotionEvent.ACTION_BUTTON_PRESS) {
            motionEdges(event).forEach { dispatch(it) }
        }
        return true
    }

    // -----------------------------------------------------------------
    // Analog axes (Cemu InputMapper pattern: threshold + press edges)
    // -----------------------------------------------------------------

    /** Cemu's MIN_ABS_AXIS_VALUE — stick center dead zone. */
    private const val MIN_AXIS = 0.33f
    /** Below this an axis is considered fully released (spring-back). */
    private const val RELEASE_AXIS = 0.05f

    private val pressedAxes = HashSet<Int>()

    /**
     * Axis press-edges in [event] (rising edges only: an axis crossing
     * [MIN_AXIS] yields one event; it clears when the axis returns to
     * rest). Usable by both the activity and the binding dialog.
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
            // two-directional (-1..1): each direction is its own key
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

    fun isControllerMotion(event: MotionEvent): Boolean =
        (event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)) != 0

    private fun stateKey(deviceId: Int, axis: Int, sign: Int) = deviceId * 10000 + axis * 2 + sign

    private fun markPressed(deviceId: Int, axis: Int, sign: Int): Boolean =
        pressedAxes.add(stateKey(deviceId, axis, sign))

    private fun unpress(deviceId: Int, axis: Int, sign: Int) {
        pressedAxes.remove(stateKey(deviceId, axis, sign))
    }
}

private val AXIS_STICK_RIGHT = listOf(MotionEvent.AXIS_RX, MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ)

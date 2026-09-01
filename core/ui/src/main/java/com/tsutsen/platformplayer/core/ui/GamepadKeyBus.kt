package com.tsutsen.platformplayer.core.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.tsutsen.platformplayer.core.model.PlayerControllerActions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Gamepad/controller input bus — Cemu's `GamepadInputSource` design.
 *
 * Android delivers controller input on two channels:
 *  - [KeyEvent]s for every *digital* button (A/B/X/Y, L1/R1/L2/R2, start,
 *    select, media keys, d-pad on controllers that report it as keys);
 *  - generic [MotionEvent]s (gamepad/joystick source, via
 *    `dispatchGenericMotionEvent`) for *analog* inputs that have no
 *    keycodes: both sticks (AXIS_X/Y, or AXIS_Z/RZ on drivers that map
 *    the right stick there) and d-pads that report as HAT axes.
 *
 * The activity feeds both channels in ([onKeyEvent]/[onMotionEvent]), which
 * turns raw input into press/release edges on [events]. Two consumers:
 *  - the **player** registers a [setPlayerHandler] while composed; the
 *    activity consumes an event only when that handler handles it, so
 *    unbound keys keep normal behavior (navigation, system keys);
 *  - the **settings binding popup** is a non-focusable Compose `Popup`, so
 *    the activity keeps input focus; it calls [beginCapture] and collects
 *    [events] — while capturing the activity consumes *everything* (Cemu's
 *    `hasSubscribers` rule), so the very first press binds and BACK cannot
 *    dismiss the popup mid-capture.
 *
 * Thresholds were tuned against a real controller capture (getevent): full
 * stick pushes reach ±1.0, push crosstalk stays ≤ ~0.45, rest jitter ≤ ~0.02
 * — press at 0.5, release at 0.2.
 */
object GamepadKeyBus {

    data class GamepadEvent(
        val keyCode: Int,
        val deviceName: String?,
        val isPress: Boolean,
    )

    private val _events = MutableSharedFlow<GamepadEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GamepadEvent> = _events.asSharedFlow()

    // ----- capture (settings binding popup) -----

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
    // Activity entry points (main thread only)
    // -----------------------------------------------------------------

    /**
     * Handle a key event from `Activity.dispatchKeyEvent`. Returns true to
     * consume. Emits press/release edges for every real key-edge; while
     * capturing everything is consumed, otherwise only what the player
     * handler handles.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val isPress = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        if (!isPress && event.action != KeyEvent.ACTION_UP) return false
        val edge = emitEdge(event.deviceId, event.keyCode, isPress, event.device?.name)
            ?: return false
        return if (capturing) true else consumeIfPlayer(edge)
    }

    /**
     * Handle controller motion from `Activity.dispatchGenericMotionEvent`.
     * Sticks and HAT d-pads travel this channel; real touch events never
     * have a gamepad/joystick source and pass through untouched.
     */
    fun onMotionEvent(event: MotionEvent): Boolean {
        if (!isControllerMotion(event)) return false
        val action = event.actionMasked
        if (
            action != MotionEvent.ACTION_MOVE &&
            action != MotionEvent.ACTION_BUTTON_PRESS &&
            action != MotionEvent.ACTION_BUTTON_RELEASE
        ) {
            return false
        }
        val deviceId = event.deviceId
        val name = InputDevice.getDevice(deviceId)?.name
        var consumed = false
        for (axis in AXIS_KEYS) {
            for (edge in axisEdges(deviceId, axis, event.getAxisValue(axis), name)) {
                if (!capturing) consumed = consumed || consumeIfPlayer(edge)
            }
        }
        return if (capturing) true else consumed
    }

    fun isControllerMotion(event: MotionEvent): Boolean =
        (event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)) != 0

    // -----------------------------------------------------------------
    // Press/release edges
    // -----------------------------------------------------------------

    /** Axes that synthesize keys; values are normalized to -1..1 / 0..1. */
    private val AXIS_KEYS =
        listOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_RX,
            MotionEvent.AXIS_RY,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_HAT_Y,
        )

    /** Full pushes reach 1.0, crosstalk ≤ ~0.45 — press above this. */
    private const val PRESS = 0.5f
    /** Stick counts as released below this (hysteresis vs [PRESS]). */
    private const val RELEASE = 0.2f

    /** Logical keys currently pressed, keyed by (deviceId, keyCode). */
    private val pressedKeys = HashSet<Long>()
    /** Currently held direction per (deviceId, axis); 0 = none. */
    private val axisState = HashMap<Long, Int>()

    /**
     * Emit a press/release edge unless it duplicates the current state
     * (e.g. the same logical key driven by two sources). Returns the
     * emitted event, or null if nothing changed.
     */
    private fun emitEdge(deviceId: Int, keyCode: Int, isPress: Boolean, name: String?): GamepadEvent? {
        val id = (deviceId.toLong() shl 32) or (keyCode.toLong() and 0xFFFFFFFFL)
        val edge =
            when {
                isPress && !pressedKeys.contains(id) -> {
                    pressedKeys.add(id)
                    GamepadEvent(keyCode, name, isPress = true)
                }

                !isPress && pressedKeys.contains(id) -> {
                    pressedKeys.remove(id)
                    GamepadEvent(keyCode, name, isPress = false)
                }

                else -> null
            }
        edge?.let { _events.tryEmit(it) }
        return edge
    }

    /**
     * Direction a stick/HAT axis is currently pointing at, using [PRESS]
     * (sticks) / ±0.5 (HATs are exactly ±1/0). Left stick = AXIS_X/Y,
     * right stick = AXIS_RX/RY *or* AXIS_Z/RZ (driver-dependent).
     */
    private fun pressKeyFor(axis: Int, value: Float): Int? =
        when (axis) {
            MotionEvent.AXIS_X ->
                if (value > PRESS) PlayerControllerActions.KEY_LEFT_STICK_RIGHT
                else if (value < -PRESS) PlayerControllerActions.KEY_LEFT_STICK_LEFT
                else null

            MotionEvent.AXIS_Y ->
                if (value > PRESS) PlayerControllerActions.KEY_LEFT_STICK_DOWN
                else if (value < -PRESS) PlayerControllerActions.KEY_LEFT_STICK_UP
                else null

            MotionEvent.AXIS_RX,
            MotionEvent.AXIS_Z ->
                if (value > PRESS) PlayerControllerActions.KEY_RIGHT_STICK_RIGHT
                else if (value < -PRESS) PlayerControllerActions.KEY_RIGHT_STICK_LEFT
                else null

            MotionEvent.AXIS_RY,
            MotionEvent.AXIS_RZ ->
                if (value > PRESS) PlayerControllerActions.KEY_RIGHT_STICK_DOWN
                else if (value < -PRESS) PlayerControllerActions.KEY_RIGHT_STICK_UP
                else null

            MotionEvent.AXIS_HAT_X ->
                if (value > 0.5f) KeyEvent.KEYCODE_DPAD_RIGHT
                else if (value < -0.5f) KeyEvent.KEYCODE_DPAD_LEFT
                else null

            MotionEvent.AXIS_HAT_Y ->
                if (value > 0.5f) KeyEvent.KEYCODE_DPAD_DOWN
                else if (value < -0.5f) KeyEvent.KEYCODE_DPAD_UP
                else null

            else -> null
        }

    /**
     * State machine per (deviceId, axis): emits the release of the held
     * direction when the axis returns near center ([RELEASE]), and
     * release+press when it flips straight through center to the other
     * side. Values between [RELEASE] and [PRESS] in the held direction
     * are held state (no events) — that hysteresis kills threshold flapping.
     */
    private fun axisEdges(deviceId: Int, axis: Int, value: Float, name: String?): List<GamepadEvent> {
        val stateKey = (deviceId.toLong() shl 32) or (axis.toLong() and 0xFFFFFL)
        val prev = axisState[stateKey] ?: 0
        val current = pressKeyFor(axis, value)
        val released = value in -RELEASE..RELEASE
        return when {
            prev == 0 && current != null -> {
                axisState[stateKey] = current
                listOfNotNull(emitEdge(deviceId, current, isPress = true, name = name))
            }

            prev != 0 && current != null && current != prev -> {
                axisState[stateKey] = current
                listOfNotNull(
                    emitEdge(deviceId, prev, isPress = false, name = name),
                    emitEdge(deviceId, current, isPress = true, name = name),
                )
            }

            prev != 0 && current == null && released -> {
                axisState[stateKey] = 0
                listOfNotNull(emitEdge(deviceId, prev, isPress = false, name = name))
            }

            else -> emptyList()
        }
    }
}

package com.tsutsen.platformplayer.core.model

import android.view.KeyEvent

/**
 * Player controller (gamepad / TV remote) action catalog.
 *
 * The stable action ids are the keys of the button mapping stored in the
 * settings ([com.tsutsen.platformplayer.core.datastore.model.ControllerPreferences]).
 * [defaultKeyCode] is the sensible binding for a standard gamepad or Android
 * remote; the user can remap any action to any key.
 */
object PlayerControllerActions {
    /**
     * Synthesized keycodes for the sticks, which the framework exposes only
     * as motion axes (no real keycodes exist). High values keep them clear
     * of real keycodes; GamepadKeyBus emits them as press edges.
     *
     * The right stick may arrive on AXIS_RX/RY (standard) or AXIS_Z/RZ
     * (some uinput drivers map it there — measured on a real controller);
     * both pairs produce these same keys.
     */
    const val KEY_RIGHT_STICK_UP = 0x10001
    const val KEY_RIGHT_STICK_DOWN = 0x10002
    const val KEY_RIGHT_STICK_LEFT = 0x10003
    const val KEY_RIGHT_STICK_RIGHT = 0x10004
    const val KEY_LEFT_STICK_UP = 0x10005
    const val KEY_LEFT_STICK_DOWN = 0x10006
    const val KEY_LEFT_STICK_LEFT = 0x10007
    const val KEY_LEFT_STICK_RIGHT = 0x10008

    const val PLAY_PAUSE = "play_pause"
    const val SEEK_BACK = "seek_back"
    const val SEEK_FORWARD = "seek_forward"
    const val SPEED_UP = "speed_up"
    const val SPEED_DOWN = "speed_down"
    const val NEXT = "next"
    const val PREVIOUS = "previous"
    const val CLOSE = "close"
    const val BRIGHTNESS_UP = "brightness_up"
    const val BRIGHTNESS_DOWN = "brightness_down"
    const val VOLUME_UP = "volume_up"
    const val VOLUME_DOWN = "volume_down"

    data class Action(
        val id: String,
        val label: String,
        val defaultKeyCode: Int,
    )

    val ALL: List<Action> =
        listOf(
            Action(PLAY_PAUSE, "Play / pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
            Action(SEEK_BACK, "Jump backwards", KeyEvent.KEYCODE_MEDIA_REWIND),
            Action(SEEK_FORWARD, "Jump forwards", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD),
            Action(SPEED_UP, "Speed up (hold)", KeyEvent.KEYCODE_BUTTON_L2),
            Action(SPEED_DOWN, "Speed down (hold)", KeyEvent.KEYCODE_BUTTON_R2),
            Action(NEXT, "Next video", KeyEvent.KEYCODE_MEDIA_NEXT),
            Action(PREVIOUS, "Previous video", KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            Action(CLOSE, "Close video", KeyEvent.KEYCODE_BACK),
            Action(BRIGHTNESS_UP, "Brightness up", KeyEvent.KEYCODE_BRIGHTNESS_UP),
            Action(BRIGHTNESS_DOWN, "Brightness down", KeyEvent.KEYCODE_BRIGHTNESS_DOWN),
            Action(VOLUME_UP, "Volume up", KeyEvent.KEYCODE_VOLUME_UP),
            Action(VOLUME_DOWN, "Volume down", KeyEvent.KEYCODE_VOLUME_DOWN),
        )

    /** "KEYCODE_MEDIA_PLAY_PAUSE" → "Media play pause". */
    fun labelFor(keyCode: Int): String =
        when (keyCode) {
            KEY_RIGHT_STICK_UP -> "Right stick up"
            KEY_RIGHT_STICK_DOWN -> "Right stick down"
            KEY_RIGHT_STICK_LEFT -> "Right stick left"
            KEY_RIGHT_STICK_RIGHT -> "Right stick right"
            KEY_LEFT_STICK_UP -> "Left stick up"
            KEY_LEFT_STICK_DOWN -> "Left stick down"
            KEY_LEFT_STICK_LEFT -> "Left stick left"
            KEY_LEFT_STICK_RIGHT -> "Left stick right"
            KeyEvent.KEYCODE_DPAD_UP -> "D-pad up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "D-pad down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "D-pad left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "D-pad right"
            else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')
        }
}

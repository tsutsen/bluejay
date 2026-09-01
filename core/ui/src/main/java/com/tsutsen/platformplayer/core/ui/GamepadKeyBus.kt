package com.tsutsen.platformplayer.core.ui

import android.view.KeyEvent

/**
 * Routes raw Android key events (gamepads, TV remotes) to interested parties.
 *
 * [dispatch] is called from the activity's [android.app.Activity.dispatchKeyEvent].
 *
 * - [capture]: at most one binding-capture listener (the Settings controller
 *   page). While set, it swallows every key-down so a keypress cannot leak
 *   through to the app (e.g. back exiting the screen mid-capture).
 * - handler list: long-lived consumers (the player screen registers one).
 *   A handler returns true to consume the event.
 */
object GamepadKeyBus {

    @Volatile
    var capture: ((KeyEvent) -> Unit)? = null

    private val handlers = mutableListOf<(KeyEvent) -> Boolean>()

    fun addHandler(handler: (KeyEvent) -> Boolean) {
        handlers.add(handler)
    }

    fun removeHandler(handler: (KeyEvent) -> Boolean) {
        handlers.remove(handler)
    }

    /** @return true when the event was consumed by the bus. */
    fun dispatch(event: KeyEvent): Boolean {
        val c = capture
        if (c != null && event.action == KeyEvent.ACTION_DOWN) {
            c(event)
            return true
        }
        for (h in handlers.toList()) {
            if (h(event)) return true
        }
        return false
    }
}

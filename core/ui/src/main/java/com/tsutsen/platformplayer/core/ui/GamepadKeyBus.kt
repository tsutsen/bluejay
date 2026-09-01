package com.tsutsen.platformplayer.core.ui

import android.view.KeyEvent

/**
 * Routes raw Android key events (gamepads, TV remotes) to long-lived
 * consumers.
 *
 * [dispatch] is called from the activity's
 * [android.app.Activity.dispatchKeyEvent], so it sees every key event for
 * the main window. (Key events for child windows — e.g. a Compose Dialog —
 * never pass through here; such UI captures keys locally instead.)
 *
 * Handlers are registered by long-lived screens (the player registers one
 * while composed). A handler returns true to consume the event.
 */
object GamepadKeyBus {

    private val handlers = mutableListOf<(KeyEvent) -> Boolean>()

    fun addHandler(handler: (KeyEvent) -> Boolean) {
        handlers.add(handler)
    }

    fun removeHandler(handler: (KeyEvent) -> Boolean) {
        handlers.remove(handler)
    }

    /** @return true when the event was consumed by the bus. */
    fun dispatch(event: KeyEvent): Boolean {
        for (h in handlers.toList()) {
            if (h(event)) return true
        }
        return false
    }
}

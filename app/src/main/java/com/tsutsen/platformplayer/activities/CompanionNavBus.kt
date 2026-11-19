package com.tsutsen.platformplayer.activities

import java.lang.ref.WeakReference

/**
 * Process-wide bridge between [MainActivity] (which owns the [Navigator])
 * and [CompanionActivity]. When screens are swapped, MainActivity sits on
 * the non-default display and the companion UI runs as a real activity on
 * the DEFAULT display — a separate task that cannot reach MainActivity's
 * navigator instance, so cross-screen navigation goes through these
 * callbacks instead.
 *
 * MainActivity installs the callbacks once at startup and refreshes them
 * whenever it (re)creates a companion host; CompanionActivity reads them
 * null-safely (a null callback means MainActivity is gone — the companion
 * finishes itself, see [requestFinish]).
 */
object CompanionNavBus {
    /** Tapping the channel badge on the second screen: navigate the main screen. */
    var onChannelClick: ((String) -> Unit)? = null

    /** Tapping a library playlist title: open the playlist on the main screen. */
    var onPlaylistClick: ((String) -> Unit)? = null

    /** Tapping the stats card: open the watch-stats detail on the main screen. */
    var onWatchStats: (() -> Unit)? = null

    /**
     * Set by MainActivity whenever the companion host (presentation or
     * activity) should go away — dual-screen disabled, no display, or the
     * app left the foreground. CompanionActivity checks this in onResume
     * and finishes; the weak reference lets MainActivity also finish it
     * directly.
     */
    @Volatile
    var requestFinish: Boolean = false

    /** Live CompanionActivity instance, if any (set in onCreate/onDestroy). */
    var companionActivityRef: WeakReference<CompanionActivity>? = null
        set(value) {
            field = value
            // A fresh registration clears any pending finish request.
            if (value != null) requestFinish = false
        }

    /** Finish the companion activity if one is alive. */
    fun finishCompanionActivity() {
        requestFinish = true
        companionActivityRef?.get()?.finish()
    }
}

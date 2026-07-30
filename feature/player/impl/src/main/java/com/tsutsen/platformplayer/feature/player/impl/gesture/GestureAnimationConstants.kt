package com.tsutsen.platformplayer.feature.player.impl.gesture

/** Shared animation constants for gesture indicators. */
internal object GestureAnimationConstants {
    /** Fade in / fade out duration. */
    const val INDICATOR_ANIM_MS = 200

    /** Time the badge stays fully visible before fading out. */
    const val INDICATOR_HIDE_DELAY_MS = 1500L

    /**
     * How often to refresh a hold badge to keep it visible.
     * Set to hide_delay so the refresh fires before fade-out begins.
     */
    const val BADGE_REFRESH_MS: Long = INDICATOR_HIDE_DELAY_MS
}

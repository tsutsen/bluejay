package com.tsutsen.platformplayer.feature.player.impl.gesture

/** Shared animation constants for gesture indicators. */
internal object GestureAnimationConstants {
    /** Fade in / fade out duration. */
    const val INDICATOR_ANIM_MS = 200

    /** Time the badge stays fully visible before fading out. */
    const val INDICATOR_HIDE_DELAY_MS = 1500L

    /**
     * How often to refresh a hold badge to keep it visible.
     * Set to hide_delay + anim_ms so the refresh fires just before
     * the fade-out would begin (1500 + 200 = 1700ms).
     */
    const val BADGE_REFRESH_MS: Long = INDICATOR_HIDE_DELAY_MS + INDICATOR_ANIM_MS
}

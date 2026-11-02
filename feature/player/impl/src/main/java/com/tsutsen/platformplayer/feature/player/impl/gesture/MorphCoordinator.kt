package com.tsutsen.platformplayer.feature.player.impl.gesture

/**
 * Pure decision core of the morph / shrink / fullscreen drag choreography
 * (moved out of PlayerView's remember-block). No Compose: drag values in,
 * settle decisions out — PlayerView applies the results to its Animatables
 * and view-model flips. Thresholds, commit fractions and velocity seeding
 * are moved verbatim from the drag callbacks.
 */
internal class MorphCoordinator {
    companion object {
        /** A release past this fraction of the travel commits the transition. */
        const val COMMIT_FRACTION = 0.4f
    }

    /** One drag frame's progress toward the target (0..1; 0 without travel). */
    fun dragProgress(dragPx: Float, travelPx: Float): Float =
        if (travelPx > 0f) (dragPx / travelPx).coerceIn(0f, 1f) else 0f

    /** Release velocity in progress/ms (0 without travel). */
    fun releaseVelocity(velocityPxPerMs: Float, travelPx: Float): Float =
        if (travelPx > 0f) velocityPxPerMs / travelPx else 0f

    /**
     * A release flick AWAY from the target would make the spring move the
     * wrong way before reversing — clamp it to rest. Toward the target it
     * is kept as-is (overshoot included: that is the momentum).
     */
    fun velocityToward(current: Float, target: Float, velocityPps: Float): Float =
        if (target >= current) velocityPps.coerceAtLeast(0f) else velocityPps.coerceAtMost(0f)

    /**
     * Drag release for the morph-to-floating and fullscreen drags (same
     * rule): commit past [COMMIT_FRACTION] → settle to 1, cancel → settle
     * to 0; the settle spring is seeded with the release velocity.
     */
    fun release(
        progress: Float,
        velocityPxPerMs: Float,
        travelPx: Float,
    ): MorphSettle {
        val commit = progress > COMMIT_FRACTION
        return MorphSettle(
            commit = commit,
            target = if (commit) 1f else 0f,
            velocityProgressPerMs = releaseVelocity(velocityPxPerMs, travelPx),
        )
    }

    /**
     * Shrink release. Commit: fold the shrink axis into the fullscreen axis
     * (single-axis move via [fold]) and settle fullscreen to normal — the
     * axis inherits the shrink velocity, REVERSED: the shrink axis grows
     * downward as the fs axis shrinks downward. Cancel: spring the shrink
     * axis back with the same reversed velocity, keeping it continuous with
     * the release instead of a snap-pop.
     */
    fun shrinkRelease(
        progress: Float,
        velocityPxPerMs: Float,
        travelPx: Float,
    ): ShrinkSettle =
        ShrinkSettle(
            commit = progress > COMMIT_FRACTION,
            velocityProgressPerMs = -releaseVelocity(velocityPxPerMs, travelPx),
        )

    /** Commit-fold: the fullscreen progress that absorbs the shrink axis. */
    fun fold(fullscreenProgress: Float, shrinkProgress: Float): Float =
        fullscreenProgress * (1f - shrinkProgress)
}

/** Settle decision for the morph / fullscreen axis. */
data class MorphSettle(
    val commit: Boolean,
    val target: Float,
    val velocityProgressPerMs: Float,
)

/** Shrink release decision: commit flag + the reversed seeded settle velocity. */
data class ShrinkSettle(
    val commit: Boolean,
    val velocityProgressPerMs: Float,
)

package com.tsutsen.platformplayer.feature.player.impl

/**
 * Three-branch alpha interpolation: 0 before `start`, 1 after `end`, linear in between.
 * When `reversed = true`, starts at 1 and fades to 0 across the same range.
 * Result is always coerced to [0f, 1f].
 *
 * This replaces the repeated inline 3-branch pattern:
 * ```
 * if (p <= start) 0f
 * else if (p >= end) 1f
 * else (p - start) / (end - start)
 * ```
 *
 * Used by PlayerContent.kt and PlayerControls.kt for all morph-transition alpha
 * computations. Pure function — no Compose state, no side effects, unit-testable.
 */
fun progressAlpha(
    p: Float,
    start: Float,
    end: Float,
    reversed: Boolean = false
): Float = when {
    !reversed -> when {
        p <= start -> 0f
        p >= end -> 1f
        else -> (p - start) / (end - start)
    }
    else -> when {
        p <= start -> 1f
        p >= end -> 0f
        else -> 1f - (p - start) / (end - start)
    }
}.coerceIn(0f, 1f)

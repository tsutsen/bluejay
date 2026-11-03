package com.tsutsen.platformplayer.feature.player.impl

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the seek badge accumulation: same-direction taps inside the window
 * add up into one running total; a direction flip or a window-gap restarts.
 */
class SeekBadgeAccumulatorTest {
    @Test
    fun sameDirectionInsideWindowAccumulates() {
        val acc = SeekBadgeAccumulator()
        assertEquals(-5_000L, acc.accumulate(-5_000L, nowMs = 1_000L))
        // Second -5s tap 300ms later: running total "-10s".
        assertEquals(-10_000L, acc.accumulate(-5_000L, nowMs = 1_300L))
    }

    @Test
    fun gapBeyondWindowRestarts() {
        val acc = SeekBadgeAccumulator(windowMs = 800L)
        assertEquals(-5_000L, acc.accumulate(-5_000L, nowMs = 1_000L))
        // 800ms after the first tap: outside the window, restart at the new delta.
        assertEquals(-5_000L, acc.accumulate(-5_000L, nowMs = 1_800L))
    }

    @Test
    fun directionFlipRestarts() {
        val acc = SeekBadgeAccumulator()
        assertEquals(-5_000L, acc.accumulate(-5_000L, nowMs = 1_000L))
        // Rewind then forward inside the window: total restarts at +5s, not -0s.
        assertEquals(5_000L, acc.accumulate(5_000L, nowMs = 1_100L))
    }

    @Test
    fun mixedSequenceUsesRunningTotalForSign() {
        val acc = SeekBadgeAccumulator()
        acc.accumulate(5_000L, nowMs = 0L)
        acc.accumulate(5_000L, nowMs = 100L)
        assertEquals(15_000L, acc.accumulate(5_000L, nowMs = 200L))
    }
}

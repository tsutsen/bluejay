package com.tsutsen.platformplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edge coverage for the shared continue-watching policy. */
class WatchStateTest {
    @Test
    fun `finished is false for zero or negative position`() {
        assertFalse(WatchState.finished(0L, 1_000L))
        assertFalse(WatchState.finished(-1L, 1_000L))
    }

    @Test
    fun `finished is false for zero or negative duration`() {
        assertFalse(WatchState.finished(500L, 0L))
        assertFalse(WatchState.finished(500L, -1_000L))
    }

    @Test
    fun `finished is false just under the watched fraction`() {
        // 100 * 0.95f rounds to exactly 95.0f, so 94 is still unfinished.
        assertFalse(WatchState.finished(94L, 100L))
    }

    @Test
    fun `finished is true exactly at the watched fraction and past end`() {
        assertTrue(WatchState.finished(95L, 100L))
        assertTrue(WatchState.finished(100L, 100L))
        assertTrue(WatchState.finished(150L, 100L))
    }

    @Test
    fun `continue pool scans five recent entries`() {
        assertEquals(5, WatchState.CONTINUE_POOL_SIZE)
    }
}

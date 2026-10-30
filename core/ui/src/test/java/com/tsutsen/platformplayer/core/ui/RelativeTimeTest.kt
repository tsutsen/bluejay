package com.tsutsen.platformplayer.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    @Test
    fun futureTimestampShowsSoon() {
        val now = System.currentTimeMillis()
        assertEquals("Soon", RelativeTime.format(now + 60_000))
        assertEquals("Soon", RelativeTime.format(now + 86_400_000L))
    }

    @Test
    fun pastTimestampsUnchanged() {
        val now = System.currentTimeMillis()
        assertEquals("just now", RelativeTime.format(now - 5_000))
        assertEquals("5m ago", RelativeTime.format(now - 5 * 60_000))
    }
}

package com.tsutsen.platformplayer.sabr

/**
 * Tick/microsecond conversions shared by the SABR session and its helpers.
 * Verbatim from SabrSession's companion.
 */
internal object SabrTimeMath {
    private const val MICROS_PER_SECOND = 1_000_000L

    internal fun ticksToUs(ticks: Long, timescale: Int): Long =
        ticks / timescale * MICROS_PER_SECOND + (ticks % timescale) * MICROS_PER_SECOND / timescale

    internal fun usToTicks(us: Long, timescale: Int): Long =
        us / MICROS_PER_SECOND * timescale + (us % MICROS_PER_SECOND) * timescale / MICROS_PER_SECOND
}

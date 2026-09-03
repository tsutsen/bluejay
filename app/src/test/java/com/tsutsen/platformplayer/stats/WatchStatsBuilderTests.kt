package com.tsutsen.platformplayer.stats

import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.tsutsen.platformplayer.models.HistoryVideo
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStatsBuilderTests {
    private val now = LocalDate.of(2026, 9, 1)

    private fun history(positionMs: Long, date: LocalDate, author: String = "Anna"): HistoryVideo {
        val url = "url-$author-${date.toEpochDay()}"
        val video =
            SerializedPlatformVideo(
                id = PlatformID.asUrlID(url),
                name = "Video $url",
                author = PlatformAuthorLink(PlatformID.NONE, author, ""),
                url = url,
                duration = 0,
                viewCount = 0,
            )
        return HistoryVideo(
            video,
            positionMs,
            // Local midnight of the intended day: the builder attributes
            // history to the device-local day, so the fixture must be
            // timezone-independent.
            date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime(),
            null,
        )
    }

    private val hour = 3_600_000L

    @Test
    fun `empty history yields empty stats with full bucket shapes`() {
        val stats = WatchStatsBuilder.build(emptyList(), now)
        assertTrue(stats.isEmpty)
        assertEquals(0L, stats.allTimeMs)
        assertEquals(7, stats.lastWeekDaily.size)
        assertEquals(30, stats.last30Days.size)
        assertEquals(0, stats.topCreators.size)
    }

    @Test
    fun `today week and all-time totals bucket by last-watched date`() {
        val stats =
            WatchStatsBuilder.build(
                listOf(
                    history(2 * hour, now, "Anna"),
                    history(1 * hour, now.minusDays(3), "Bob"),
                    history(30 * 60_000, now, "Bob"),
                    history(5 * hour, now.minusDays(8), "Anna"), // outside the week
                ),
                now,
            )
        assertEquals(2 * hour + 30 * 60_000, stats.todayMs)
        // (2h + 1h + 30m) / 7
        assertEquals((3 * hour + 30 * 60_000) / 7, stats.weekAverageMs)
        // All time includes the 5h from 8 days ago.
        assertEquals(2 * hour + hour + 30 * 60_000 + 5 * hour, stats.allTimeMs)
        assertEquals(4, stats.videoCount)
    }

    @Test
    fun `daily buckets hold 7 days oldest first and sum per day`() {
        val stats =
            WatchStatsBuilder.build(
                listOf(
                    history(hour, now),
                    history(2 * hour, now.minusDays(1)),
                    history(3 * hour, now.minusDays(2)),
                ),
                now,
            )
        assertEquals(7, stats.lastWeekDaily.size)
        assertEquals(now.minusDays(6), stats.lastWeekDaily.first().day)
        assertEquals(now, stats.lastWeekDaily.last().day)
        assertEquals(3 * hour, stats.lastWeekDaily[4].ms)
        assertEquals(2 * hour, stats.lastWeekDaily[5].ms)
        assertEquals(hour, stats.lastWeekDaily[6].ms)
    }

    @Test
    fun `creators rank by watch time and week list caps at 3`() {
        val stats =
            WatchStatsBuilder.build(
                listOf(
                    history(2 * hour, now, "Anna"),
                    history(1 * hour, now, "Bob"),
                    history(1 * hour, now.minusDays(2), "Carla"),
                    history(10 * 60_000, now, "Dave"),
                    history(10 * 60_000, now.minusDays(5), "Erin"),
                ),
                now,
            )
        assertEquals(listOf("Anna", "Bob", "Carla"), stats.topCreatorsLastWeek.map { it.author })
        assertEquals(3, stats.topCreatorsLastWeek.size)
        assertEquals(5, stats.topCreators.size)
        assertEquals("Anna", stats.topCreators.first().author)
        assertEquals(2 * hour, stats.topCreators.first().ms)
    }

    @Test
    fun `zero-position entries are excluded`() {
        val stats = WatchStatsBuilder.build(listOf(history(0, now), history(-5, now)), now)
        assertEquals(0L, stats.allTimeMs)
        assertEquals(0, stats.topCreators.size)
        assertEquals(2, stats.videoCount)
    }
}

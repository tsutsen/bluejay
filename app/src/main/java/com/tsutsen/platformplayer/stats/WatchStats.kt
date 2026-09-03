package com.tsutsen.platformplayer.stats

import com.tsutsen.platformplayer.core.database.entity.HistoryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Aggregated watch behaviour for the Dash stats card / detail screen,
 * derived from the Room history the player's HistoryTracker writes to.
 *
 * The granularity of the underlying data: history stores ONE entry per
 * video — the last position (ms) and when it was last watched.
 * There is no per-session log. So:
 *  - "watch time" for a video = its last position (a proxy for cumulative
 *    time watched on that video, not the time spent on the last session);
 *  - a video's time is attributed to the day it was LAST watched.
 *
 * ponytail: this keeps the stats honest with what is stored; a per-session
 * log (and thus exact daily totals) is the upgrade path if the numbers
 * ever feel too coarse.
 */
data class WatchStats(
    val todayMs: Long = 0,
    val todayVideoCount: Int = 0,
    val weekAverageMs: Long = 0,
    val weekVideoCount: Int = 0,
    val lastWeekDaily: List<DailyWatch> = emptyList(), // 7 days, oldest first
    val topCreatorsLastWeek: List<CreatorWatch> = emptyList(), // top 3
    val allTimeMs: Long = 0,
    val topCreators: List<CreatorWatch> = emptyList(), // top 10
    val last30Days: List<DailyWatch> = emptyList(), // 30-day sliding window, oldest first
    val videoCount: Int = 0,
) {
    val isEmpty: Boolean get() = videoCount == 0

    companion object {
        val Empty = WatchStats()
    }
}

data class DailyWatch(
    val day: LocalDate,
    val ms: Long,
    val topCreator: String? = null,
)

data class CreatorWatch(
    val author: String,
    val ms: Long,
    val videoCount: Int,
    val avatarUrl: String? = null,
)

object WatchStatsBuilder {
    fun build(
        history: List<HistoryEntity>,
        now: LocalDate = LocalDate.now(),
    ): WatchStats {
        val weekStart = now.minusDays(6)
        val windowStart = now.minusDays(29)
        val daySums = HashMap<LocalDate, Long>() // last 30 days
        val dayAuthorMs = HashMap<LocalDate, MutableMap<String, Long>>() // last 30 days
        val weekCreators = HashMap<String, LongArray>() // [ms, count]
        val allCreators = HashMap<String, LongArray>()
        val creatorAvatars = HashMap<String, String?>()
        var todayMs = 0L
        var todayVideos = 0
        var weekMs = 0L
        var weekVideos = 0
        var allTimeMs = 0L

        for (h in history) {
            val ms = h.lastPositionMs.coerceAtLeast(0L)
            if (ms <= 0L) continue
            // Attribute each video to the day it was last watched in the
            // device's local zone (watchedAt is an epoch-millis instant).
            val day =
                Instant.ofEpochMilli(h.watchedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val creator = h.author?.takeIf { it.isNotBlank() } ?: "Unknown"
            val avatar = h.thumbnailUrl?.takeIf { it.isNotBlank() }
            if (avatar != null && !creatorAvatars.containsKey(creator)) {
                creatorAvatars[creator] = avatar
            }

            allTimeMs += ms
            if (day == now) {
                todayMs += ms
                todayVideos++
            }

            val all = allCreators.getOrPut(creator) { LongArray(2) }
            all[0] += ms
            all[1] += 1

            if (day in windowStart..now) {
                daySums[day] = (daySums[day] ?: 0L) + ms
                dayAuthorMs
                    .getOrPut(day) { HashMap() }
                    .let { it[creator] = (it[creator] ?: 0L) + ms }
            }
            if (day in weekStart..now) {
                weekMs += ms
                weekVideos++
                val w = weekCreators.getOrPut(creator) { LongArray(2) }
                w[0] += ms
                w[1] += 1
            }
        }

        val lastWeekDaily = (0L until 7L).map { offset ->
            val day = weekStart.plusDays(offset)
            val top = dayAuthorMs[day]?.maxByOrNull { it.value }
            DailyWatch(day, daySums[day] ?: 0L, top?.key)
        }
        val last30Days = (0L until 30L).map { offset ->
            val day = windowStart.plusDays(offset)
            val top = dayAuthorMs[day]?.maxByOrNull { it.value }
            DailyWatch(day, daySums[day] ?: 0L, top?.key)
        }

        return WatchStats(
            todayMs = todayMs,
            todayVideoCount = todayVideos,
            weekAverageMs = weekMs / 7L,
            weekVideoCount = weekVideos,
            lastWeekDaily = lastWeekDaily,
            topCreatorsLastWeek = topCreators(weekCreators, creatorAvatars, 3),
            allTimeMs = allTimeMs,
            topCreators = topCreators(allCreators, creatorAvatars, 10),
            last30Days = last30Days,
            videoCount = history.size,
        )
    }

    private fun topCreators(
        sums: Map<String, LongArray>,
        avatars: Map<String, String?>,
        limit: Int,
    ): List<CreatorWatch> =
        sums.entries
            .filter { it.value[0] > 0L }
            .sortedByDescending { it.value[0] }
            .take(limit)
            .map { CreatorWatch(it.key, it.value[0], it.value[1].toInt(), avatars[it.key]) }
}

/** "1h 20m" / "43m" — the human duration labels for the stats screens. */
fun humanDuration(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
}

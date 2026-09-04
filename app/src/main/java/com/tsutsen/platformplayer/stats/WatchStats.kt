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
 * video — its actually-watched time and when it was last watched.
 * There is no per-session log. So:
 *  - "watch time" for a video = its accumulated watched time, which the
 *    HistoryTracker measures from position deltas between playback saves
 *    (skip-aware: a seek jumps the position in one sample, so it
 *    contributes ~0; rewinding and long pauses contribute nothing).
 *    Rows that predate that measurement fall back to the last position as
 *    a proxy.
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
    val topCreatorsLastWeek: List<CreatorWatch> = emptyList(), // top 10
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
    val authorUrl: String? = null,
)

object WatchStatsBuilder {
    /**
     * @param channelAvatars channel avatar per channel URL, from the
     *     subscriptions table. History rows only store the video
     *     thumbnail, so without this map "top creators" would show a
     *     random video frame as the channel face. Rows for channels
     *     the user isn't subscribed to fall back to that thumbnail.
     */
    fun build(
        history: List<HistoryEntity>,
        now: LocalDate = LocalDate.now(),
        channelAvatars: Map<String, String?> = emptyMap(),
    ): WatchStats {
        val weekStart = now.minusDays(6)
        val windowStart = now.minusDays(29)
        val daySums = HashMap<LocalDate, Long>() // last 30 days
        val dayAuthorMs = HashMap<LocalDate, MutableMap<String, Long>>() // last 30 days
        val weekCreators = HashMap<String, LongArray>() // [ms, count]
        val allCreators = HashMap<String, LongArray>()
        val creatorAvatars = HashMap<String, String?>()
        val creatorUrls = HashMap<String, String?>()
        var todayMs = 0L
        var todayVideos = 0
        var weekMs = 0L
        var weekVideos = 0
        var allTimeMs = 0L

        for (h in history) {
            val ms =
                if (h.watchedMs > 0L) h.watchedMs
                // Legacy row from before watched-time measurement.
                else h.lastPositionMs
            if (ms <= 0L) continue
            // Attribute each video to the day it was last watched in the
            // device's local zone (watchedAt is an epoch-millis instant).
            val day =
                Instant.ofEpochMilli(h.watchedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val creator = h.author?.takeIf { it.isNotBlank() } ?: "Unknown"
            val avatar =
                h.authorUrl?.let { channelAvatars[it] }
                    ?.takeIf { it.isNotBlank() }
                    ?: h.thumbnailUrl?.takeIf { it.isNotBlank() }
            if (avatar != null && !creatorAvatars.containsKey(creator)) {
                creatorAvatars[creator] = avatar
            }
            val url = h.authorUrl?.takeIf { it.isNotBlank() }
            if (url != null && !creatorUrls.containsKey(creator)) {
                creatorUrls[creator] = url
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
            topCreatorsLastWeek = topCreators(weekCreators, creatorAvatars, creatorUrls, 10),
            allTimeMs = allTimeMs,
            topCreators = topCreators(allCreators, creatorAvatars, creatorUrls, 10),
            last30Days = last30Days,
            videoCount = history.size,
        )
    }

    private fun topCreators(
        sums: Map<String, LongArray>,
        avatars: Map<String, String?>,
        urls: Map<String, String?>,
        limit: Int,
    ): List<CreatorWatch> =
        sums.entries
            .filter { it.value[0] > 0L }
            .sortedByDescending { it.value[0] }
            .take(limit)
            .map {
                CreatorWatch(
                    it.key,
                    it.value[0],
                    it.value[1].toInt(),
                    avatars[it.key],
                    urls[it.key],
                )
            }
}

/** "1h 20m" / "43m" — the human duration labels for the stats screens. */
fun humanDuration(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
}

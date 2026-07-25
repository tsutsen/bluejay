package com.futo.platformplayer.feature.player.impl

import java.util.Locale
import java.util.concurrent.TimeUnit

internal fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

internal fun formatViewCount(viewCount: Long): String {
    return when {
        viewCount >= 1_000_000 -> "${String.format(Locale.getDefault(), "%.1f", viewCount / 1_000_000.0)}M"
        viewCount >= 1_000 -> "${String.format(Locale.getDefault(), "%.1f", viewCount / 1_000.0)}K"
        else -> viewCount.toString()
    }
}

internal fun formatRelativeTime(publishedAt: Long?): String {
    if (publishedAt == null) return ""
    val now = System.currentTimeMillis()
    val diffMs = now - publishedAt
    val diffSeconds = diffMs / 1000
    val diffMinutes = diffSeconds / 60
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24
    val diffWeeks = diffDays / 7
    val diffMonths = diffDays / 30
    val diffYears = diffDays / 365

    return when {
        diffYears > 0L -> "$diffYears${if (diffYears == 1L) " year" else " years"} ago"
        diffMonths > 0L -> "$diffMonths${if (diffMonths == 1L) " month" else " months"} ago"
        diffWeeks > 0L -> "$diffWeeks${if (diffWeeks == 1L) " week" else " weeks"} ago"
        diffDays > 0L -> "$diffDays${if (diffDays == 1L) " day" else " days"} ago"
        diffHours > 0L -> "$diffHours${if (diffHours == 1L) " hour" else " hours"} ago"
        diffMinutes > 0L -> "$diffMinutes${if (diffMinutes == 1L) " minute" else " minutes"} ago"
        else -> "Just now"
    }
}

package com.tsutsen.platformplayer.core.ui

/**
 * Formats timestamps into relative time strings (e.g., "5m ago", "3h ago", "2d ago").
 * Implements the formatting rules from DESIGN.md §13.
 */
object RelativeTime {

    fun format(millis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - millis

        return when {
            diff < 60_000 -> "just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 604_800_000 -> "${diff / 86_400_000}d ago"
            diff < 2_592_000_000 -> "${diff / 604_800_000}w ago"
            diff < 31_536_000_000 -> "${diff / 2_592_000_000}mo ago"
            else -> "${diff / 31_536_000_000}y ago"
        }
    }

    fun formatExact(millis: Long): String {
        val date = java.util.Date(millis)
        val format = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        return format.format(date)
    }

    fun isRecent(millis: Long, thresholdHours: Long = 24): Boolean {
        val now = System.currentTimeMillis()
        return (now - millis) < (thresholdHours * 3_600_000)
    }
}

package com.tsutsen.platformplayer.core.model

/** A video chapter with its title and time range in milliseconds. */
data class VideoChapter(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
)

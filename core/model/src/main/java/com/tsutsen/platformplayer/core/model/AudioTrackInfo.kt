package com.tsutsen.platformplayer.core.model

/**
 * One selectable audio track of the playing media. [id] is stable within a
 * media source ("<groupId>:<index>"), [label] the UI text, [language] the
 * ISO language code the engine selects by (null when the track has none).
 */
data class AudioTrackInfo(
    val id: String,
    val label: String,
    val language: String?,
)

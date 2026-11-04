package com.tsutsen.platformplayer.core.datastore.model

import kotlinx.serialization.Serializable

/**
 * One hidden/reduced item (Settings > Content > Recommendations > Hidden
 * content): a video or creator (keyed by url / authorUrl) with the
 * probability (percent) that its cards still spawn into the homebrew feed.
 * 0 = never shown (the sheet's "Hide" / "Don't show this creator"),
 * 100 = fully shown (entry removed, never stored).
 */
@Serializable
data class HiddenContentEntry(
    val url: String,
    /** Display name captured when the entry was created ("" = unknown, show url). */
    val name: String = "",
    /** Thumbnail (video) / avatar (creator) url captured when created ("" = none). */
    val imageUrl: String = "",
    val probability: Int = 100,
) {
    companion object {
        /** Selectable probability tiers, high to low. */
        val TIERS = listOf(100, 75, 50, 25, 0)

        /** The tier below [probability]; null when already at the bottom (0). */
        fun lowerTier(probability: Int): Int? = TIERS.firstOrNull { it < probability }
    }
}

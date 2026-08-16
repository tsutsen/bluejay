package com.tsutsen.platformplayer.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Sealed interface for card data types used in the UI.
 * Cards are data-only, no UI logic.
 */
sealed interface Card : Parcelable {
    val id: String
    val title: String
    val thumbnailUrl: String?
}

@Parcelize
data class VideoCard(
    override val id: String,
    override val title: String,
    override val thumbnailUrl: String?,
    val author: String?,
    val authorUrl: String? = null,
    val durationMs: Long? = null,
    val viewCount: Long? = null,
    val publishedAt: Long? = null,
    val url: String,
) : Card

@Parcelize
data class ShortCard(
    override val id: String,
    override val title: String,
    override val thumbnailUrl: String?,
    val author: String?,
    val viewCount: Long? = null,
    val url: String,
) : Card

@Parcelize
data class PlaylistCard(
    override val id: String,
    override val title: String,
    override val thumbnailUrl: String?,
    val videoCount: Int? = null,
    val author: String? = null,
    val url: String,
) : Card

@Parcelize
data class ChannelCard(
    override val id: String,
    override val title: String,
    override val thumbnailUrl: String?,
    val subscriberCount: Long? = null,
    val url: String,
) : Card

@Parcelize
data class PostCard(
    override val id: String,
    override val title: String,
    override val thumbnailUrl: String?,
    val author: String?,
    val publishedAt: Long? = null,
    val url: String,
) : Card

@Parcelize
data class ArticleCard(
    override val id: String,
    override val title: String,
    override val thumbnailUrl: String?,
    val author: String?,
    val publishedAt: Long? = null,
    val url: String,
) : Card

package com.tsutsen.platformplayer.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ContentItem(
    val id: String,
    val url: String,
    val title: String,
    val author: Author?,
    val thumbnailUrl: String?,
    val contentType: ContentType,
    val publishedAt: Long? = null,
    val durationMs: Long? = null,
    val viewCount: Long? = null,
    val description: String? = null,
    val tags: List<String> = emptyList()
) : Parcelable

@Parcelize
data class Author(
    val id: String,
    val name: String,
    val url: String?,
    val thumbnailUrl: String?
) : Parcelable

enum class ContentType {
    VIDEO, PLAYLIST, CHANNEL, POST, ARTICLE, WEB, SHORTS, LIVE
}

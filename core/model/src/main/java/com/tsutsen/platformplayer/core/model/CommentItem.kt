package com.tsutsen.platformplayer.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CommentItem(
    val id: String,
    val author: String,
    val authorThumbnailUrl: String?,
    val text: String,
    val likeCount: Long,
    val replyCount: Int,
    val publishedAtMs: Long?
) : Parcelable

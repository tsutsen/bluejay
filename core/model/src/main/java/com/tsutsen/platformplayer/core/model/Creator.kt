package com.tsutsen.platformplayer.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Creator(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val subscriberCount: Long? = null,
    val url: String? = null
) : Parcelable

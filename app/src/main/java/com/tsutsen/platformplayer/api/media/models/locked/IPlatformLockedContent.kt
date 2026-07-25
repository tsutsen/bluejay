package com.tsutsen.platformplayer.api.media.models.locked

import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.contents.ContentType
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent

interface IPlatformLockedContent: IPlatformContent {
    val lockContentType: ContentType;
    val lockDescription: String?;
    val unlockUrl: String?;
    val contentName: String?;
    val contentThumbnails: Thumbnails;
}
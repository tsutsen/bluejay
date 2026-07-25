package com.tsutsen.platformplayer.api.media.models.article

import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.tsutsen.platformplayer.api.media.models.ratings.IRating
import com.tsutsen.platformplayer.api.media.platforms.js.models.IJSArticleSegment

interface IPlatformArticleDetails: IPlatformContent, IPlatformArticle, IPlatformContentDetails {
    val segments: List<IJSArticleSegment>;
    val rating : IRating;
}
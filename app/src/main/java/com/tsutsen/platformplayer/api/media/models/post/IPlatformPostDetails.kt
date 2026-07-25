package com.tsutsen.platformplayer.api.media.models.post

import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.tsutsen.platformplayer.api.media.models.ratings.IRating

/**
 * A detailed video model with data including video/audio sources
 */
interface IPlatformPostDetails : IPlatformPost, IPlatformContentDetails {
    val rating : IRating;

    val textType: TextType;
    val content: String;
}
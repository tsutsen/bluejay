package com.tsutsen.platformplayer.api.media.models.comments

import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.ratings.IRating
import com.tsutsen.platformplayer.api.media.structures.IPager
import java.time.OffsetDateTime

interface IPlatformComment {
    val contextUrl: String;
    val author : PlatformAuthorLink;
    val message : String;
    val rating : IRating;
    val date : OffsetDateTime?;

    val replyCount : Int?;

    fun getReplies(client: IPlatformClient) : IPager<IPlatformComment>?;
}
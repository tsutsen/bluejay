package com.tsutsen.platformplayer.api.media.models.comments

import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.ratings.IRating
import com.tsutsen.platformplayer.api.media.structures.IPager
import java.time.OffsetDateTime

open class PlatformComment(
    override val contextUrl: String,
    override val author: PlatformAuthorLink,
    override val message: String,
    override val rating: IRating,
    override val date: OffsetDateTime,
    override val replyCount: Int? = null
) : IPlatformComment {

    override fun getReplies(client: IPlatformClient): IPager<IPlatformComment> =
        NoCommentsPager()
}

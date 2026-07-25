package com.tsutsen.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.IPluginSourced
import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.comments.IPlatformComment
import com.tsutsen.platformplayer.api.media.models.contents.ContentType
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.tsutsen.platformplayer.api.media.models.playback.IPlaybackTracker
import com.tsutsen.platformplayer.api.media.models.post.TextType
import com.tsutsen.platformplayer.api.media.models.ratings.IRating
import com.tsutsen.platformplayer.api.media.models.ratings.RatingLikes
import com.tsutsen.platformplayer.api.media.platforms.js.DevJSClient
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.getOrDefault
import com.tsutsen.platformplayer.getOrThrow
import com.tsutsen.platformplayer.getOrThrowNullableList
import com.tsutsen.platformplayer.states.StateDeveloper

open class JSWebDetails : JSContent, IPluginSourced, IPlatformContentDetails {
    final override val contentType: ContentType get() = ContentType.WEB;

    val html: String?;
    //TODO: Options?


    constructor(client: JSClient, obj: V8ValueObject): super(client.config, obj) {
        val contextName = "PlatformWeb";

        html = obj.getOrDefault(client.config, "html", contextName, null);
    }

    override fun getComments(client: IPlatformClient): IPager<IPlatformComment>? = null;
    override fun getPlaybackTracker(): IPlaybackTracker? = null;
    override fun getContentRecommendations(client: IPlatformClient): IPager<IPlatformContent>? = null;

}

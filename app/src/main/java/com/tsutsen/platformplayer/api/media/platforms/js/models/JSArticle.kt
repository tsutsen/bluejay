package com.tsutsen.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.IPluginSourced
import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.article.IPlatformArticle
import com.tsutsen.platformplayer.api.media.models.comments.IPlatformComment
import com.tsutsen.platformplayer.api.media.models.contents.ContentType
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.tsutsen.platformplayer.api.media.models.playback.IPlaybackTracker
import com.tsutsen.platformplayer.api.media.models.post.IPlatformPost
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
import com.tsutsen.platformplayer.getSourcePlugin
import com.tsutsen.platformplayer.states.StateDeveloper

open class JSArticle(
    config: SourcePluginConfig,
    obj: V8ValueObject
) : JSContent(config, obj), IPlatformArticle, IPluginSourced {

    final override val contentType: ContentType = ContentType.ARTICLE

    override val summary: String =
        obj.getOrDefault<String>(config, "summary", "PlatformArticle", "") ?: ""

    override val thumbnails: Thumbnails? =
        if (obj.getSourcePlugin()?.busy { obj.has("thumbnails") } ?: obj.has("thumbnails"))
            Thumbnails.fromV8(
                config,
                obj.getOrThrow<V8ValueObject>(config, "thumbnails", "PlatformArticle")
            )
        else
            null
}

package com.tsutsen.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.IPluginSourced
import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.contents.ContentType
import com.tsutsen.platformplayer.api.media.models.post.IPlatformPost
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.getOrThrow
import com.tsutsen.platformplayer.getOrThrowNullableList

open class JSPost : JSContent, IPlatformPost, IPluginSourced {
    final override val contentType: ContentType get() = ContentType.POST;

    final override val description: String;
    final override val thumbnails: List<Thumbnails?>;
    final override val images: List<String>;

    constructor(config: SourcePluginConfig, obj: V8ValueObject): super(config, obj) {
        val contextName = "PlatformPost";

        description = _content.getOrThrow(config, "description", contextName);
        thumbnails = _content.getOrThrowNullableList<V8ValueObject?>(config, "thumbnails", contextName)
            ?.map {
                if(it != null)
                    Thumbnails.fromV8(config, it);
                else
                    null;
            } ?: listOf();

        images = _content.getOrThrowNullableList<String>(config, "images", contextName) ?: listOf();
    }
}
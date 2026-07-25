package com.tsutsen.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.models.contents.ContentType
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.ensureIsBusy
import com.tsutsen.platformplayer.getOrThrow

interface IJSContentDetails: IPlatformContent  {

    companion object {
        fun fromV8(plugin: JSClient, obj: V8ValueObject): IPlatformContentDetails {
            obj.ensureIsBusy();
            val type: Int = obj.getOrThrow(plugin.config, "contentType", "ContentDetails");
            return when(ContentType.fromInt(type)) {
                ContentType.MEDIA -> JSVideoDetails(plugin, obj);
                ContentType.POST -> JSPostDetails(plugin.config, obj);
                ContentType.ARTICLE -> JSArticleDetails(plugin, obj);
                ContentType.WEB -> JSWebDetails(plugin, obj);
                else -> throw NotImplementedError("Unknown content type ${type}");
            }
        }
    }
}
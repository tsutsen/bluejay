package com.tsutsen.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.engine.V8Plugin

class JSVideoPager : JSPager<IPlatformVideo> {

    constructor(config: SourcePluginConfig, plugin: JSClient, pager: V8ValueObject) : super(config, plugin, pager) {}

    override fun convertResult(obj: V8ValueObject): IPlatformVideo {
        return JSVideo(config, obj);
    }
}
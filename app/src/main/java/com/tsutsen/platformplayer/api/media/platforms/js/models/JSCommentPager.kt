package com.tsutsen.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.models.comments.IPlatformComment
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.engine.V8Plugin
import com.tsutsen.platformplayer.requireSourcePlugin

class JSCommentPager : JSPager<IPlatformComment>, IPager<IPlatformComment> {

    constructor(config: SourcePluginConfig, plugin: JSClient, pager: V8ValueObject) : super(config, plugin, pager) { }

    override fun convertResult(obj: V8ValueObject): IPlatformComment {
        return JSComment(config, obj.requireSourcePlugin("JSCommentPager.convertResult"), obj);
    }
}
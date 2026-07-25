package com.tsutsen.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.engine.V8Plugin

class JSPlaylistPager : JSPager<IPlatformPlaylist>, IPager<IPlatformPlaylist> {

    constructor(config: SourcePluginConfig, plugin: JSClient, pager: V8ValueObject) : super(config, plugin, pager) {}

    override fun convertResult(obj: V8ValueObject): IPlatformPlaylist {
        return JSPlaylist(config, obj);
    }
}
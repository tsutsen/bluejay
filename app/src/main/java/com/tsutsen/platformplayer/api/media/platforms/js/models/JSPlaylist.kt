package com.tsutsen.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.models.contents.ContentType
import com.tsutsen.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.getOrDefault

open class JSPlaylist(
    config: SourcePluginConfig,
    obj: V8ValueObject
) : JSContent(config, obj), IPlatformPlaylist {

    override val contentType: ContentType = ContentType.PLAYLIST

    override val thumbnail: String? =
        _content.getOrDefault<String>(_pluginConfig, "thumbnail", "Playlist", null)

    override val videoCount: Int =
        _content.getOrDefault<Int>(_pluginConfig, "videoCount", "Playlist", null)?.toInt() ?: -1
}

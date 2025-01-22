package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.getOrNull
import com.futo.platformplayer.getOrThrow
import com.futo.platformplayer.others.Language

class JSHLSManifestSource : IHLSManifestSource, JSSource {
    override var width : Int = 0;
    override var height : Int = 0;
    override val container : String get() = "application/vnd.apple.mpegurl";
    override val codec: String = "HLS";
    override val name : String;
    override var bitrate : Int = 0;
    override val url : String;
    override val duration: Long;
    override var language: String = Language.UNKNOWN

    override var priority: Boolean = false;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_HLS, plugin, obj) {
        val contextName = "HLSSource";
        val config = plugin.config;

        name = _obj.getOrThrow(config, "name", contextName);
        url = _obj.getOrThrow(config, "url", contextName);
        duration = _obj.getOrThrow<Int>(config, "duration", contextName).toLong();

        priority = obj.getOrNull(config, "priority", contextName) ?: false;
    }

    fun setPreferredWidth(width: Int) {
        this@JSHLSManifestSource.width = width
    }

    fun setPreferredHeight(height: Int) {
        this@JSHLSManifestSource.height = height
    }

    fun setPreferredBitrate(bitrate: Int) {
        this@JSHLSManifestSource.bitrate = bitrate;
    }

    fun setPreferredLanguage(language: String) {
        this@JSHLSManifestSource.language = language;
    }
}
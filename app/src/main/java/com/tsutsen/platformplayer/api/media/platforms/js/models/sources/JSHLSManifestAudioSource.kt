package com.tsutsen.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioUrlSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IHLSManifestAudioSource
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.engine.IV8PluginConfig
import com.tsutsen.platformplayer.engine.V8Plugin
import com.tsutsen.platformplayer.ensureIsBusy
import com.tsutsen.platformplayer.getOrNull
import com.tsutsen.platformplayer.getOrThrow
import com.tsutsen.platformplayer.orNull

class JSHLSManifestAudioSource : IHLSManifestAudioSource, JSSource {
    override val container : String get() = "application/vnd.apple.mpegurl";
    override val codec: String = "HLS";
    override val name : String;
    override val bitrate : Int = 0;
    override val url : String;
    override val duration: Long;
    override val language: String;

    override var priority: Boolean = false;
    override var original: Boolean = false;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_HLS, plugin, obj) {
        val contextName = "HLSAudioSource";
        val config = plugin.config;

        name = _obj.getOrThrow(config, "name", contextName);
        url = _obj.getOrThrow(config, "url", contextName);
        duration = _obj.getOrThrow<Int>(config, "duration", contextName).toLong();
        language = _obj.getOrThrow(config, "language", contextName);

        priority = obj.getOrNull(config, "priority", contextName) ?: false;
        original =  obj.getOrNull(config, "original", contextName) ?: false;
    }


    companion object {
        fun fromV8HLSNullable(plugin: JSClient, obj: V8Value?) : JSHLSManifestAudioSource? {
            obj?.ensureIsBusy();
            return obj.orNull { fromV8HLS(plugin, it as V8ValueObject) }
        };
        fun fromV8HLS(plugin: JSClient, obj: V8ValueObject) : JSHLSManifestAudioSource {
            obj.ensureIsBusy();
            return JSHLSManifestAudioSource(plugin, obj)
        };
    }
}
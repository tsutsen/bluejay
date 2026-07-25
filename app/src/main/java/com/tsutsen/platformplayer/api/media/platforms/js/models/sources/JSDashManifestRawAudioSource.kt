package com.tsutsen.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.primitive.V8ValueString
import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.V8Deferred
import com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoUrlSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.other.IStreamMetaDataSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.other.StreamMetaData
import com.tsutsen.platformplayer.api.media.platforms.js.DevJSClient
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.engine.IV8PluginConfig
import com.tsutsen.platformplayer.engine.V8Plugin
import com.tsutsen.platformplayer.getOrDefault
import com.tsutsen.platformplayer.getOrNull
import com.tsutsen.platformplayer.getOrThrow
import com.tsutsen.platformplayer.invokeV8
import com.tsutsen.platformplayer.invokeV8Async
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.others.Language
import com.tsutsen.platformplayer.requireSourcePlugin
import com.tsutsen.platformplayer.states.StateDeveloper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

class JSDashManifestRawAudioSource : JSSource, IAudioSource, IJSDashManifestRawSource, IStreamMetaDataSource {
    override val container : String;
    override val name : String;
    override val codec: String;
    override val bitrate: Int;
    override val duration: Long;
    override val priority: Boolean;
    override var original: Boolean = false;

    override val language: String;

    val url: String;
    override var manifest: String?;

    override val hasGenerate: Boolean;

    override var streamMetaData: StreamMetaData? = null;

    constructor(plugin: JSClient, obj: V8ValueObject) : super(TYPE_DASH_RAW, plugin, obj) {
        val contextName = "DashRawSource";
        val config = plugin.config;
        name = _obj.getOrThrow(config, "name", contextName);
        url = _obj.getOrThrow(config, "url", contextName);
        container = _obj.getOrDefault<String>(config, "container", contextName, null) ?: "application/dash+xml";
        manifest = _obj.getOrThrow(config, "manifest", contextName);
        codec = _obj.getOrDefault(config, "codec", contextName, "") ?: "";
        bitrate = _obj.getOrDefault(config, "bitrate", contextName, 0) ?: 0;
        duration = _obj.getOrDefault(config, "duration", contextName, 0) ?: 0;
        priority = _obj.getOrDefault(config, "priority", contextName, false) ?: false;
        language = _obj.getOrDefault(config, "language", contextName, Language.UNKNOWN) ?: Language.UNKNOWN;
        original =  obj.getOrNull(config, "original", contextName) ?: false;
        hasGenerate = plugin.busy { _obj.has("generate") };
    }

    private var _pregenerate: V8Deferred<String?>? = null;
    fun pregenerateAsync(scope: CoroutineScope): V8Deferred<String?>? {
        _pregenerate = generateAsync(scope);
        return _pregenerate;
    }

    override fun generateAsync(scope: CoroutineScope): V8Deferred<String?> {
        if(!hasGenerate)
            return V8Deferred(CompletableDeferred(manifest));
        val pluginV8 = _obj.requireSourcePlugin("DashManifestRawAudioSource.generateAsync");
        if(pluginV8.busy { _obj.isClosed })
            throw IllegalStateException("Source object already closed");

        val pregenerated = _pregenerate;
        if(pregenerated != null) {
            Logger.w("JSDashManifestRawAudioSource", "Returning pre-generated audio");
            return pregenerated;
        }

        var result: V8Deferred<V8ValueString>? = null;
        if(_plugin is DevJSClient)
            result = StateDeveloper.instance.handleDevCall(_plugin.devID, "DashManifestRaw", false) {
                pluginV8.catchScriptErrors("DashManifestRaw", "dashManifestRaw.generate()") {
                    pluginV8.busy {
                        _obj.invokeV8Async<V8ValueString>("generate");
                    }
                }
            }
        else
            result = pluginV8.catchScriptErrors("DashManifestRaw", "dashManifestRaw.generate()") {
                pluginV8.busy {
                    _obj.invokeV8Async<V8ValueString>("generate");
                }
            }

        return pluginV8.busy {
            val initStart = _obj.getOrDefault<Int>(_config, "initStart", "JSDashManifestRawSource", null) ?: 0;
            val initEnd = _obj.getOrDefault<Int>(_config, "initEnd", "JSDashManifestRawSource", null) ?: 0;
            val indexStart = _obj.getOrDefault<Int>(_config, "indexStart", "JSDashManifestRawSource", null) ?: 0;
            val indexEnd = _obj.getOrDefault<Int>(_config, "indexEnd", "JSDashManifestRawSource", null) ?: 0;
            if(initEnd > 0 && indexStart > 0 && indexEnd > 0) {
                streamMetaData = StreamMetaData(initStart, initEnd, indexStart, indexEnd);
            }

            return@busy result.convert {
                it.value
            };
        }
    }
    override fun generate(): String? {
        if(!hasGenerate)
            return manifest;
        val pluginV8 = _obj.requireSourcePlugin("DashManifestRawAudioSource.generate");
        if(pluginV8.busy { _obj.isClosed })
            throw IllegalStateException("Source object already closed");

        var result: String? = null;
        if(_plugin is DevJSClient)
            result = StateDeveloper.instance.handleDevCall(_plugin.devID, "DashManifestRaw", false) {
                pluginV8.catchScriptErrors("DashManifestRaw", "dashManifestRaw.generate()") {
                    pluginV8.busy {
                        _obj.invokeV8<V8ValueString>("generate").value;
                    }
                }
            }
        else
            result = pluginV8.catchScriptErrors("DashManifestRaw", "dashManifestRaw.generate()") {
                pluginV8.busy {
                    _obj.invokeV8<V8ValueString>("generate").value;
                }
            }

        if(result != null){
            pluginV8.busy {
                val initStart = _obj.getOrDefault<Int>(_config, "initStart", "JSDashManifestRawSource", null) ?: 0;
                val initEnd = _obj.getOrDefault<Int>(_config, "initEnd", "JSDashManifestRawSource", null) ?: 0;
                val indexStart = _obj.getOrDefault<Int>(_config, "indexStart", "JSDashManifestRawSource", null) ?: 0;
                val indexEnd = _obj.getOrDefault<Int>(_config, "indexEnd", "JSDashManifestRawSource", null) ?: 0;
                if(initEnd > 0 && indexStart > 0 && indexEnd > 0) {
                    streamMetaData = StreamMetaData(initStart, initEnd, indexStart, indexEnd);
                }
            }
        }
        return result;
    }
}

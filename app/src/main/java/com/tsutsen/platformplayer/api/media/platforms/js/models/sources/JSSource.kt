package com.tsutsen.platformplayer.api.media.platforms.js.models.sources

import com.tsutsen.platformplayer.api.media.models.modifier.AdhocRequestModifier
import com.tsutsen.platformplayer.api.media.models.modifier.IRequestModifier
import com.tsutsen.platformplayer.api.media.models.streams.sources.IAudioSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.tsutsen.platformplayer.api.media.models.streams.sources.IVideoSource
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.models.JSRequest
import com.tsutsen.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.tsutsen.platformplayer.api.media.platforms.js.models.JSRequestModifier
import com.tsutsen.platformplayer.engine.IV8PluginConfig
import com.tsutsen.platformplayer.engine.V8Plugin
import com.tsutsen.platformplayer.ensureIsBusy
import com.tsutsen.platformplayer.getOrDefault
import com.tsutsen.platformplayer.invokeV8
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.orNull
import com.tsutsen.platformplayer.requireSourcePlugin
import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueObject

// Type constants for JSSource subclasses (matching JS plugin plugin_type strings)
const val TYPE_AUDIOURL = "AudioUrlSource"
const val TYPE_DASH_RAW = "DashRawSource"
const val TYPE_HLS_RAW = "HLSSource"
const val TYPE_UMP = "UMPSource"
const val TYPE_VIDEOURL = "VideoUrlSource"
const val TYPE_WIDEVINE_DASH = "DashWidevineSource"
const val TYPE_WIDEVINE_HLS = "HLSWidevineSource"
const val TYPE_WIDEVINE_UMP = "UMPWidevineSource"
const val TYPE_WIDEVINE_VIDEOURL = "VideoUrlWidevineSource"
const val TYPE_WIDEVINE_AUDIOURL = "AudioUrlWidevineSource"
const val TYPE_VIDEOURL_WIDEVINE = "VideoUrlWidevineSource"
const val TYPE_VIDEO_WITH_METADATA = "VideoUrlRangeSource"
const val TYPE_DASH_WIDEVINE = "DashWidevineSource"
const val TYPE_DASH_RAW_AUDIO = "DashRawAudioSource"
const val TYPE_AUDIOURL_WIDEVINE = "AudioUrlWidevineSource"
const val TYPE_AUDIO_WITH_METADATA = "AudioUrlRangeSource"
// Aliases for TYPE_HLS and TYPE_DASH used by HLS/Dash manifest sources
const val TYPE_HLS = TYPE_HLS_RAW
const val TYPE_DASH = TYPE_DASH_RAW

/**
 * Base class for JavaScript source configurations.
 * Parses V8 objects from the plugin engine and creates appropriate source types.
 */
abstract class JSSource {
    protected val _plugin: JSClient;
    protected val _config: IV8PluginConfig;
    protected val _obj: V8ValueObject;

    val hasRequestModifier: Boolean;
    private val _requestModifier: JSRequest?;

    val hasRequestExecutor: Boolean;
    private val _requestExecutor: JSRequest?;

    val requiresCustomDatasource: Boolean get() {
        return hasRequestModifier || hasRequestExecutor;
    }

    val type : String;

    constructor(type: String, plugin: JSClient, obj: V8ValueObject) {
        this._plugin = plugin;
        this._config = plugin.config;
        this._obj = obj;
        this.type = type;

        var parsedRequestModifier: JSRequest? = null;
        var parsedHasRequestModifier = false;
        var parsedRequestExecutor: JSRequest? = null;
        var parsedHasRequestExecutor = false;
        plugin.busy {
            parsedRequestModifier = obj.getOrDefault<V8ValueObject>(_config, "requestModifier", "JSSource.requestModifier", null)?.let {
                JSRequest(plugin, it, null, null, true);
            };
            parsedHasRequestModifier = parsedRequestModifier != null || obj.has("getRequestModifier");

            parsedRequestExecutor = obj.getOrDefault<V8ValueObject>(_config, "requestExecutor", "JSSource.requestExecutor", null)?.let {
                JSRequest(plugin, it, null, null, true);
            };
            parsedHasRequestExecutor = parsedRequestExecutor != null || obj.has("getRequestExecutor");
        }

        _requestModifier = parsedRequestModifier;
        hasRequestModifier = parsedHasRequestModifier;
        _requestExecutor = parsedRequestExecutor;
        hasRequestExecutor = parsedHasRequestExecutor;
    }

    fun getRequestModifier(): IRequestModifier? = _obj.requireSourcePlugin("JSSource.getRequestModifier").busy {
        if(_requestModifier != null)
            return@busy AdhocRequestModifier { url, headers ->
                  return@AdhocRequestModifier _requestModifier.modify(_plugin, url, headers);
            };

        if (!hasRequestModifier || _obj.isClosed)
            return@busy null;

        val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSVideoUrlSource", "obj.getRequestModifier()") {
            _obj.invokeV8("getRequestModifier", arrayOf<Any>());
        };

        if (result !is V8ValueObject)
            return@busy null;

        return@busy JSRequestModifier(_plugin, result)
    }

    open fun getRequestExecutor(): JSRequestExecutor? = _obj.requireSourcePlugin("JSSource.getRequestExecutor").busy {
        if (!hasRequestExecutor || _obj.isClosed)
            return@busy null;

        Logger.v("JSSource", "Request executor for [${type}] requesting");
        val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSSource", "obj.getRequestExecutor()") {
            _obj.invokeV8("getRequestExecutor", arrayOf<Any>());
        };

        Logger.v("JSSource", "Request executor for [${type}] received");

        if (result !is V8ValueObject)
            return@busy null;

        return@busy JSRequestExecutor(_plugin, result)
    }

    fun getUnderlyingPlugin(): JSClient? {
        return _plugin;
    }

    fun getUnderlyingObject(): V8ValueObject? {
        return _obj;
    }

    companion object {
        fun fromV8VideoNullable(plugin: JSClient, obj: V8ValueObject?, contextName: String) : IVideoSource? {
            obj?.ensureIsBusy();
            return obj.orNull { fromV8Video(plugin, it as V8ValueObject) }
        }

        fun fromV8Video(plugin: JSClient, obj: V8ValueObject) : IVideoSource? {
            obj.ensureIsBusy()
            val type = obj.getString("plugin_type");
            return when(type) {
                TYPE_VIDEOURL -> JSVideoUrlSource(plugin, obj);
                TYPE_VIDEOURL_WIDEVINE -> JSVideoUrlWidevineSource(plugin, obj);
                TYPE_VIDEO_WITH_METADATA -> JSVideoUrlRangeSource(plugin, obj);
                TYPE_HLS -> fromV8HLS(plugin, obj);
                TYPE_DASH_WIDEVINE -> JSDashManifestWidevineSource(plugin, obj)
                TYPE_DASH -> fromV8Dash(plugin, obj);
                TYPE_DASH_RAW -> fromV8DashRaw(plugin, obj);
                TYPE_UMP -> JSUMPSource(plugin, obj);
                else -> {
                    Logger.w("JSSource", "Unknown video type ${type}");
                    null;
                };
            }
        }

        fun fromV8DashNullable(plugin: JSClient, obj: V8ValueObject?, contextName: String): IDashManifestSource? = obj.orNull { fromV8Dash(plugin, it as V8ValueObject) };

        fun fromV8Dash(plugin: JSClient, obj: V8ValueObject) : JSDashManifestSource{
            obj.ensureIsBusy();
            return JSDashManifestSource(plugin, obj)
        };

        fun fromV8DashRaw(plugin: JSClient, obj: V8ValueObject) : JSDashManifestRawSource{
            obj.ensureIsBusy()
            return JSDashManifestRawSource(plugin, obj);
        }

        fun fromV8DashRawAudio(plugin: JSClient, obj: V8ValueObject) : JSDashManifestRawAudioSource {
            obj?.ensureIsBusy();
            return JSDashManifestRawAudioSource(plugin, obj)
        };

        fun fromV8HLSNullable(plugin: JSClient, obj: V8ValueObject?, contextName: String): IHLSManifestSource? = obj.orNull { fromV8HLS(plugin, it as V8ValueObject) };

        fun fromV8HLS(plugin: JSClient, obj: V8ValueObject) : JSHLSManifestSource {
            obj.ensureIsBusy();
            return JSHLSManifestSource(plugin, obj)
        };

        fun fromV8Audio(plugin: JSClient, obj: V8ValueObject) : IAudioSource? {
            obj.ensureIsBusy();
            val type = obj.getString("plugin_type");
            return when(type) {
                TYPE_HLS -> JSHLSManifestAudioSource.fromV8HLS(plugin, obj);
                TYPE_AUDIOURL -> JSAudioUrlSource(plugin, obj);
                TYPE_DASH_RAW_AUDIO -> fromV8DashRawAudio(plugin, obj);
                TYPE_AUDIOURL_WIDEVINE -> JSAudioUrlWidevineSource(plugin, obj);
                TYPE_AUDIO_WITH_METADATA -> JSAudioUrlRangeSource(plugin, obj);
                else -> {
                    Logger.w("JSSource", "Unknown audio type ${type}");
                    null;
                };
            }
        }
    }
}

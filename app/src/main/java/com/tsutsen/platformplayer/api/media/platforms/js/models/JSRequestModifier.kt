package com.tsutsen.platformplayer.api.media.platforms.js.models

import android.net.Uri
import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.models.modifier.IRequest
import com.tsutsen.platformplayer.api.media.models.modifier.IRequestModifier
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.engine.IV8PluginConfig
import com.tsutsen.platformplayer.engine.V8Plugin
import com.tsutsen.platformplayer.engine.exceptions.ScriptImplementationException
import com.tsutsen.platformplayer.getOrDefault
import com.tsutsen.platformplayer.getOrNull
import com.tsutsen.platformplayer.getOrThrow
import com.tsutsen.platformplayer.invokeV8
import com.tsutsen.platformplayer.invokeV8Void
import com.tsutsen.platformplayer.requireSourcePlugin

class JSRequestModifier: IRequestModifier {
    private val _plugin: JSClient;
    private val _config: IV8PluginConfig;
    private var _modifier: V8ValueObject;
    override var allowByteSkip: Boolean = false;

    constructor(plugin: JSClient, modifier: V8ValueObject) {
        this._plugin = plugin;
        this._modifier = modifier;
        this._config = plugin.config;
        val config = plugin.config;

        plugin.busy {
            allowByteSkip = modifier.getOrNull(config, "allowByteSkip", "JSRequestModifier") ?: true;

            if(!modifier.has("modifyRequest"))
                throw ScriptImplementationException(config, "RequestModifier is missing modifyRequest", null);
        }

    }

    override fun modifyRequest(url: String, headers: Map<String, String>): IRequest {
        return _modifier.requireSourcePlugin("JSRequestModifier.modifyRequest").busy {
            if (_modifier.isClosed) {
                return@busy Request(url, headers);
            }

            val result = V8Plugin.catchScriptErrors<Any>(_config, "[${_config.name}] JSRequestModifier", "builder.modifyRequest()") {
                _modifier.invokeV8("modifyRequest", url, headers);
            } as V8ValueObject;

            val req = JSRequest(_plugin, result, url, headers);
            result.close();
            return@busy req;
        }
    }


    data class Request(override val url: String, override val headers: Map<String, String>) : IRequest;
}

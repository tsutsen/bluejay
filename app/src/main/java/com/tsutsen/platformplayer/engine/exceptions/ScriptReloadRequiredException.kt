package com.tsutsen.platformplayer.engine.exceptions

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.engine.IV8PluginConfig
import com.tsutsen.platformplayer.engine.V8PluginConfig
import com.tsutsen.platformplayer.ensureIsBusy
import com.tsutsen.platformplayer.getOrDefault
import com.tsutsen.platformplayer.getOrThrow

class ScriptReloadRequiredException(config: IV8PluginConfig, val msg: String?, val reloadData: String?, ex: Exception? = null, stack: String? = null, code: String? = null) : ScriptException(config, msg ?: "ReloadRequired", ex, stack, code) {

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : ScriptException {
            obj.ensureIsBusy();
            val contextName = "ScriptReloadRequiredException";
            return ScriptReloadRequiredException(config,
                obj.getOrThrow(config, "message", contextName),
                obj.getOrDefault<String>(config, "reloadData", contextName, null));
        }
    }
}
package com.tsutsen.platformplayer.engine.exceptions

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.engine.IV8PluginConfig
import com.tsutsen.platformplayer.ensureIsBusy
import com.tsutsen.platformplayer.getOrThrow

class ScriptUnavailableException(config: IV8PluginConfig, error: String, ex: Exception? = null, stack: String? = null, code: String? = null) : ScriptException(config, error, ex, stack, code) {

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : ScriptException {
            obj.ensureIsBusy();
            return ScriptUnavailableException(config, obj.getOrThrow(config, "message", "ScriptUnavailableException"));
        }
    }
}
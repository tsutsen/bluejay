package com.tsutsen.platformplayer.engine.exceptions

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.engine.IV8PluginConfig
import com.tsutsen.platformplayer.ensureIsBusy
import com.tsutsen.platformplayer.getOrThrow

class ScriptTimeoutException(config: IV8PluginConfig, error: String, ex: Exception? = null) : ScriptException(config, error, ex) {
    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : ScriptTimeoutException {
            obj.ensureIsBusy();
            return ScriptTimeoutException(config, obj.getOrThrow(config, "message", "ScriptException"));
        }
    }
}
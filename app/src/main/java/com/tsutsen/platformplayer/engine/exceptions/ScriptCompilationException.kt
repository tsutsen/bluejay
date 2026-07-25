package com.tsutsen.platformplayer.engine.exceptions

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.engine.IV8PluginConfig
import com.tsutsen.platformplayer.ensureIsBusy
import com.tsutsen.platformplayer.getOrThrow

class ScriptCompilationException(config: IV8PluginConfig, error: String, ex: Exception? = null, code: String? = null) : PluginException(config, error, ex, code) {

    companion object {
        fun fromV8(config: IV8PluginConfig, obj: V8ValueObject) : ScriptCompilationException {
            obj.ensureIsBusy();
            return ScriptCompilationException(config, obj.getOrThrow(config, "message", "ScriptCompilationException"));
        }
    }
}
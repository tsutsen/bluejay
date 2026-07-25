package com.tsutsen.platformplayer.engine.exceptions

import com.tsutsen.platformplayer.engine.IV8PluginConfig


open class PluginEngineException(config: IV8PluginConfig, error: String, code: String? = null) : PluginException(config, error, null, code) {

}
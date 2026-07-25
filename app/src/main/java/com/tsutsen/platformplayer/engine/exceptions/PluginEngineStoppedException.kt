package com.tsutsen.platformplayer.engine.exceptions

import com.tsutsen.platformplayer.engine.IV8PluginConfig


class PluginEngineStoppedException(config: IV8PluginConfig, error: String, code: String? = null) : PluginEngineException(config, error, code) {

}
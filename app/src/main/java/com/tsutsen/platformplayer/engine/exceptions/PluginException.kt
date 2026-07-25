package com.tsutsen.platformplayer.engine.exceptions

import com.tsutsen.platformplayer.engine.IV8PluginConfig

open class PluginException(val config: IV8PluginConfig, msg: String, ex: Exception? = null, val code: String? = null): Exception("[${config.name}] " + msg, ex) {

}
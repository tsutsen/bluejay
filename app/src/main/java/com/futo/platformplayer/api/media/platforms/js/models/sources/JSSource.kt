package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.models.IJSContentDetails
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.futo.platformplayer.engine.IV8PluginConfig
import com.caoccao.javet.values.reference.V8ValueObject

// Type constants for JSSource subclasses
const val TYPE_AUDIOURL = 0
const val TYPE_DASH_RAW = 1
const val TYPE_HLS_RAW = 2
const val TYPE_UMP = 3
const val TYPE_VIDEOURL = 4
const val TYPE_WIDEVINE_DASH = 5
const val TYPE_WIDEVINE_HLS = 6
const val TYPE_WIDEVINE_UMP = 7
const val TYPE_WIDEVINE_VIDEOURL = 8
const val TYPE_WIDEVINE_AUDIOURL = 9

/**
 * Stub for JSSource.
 * The original JSSource was a class for parsing JavaScript source configurations.
 * It has been removed during the Compose migration.
 */
open class JSSource(
    val type: Int,
    val _plugin: JSClient,
    val _obj: V8ValueObject,
    val _config: IV8PluginConfig
) {
    var busy: (suspend () -> Boolean) = { false }
    var isClosed: Boolean = false
    var hasRequestExecutor: Boolean = false
    var hasRequestModifier: Boolean = false
    
    fun getUnderlyingPlugin(): JSClient? = _plugin
    fun getUnderlyingObject(): UnderlyingObject? = UnderlyingObject()
    fun getRequestModifier(): IRequestModifier? = null
    fun getRequestExecutor(): JSRequestExecutor? = null
    
    class UnderlyingObject {
        var isClosed: Boolean = false
    }
    
    companion object {
        fun fromV8DashNullable(
            plugin: JSClient,
            v8Obj: V8ValueObject?,
            contextName: String
        ): IJSContentDetails? = null
        
        fun fromV8HLSNullable(
            plugin: JSClient,
            v8Obj: V8ValueObject?,
            contextName: String
        ): IJSContentDetails? = null
        
        fun fromV8VideoNullable(
            plugin: JSClient,
            v8Obj: V8ValueObject?,
            contextName: String
        ): IJSContentDetails? = null
    }
}

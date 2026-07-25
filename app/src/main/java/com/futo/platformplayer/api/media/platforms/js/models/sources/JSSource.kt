package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.futo.platformplayer.api.media.models.modifier.IRequestModifier
import com.futo.platformplayer.api.media.models.streams.sources.IAudioSource
import com.futo.platformplayer.api.media.models.streams.sources.IDashManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IHLSManifestSource
import com.futo.platformplayer.api.media.models.streams.sources.IVideoSource
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
// Aliases for TYPE_HLS and TYPE_DASH used by HLS/Dash manifest sources
const val TYPE_HLS = TYPE_HLS_RAW
const val TYPE_DASH = TYPE_DASH_RAW

/**
 * Stub for JSSource.
 * The original JSSource was a class for parsing JavaScript source configurations.
 * It has been removed during the Compose migration.
 */
open class JSSource(
    val type: Int,
    val _plugin: JSClient,
    val _obj: V8ValueObject,
    val _config: IV8PluginConfig = IV8PluginConfigStub
) {
    var busy: (suspend () -> Boolean) = { false }
    var isClosed: Boolean = false
    var hasRequestExecutor: Boolean = false
    var hasRequestModifier: Boolean = false
    
    fun getUnderlyingPlugin(): JSClient? = _plugin
    fun getUnderlyingObject(): V8ValueObject? = _obj
    fun getRequestModifier(): IRequestModifier? = null
    fun getRequestExecutor(): JSRequestExecutor? = null
    
    companion object {
        val IV8PluginConfigStub = object : IV8PluginConfig {
            override val name: String = "Stub"
            override val allowEval: Boolean = false
            override val allowUrls: List<String> = emptyList()
            override val packages: List<String> = emptyList()
            override val packagesOptional: List<String> = emptyList()
        }
        
        fun fromV8Video(plugin: JSClient, v8Obj: V8ValueObject): IVideoSource? = null
        fun fromV8Audio(plugin: JSClient, v8Obj: V8ValueObject): IAudioSource? = null
        
        fun fromV8DashNullable(
            plugin: JSClient,
            v8Obj: V8ValueObject?,
            contextName: String
        ): IDashManifestSource? = null
        
        fun fromV8HLSNullable(
            plugin: JSClient,
            v8Obj: V8ValueObject?,
            contextName: String
        ): IHLSManifestSource? = null
        
        fun fromV8VideoNullable(
            plugin: JSClient,
            v8Obj: V8ValueObject?,
            contextName: String
        ): IVideoSource? = null
    }
}

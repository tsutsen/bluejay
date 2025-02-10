package com.futo.platformplayer.api.media.platforms.js.models.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.models.streams.sources.IAudioUrlWidevineSource
import com.futo.platformplayer.api.media.platforms.js.JSClient
import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import com.futo.platformplayer.engine.V8Plugin
import com.futo.platformplayer.getOrThrow

class JSAudioUrlWidevineSource : JSAudioUrlSource, IAudioUrlWidevineSource {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(plugin: JSClient, obj: V8ValueObject) : super(plugin, obj) {
        val contextName = "JSAudioUrlWidevineSource"
        val config = plugin.config
    }

    override fun toString(): String {
        val url = getAudioUrl()
        return "(name=$name, container=$container, bitrate=$bitrate, codec=$codec, url=$url, language=$language, duration=$duration, hasLicenseRequestExecutor=${hasLicenseRequestExecutor}, drmLicenseUri=$drmLicenseUri)"
    }
}

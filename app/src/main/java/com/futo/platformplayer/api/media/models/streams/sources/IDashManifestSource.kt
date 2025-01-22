package com.futo.platformplayer.api.media.models.streams.sources

import com.caoccao.javet.values.reference.V8ValueObject
import com.futo.platformplayer.api.media.platforms.js.models.sources.IUnderlyingObject
import com.futo.platformplayer.api.media.platforms.js.models.sources.JSDashManifestSource

interface IDashManifestSource : IVideoSource {
    val url: String
}

interface DashWrapper {
    val source: IDashManifestSource
}

class DashManifestAudioSourceDelegate(
    override val source: JSDashManifestSource, override val language: String, override val bitrate: Int, override val container: String
) : IDashManifestSource by source, IAudioSource, DashWrapper, IUnderlyingObject {
    override fun getUnderlyingObject(): V8ValueObject? {
        return source.getUnderlyingObject()
    }
}

class DashManifestSourceDelegate(
    override val source: JSDashManifestSource, override val width: Int, override val height: Int, override val container: String
) : IDashManifestSource by source, DashWrapper, IUnderlyingObject {
    override fun getUnderlyingObject(): V8ValueObject? {
        return source.getUnderlyingObject()
    }
}
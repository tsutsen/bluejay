package com.tsutsen.platformplayer.di

import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.MediaDrmCallback
import com.tsutsen.platformplayer.api.media.platforms.js.models.JSRequestExecutor
import java.util.UUID

/**
 * MediaDrmCallback that routes license key requests through the plugin's JS
 * request executor so plugin-side auth/cookies apply (ported from grayjay).
 */
class PluginMediaDrmCallback(
    private val delegate: MediaDrmCallback,
    private val requestExecutor: JSRequestExecutor,
    private val licenseUrl: String,
) : MediaDrmCallback by delegate {
    override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): MediaDrmCallback.Response {
        val pluginResponse = requestExecutor.executeRequest("POST", licenseUrl, request.data, mapOf())
        return MediaDrmCallback.Response(pluginResponse)
    }
}

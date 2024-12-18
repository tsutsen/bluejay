package com.futo.platformplayer.api.media.models.streams.sources

import com.futo.platformplayer.api.media.platforms.js.models.JSRequestExecutor

interface IWidevineSource {
    val widevineLicenseUri: String?
    val hasLicenseRequestExecutor: Boolean
    fun getLicenseRequestExecutor(): JSRequestExecutor?
}
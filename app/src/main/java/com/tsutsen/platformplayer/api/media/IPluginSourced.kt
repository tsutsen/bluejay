package com.tsutsen.platformplayer.api.media

import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig

interface IPluginSourced {
    val sourceConfig: SourcePluginConfig;
}
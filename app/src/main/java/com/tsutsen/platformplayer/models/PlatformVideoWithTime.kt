package com.tsutsen.platformplayer.models

import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo

data class PlatformVideoWithTime(val video: IPlatformVideo, val time: Long);
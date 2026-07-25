package com.tsutsen.platformplayer.api.media.models.contents

import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.models.comments.IPlatformComment
import com.tsutsen.platformplayer.api.media.models.playback.IPlaybackTracker
import com.tsutsen.platformplayer.api.media.structures.IPager

interface IPlatformContentDetails : IPlatformContent {


    fun getComments(client: IPlatformClient): IPager<IPlatformComment>?;
    fun getPlaybackTracker(): IPlaybackTracker?;

    fun getContentRecommendations(client: IPlatformClient): IPager<IPlatformContent>?;
}
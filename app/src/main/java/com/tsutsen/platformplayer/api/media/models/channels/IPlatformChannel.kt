package com.tsutsen.platformplayer.api.media.models.channels

import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.structures.IPager

interface IPlatformChannel {
    val id : PlatformID;
    val name : String;
    val thumbnail : String?;
    val banner : String?;
    val subscribers : Long;
    val description: String?;
    val url: String;
    val links: Map<String, String>;
    val urlAlternatives: List<String>;

    fun getContents(client: IPlatformClient): IPager<IPlatformContent>;
}
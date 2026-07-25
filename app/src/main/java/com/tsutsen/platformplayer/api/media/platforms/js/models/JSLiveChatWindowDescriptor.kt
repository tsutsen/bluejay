package com.tsutsen.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.live.ILiveChatWindowDescriptor
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.getOrDefault
import com.tsutsen.platformplayer.getOrThrow
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class JSLiveChatWindowDescriptor: ILiveChatWindowDescriptor {
    override val url: String;
    override val removeElements: List<String>;
    override val removeElementsInterval: List<String>;

    constructor(config: SourcePluginConfig, obj: V8ValueObject) {
        val contextName = "LiveChatWindowDescriptor";

        url = obj.getOrThrow(config, "url", contextName);
        removeElements = obj.getOrDefault(config, "removeElements", contextName, listOf()) ?: listOf();
        removeElementsInterval = obj.getOrDefault(config, "removeElementsInterval", contextName, listOf()) ?: listOf();
    }
}
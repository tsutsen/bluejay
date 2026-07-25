package com.tsutsen.platformplayer.api.media.platforms.js.models

import com.caoccao.javet.values.V8Value
import com.caoccao.javet.values.reference.V8ValueObject
import com.tsutsen.platformplayer.api.media.models.live.IPlatformLiveEvent
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.api.media.structures.IPlatformLiveEventPager
import com.tsutsen.platformplayer.getOrDefault
import com.tsutsen.platformplayer.getOrThrow
import com.tsutsen.platformplayer.invokeV8
import com.tsutsen.platformplayer.warnIfMainThread
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class JSVODEventPager : JSPager<IPlatformLiveEvent>, IPlatformLiveEventPager {
    override var nextRequest: Int;

    constructor(config: SourcePluginConfig, plugin: JSClient, pager: V8ValueObject) : super(config, plugin, pager) {
        nextRequest = pager.getOrThrow(config, "nextRequest", "LiveEventPager");
    }

    fun nextPage(ms: Int) = plugin.isBusyWith("JSLiveEventPager.nextPage") {
        warnIfMainThread("VODEventPager.nextPage");

        val pluginV8 = requirePagerPluginV8("nextPage");
        pluginV8.busy {
            val newPager: V8Value = pluginV8.catchScriptErrors("[${plugin.config.name}] JSPager", "pager.nextPage(...)") {
                pager.invokeV8<V8Value>("nextPage", ms);
            };
            if(newPager is V8ValueObject)
                pager = newPager;
            _hasMorePages = pager.getOrDefault(config, "hasMore", "Pager", false) ?: false;
            _resultChanged = true;
            nextRequest = pager.getOrThrow(config, "nextRequest", "LiveEventPager");
        }
    }

    override fun nextPage() = nextPage(0);

    override fun convertResult(obj: V8ValueObject): IPlatformLiveEvent {
        return IPlatformLiveEvent.fromV8(config, obj, "LiveEventPager");
    }
}
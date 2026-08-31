package com.tsutsen.platformplayer.api.media

import com.tsutsen.platformplayer.api.media.models.live.IPlatformLiveEvent
import com.tsutsen.platformplayer.api.media.models.live.LiveEventComment
import com.tsutsen.platformplayer.api.media.models.live.LiveEventEmojis
import com.tsutsen.platformplayer.api.media.platforms.js.models.JSLiveEventPager
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StateApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Polls a platform's live event pager and fans events out to followers.
 *
 * Ported from grayjay's LiveChatManager, simplified: emote bitmaps are no
 * longer downloaded/cached here (the Compose UI renders emotes straight from
 * their URLs via Coil); only the name -> URL mapping is kept. VOD chat
 * replay is intentionally not ported.
 */
class LiveChatManager {
    private val _scope: CoroutineScope
    private val _pager: IPager<IPlatformLiveEvent>

    /** The underlying event pager (the repo watchdog re-creates it when it
     *  dies — socket-based plugins never reconnect on their own). */
    val pager: IPager<IPlatformLiveEvent> get() = _pager

    /** Emote name -> image URL (streamer emote set, refreshed over chat). */
    private val _emoteUrls: HashMap<String, String> = HashMap()

    private var _startCounter = 0

    private val _followers: HashMap<Any, (List<IPlatformLiveEvent>) -> Unit> = hashMapOf()

    var viewCount: Long = 0
        private set

    constructor(scope: CoroutineScope, pager: IPager<IPlatformLiveEvent>, initialViewCount: Long = 0) {
        _scope = scope
        _pager = pager
        viewCount = initialViewCount
        handleEvents(
            listOf(
                LiveEventComment(
                    "SYSTEM",
                    null,
                    "Live chat is still under construction. While it is mostly functional, the experience still needs to be improved.\n",
                ),
            ),
        )
    }

    fun start() {
        val counter = ++_startCounter
        startLoop(counter)
    }

    fun stop() {
        _startCounter++
    }

    fun follow(
        tag: Any,
        eventHandler: (List<IPlatformLiveEvent>) -> Unit,
    ) {
        eventHandler(emptyList())
        synchronized(_followers) {
            _followers.put(tag, eventHandler)
        }
    }

    fun unfollow(tag: Any) {
        synchronized(_followers) {
            _followers.remove(tag)
        }
    }

    fun hasEmote(emoji: String): Boolean {
        synchronized(_emoteUrls) {
            return _emoteUrls.containsKey(emoji)
        }
    }

    fun getEmoteUrl(emoji: String): String? {
        synchronized(_emoteUrls) {
            return _emoteUrls[emoji]
        }
    }

    private fun startLoop(counter: Int) {
        _scope.launch(Dispatchers.IO) {
            try {
                while (_startCounter == counter) {
                    var nextInterval = 1000L
                    try {
                        if (!_pager.hasMorePages()) {
                            // Do NOT exit: the pager can go idle temporarily
                            // (plugin internal error, runtime being
                            // reinstalled) and come back. Re-poll slowly
                            // instead of killing the loop — that is what
                            // made chat stop fetching for good.
                            nextInterval = 5000L
                        } else {
                            _pager.nextPage()
                            val newEvents = _pager.getResults()
                            if (_pager is JSLiveEventPager) {
                                // The plugin controls the poll interval; cap
                                // it so a broken plugin can't stall chat
                                // indefinitely.
                                nextInterval = _pager.nextRequest.coerceIn(800, 30_000).toLong()
                            }

                            if (newEvents.size > 0) {
                                Logger.i(
                                    TAG,
                                    "New Live Events (${newEvents.size}) [${newEvents.map { it.type.name }.joinToString(", ")}]",
                                )
                            } else {
                                Logger.v(TAG, "No new Live Events")
                            }

                            _scope.launch(Dispatchers.Main) {
                                try {
                                    handleEvents(newEvents)
                                } catch (e: Throwable) {
                                    Logger.e(TAG, "Failed to handle new live events.", e)
                                }
                            }
                        }
                    } catch (ex: Throwable) {
                        Logger.e(TAG, "Failed to load live events", ex)
                    }
                    delay(nextInterval)
                }
            } catch (e: Throwable) {
                Logger.e(TAG, "Live events loop crashed.", e)
            }
        }
    }

    fun handleEvents(events: List<IPlatformLiveEvent>) {
        for (event in events) {
            if (event is LiveEventEmojis) {
                synchronized(_emoteUrls) {
                    for ((name, url) in event.emojis) {
                        _emoteUrls[name] = url
                    }
                }
            }
            val handlers = synchronized(_followers) { _followers.values.toList() }
            for (handler in handlers) {
                try {
                    handler(events)
                } catch (ex: Throwable) {
                    Logger.e(TAG, "Failed to handle live events on handler", ex)
                }
            }
        }
    }

    companion object {
        private const val TAG = "LiveChatManager"
    }
}

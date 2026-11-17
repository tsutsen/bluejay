package com.tsutsen.platformplayer.core.data.repository.impl

import android.util.Log
import com.tsutsen.platformplayer.core.model.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Small thread-safe LRU cache keyed by video URL.
 */
internal class ExtrasCache<V>(
    private val capacity: Int = 50,
) {
    private val map = LinkedHashMap<String, V>(16, 0.75f, true)

    @Synchronized
    fun get(key: String): V? = map[key]

    @Synchronized
    fun put(
        key: String,
        value: V,
    ) {
        map[key] = value
        while (map.size > capacity) {
            val eldest = map.keys.first()
            map.remove(eldest)
        }
    }
}

/**
 * Cache-or-fetch helper for one kind of per-video side content (comments /
 * recommendations / chapters): replays the session-cached value instantly,
 * else fetches over the network on IO and publishes into the shared
 * [PlayerState] — but only while [generationValue] is still the current
 * [playGeneration], so an in-flight fetch for a previous video can never
 * overwrite the new video's extras (the "comments from the previous video"
 * bug). One instance per content kind; the three call sites in
 * [PlayerRepositoryImpl.play] were near-identical blocks.
 */
internal class SideContentCache<T>(
    private val logTag: String,
    private val what: String,
    private val playGeneration: AtomicInteger,
    private val state: MutableStateFlow<PlayerState>,
) {
    private val cache = ExtrasCache<T>()

    /**
     * Fetches (or replays from cache) and publishes the result.
     *
     * @param generation the [PlayerRepositoryImpl] play generation captured
     *   when play() started this fetch — the staleness guard.
     * @param fetch the network fetch; must return a non-null value (an
     *   empty result is fine and still cached).
     * @param update the state update to publish a fetched value; a no-op
     *   transform never emits (chapters publish only when non-empty).
     */
    fun fetchOrReplay(
        scope: CoroutineScope,
        videoId: String,
        generation: Int,
        fetch: suspend () -> T,
        update: PlayerState.(T) -> PlayerState,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val value = cache.get(videoId) ?: fetch()
                cache.put(videoId, value)
                if (generation == playGeneration.get()) {
                    state.update { it.update(value) }
                }
            } catch (e: Exception) {
                Log.e(logTag, "Failed to fetch $what for $videoId", e)
            }
        }
    }
}

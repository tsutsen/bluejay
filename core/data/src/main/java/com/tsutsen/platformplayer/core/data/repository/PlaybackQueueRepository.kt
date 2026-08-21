package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.ContentItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Playback queue: videos waiting to be played. The currently playing
 * video stays in the list (it renders as the now-playing card); it drops
 * out when it finishes or when another video takes over.
 */
interface PlaybackQueueRepository {
    /** Pending queue items, in play order. */
    val queue: StateFlow<List<ContentItem>>

    /**
     * Enqueue [item]. Adding never starts playback by itself — the item
     * waits its turn (auto-advance starts it when the current video ends).
     * Re-adding an already-queued item moves it to the end.
     */
    fun add(item: ContentItem)

    fun addAll(items: List<ContentItem>)

    /**
     * Plays the item at [index]. The tapped item moves to the front of
     * the queue and the previously playing video is evicted from it
     * (replaced, not re-added). Tapping the already-playing video just
     * (re)starts it.
     */
    fun playAt(index: Int)

    /** Evicts the currently playing video and plays the next queued one. */
    fun playNext()

    fun removeAt(index: Int)
    fun remove(url: String)
    fun move(from: Int, to: Int)
    fun clear()
}

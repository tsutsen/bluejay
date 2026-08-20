package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.ContentItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Playback queue: videos waiting to be played. The currently playing
 * video is NOT part of this list — it lives in [PlayerRepository]'s
 * state, so "the queue" always means "what comes next".
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

    /** Plays the item at [index] and removes it from the queue. */
    fun playAt(index: Int)

    fun removeAt(index: Int)
    fun remove(url: String)
    fun move(from: Int, to: Int)
    fun clear()
}

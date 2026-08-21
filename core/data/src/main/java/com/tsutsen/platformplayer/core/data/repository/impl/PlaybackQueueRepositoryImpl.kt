package com.tsutsen.platformplayer.core.data.repository.impl

import com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import android.util.Log
import com.tsutsen.platformplayer.core.model.ContentItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueRepositoryImpl
    @Inject
    constructor(
        private val playerRepository: PlayerRepository,
    ) : PlaybackQueueRepository {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private val _queue = MutableStateFlow<List<ContentItem>>(emptyList())
        override val queue: StateFlow<List<ContentItem>> = _queue.asStateFlow()

        init {
            scope.launch {
                playerRepository.playerState.collect { state ->
                    // Auto-advance: when the current video ends, play the
                    // next queued item (the current one drops out of the
                    // queue if it was in it).
                    if (state.isCompleted) advance()
                }
            }
        }

        private fun advance() {
            Log.i(TAG, "Auto-advancing queue")
            playNext()
        }

        private fun play(item: ContentItem) {
            scope.launch { playerRepository.play(item.url, item) }
        }

        override fun add(item: ContentItem) {
            // Re-adding an already-queued item moves it to the end.
            // Adding never auto-plays — only auto-advance does.
            _queue.value = _queue.value.filterNot { it.url == item.url } + item
        }

        override fun addAll(items: List<ContentItem>) {
            items.forEach { add(it) }
        }

        override fun playAt(index: Int) {
            val item = _queue.value.getOrNull(index) ?: return
            val currentUrl = playerRepository.playerState.value.currentVideo?.url
            if (item.url == currentUrl) {
                // Tapping the already-playing video just (re)starts it —
                // no reordering.
                play(item)
                return
            }
            // Tap-to-play: the tapped video takes over as the first queued
            // item and the previously playing video is evicted (replaced,
            // not re-added).
            _queue.value =
                listOf(item) +
                    _queue.value.filter {
                        it.url != item.url && it.url != currentUrl
                    }
            play(item)
        }

        override fun playNext() {
            val currentUrl = playerRepository.playerState.value.currentVideo?.url
            // The finished / superseded video drops out of the queue, then
            // the first remaining item plays.
            val remaining = _queue.value.filterNot { it.url == currentUrl }
            val next = remaining.firstOrNull() ?: return
            _queue.value = remaining
            play(next)
        }

        override fun removeAt(index: Int) {
            val q = _queue.value
            if (index in q.indices) _queue.value = q.filterIndexed { i, _ -> i != index }
        }

        override fun remove(url: String) {
            _queue.value = _queue.value.filterNot { it.url == url }
        }

        override fun move(from: Int, to: Int) {
            val q = _queue.value.toMutableList()
            if (from !in q.indices || to !in q.indices) return
            // The now-playing item stays put: nothing can move in front of
            // it, and it itself can't be moved.
            val currentIndex =
                q.indexOfFirst { it.url == playerRepository.playerState.value.currentVideo?.url }
            if (currentIndex >= 0 && (from == currentIndex || to < currentIndex)) return
            val item = q.removeAt(from)
            q.add(to, item)
            _queue.value = q
        }

        override fun clear() {
            _queue.value = emptyList()
        }

        companion object {
            private const val TAG = "PlaybackQueue"
        }
    }

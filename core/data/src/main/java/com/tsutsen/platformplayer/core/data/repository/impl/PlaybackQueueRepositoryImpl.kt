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
            val currentUrl = playerRepository.playerState.value.currentVideo?.url ?: return
            val next = _queue.value.firstOrNull { it.url != currentUrl } ?: return
            _queue.value = _queue.value.filterNot { it.url == currentUrl }
            Log.i(TAG, "Auto-advancing queue to: ${next.title}")
            play(next)
        }

        private fun play(item: ContentItem) {
            scope.launch { playerRepository.play(item.url, item) }
        }

        override fun add(item: ContentItem) {
            val state = playerRepository.playerState.value
            if (state.currentVideo == null || state.isCompleted) {
                play(item)
            } else {
                // Re-adding an already-queued item moves it to the end.
                _queue.value = _queue.value.filterNot { it.url == item.url } + item
            }
        }

        override fun addAll(items: List<ContentItem>) {
            items.forEach { add(it) }
        }

        override fun playAt(index: Int) {
            val item = _queue.value.getOrNull(index) ?: return
            _queue.value = _queue.value.filterNot { it.url == item.url }
            play(item)
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

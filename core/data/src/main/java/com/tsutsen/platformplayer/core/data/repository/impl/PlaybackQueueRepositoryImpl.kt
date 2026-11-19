package com.tsutsen.platformplayer.core.data.repository.impl

import com.tsutsen.platformplayer.core.data.di.ApplicationScope
import com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import android.util.Log
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.WatchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        private val settingsRepository: SettingsRepository,
        private val historyDao: HistoryDao,
        @ApplicationScope appScope: CoroutineScope,
    ) : PlaybackQueueRepository {
        // App-lifetime scope parented to the injected application scope;
        // stays on Main.immediate (unchanged dispatch semantics — the queue
        // advance/play calls expect main-thread behavior).
        private val scope =
            CoroutineScope(SupervisorJob(appScope.coroutineContext[Job]) + Dispatchers.Main.immediate)

        private val _queue = MutableStateFlow<List<ContentItem>>(emptyList())
        override val queue: StateFlow<List<ContentItem>> = _queue.asStateFlow()

        /** URLs whose history says they are finished (>= 95% watched),
         *  updated live from the history table. */
        private val watchedUrls = MutableStateFlow<Set<String>>(emptySet())

        init {
            scope.launch {
                // Advance only on the TRANSITION into completed: the
                // position ticker re-emits isCompleted several times per
                // second while a video sits at its end, and each emission
                // used to fire another advance() (double-skip). Reset when
                // playback is no longer completed (a new video started).
                var wasCompleted = false
                playerRepository.playerState.collect { state ->
                    val current = state.currentVideo ?: return@collect
                    if (state.isCompleted) {
                        // Auto-advance: when the current video ends, play
                        // the next queued item — unless autoplay is off
                        // (Settings > Content > Video page). Manual
                        // playNext() (skip-next) ignores the setting.
                        if (!wasCompleted && settingsRepository.preferences.value.playback.autoPlay) {
                            advance()
                        }
                        wasCompleted = true
                        return@collect
                    }
                    wasCompleted = false
                    // A finished (watched) video leaves the queue, even while
                    // it is still the current one at the head — the history
                    // collector owns that eviction, and re-adding it here
                    // would fight the eviction right before auto-advance.
                    if (current.url in watchedUrls.value) return@collect
                    // The current video is always the FIRST queue item, even
                    // when it was started from outside the queue (feed tap,
                    // recommendation, search...). Keeps the queue from ever
                    // reading as empty while something is playing.
                    val q = _queue.value
                    val idx = q.indexOfFirst { it.url == current.url }
                    if (idx != 0) {
                        _queue.value =
                            if (idx >= 0)
                                listOf(current) + q.filter { it.url != current.url }
                            else listOf(current) + q
                    }
                }
            }
            scope.launch {
                // Watched videos leave the queue: whenever history says an
                // item is finished (>= 95% of its duration, per WatchState),
                // evict it — including the item at the head. The "current
                // stays first" collector above deliberately does not re-add
                // a watched video, so the eviction sticks until auto-advance
                // (or the user) moves on.
                historyDao.observeAll().collect { entries ->
                    val watched =
                        entries
                            .filter { WatchState.finished(it.lastPositionMs, it.totalDurationMs) }
                            .mapTo(HashSet()) { it.contentUrl }
                    watchedUrls.value = watched
                    val q = _queue.value
                    val remaining = q.filterNot { it.url in watched }
                    if (remaining.size != q.size) _queue.value = remaining
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
            // Skip watched heads when advancing: a finished video is evicted
            // by the history collector, but until that emission lands an
            // already-watched item could still sit at the head.
            val next = remaining.firstOrNull { it.url !in watchedUrls.value } ?: return
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
            if (currentIndex >= 0 && (from == currentIndex || to <= currentIndex)) return
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

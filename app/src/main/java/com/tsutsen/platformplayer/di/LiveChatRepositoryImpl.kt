package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.api.media.LiveChatManager
import com.tsutsen.platformplayer.api.media.models.live.IPlatformLiveEvent
import com.tsutsen.platformplayer.api.media.models.live.LiveEventComment
import com.tsutsen.platformplayer.api.media.models.live.LiveEventDonation
import com.tsutsen.platformplayer.api.media.models.live.LiveEventEmojis
import com.tsutsen.platformplayer.api.media.models.live.LiveEventRaid
import com.tsutsen.platformplayer.api.media.models.live.LiveEventViewCount
import com.tsutsen.platformplayer.core.data.repository.LiveChatRepository
import com.tsutsen.platformplayer.core.model.LiveChatEntry
import com.tsutsen.platformplayer.core.model.LiveChatUiState
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StatePlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live chat implementation: resolves the platform's live event pager for the
 * played URL and polls it through [LiveChatManager], exposing a UI snapshot.
 */
@Singleton
class LiveChatRepositoryImpl
    @Inject
    constructor() : LiveChatRepository {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val _state = MutableStateFlow<LiveChatUiState?>(null)
        override val state: StateFlow<LiveChatUiState?> = _state.asStateFlow()

        private var _manager: LiveChatManager? = null
        private var _url: String? = null
        private val uidCounter = java.util.concurrent.atomic.AtomicLong(0)

        override suspend fun start(url: String) {
            if (_url == url) return
            stop()
            _url = url
            _state.value = LiveChatUiState()
            val pager =
                withContext(Dispatchers.IO) {
                    StatePlatform.instance.getLiveEvents(url)
                }
            if (pager == null) {
                Logger.i(TAG, "No live events for $url")
                _state.value = LiveChatUiState(error = "Live chat is not available for this video")
                _url = null
                return
            }
            val manager = LiveChatManager(scope, pager)
            _manager = manager
            manager.follow(this) { events -> merge(events) }
            manager.start()
        }

        override fun stop() {
            _manager?.unfollow(this)
            _manager?.stop()
            _manager = null
            _url = null
            _state.value = null
        }

        private fun merge(events: List<IPlatformLiveEvent>) {
            val current = _state.value ?: return
            val mapped =
                events.mapNotNull { event ->
                    when (event) {
                        is LiveEventComment -> {
                            LiveChatEntry.ChatMessage(
                                uid = 0,
                                timeMs = event.time,
                                name = event.name,
                                colorName = event.colorName,
                                badge = event.badges.firstOrNull(),
                                text = event.message,
                                thumbnail = event.thumbnail,
                            )
                        }

                        is LiveEventRaid -> {
                            LiveChatEntry.Raid(
                                uid = 0,
                                timeMs = event.time,
                                targetName = event.targetName,
                                targetUrl = event.targetUrl,
                                isOutgoing = event.isOutgoing,
                            )
                        }

                        is LiveEventDonation -> {
                            LiveChatEntry.Donation(
                                uid = 0,
                                timeMs = event.time,
                                name = event.name,
                                message = event.message,
                            )
                        }

                        is LiveEventViewCount, is LiveEventEmojis -> {
                            null
                        }

                        else -> {
                            null
                        }
                    }
                }
            // Overlapping poll windows can redeliver events already on
            // screen — drop duplicates (by content, before the uid is
            // stamped, which would break equality).
            val existing = current.entries.map { signature(it) }.toHashSet()
            val newEntries =
                mapped
                    .filter { signature(it) !in existing }
                    .map { entry ->
                        when (entry) {
                            is LiveChatEntry.ChatMessage -> entry.copy(uid = uidCounter.incrementAndGet())

                            is LiveChatEntry.Raid -> entry.copy(uid = uidCounter.incrementAndGet())

                            is LiveChatEntry.Donation -> entry.copy(uid = uidCounter.incrementAndGet())
                        }
                    }
            val emotes =
                events
                    .filterIsInstance<LiveEventEmojis>()
                    .flatMap { it.emojis.entries }
                    .associate { it.key to it.value }
            val viewCount =
                events
                    .filterIsInstance<LiveEventViewCount>()
                    .lastOrNull()
                    ?.viewCount
                    ?.toLong()
                    ?: current.viewCount
            // Cap history so long-lived streams don't grow unbounded.
            val entries =
                (current.entries + newEntries).let { if (it.size > MAX_ENTRIES) it.takeLast(MAX_ENTRIES) else it }
            _state.value =
                current.copy(
                    entries = entries,
                    emoteUrls = current.emoteUrls + emotes,
                    viewCount = viewCount,
                )
        }

        /** uid-free identity of an entry (dedupe across poll windows). */
        private fun signature(e: LiveChatEntry): String =
            when (e) {
                is LiveChatEntry.ChatMessage -> "m|${e.timeMs}|${e.name}|${e.badge}|${e.text}"

                is LiveChatEntry.Raid -> "r|${e.timeMs}|${e.targetName}|${e.isOutgoing}"

                is LiveChatEntry.Donation -> "d|${e.timeMs}|${e.name}|${e.message}"
            }

        private companion object {
            const val TAG = "LiveChatRepository"
            const val MAX_ENTRIES = 400
        }
    }

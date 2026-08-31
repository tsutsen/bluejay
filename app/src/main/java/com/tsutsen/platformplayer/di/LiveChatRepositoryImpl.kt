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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        private var _watchdog: Job? = null
        private val uidCounter = java.util.concurrent.atomic.AtomicLong(0)

        // Signatures of the previously delivered batch. Overlapping poll
        // windows redeliver events at most one poll apart, so dedupe only
        // against the last batch — NOT the whole history: a time-free
        // signature (name|text) collides with any earlier identical message
        // ("lol", emote spam) and would silently drop new chat forever.
        @Volatile
        private var _lastBatchSignatures: Set<String> = emptySet()

        // When any event (messages, view counts, ...) last arrived.
        @Volatile
        private var _lastEventBatchAt: Long = 0

        override suspend fun start(url: String) {
            if (_url == url) return
            stop()
            _url = url
            _state.value = LiveChatUiState()
            _lastBatchSignatures = emptySet()
            _lastEventBatchAt = 0
            if (!createManager(url)) {
                Logger.i(TAG, "No live events for $url")
                _state.value = LiveChatUiState(error = "Live chat is not available for this video")
                _url = null
                return
            }
            startWatchdog(url)
        }

        override fun stop() {
            _watchdog?.cancel()
            _watchdog = null
            _manager?.unfollow(this)
            _manager?.stop()
            _manager = null
            _url = null
            _state.value = null
        }

        /** Resolves a fresh event pager (constructor opens fresh sockets) and
         *  starts polling it. */
        private suspend fun createManager(url: String): Boolean {
            val pager =
                withContext(Dispatchers.IO) {
                    StatePlatform.instance.getLiveEvents(url)
                }
            if (pager == null) return false
            val manager = LiveChatManager(scope, pager)
            _manager = manager
            manager.follow(this) { events -> merge(events) }
            manager.start()
            return true
        }

        /**
         * Event pagers are socket-based (Twitch): when the socket drops the
         * plugin never reconnects and the pager dies forever (hasMore flips
         * false, or it keeps "having more" while delivering nothing). The
         * only client-side recovery is re-resolving the pager, whose
         * constructor opens fresh sockets. Watch for a pager that was alive
         * and went quiet.
         */
        private fun startWatchdog(url: String) {
            _watchdog =
                scope.launch {
                    var wasActive = false
                    var lastRecreate = 0L
                    var idleSince = 0L
                    while (_url == url) {
                        delay(5_000)
                        val manager = _manager ?: continue
                        val idle =
                            try {
                                !manager.pager.hasMorePages()
                            } catch (t: Throwable) {
                                true
                            }
                        val now = System.currentTimeMillis()
                        if (idle && idleSince == 0L) idleSince = now
                        if (!idle) idleSince = 0L
                        val silentFor = now - _lastEventBatchAt
                        val dead =
                            wasActive &&
                                _lastEventBatchAt > 0 &&
                                (silentFor > SILENCE_DEATH_MS ||
                                    (idle && now - idleSince > IDLE_DEATH_MS))
                        if (!dead) {
                            wasActive = !idle || _lastEventBatchAt > 0
                            continue
                        }
                        if (now - lastRecreate < RECREATE_COOLDOWN_MS) continue
                        lastRecreate = now
                        Logger.i(
                            TAG,
                            "Live event pager appears dead (idle=$idle, silent=${silentFor}ms) — recreating",
                        )
                        wasActive = false
                        idleSince = 0L
                        manager.unfollow(this)
                        manager.stop()
                        _manager = null
                        _lastBatchSignatures = emptySet()
                        try {
                            createManager(url)
                        } catch (t: Throwable) {
                            Logger.w(TAG, "Live event pager recreate failed", t)
                        }
                    }
                }
        }

        private fun merge(events: List<IPlatformLiveEvent>) {
            if (events.isNotEmpty()) _lastEventBatchAt = System.currentTimeMillis()
            val current = _state.value ?: return
            // Plugins may not carry a timestamp (default -1): record the
            // time we received the event instead, so messages always show
            // a sensible time.
            val receivedAt = System.currentTimeMillis()
            val mapped =
                events.mapNotNull { event ->
                    when (event) {
                        is LiveEventComment -> {
                            LiveChatEntry.ChatMessage(
                                uid = 0,
                                timeMs = if (event.time > 0) event.time else receivedAt,
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
                                timeMs = if (event.time > 0) event.time else receivedAt,
                                targetName = event.targetName,
                                targetUrl = event.targetUrl,
                                isOutgoing = event.isOutgoing,
                            )
                        }

                        is LiveEventDonation -> {
                            LiveChatEntry.Donation(
                                uid = 0,
                                timeMs = if (event.time > 0) event.time else receivedAt,
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
            // Overlapping poll windows can redeliver events from the
            // previous poll — drop those duplicates (by content, before the
            // uid is stamped, which would break equality).
            val batchSigs = mutableSetOf<String>()
            val newEntries =
                mapped
                    .filter { entry ->
                        val sig = signature(entry)
                        sig !in _lastBatchSignatures && sig !in batchSigs && batchSigs.add(sig)
                    }
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
            _lastBatchSignatures = batchSigs
            _state.value =
                current.copy(
                    entries = entries,
                    emoteUrls = current.emoteUrls + emotes,
                    viewCount = viewCount,
                )
        }

        /**
         * uid-free, time-free identity of an entry (dedupe across poll
         * windows). timeMs is excluded because it may be stamped with the
         * receive time (above), which differs on every poll — including it
         * would defeat the dedupe.
         */
        private fun signature(e: LiveChatEntry): String =
            when (e) {
                is LiveChatEntry.ChatMessage -> "m|${e.name}|${e.badge}|${e.text}"

                is LiveChatEntry.Raid -> "r|${e.targetName}|${e.isOutgoing}"

                is LiveChatEntry.Donation -> "d|${e.name}|${e.message}"
            }

        private companion object {
            const val TAG = "LiveChatRepository"
            const val MAX_ENTRIES = 400
            // A live stream emits view counts every few seconds; longer
            // than this with zero events means the socket is gone.
            const val SILENCE_DEATH_MS = 30_000L
            // Pager definitively reports "no more pages": give it a grace
            // period, then treat it as dead too (some plugins flip the
            // flag when their socket drops).
            const val IDLE_DEATH_MS = 15_000L
            // Avoid a recreate storm if getLiveEvents keeps failing.
            const val RECREATE_COOLDOWN_MS = 60_000L
        }
    }

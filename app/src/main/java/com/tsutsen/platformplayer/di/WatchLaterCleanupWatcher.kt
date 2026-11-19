package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.core.data.di.ApplicationScope
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.database.dao.SavedVideoDao
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.WatchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watch Later auto-cleanup (Settings > Content > Library).
 *
 * While [SettingsRepository]'s `watchLaterAutoCleanup` flag is on, observes
 * the history table and removes the matching Watch Later entry every time a
 * video transitions into "finished" (>= [WatchState.WATCHED_FRACTION] of its
 * duration — the same rule as the card checkmark). Re-emissions of entries
 * that are already finished are ignored via an in-memory dedup set, so a
 * finished video is removed at most once per process.
 *
 * Instantiated eagerly by [PlatformPlayerApp] so the collector runs for the
 * whole process lifetime (same pattern as the playback queue's
 * watched-eviction collector).
 */
@Singleton
class WatchLaterCleanupWatcher
    @Inject
    constructor(
        private val savedVideoDao: SavedVideoDao,
        private val historyDao: HistoryDao,
        private val settingsRepository: SettingsRepository,
        @ApplicationScope appScope: CoroutineScope,
    ) {
        // App-lifetime scope parented to the injected application scope.
        private val scope =
            CoroutineScope(SupervisorJob(appScope.coroutineContext[Job]) + Dispatchers.IO)

        init {
            scope.launch {
                settingsRepository.preferences
                    .map { it.other.watchLaterAutoCleanup }
                    .distinctUntilChanged()
                    .collectLatest { enabled ->
                        if (!enabled) return@collectLatest
                        // URLs already seen finished this process — dedup so
                        // the position ticker's re-emissions don't re-fire.
                        val finished = HashSet<String>()
                        historyDao.observeAll().collect { entries ->
                            for (entry in entries) {
                                if (entry.contentUrl in finished) continue
                                if (WatchState.finished(entry.lastPositionMs, entry.totalDurationMs)) {
                                    finished.add(entry.contentUrl)
                                    savedVideoDao.deleteByType(entry.contentUrl, SavedVideoType.WATCH_LATER)
                                }
                            }
                        }
                    }
            }
        }
    }

package com.tsutsen.platformplayer.states

import com.tsutsen.platformplayer.UIDialogs
import com.tsutsen.platformplayer.api.media.PlatformID
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.Thumbnail
import com.tsutsen.platformplayer.api.media.models.Thumbnails
import com.tsutsen.platformplayer.api.media.models.contents.ContentType
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.models.video.SerializedPlatformVideo
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.core.database.AppDatabaseProvider
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.database.entity.HistoryEntity
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.models.HistoryVideo
import com.tsutsen.platformplayer.stores.FragmentedStorage
import com.tsutsen.platformplayer.stores.StringDateMapStorage
import com.tsutsen.platformplayer.sync.internal.GJSyncOpcodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * History import/export facade over the shared Room history table
 * (core/database [HistoryDao]) — the same table the player's HistoryTracker
 * writes to, so everything (playback, sync, backup, UI) reads one substrate.
 *
 * Wire formats kept for cross-device/backup compatibility:
 * - GJ sync broadcasts `List<HistoryVideo>` JSON (see [toWireHistory])
 * - backup stores recon strings `url|||dateSec|||position|||name` (see [toReconString])
 */
class StateHistory {
    private val historyDao: HistoryDao by lazy {
        AppDatabaseProvider.get(StateApp.instance.context).historyDao()
    }

    private val _remoteHistoryDatesStore = FragmentedStorage.get<StringDateMapStorage>("remoteHistoryDates");

    // --- Reads -------------------------------------------------------------

    /** History entries newer than [minDate], newest first (max [max]). */
    fun getRecentHistory(minDate: OffsetDateTime, max: Int = 1000): List<HistoryEntity> {
        return runBlocking {
            historyDao.getSince(minDate.toInstant().toEpochMilli(), max)
        }
    }

    // --- Writes ------------------------------------------------------------

    /**
     * Upsert history entries (device sync receive, backup import).
     * No-ops in private mode (history must not accumulate).
     */
    fun importHistoryVideos(videos: List<HistoryVideo>) {
        if (StateApp.instance.privateMode)
            return
        runBlocking {
            for (video in videos)
                historyDao.upsert(video.toHistoryEntity())
        }
    }

    // --- Wire mapping ------------------------------------------------------

    /** Entity -> wire/backup model (lossy: no playlist id over the wire). */
    fun toWireHistory(entity: HistoryEntity): HistoryVideo =
        HistoryVideo(
            SerializedPlatformVideo(
                ContentType.MEDIA,
                id = PlatformID.asUrlID(entity.contentUrl),
                name = entity.title,
                thumbnails = Thumbnails(entity.thumbnailUrl?.let { arrayOf(Thumbnail(it)) } ?: emptyArray()),
                author = PlatformAuthorLink(PlatformID.NONE, entity.author ?: "Unknown", entity.authorUrl ?: ""),
                datetime = null,
                url = entity.contentUrl,
                shareUrl = entity.contentUrl,
                duration = entity.totalDurationMs,
                viewCount = entity.viewCount
            ),
            position = entity.lastPositionMs,
            date = OffsetDateTime.ofInstant(Instant.ofEpochMilli(entity.watchedAt), ZoneOffset.UTC),
            playlistId = null
        )

    /** Entity -> backup recon string (same format as HistoryVideo.toReconString). */
    fun toReconString(entity: HistoryEntity): String =
        "${entity.contentUrl}|||${entity.watchedAt / 1000}|||${entity.lastPositionMs}|||${entity.title}"

    // --- Remote plugin history sync -----------------------------------------

    fun syncRemoteHistory(plugin: JSClient): Int {
        if (plugin.capabilities.hasGetUserHistory &&
            plugin.isLoggedIn) {
            Logger.i(TAG, "Syncing remote history for plugin [${plugin.name}]");

            val hist = StatePlatform.instance.getUserHistory(plugin.id);

            return syncRemoteHistory(plugin.id, hist, 100, 3);
        }
        return 0;
    }
    fun syncRemoteHistory(pluginId: String, videos: IPager<IPlatformContent>, maxVideos: Int, maxPages: Int): Int {
        if (StateApp.instance.privateMode)
            return 0
        val lastDate = _remoteHistoryDatesStore.get(pluginId) ?: OffsetDateTime.MIN;
        val maxVideosCount = if(maxVideos <= 0) 500 else maxVideos;
        val maxPageCount = if(maxPages <= 0) 3 else maxPages;
        var exceededDate = false;
        try {
            val toSync = mutableListOf<IPlatformVideo>();
            var pageCount = 0;
            var videoCount = 0;
            var isFirst = true;
            var oldestPlayback = OffsetDateTime.MAX;
            var newestPlayback = OffsetDateTime.MIN;
            do {
                if (!isFirst) videos.nextPage();
                val newVideos = videos.getResults();

                var foundVideos = false;
                var toSyncAddedCount = 0;
                for(video in newVideos) {
                    if(video is IPlatformVideo && video.playbackDate != null) {

                        if(video.playbackDate!! < lastDate) {
                            exceededDate = true;
                            break;
                        }

                        if(video.playbackTime > 0) {
                            toSync.add(video);
                            toSyncAddedCount++;
                            foundVideos = true;
                            oldestPlayback = video.playbackDate!!;
                            if(newestPlayback == OffsetDateTime.MIN)
                                newestPlayback = video.playbackDate!!;
                        }
                    }
                }

                pageCount++;
                videoCount += newVideos.size;
                isFirst = false;

                if(!foundVideos)
                {
                    Logger.i(TAG, "Found no more videos in remote history");
                    break;
                }
            }
            while(videos.hasMorePages() && videoCount <= maxVideosCount && pageCount <= maxPageCount && !exceededDate);

            var updated = 0;
            if(oldestPlayback < OffsetDateTime.MAX) {
                for(video in toSync){
                    val updatedVideo = runBlocking {
                        val existing = historyDao.getByUrl(video.url);
                        if (existing == null || existing.lastPositionMs < video.playbackTime) {
                            historyDao.upsert(video.toHistoryEntity(video.playbackDate));
                            true
                        }
                        else
                            false
                    };
                    if (updatedVideo) {
                        Logger.i(TAG, "Updated history for video [${video.name}] from remote history");
                        updated++;
                    }
                }
                if(updated > 0) {
                    _remoteHistoryDatesStore.setAndSave(pluginId, newestPlayback);

                    try {
                        val client = StatePlatform.instance.getClient(pluginId);
                        UIDialogs.appToast("Updated ${updated} history from ${client.name}")
                    }
                    catch(ex: Throwable){}
                }
                return updated;
            }
        }
        catch(ex: Throwable) {
            val plugin = if(pluginId != StateDeveloper.DEV_ID) StatePlugins.instance.getPlugin(pluginId) else null;
            Logger.e(TAG, "Sync Remote History failed for [${plugin?.config?.name}] due to: " + ex.message)
        }
        return 0;
    }

    companion object {
        val TAG = "StateHistory";
        private var _instance : StateHistory? = null;
        val instance : StateHistory
            get(){
                if(_instance == null)
                    _instance = StateHistory();
                return _instance!!;
            };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}

// --- Model mapping -----------------------------------------------------------

private fun HistoryVideo.toHistoryEntity(): HistoryEntity =
    HistoryEntity(
        contentUrl = video.url,
        title = video.name,
        author = video.author.name,
        authorUrl = video.author.url.takeIf { it.isNotBlank() },
        thumbnailUrl = video.thumbnails.getLQThumbnail(),
        lastPositionMs = position,
        totalDurationMs = video.duration,
        watchedAt = date.toInstant().toEpochMilli(),
        viewedAt = date.toInstant().toEpochMilli(),
        viewCount = video.viewCount.coerceAtLeast(0),
    )

private fun IPlatformVideo.toHistoryEntity(playbackDate: OffsetDateTime?): HistoryEntity {
    val date = playbackDate ?: OffsetDateTime.now()
    return HistoryEntity(
        contentUrl = url,
        title = name,
        author = author.name,
        authorUrl = author.url.takeIf { it.isNotBlank() },
        thumbnailUrl = thumbnails.getLQThumbnail(),
        lastPositionMs = playbackTime.coerceAtLeast(0),
        totalDurationMs = duration,
        watchedAt = date.toInstant().toEpochMilli(),
        viewedAt = date.toInstant().toEpochMilli(),
        viewCount = viewCount.coerceAtLeast(0),
    )
}

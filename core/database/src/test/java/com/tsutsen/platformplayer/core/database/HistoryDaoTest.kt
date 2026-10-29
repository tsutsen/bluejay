package com.tsutsen.platformplayer.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tsutsen.platformplayer.core.database.dao.HistoryDao
import com.tsutsen.platformplayer.core.database.entity.HistoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryDaoTest {
    private fun dao(): HistoryDao =
        Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
            .historyDao()

    private fun entry(
        url: String,
        authorUrl: String? = null,
        watchedMs: Long = 0,
        lastPositionMs: Long = 0,
        watchedAt: Long = System.currentTimeMillis(),
    ) = HistoryEntity(
        contentUrl = url,
        title = url,
        author = authorUrl,
        thumbnailUrl = null,
        lastPositionMs = lastPositionMs,
        totalDurationMs = 60_000,
        watchedMs = watchedMs,
        watchedAt = watchedAt,
        authorUrl = authorUrl,
    )

    @Test
    fun `top creators above threshold within window ordered by watched time`() =
        runBlocking {
            val dao = dao()
            val now = System.currentTimeMillis()
            // 20 min watched in the window -> qualifies (> 15 min).
            dao.upsert(entry("v1", "chanA", watchedMs = 12 * 60_000, watchedAt = now - 1_000))
            dao.upsert(entry("v2", "chanA", watchedMs = 8 * 60_000, watchedAt = now - 2_000))
            // 16 min, but too old (outside the 28-day window) -> excluded.
            dao.upsert(entry("v3", "chanB", watchedMs = 16 * 60_000, watchedAt = now - 29L * 24 * 60 * 60 * 1000))
            // 10 min in the window -> below threshold, excluded.
            dao.upsert(entry("v4", "chanC", watchedMs = 10 * 60_000, watchedAt = now - 3_000))
            // No author url -> excluded.
            dao.upsert(entry("v5", null, watchedMs = 60 * 60_000, watchedAt = now - 4_000))

            val top = dao.topCreatorsByWatchTimeSince(
                sinceMs = now - 28L * 24 * 60 * 60 * 1000,
                minWatchedMs = 15L * 60 * 1000,
                limit = 5,
            )

            assertEquals(listOf("chanA"), top.map { it.authorUrl })
            assertEquals(20L * 60_000, top.single().watchedMs)
        }

    @Test
    fun `top creators respects limit and orders most watched first`() =
        runBlocking {
            val dao = dao()
            val now = System.currentTimeMillis()
            dao.upsert(entry("a1", "chanA", watchedMs = 20 * 60_000, watchedAt = now))
            dao.upsert(entry("b1", "chanB", watchedMs = 40 * 60_000, watchedAt = now))
            dao.upsert(entry("c1", "chanC", watchedMs = 30 * 60_000, watchedAt = now))

            val top = dao.topCreatorsByWatchTimeSince(
                sinceMs = now - 28L * 24 * 60 * 60 * 1000,
                minWatchedMs = 15L * 60 * 1000,
                limit = 2,
            )

            assertEquals(listOf("chanB", "chanC"), top.map { it.authorUrl })
        }

    @Test
    fun `markWatched inserts a fully watched row for an unknown video`() =
        runBlocking {
            val dao = dao()
            val now = System.currentTimeMillis()

            dao.markWatched(
                url = "v1",
                title = "t1",
                author = "a1",
                thumbnailUrl = "thumb1",
                durationMs = 90_000,
                watchedAt = now,
            )

            val row = dao.getByUrl("v1")!!
            assertEquals(90_000, row.lastPositionMs)
            assertEquals(90_000, row.totalDurationMs)
            assertEquals(90_000, row.watchedMs)
            assertEquals(now, row.watchedAt)
        }

    @Test
    fun `markWatched on an existing row updates watched fields but keeps the rest`() =
        runBlocking {
            val dao = dao()
            val old = System.currentTimeMillis() - 5 * 60_000
            // A partially watched row with extra fields a REPLACE would wipe.
            dao.upsert(
                HistoryEntity(
                    contentUrl = "v1",
                    title = "old title",
                    author = null,
                    thumbnailUrl = null,
                    lastPositionMs = 30_000,
                    totalDurationMs = 90_000,
                    watchedMs = 12_000,
                    watchedAt = old,
                    viewedAt = old,
                    authorUrl = "chanA",
                    viewCount = 42,
                    postedAt = old - 60_000,
                )
            )

            dao.markWatched(url = "v1", title = "t1", durationMs = 90_000)

            val row = dao.getByUrl("v1")!!
            assertEquals(90_000, row.lastPositionMs)
            assertEquals(90_000, row.watchedMs)
            // Fields the tile doesn't know about survive the upsert.
            assertEquals(old, row.viewedAt)
            assertEquals("chanA", row.authorUrl)
            assertEquals(42, row.viewCount)
            assertEquals(old - 60_000, row.postedAt)
        }
}

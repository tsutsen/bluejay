package com.tsutsen.platformplayer.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tsutsen.platformplayer.core.database.dao.PlaylistDao
import com.tsutsen.platformplayer.core.database.entity.PlaylistVideoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the v2 -> v3 migration and the duplicate guard:
 * a video could be added to a playlist twice, and the duplicate rows
 * crashed the playlist grid (duplicate lazy keys).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistVideoMigrationTest {
    /** Builds an in-memory DB frozen at v2 with the old playlist_videos schema. */
    private fun v2DatabaseWithDuplicates(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val writable = db.openHelper.writableDatabase
        // Freeze at v2 with the pre-fix schema (no durationMs, no unique index).
        writable.execSQL("PRAGMA user_version = 2")
        writable.execSQL("DROP TABLE playlist_videos")
        writable.execSQL(
            """
            CREATE TABLE `playlist_videos` (
                `playlistId` INTEGER NOT NULL,
                `videoOrder` INTEGER NOT NULL,
                `contentUrl` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `author` TEXT,
                `thumbnailUrl` TEXT,
                `addedAt` INTEGER NOT NULL,
                PRIMARY KEY(`playlistId`, `videoOrder`),
                FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        writable.execSQL(
            "INSERT INTO playlists (id, name, videoCount, createdAt, updatedAt) VALUES (1, 'p', 0, 0, 0)",
        )
        // Two distinct videos + the same video twice (the bug).
        writable.execSQL("INSERT INTO playlist_videos VALUES (1, 1, 'u1', 'A', NULL, NULL, 0)")
        writable.execSQL("INSERT INTO playlist_videos VALUES (1, 2, 'u2', 'B', NULL, NULL, 0)")
        writable.execSQL("INSERT INTO playlist_videos VALUES (1, 3, 'u1', 'A', NULL, NULL, 0)")
        return db
    }

    /**
     * Applies MIGRATION_2_3 directly (the migration manager needs the real
     * DB file lifecycle), then the playlist_videos part of MIGRATION_4_5
     * (authorUrl) — without it, DAO queries against the current entity fail
     * on the v3-frozen table. The other 4_5 tables already carry the column
     * because the in-memory builder created the full current schema.
     */
    private fun migrate(db: AppDatabase) {
        val writable = db.openHelper.writableDatabase
        AppDatabase.MIGRATION_2_3.migrate(writable)
        writable.execSQL("ALTER TABLE `playlist_videos` ADD COLUMN `authorUrl` TEXT")
        writable.execSQL("PRAGMA user_version = 5")
    }

    private fun AppDatabase.dao(): PlaylistDao = playlistDao()

    @Test
    fun migrationRemovesDuplicateRowsAndRenumbers() =
        runBlocking {
            val db = v2DatabaseWithDuplicates()
            migrate(db)
            val dao = db.dao()

            assertEquals(2, dao.countVideos(1L))

            val videos = dao.observeVideos(1L).first()
            assertEquals(listOf("u1", "u2"), videos.map { it.contentUrl })
            // Orders renumbered to 1..N after the duplicate was removed.
            assertEquals(listOf(1, 2), videos.map { it.videoOrder })
        }

    @Test
    fun migrationCreatesUniqueIndexAndIgnoresDuplicates() =
        runBlocking {
            val db = v2DatabaseWithDuplicates()
            migrate(db)
            val dao = db.dao()

            val indexNames =
                db.openHelper.writableDatabase
                    .query("PRAGMA index_list(playlist_videos)")
                    .use { cursor ->
                        val names = mutableListOf<String>()
                        while (cursor.moveToNext()) names.add(cursor.getString(0))
                        names
                    }
            assertTrue("unique index missing: $indexNames", indexNames.isNotEmpty())

            dao.insertVideo(
                PlaylistVideoEntity(
                    playlistId = 1,
                    videoOrder = 99,
                    contentUrl = "u1",
                    title = "A again",
                    author = null,
                    thumbnailUrl = null,
                    durationMs = 123_456L,
                ),
            )
            // Duplicate is a no-op: row count unchanged, original row kept
            // (title not overwritten, no second row).
            assertEquals(2, dao.countVideos(1L))
            assertEquals(
                "A",
                dao
                    .observeVideos(1L)
                    .first()
                    .first { it.contentUrl == "u1" }
                    .title,
            )
        }

    @Test
    fun statsQuerySumsDuration() =
        runBlocking {
            val db = v2DatabaseWithDuplicates()
            migrate(db)
            val dao = db.dao()
            dao.insertVideo(
                PlaylistVideoEntity(
                    playlistId = 1,
                    videoOrder = 99,
                    contentUrl = "u3",
                    title = "C",
                    author = null,
                    thumbnailUrl = null,
                    durationMs = 61_000L,
                ),
            )

            val stats = dao.observePlaylistStats(1L).first()
            assertEquals(3, stats.videoCount)
            assertEquals(61_000L, stats.totalDurationMs)
            assertEquals("u1", dao.firstVideoUrl(1L))
        }
}

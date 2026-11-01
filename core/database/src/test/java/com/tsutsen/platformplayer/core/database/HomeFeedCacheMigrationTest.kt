package com.tsutsen.platformplayer.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tsutsen.platformplayer.core.database.entity.HomeFeedCacheEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the v10 -> v11 migration: home_feed_cache gains the
 * video stats columns (durationMs/viewCount/publishedAt, all nullable), so
 * cold-start cached cards keep their duration/view/posted pills. Follows
 * the PlaylistVideoMigrationTest pattern: the in-memory DB is frozen at v10
 * with the old table shape and MIGRATION_10_11 is applied directly (the
 * migration manager needs the real DB file lifecycle, which the
 * file-based SavedVideoDaoTest covers for v1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeFeedCacheMigrationTest {
    /** Builds an in-memory DB frozen at v10 with the pre-stats home_feed_cache schema. */
    private fun v10DatabaseWithCachedRow(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val writable = db.openHelper.writableDatabase
        // Freeze at v10 with the pre-v11 schema (no stats columns).
        writable.execSQL("PRAGMA user_version = 10")
        writable.execSQL("DROP TABLE home_feed_cache")
        writable.execSQL(
            """
            CREATE TABLE `home_feed_cache` (
                `cacheKey` TEXT NOT NULL,
                `contentUrl` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `author` TEXT,
                `thumbnailUrl` TEXT,
                `contentType` TEXT NOT NULL,
                `cachedAt` INTEGER NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                PRIMARY KEY(`cacheKey`)
            )
            """.trimIndent(),
        )
        writable.execSQL(
            "INSERT INTO home_feed_cache VALUES ('youtube:v1', 'https://yt.example/v1', 'T', 'a', NULL, 'video', 1000, 9999999999999)",
        )
        return db
    }

    private fun columnNames(db: AppDatabase, table: String): Map<String, Boolean> {
        val columns = mutableMapOf<String, Boolean>()
        db.openHelper.writableDatabase.query("PRAGMA table_info($table)").use { cursor ->
            while (cursor.moveToNext()) {
                columns[cursor.getString(1)] = cursor.getInt(3) == 1 // notnull flag
            }
        }
        return columns
    }

    @Test
    fun migrationAddsNullableStatsColumns_andOldRowsKeepNull() =
        runBlocking {
            val db = v10DatabaseWithCachedRow()
            AppDatabase.MIGRATION_10_11.migrate(db.openHelper.writableDatabase)
            AppDatabase.MIGRATION_11_12.migrate(db.openHelper.writableDatabase)

            val columns = columnNames(db, "home_feed_cache")
            for (name in listOf("durationMs", "viewCount", "publishedAt", "authorUrl")) {
                assertEquals("column $name missing", true, name in columns)
                assertEquals("column $name must be nullable", false, columns.getValue(name))
            }

            // The pre-v11 row survives with NULL stats (pills hidden until re-cached).
            val cached = db.homeFeedCacheDao().observeValidCache().first().single()
            assertEquals("youtube:v1", cached.cacheKey)
            assertNull(cached.durationMs)
            assertNull(cached.viewCount)
            assertNull(cached.publishedAt)
        }

    @Test
    fun statsRoundTripThroughDao() =
        runBlocking {
            val db = v10DatabaseWithCachedRow()
            AppDatabase.MIGRATION_10_11.migrate(db.openHelper.writableDatabase)
            AppDatabase.MIGRATION_11_12.migrate(db.openHelper.writableDatabase)
            val dao = db.homeFeedCacheDao()

            dao.insert(
                HomeFeedCacheEntity(
                    cacheKey = "youtube:v2",
                    contentUrl = "https://yt.example/v2",
                    title = "T2",
                    author = "a",
                    thumbnailUrl = null,
                    contentType = "video",
                    durationMs = 90_000L,
                    viewCount = 1234L,
                    publishedAt = 1714766400000L,
                    cachedAt = 2000,
                    expiresAt = 9999999999999,
                ),
            )

            val cached = dao.observeValidCache().first().single { it.cacheKey == "youtube:v2" }
            assertEquals(90_000L, cached.durationMs)
            assertEquals(1234L, cached.viewCount)
            assertEquals(1714766400000L, cached.publishedAt)
        }
}

package com.tsutsen.platformplayer.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tsutsen.platformplayer.core.database.dao.SavedVideoDao
import com.tsutsen.platformplayer.core.database.entity.SavedVideoEntity
import com.tsutsen.platformplayer.core.database.entity.SavedVideoType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedVideoDaoTest {
    private fun dao(): SavedVideoDao =
        Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
            .savedVideoDao()

    @Test
    fun upsertAndObserveByType() =
        runBlocking {
            val saved = dao()
            saved.upsert(SavedVideoEntity("u1", SavedVideoType.WATCH_LATER, "T1", null, null))
            saved.upsert(SavedVideoEntity("u2", SavedVideoType.LIKED, "T2", null, null))

            val watchLater = saved.observeByType(SavedVideoType.WATCH_LATER).first()
            val liked = saved.observeByType(SavedVideoType.LIKED).first()
            assertEquals(listOf("u1"), watchLater.map { it.contentUrl })
            assertEquals(listOf("u2"), liked.map { it.contentUrl })
        }

    @Test
    fun sameUrlCanHoldMultipleTypes() =
        runBlocking {
            val saved = dao()
            saved.upsert(SavedVideoEntity("u1", SavedVideoType.WATCH_LATER, "T", null, null))
            saved.upsert(SavedVideoEntity("u1", SavedVideoType.FAVOURITE, "T", null, null))

            assertTrue(saved.containsUrl("u1", SavedVideoType.WATCH_LATER))
            assertTrue(saved.containsUrl("u1", SavedVideoType.FAVOURITE))
            assertFalse(saved.containsUrl("u1", SavedVideoType.LIKED))

            saved.deleteByType("u1", SavedVideoType.WATCH_LATER)
            assertFalse(saved.containsUrl("u1", SavedVideoType.WATCH_LATER))
            assertTrue(saved.containsUrl("u1", SavedVideoType.FAVOURITE))
        }

    @Test
    fun upsertReplacesExistingRow() =
        runBlocking {
            val saved = dao()
            saved.upsert(SavedVideoEntity("u1", SavedVideoType.LIKED, "old", null, null))
            saved.upsert(SavedVideoEntity("u1", SavedVideoType.LIKED, "new", null, null))

            val rows = saved.observeByType(SavedVideoType.LIKED).first()
            assertEquals(1, rows.size)
            assertEquals("new", rows.single().title)
        }

    @Test
    fun deleteRemovesRow() =
        runBlocking {
            val saved = dao()
            val entity = SavedVideoEntity("u1", SavedVideoType.LIKED, "T", "a", "t")
            saved.upsert(entity)
            saved.delete(entity)
            assertFalse(saved.containsUrl("u1", SavedVideoType.LIKED))
        }

    @Test
    fun migrationSeedsWatchLaterFromUnstartedHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "migration_test.db")
        createV1Database(context, dbFile)

        // One never-started history row (must be seeded), one partially watched (must not).
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL(
                "INSERT INTO history (contentUrl, title, author, thumbnailUrl, lastPositionMs, totalDurationMs, watchedAt, viewedAt) " +
                    "VALUES ('unstarted', 'U', 'a', null, 0, 1000, 111, 111)",
            )
            db.execSQL(
                "INSERT INTO history (contentUrl, title, author, thumbnailUrl, lastPositionMs, totalDurationMs, watchedAt, viewedAt) " +
                    "VALUES ('partially', 'P', 'a', null, 500, 1000, 222, 222)",
            )
        }

        val database =
            Room
                .databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()

        val watchLater =
            runBlocking {
                database.savedVideoDao().observeByType(SavedVideoType.WATCH_LATER).first()
            }
        assertEquals(listOf("unstarted"), watchLater.map { it.contentUrl })
        assertEquals(111L, watchLater.single().addedAt)
        database.close()
        dbFile.delete()
    }

    /**
     * Builds a v1 database file from the exported schema JSON (test asset),
     * so MIGRATION_1_2 runs against the real legacy state. Room validates the
     * final schema against the compiled v2 one and throws on any mismatch.
     */
    private fun createV1Database(
        context: Context,
        file: File,
    ) {
        val json =
            context
                .assets
                .open("com/tsutsen/platformplayer/core/database/AppDatabase/1.json")
                .bufferedReader()
                .readText()
        val database = JSONObject(json).getJSONObject("database")

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.beginTransaction()
            val setupQueries = database.getJSONArray("setupQueries")
            for (i in 0 until setupQueries.length()) {
                db.execSQL(setupQueries.getString(i))
            }
            val entities = database.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val tableName = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        val index = indices.getJSONObject(j)
                        db.execSQL(index.getString("createSql").replace("\${TABLE_NAME}", tableName))
                    }
                }
            }
            db.setTransactionSuccessful()
            db.endTransaction()
            db.execSQL("PRAGMA user_version = 1")
        }
    }
}

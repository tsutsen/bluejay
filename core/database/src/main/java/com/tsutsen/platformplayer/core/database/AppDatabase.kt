package com.tsutsen.platformplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tsutsen.platformplayer.core.database.dao.*
import com.tsutsen.platformplayer.core.database.entity.*

@Database(
    entities = [
        QueueEntity::class,
        HistoryEntity::class,
        PlaylistEntity::class,
        PlaylistVideoEntity::class,
        HomeFeedCacheEntity::class,
        SubscriptionEntity::class,
        SavedVideoEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(SavedVideoTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    abstract fun historyDao(): HistoryDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun homeFeedCacheDao(): HomeFeedCacheDao

    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun savedVideoDao(): SavedVideoDao

    companion object {
        /**
         * v1 -> v2: adds the `saved_video` table and seeds WATCH_LATER from
         * history rows that were never started (lastPositionMs == 0).
         * Runs in a single transaction (Room wraps migrations in one).
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `saved_video` (
                            `contentUrl` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `author` TEXT,
                            `thumbnailUrl` TEXT,
                            `addedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`contentUrl`, `type`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `saved_video` (
                            `contentUrl`, `type`, `title`, `author`, `thumbnailUrl`, `addedAt`
                        )
                        SELECT `contentUrl`, 'WATCH_LATER', `title`, `author`, `thumbnailUrl`, `watchedAt`
                        FROM `history`
                        WHERE `lastPositionMs` = 0
                        """.trimIndent(),
                    )
                }
            }
    }
}

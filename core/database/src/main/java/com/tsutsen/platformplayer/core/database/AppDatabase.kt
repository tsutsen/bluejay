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
        NotificationEntity::class,
    ],
    version = 6,
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

    abstract fun notificationDao(): NotificationDao

    companion object {
        /**
         * v4 -> v5: stores the channel URL alongside the author name in
         * saved videos, history, and playlist videos — the long-press
         * "Go to channel" action needs it and the live-feed cards already
         * carry it; local cards used to lose it on the way to the DB.
         * Existing rows keep NULL (tile hidden) until re-saved/re-watched.
         */
        /**
         * v5 -> v6: stores the video duration in saved videos so
         * Watch Later/Liked/Favourite cards can show a duration tile
         * like the live-feed cards do. Existing rows keep 0 (tile
         * hidden) until re-saved.
         */
        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE `saved_video` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `saved_video` ADD COLUMN `authorUrl` TEXT")
                    db.execSQL("ALTER TABLE `history` ADD COLUMN `authorUrl` TEXT")
                    db.execSQL("ALTER TABLE `playlist_videos` ADD COLUMN `authorUrl` TEXT")
                }
            }

        /** v3 -> v4: adds the in-app notifications table. */
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `notifications` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `subscriptionUrl` TEXT NOT NULL,
                            `subscriptionName` TEXT NOT NULL,
                            `contentUrl` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `thumbnailUrl` TEXT,
                            `timestamp` INTEGER NOT NULL,
                            `isRead` INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS `index_notifications_subscriptionUrl_contentUrl`
                        ON `notifications` (`subscriptionUrl`, `contentUrl`)
                        """.trimIndent(),
                    )
                }
            }

        /**
         * v2 -> v3: cleans up duplicate playlist videos (a video could be
         * added twice, which crashed the playlist grid with duplicate
         * lazy keys), makes (playlistId, contentUrl) unique so it can
         * never happen again, and adds `durationMs` for playlist totals.
         * Runs in a single transaction (Room wraps migrations in one).
         */
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Keep the first insert per (playlist, video).
                    db.execSQL(
                        """
                        DELETE FROM `playlist_videos` WHERE rowid NOT IN (
                            SELECT MIN(rowid) FROM `playlist_videos`
                            GROUP BY `playlistId`, `contentUrl`
                        )
                        """.trimIndent(),
                    )
                    // Renumber videoOrder 1..N per playlist (gaps from the
                    // deleted duplicates).
                    db.execSQL(
                        """
                        UPDATE `playlist_videos` SET `videoOrder` = (
                            SELECT COUNT(*) FROM `playlist_videos` AS `pv`
                            WHERE `pv`.`playlistId` = `playlist_videos`.`playlistId`
                              AND `pv`.`rowid` <= `playlist_videos`.`rowid`
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_videos_playlist_content`
                        ON `playlist_videos` (`playlistId`, `contentUrl`)
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        ALTER TABLE `playlist_videos` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0
                        """.trimIndent(),
                    )
                }
            }

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

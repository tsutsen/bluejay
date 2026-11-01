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
    version = 12,
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
         * v10 -> v11: stores the video stats (duration/view count/posted date)
         * in the home-feed cache so cold-start cached cards paint with the
         * same pills as live cards. Existing rows keep NULL (pills hidden)
         * until the next refresh re-caches them.
         */
        val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `home_feed_cache` ADD COLUMN `durationMs` INTEGER")
                    db.execSQL("ALTER TABLE `home_feed_cache` ADD COLUMN `viewCount` INTEGER")
                    db.execSQL("ALTER TABLE `home_feed_cache` ADD COLUMN `publishedAt` INTEGER")
                }
            }

        /**
         * v11 -> v12: stores the author's channel url in the home-feed cache,
         * so cold-start cached cards keep the options sheet's "Go to channel"
         * tile and the Hide button's creator-level arrow. Existing rows keep
         * NULL (tile/arrow hidden) until the next refresh re-caches them.
         */
        val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `home_feed_cache` ADD COLUMN `authorUrl` TEXT")
                }
            }

        /**
         * v9 -> v10: stores the video's real posted date (captured at
         * save/watch time) in saved videos, history, and playlist videos, so
         * library cards can show the posted date in the bottom-left pill
         * separately from the "added X ago" badge. Existing rows keep 0
         * (posted pill hidden) until re-saved/re-watched — same pattern as
         * the duration/view-count migrations.
         */
        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE `saved_video` ADD COLUMN `postedAt` INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE `history` ADD COLUMN `postedAt` INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE `playlist_videos` ADD COLUMN `postedAt` INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

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
        /**
         * v6 -> v7: stores the view count in saved videos, history, and
         * playlist videos so library cards can show it like the live-feed
         * cards do. Existing rows keep 0 (pill hidden) until re-saved or
         * re-watched.
         */
        /**
         * v7 -> v8: accumulates actually-watched time (skip-aware) per
         * history entry. Existing rows keep 0 until re-watched; stats
         * fall back to lastPositionMs for those.
         */
        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE `history` ADD COLUMN `watchedMs` INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        /**
         * v8 -> v9: repairs rows written while history sampling only ran
         * on state changes (pause/seek), so the skip-aware tracker
         * credited only the samples close enough together and watchedMs
         * tracked a fraction of real playback. Caps each row at
         * lastPositionMs (the legacy proxy) and at totalDurationMs when
         * known; rows with correct data are untouched (MAX).
         */
        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        UPDATE `history`
                        SET `watchedMs` = MAX(
                            `watchedMs`,
                            MIN(`lastPositionMs`, MAX(`totalDurationMs`, `lastPositionMs`))
                        )
                        """.trimIndent(),
                    )
                }
            }

        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE `saved_video` ADD COLUMN `viewCount` INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE `history` ADD COLUMN `viewCount` INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "ALTER TABLE `playlist_videos` ADD COLUMN `viewCount` INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

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

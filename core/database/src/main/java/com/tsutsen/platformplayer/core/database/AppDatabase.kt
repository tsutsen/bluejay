package com.tsutsen.platformplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tsutsen.platformplayer.core.database.dao.*
import com.tsutsen.platformplayer.core.database.entity.*

@Database(
    entities = [
        QueueEntity::class,
        HistoryEntity::class,
        PlaylistEntity::class,
        PlaylistVideoEntity::class,
        HomeFeedCacheEntity::class,
        SubscriptionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun queueDao(): QueueDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun homeFeedCacheDao(): HomeFeedCacheDao
    abstract fun subscriptionDao(): SubscriptionDao
}

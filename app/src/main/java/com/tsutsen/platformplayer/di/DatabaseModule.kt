package com.tsutsen.platformplayer.di

import android.content.Context
import com.tsutsen.platformplayer.core.database.AppDatabase
import com.tsutsen.platformplayer.core.database.AppDatabaseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = AppDatabaseProvider.get(context)

    @Provides
    fun provideQueueDao(database: AppDatabase) = database.queueDao()

    @Provides
    fun provideHistoryDao(database: AppDatabase) = database.historyDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase) = database.playlistDao()

    @Provides
    fun provideHomeFeedCacheDao(database: AppDatabase) = database.homeFeedCacheDao()

    @Provides
    fun provideSubscriptionDao(database: AppDatabase) = database.subscriptionDao()

    @Provides
    fun provideSavedVideoDao(database: AppDatabase) = database.savedVideoDao()

    @Provides
    fun provideNotificationDao(database: AppDatabase) = database.notificationDao()
}

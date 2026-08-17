package com.tsutsen.platformplayer.di

import android.content.Context
import androidx.room.Room
import com.tsutsen.platformplayer.core.database.AppDatabase
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
    ): AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                "grayjay_database",
            )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            // Safety net only: MIGRATION_1_2 is registered above, so this
            // triggers just for an unregistered future version (same as before).
            .fallbackToDestructiveMigration()
            .build()

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
}

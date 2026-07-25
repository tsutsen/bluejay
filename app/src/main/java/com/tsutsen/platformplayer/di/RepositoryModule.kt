package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SearchRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.data.repository.SubscriptionRepository
import com.tsutsen.platformplayer.core.data.repository.impl.HomeRepositoryImpl
import com.tsutsen.platformplayer.core.data.repository.impl.LibraryRepositoryImpl
import com.tsutsen.platformplayer.core.data.repository.impl.PlayerRepositoryImpl
import com.tsutsen.platformplayer.core.data.repository.impl.SearchRepositoryImpl
import com.tsutsen.platformplayer.core.data.repository.impl.SettingsRepositoryImpl
import com.tsutsen.platformplayer.core.data.repository.impl.SubscriptionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPlayerRepository(impl: PlayerRepositoryImpl): PlayerRepository

    // HomeRepository is now bound by HomeEngineModule (EngineHomeRepositoryImpl)

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: SubscriptionRepositoryImpl): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}

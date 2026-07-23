package com.futo.platformplayer.di

import com.futo.platformplayer.core.data.repository.HomeRepository
import com.futo.platformplayer.core.data.repository.LibraryRepository
import com.futo.platformplayer.core.data.repository.PlayerRepository
import com.futo.platformplayer.core.data.repository.SearchRepository
import com.futo.platformplayer.core.data.repository.SettingsRepository
import com.futo.platformplayer.core.data.repository.SubscriptionRepository
import com.futo.platformplayer.core.data.repository.impl.HomeRepositoryImpl
import com.futo.platformplayer.core.data.repository.impl.LibraryRepositoryImpl
import com.futo.platformplayer.core.data.repository.impl.PlayerRepositoryImpl
import com.futo.platformplayer.core.data.repository.impl.SearchRepositoryImpl
import com.futo.platformplayer.core.data.repository.impl.SettingsRepositoryImpl
import com.futo.platformplayer.core.data.repository.impl.SubscriptionRepositoryImpl
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

    @Binds
    @Singleton
    abstract fun bindHomeRepository(impl: HomeRepositoryImpl): HomeRepository

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

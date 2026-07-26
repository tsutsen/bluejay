package com.tsutsen.platformplayer.di

import android.content.Context
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SearchRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.data.repository.SubscriptionRepository
import com.tsutsen.platformplayer.core.data.repository.VideoUrlResolver
import com.tsutsen.platformplayer.core.data.repository.impl.HomeRepositoryImpl
import com.tsutsen.platformplayer.core.data.repository.impl.LibraryRepositoryImpl
import com.tsutsen.platformplayer.core.data.repository.impl.PlayerRepositoryImpl
import com.tsutsen.platformplayer.core.data.repository.impl.SearchRepositoryImpl
import com.tsutsen.platformplayer.core.data.repository.impl.SettingsRepositoryImpl
import com.tsutsen.platformplayer.di.EngineSubscriptionsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoUrlResolver(impl: EngineVideoUrlResolver): VideoUrlResolver

    // HomeRepository is now bound by HomeEngineModule (EngineHomeRepositoryImpl)

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: EngineSubscriptionsRepositoryImpl): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object PlayerRepositoryModule {

    @Provides
    @Singleton
    fun providePlayerRepository(
        @ApplicationContext context: Context,
        urlResolver: VideoUrlResolver
    ): PlayerRepository {
        val impl = PlayerRepositoryImpl(context)
        impl.setUrlResolver(urlResolver)
        return impl
    }
}

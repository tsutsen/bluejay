package com.futo.platformplayer.di

import com.futo.platformplayer.core.data.repository.HomeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeEngineModule {

    @Binds
    @Singleton
    abstract fun bindHomeRepository(impl: EngineHomeRepositoryImpl): HomeRepository
}

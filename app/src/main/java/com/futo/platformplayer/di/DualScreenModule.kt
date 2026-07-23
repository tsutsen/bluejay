package com.futo.platformplayer.di

import com.futo.platformplayer.feature.dualscreen.CompanionWindowManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DualScreenModule {

    @Provides
    @Singleton
    fun provideCompanionWindowManager(): CompanionWindowManager {
        return CompanionWindowManager()
    }
}

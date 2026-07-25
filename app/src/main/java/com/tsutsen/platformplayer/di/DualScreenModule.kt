package com.tsutsen.platformplayer.di

import android.content.Context
import com.tsutsen.platformplayer.feature.dualscreen.CompanionWindowManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DualScreenModule {

    @Provides
    @Singleton
    fun provideCompanionWindowManager(@ApplicationContext context: Context): CompanionWindowManager {
        return CompanionWindowManager(context)
    }
}

package com.futo.platformplayer.di

import androidx.navigation3.runtime.NavController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Navigation DI module.
 * Navigator and NavHostController are provided by the activity layer.
 */
@Module
@InstallIn(SingletonComponent::class)
object NavigationModule

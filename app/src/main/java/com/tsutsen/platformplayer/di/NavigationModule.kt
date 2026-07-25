package com.tsutsen.platformplayer.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Navigation DI module.
 * Navigator and NavHostController are provided by the activity layer.
 */
@Module
@InstallIn(SingletonComponent::class)
object NavigationModule

package com.tsutsen.platformplayer.core.data.di

import javax.inject.Qualifier

/**
 * Qualifier for the shared app-lifetime [kotlinx.coroutines.CoroutineScope]
 * (`SupervisorJob() + Dispatchers.Default`). Provided by the app's DI module;
 * repositories inject it instead of creating detached module-level scopes so
 * all long-lived work shares one supervisor.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

package com.tsutsen.platformplayer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Shared app-lifetime coroutine scope for fire-and-forget work that must
 * outlive any activity (update installs, telemetry uploads, fallbacks when
 * the activity-backed StateApp scope is gone). SupervisorJob keeps one
 * failure from cancelling unrelated children; the scope is never cancelled
 * explicitly — it lives for the process.
 */
object AppCoroutineScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

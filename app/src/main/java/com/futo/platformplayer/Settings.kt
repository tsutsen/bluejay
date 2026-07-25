package com.futo.platformplayer

import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * Minimal stub Settings class.
 * The original Settings.kt was heavily dependent on XML-based settings UI
 * components that have been deleted during the Compose migration.
 * This stub provides the minimal API needed for the app to compile.
 */
@Serializable
class Settings {
    var didFirstStart: Boolean = false
    
    // Casting settings (used by Extensions_Network.kt)
    val casting = CastingSettings()
    
    // Auto-update settings (used by UpdateCheckWorker.kt)
    val autoUpdate = AutoUpdateSettings()
    
    // Browsing settings (used by ManagedHttpClient.kt)
    val browsing = BrowsingSettings()
    
    // Notifications settings (used by BackgroundWorker.kt)
    val notifications = NotificationsSettings()
    
    // Playback settings (used by VideoHelper.kt)
    val playback = PlaybackSettings()
    
    // Downloads settings (used by DownloadService.kt, VideoDownload.kt)
    val downloads = DownloadsSettings()
    
    // Storage settings (used by StateApp.kt)
    val storage = StorageSettings()
    
    // Other settings (used by StateApp.kt)
    val other = OtherSettings()
    
    // Plugin settings (used by StateApp.kt)
    val plugins = PluginSettings()
    
    // Logging settings (used by StateApp.kt)
    val logging = LoggingSettings()
    
    // Synchronization settings (used by StateApp.kt)
    val synchronization = SynchronizationSettings()
    
    // Subscriptions settings (used by StateApp.kt)
    val subscriptions = SubscriptionsSettings()
    
    // Backup settings (used by StateApp.kt)
    val backup = BackupSettings()
    
    // Polycentric settings (used by StateApp.kt)
    val polycentric = PolycentricSettings()
    
    @Serializable
    class AutoUpdateSettings {
        var shouldBackgroundDownload: Boolean = true
        fun isAutoUpdateEnabled(): Boolean = true
    }
    
    @Serializable
    class BrowsingSettings {
        var useDownloadedCABundle: Boolean = false
        var videoCache: Boolean = true
    }
    
    @Serializable
    class NotificationsSettings {
        var plannedContentNotification: Boolean = false
    }
    
    @Serializable
    class PlaybackSettings {
        var preferOriginalAudio: Boolean = false
        var restartPlaybackAfterLoss: Boolean = false
    }
    
    @Serializable
    class DownloadsSettings {
        var byteRangeDownload: Boolean = true
        fun shouldDownload(): Boolean = true
        fun getByteRangeThreadCount(): Int = 4
    }
    
    @Serializable
    class CastingSettings {
        var allowIpv6: Boolean = false
    }
    
    @Serializable
    class StorageSettings {
        var storage_general: String? = null
        var storage_download: String? = null
        var isStorageMainValid: Boolean = true
        fun getStorageGeneralUri(): String? = null
        fun isStorageMainValid(context: android.content.Context): Boolean = isStorageMainValid
    }
    
    @Serializable
    class OtherSettings {
        @kotlinx.serialization.Contextual var other: Any? = null
        var polycentricLocalCache: Boolean = false
        var shouldClearWebviewCookies: Boolean = false
    }
    
    @Serializable
    class PluginSettings {
        @kotlinx.serialization.Contextual var plugins: Any? = null
        fun shouldClearWebviewCookies(): Boolean = false
    }
    
    @Serializable
    class LoggingSettings {
        @kotlinx.serialization.Contextual var logging: Any? = null
        var logLevel: Int = 2  // INFO level
    }
    
    @Serializable
    class SynchronizationSettings {
        var enabled: Boolean = false
        var connectThroughRelay: Boolean = false
        var connectLocalDirectThroughRelay: Boolean = false
        var connectDiscovered: Boolean = false
        var syncServer: String? = null
    }
    
    @Serializable
    class SubscriptionsSettings {
        @kotlinx.serialization.Contextual var subscriptions: Any? = null
        var fetchOnAppBoot: Boolean = true
        var useSubscriptionExchange: Boolean = false
        fun getSubscriptionsBackgroundIntervalMinutes(): Int = 60
        fun getSubscriptionsConcurrency(): Int = 4
    }
    
    @Serializable
    class BackupSettings {
        @kotlinx.serialization.Contextual var backup: Any? = null
        var didAskAutoBackup: Boolean = false
        var lastAutoBackupTime: OffsetDateTime = OffsetDateTime.now()
        fun shouldAutomaticBackup(): Boolean = false
    }
    
    @Serializable
    class PolycentricSettings {
        var watchLaterAddStart: Boolean = false
        var polycentricEnabled: Boolean = true
        var pairThroughRelay: Boolean = false
    }
    
    // Tabs settings
    var advancedSettings: Boolean = false
    
    companion object {
        @Volatile
        private var _instance: Settings? = null
        
        val instance: Settings
            get() = _instance ?: synchronized(this) {
                _instance ?: Settings().also { _instance = it }
            }
    }
    
    // Member function save() - used by StateApp.kt
    fun save() {
        // No-op stub
    }
    
    // Stub methods for deleted functionality
    fun syncGrayjay() {
        UIDialogs.toast(null, "Sync not yet migrated to Compose")
    }
    
    fun managePolycentricIdentity() {
        UIDialogs.toast(null, "Polycentric identity not yet migrated to Compose")
    }
    
    fun manageLinks() {
        UIDialogs.toast(null, "Link management not yet migrated to Compose")
    }
    
    fun ignoreBatteryOptimization() {
        UIDialogs.toast(null, "Battery optimization settings not yet migrated to Compose")
    }
}

// Extension function for OffsetDateTime to calculate hours difference
fun OffsetDateTime.getNowDiffHours(): Long {
    return java.time.Duration.between(this, OffsetDateTime.now()).toHours()
}

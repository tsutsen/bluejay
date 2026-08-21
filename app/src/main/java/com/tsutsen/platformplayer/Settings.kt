package com.tsutsen.platformplayer

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
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

    // Content settings (what the video page shows)
    val content = ContentSettings()

    // Downloads settings (used by DownloadService.kt, VideoDownload.kt)
    val downloads = DownloadsSettings()

    // Storage settings (used by StateApp.kt)
    val storage = StorageSettings()

    // Other settings (used by StateApp.kt)
    val other = OtherSettings()

    // Appearance settings (theme mode, dynamic color, contrast)
    val appearance = AppearanceSettings()

    // Feed settings (grid columns for content lists)
    val feed = FeedSettings()

    // Search settings (recent search queries)
    val search = SearchSettings()

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

    // Tabs settings
    var advancedSettings: Boolean = false

    // Second (rear) display companion window
    var dualScreen: Boolean = false

    // General settings (confirm exit dialog, UI language)
    var confirmExit: Boolean = false
    var language: String = "en"

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
        var enabled: Boolean = true
        var plannedContentNotification: Boolean = false
    }

    @Serializable
    class PlaybackSettings {
        var preferOriginalAudio: Boolean = false
        var restartPlaybackAfterLoss: Int = 0 // 0=off, 1=10s, 2=30s, 3=always
        var autoplay: Boolean = true
        var enableBackgroundPlayback: Boolean = true
        var enablePictureInPicture: Boolean = true

        // Preferred resolution for new playback sessions ("auto" = let ExoPlayer pick).
        var defaultResolution: String = "auto"

        // When true, the player restores the subtitle on/off state from the
        // last session instead of always starting with the default.
        var rememberSubtitleState: Boolean = false

        // Preferred subtitle track language ("auto" = player default).
        var preferredSubtitleLanguage: String = "auto"
    }

    @Serializable
    class ContentSettings {
        var showRecommendedVideos: Boolean = true
        var showComments: Boolean = true
    }

    @Serializable
    class DownloadsSettings {
        var byteRangeDownload: Boolean = true

        fun shouldDownload(): Boolean = true

        fun getByteRangeThreadCount(): Int = 4
    }

    @Serializable
    class CastingSettings {
        var enabled: Boolean = true
        var keepScreenOn: Boolean = true
        var alwaysProxyRequests: Boolean = false
        var allowIpv6: Boolean = false
        var allowLinkLocalIpv4: Boolean = false
    }

    @Serializable
    class StorageSettings {
        var storage_general: String? = null
        var storage_download: String? = null
        var isStorageMainValid: Boolean = true

        fun getStorageGeneralUri(): android.net.Uri? = null

        fun isStorageMainValid(context: android.content.Context): Boolean = isStorageMainValid
    }

    @Serializable
    class OtherSettings {
        var polycentricLocalCache: Boolean = false
        var shouldClearWebviewCookies: Boolean = false
        var watchLaterAddStart: Boolean = false
        var playlistAllowDups: Boolean = false
        var polycentricEnabled: Boolean = false
    }

    @Serializable
    class AppearanceSettings {
        // AUTO | LIGHT | DARK
        var themeMode: String = "AUTO"

        // Material You (wallpaper) color scheme
        var dynamicColor: Boolean = true

        // STANDARD | MEDIUM | HIGH
        var contrastLevel: String = "STANDARD"
    }

    @Serializable
    class FeedSettings {
        var gridColumns: Int = 3
    }

    @Serializable
    class SearchSettings {
        // Most recent first, capped at 10 by the writer (SearchViewModel).
        var history: List<String> = emptyList()
    }

    @Serializable
    class PluginSettings {
        fun shouldClearWebviewCookies(): Boolean = false

        var checkDisabledPluginsForUpdates: Boolean = true
    }

    @Serializable
    class LoggingSettings {
        var logLevel: Int = 2 // INFO level
    }

    @Serializable
    class SynchronizationSettings {
        var enabled: Boolean = false
        var connectThroughRelay: Boolean = false
        var connectLocalDirectThroughRelay: Boolean = false
        var connectDiscovered: Boolean = false
        var syncServer: String? = null
        var broadcast: Boolean = false
        var localConnections: Boolean = false
        var connectLast: Boolean = false
        var pairThroughRelay: Boolean = false
        var discoverThroughRelay: Boolean = false
    }

    @Serializable
    class SubscriptionsSettings {
        var fetchOnAppBoot: Boolean = true
        var useSubscriptionExchange: Boolean = false
        var peekChannelContents: Boolean = true

        fun getSubscriptionsBackgroundIntervalMinutes(): Int = 60

        fun getSubscriptionsConcurrency(): Int = 4
    }

    @Serializable
    class BackupSettings {
        var didAskAutoBackup: Boolean = false

        @kotlinx.serialization.Contextual
        var lastAutoBackupTime: OffsetDateTime = OffsetDateTime.now()

        fun shouldAutomaticBackup(): Boolean = false
    }

    @Serializable
    class PolycentricSettings {
        var watchLaterAddStart: Boolean = false
        var polycentricEnabled: Boolean = true
        var pairThroughRelay: Boolean = false
        var discoverThroughRelay: Boolean = false
    }

    // Persist the whole config as JSON so settings survive app restarts.
    fun save() {
        val context = PlatformPlayerApp.context ?: return
        runCatching { settingsJson.encodeToString(Settings.serializer(), this) }
            .onSuccess { json ->
                context
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_SETTINGS, json)
                    .apply()
            }
    }

    // encode() - used by StateBackup.kt to serialize settings to JSON string
    fun encode(): String = runCatching { settingsJson.encodeToString(Settings.serializer(), this) }.getOrDefault("{}")

    companion object {
        @Volatile
        private var _instance: Settings? = null

        private const val PREFS_NAME = "bluejay_settings"
        private const val KEY_SETTINGS = "settings_json"

        private val offsetDateTimeSerializer =
            object : KSerializer<OffsetDateTime> {
                override val descriptor =
                    PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

                override fun serialize(
                    encoder: Encoder,
                    value: OffsetDateTime,
                ) = encoder.encodeString(value.toString())

                override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())
            }

        private val settingsJson =
            Json {
                ignoreUnknownKeys = true
                serializersModule =
                    SerializersModule {
                        contextual(OffsetDateTime::class, offsetDateTimeSerializer)
                    }
            }

        val instance: Settings
            get() =
                _instance ?: synchronized(this) {
                    _instance ?: load()?.also { _instance = it } ?: Settings().also { _instance = it }
                }

        // replace() - used by StateBackup.kt to deserialize settings from JSON string
        fun replace(jsonString: String) {
            runCatching { settingsJson.decodeFromString(Settings.serializer(), jsonString) }
                .onSuccess { newSettings ->
                    synchronized(this) {
                        _instance = newSettings
                    }
                }
        }

        private fun load(): Settings? {
            val context = PlatformPlayerApp.context ?: return null
            val json =
                context
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_SETTINGS, null)
                    ?: return null
            return runCatching { settingsJson.decodeFromString(Settings.serializer(), json) }.getOrNull()
        }
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

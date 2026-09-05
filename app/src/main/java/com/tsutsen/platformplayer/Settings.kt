package com.tsutsen.platformplayer

import android.content.Context
import com.tsutsen.platformplayer.core.data.repository.impl.LibraryRepositoryImpl
import com.tsutsen.platformplayer.core.datastore.model.ControllerBinding
import com.tsutsen.platformplayer.core.datastore.model.CustomTheme
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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

    // Set once the first-launch getting-started flow is finished or
    // skipped; the flow shows again only if it is reset to false.
    var gettingStartedCompleted: Boolean = false

    // Main-screen library: display order of the sections (reorderable in
    // Settings). Defaults to the natural LibraryRepository order.
    var librarySectionOrder: List<String> =
        listOf(
            LibraryRepositoryImpl.WATCH_LATER_ID,
            LibraryRepositoryImpl.LIKED_ID,
            LibraryRepositoryImpl.DISLIKED_ID,
            LibraryRepositoryImpl.FAVOURITE_ID,
            LibraryRepositoryImpl.HISTORY_ID,
            LibraryRepositoryImpl.DOWNLOADS_ID,
            LibraryRepositoryImpl.PLAYLISTS_ID,
        )
    /** Enabled library sections (Settings > Content). Order lives in [librarySectionOrder]. */
    var librarySectionsEnabled: List<String> =
        listOf(
            LibraryRepositoryImpl.WATCH_LATER_ID,
            LibraryRepositoryImpl.LIKED_ID,
            LibraryRepositoryImpl.DISLIKED_ID,
            LibraryRepositoryImpl.FAVOURITE_ID,
            LibraryRepositoryImpl.HISTORY_ID,
            LibraryRepositoryImpl.DOWNLOADS_ID,
            LibraryRepositoryImpl.PLAYLISTS_ID,
        )

    // Second (rear) display companion window
    var dualScreen: Boolean = false
    var dualScreenPages: List<String> = listOf("dash", "video", "library", "home")
    var dualScreenVideoTabs: List<String> =
        listOf("info", "controls", "comments", "chapters", "recommended", "queue", "dot")
    // Second-screen video page: display order of the enabled tabs.
    var dualScreenVideoTabOrder: List<String> =
        listOf("info", "controls", "comments", "chapters", "recommended", "queue", "dot")
    // Second-screen video page: order of the elements (controls row, video
    // header, tab strip).
    var dualScreenPageOrder: List<String> = listOf("controls", "video", "tabs")
    // Second-screen dash page: "Top creators" scope — "week" (this week)
    // or "overall" (all time).
    var dualScreenTopCreatorsScope: String = "week"
    // Second-screen dash page: display order of the widgets.
    var dualScreenDashPageOrder: List<String> = listOf("stats", "top_creators", "continue")
    // Second screen home page: which sources to fetch from (empty = all enabled).
    var dualScreenFeedSources: List<String> = emptyList()

    // Player gesture assignments per player mode and surface slot
    // (see PlayerGesturePreferences). Each map: gesture type
    // ("swipe_v"|"swipe_h"|"double_tap"|"hold") → action id.
    // Empty = shipped defaults for that slot.
    var playerGestures: PlayerGesturePreferences = PlayerGesturePreferences()

    // Controller (gamepad / TV remote) button mapping (Settings > Controller)
    var controller: ControllerSettings = ControllerSettings()

    @Serializable
    class ControllerSettings(
        var enabled: Boolean = false,
        var mappings: Map<String, ControllerBinding> = emptyMap(),
        var seekBackSeconds: Int = 10,
        var seekForwardSeconds: Int = 30,
    )
    // Second-screen library: the four 2x2 slots. Each entry is a section id
    // or a "playlist:<id>" reference (see Settings > Dual screen).
    var dualScreenLibrarySlots: List<String> =
        listOf(
            LibraryRepositoryImpl.WATCH_LATER_ID,
            LibraryRepositoryImpl.LIKED_ID,
            LibraryRepositoryImpl.FAVOURITE_ID,
            LibraryRepositoryImpl.HISTORY_ID,
        )

    @Serializable
    class PlayerGestureSlotSet(
        var top: Map<String, String> = emptyMap(),
        var bottomLeft: Map<String, String> = emptyMap(),
        var bottomCenter: Map<String, String> = emptyMap(),
        var bottomRight: Map<String, String> = emptyMap(),
    )

    @Serializable
    class PlayerGesturePreferences(
        var fullscreen: PlayerGestureSlotSet = PlayerGestureSlotSet(),
        var normal: PlayerGestureSlotSet = PlayerGestureSlotSet(),
    )

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

        // Default speed applied when a new video starts.
        var defaultPlaybackSpeed: Float = 1f

        // Speed multiplier reached after holding the right-hand side
        // (the "speed up" hold gesture). 2x by default.
        var defaultSpeedup: Float = 2f

        // Horizontal-swipe sensitivity of the speed-up/slow-down hold:
        // 1.0 = default, higher = less movement per speed step.
        var speedupSensitivity: Float = 1f

        // Jump step (seconds) for the back/forward seek gestures.
        var jumpStepSeconds: Int = 5

        // Subtitle appearance: font (default|sans|serif|mono),
        // size (small|standard|large), bottom padding (tight|standard|wide).
        var subtitleFont: String = "default"
        var subtitleFontSize: Int = 16
        var subtitleBottomPadding: Int = 20

        // Subtitle glyph outline thickness in px (0 = no outline).
        var subtitleOutline: Int = 3
    }

    @Serializable
    class ContentSettings {
        var showRecommendedVideos: Boolean = true
        var showComments: Boolean = true

        // "Auto" or "NNNp" — quality the player starts each video at.
        var defaultVideoResolution: String = "Auto"

        // Resolution label (DownloadQuality label) a plain download uses.
        var defaultDownloadResolution: String = "480p"
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

        // UI rounding as a percent (100 = shipped radii, 0 = sharp)
        var uiRounding: Int = 100

        // User-created themes (key colors → generated scheme)
        var customThemes: List<CustomTheme> = emptyList()

        // Active custom theme id (null = default theming)
        var activeThemeId: String? = null
    }

    @Serializable
    class FeedSettings {
        var gridColumns: Int = 3

        // Hidden home source-chip ids (persisted filter selection).
        var hiddenSources: List<String> = emptyList()

        // Source ids whose home-feed "log in?" notice was declined.
        var loginPromptsDismissed: List<String> = emptyList()
    }

    @Serializable
    class SearchSettings {
        // Most recent first, capped at 10 by the writer (SearchViewModel).
        var history: List<String> = emptyList()
    }

    @Serializable
    class PluginSettings {
        fun shouldClearWebviewCookies(): Boolean = false

        // Auto-install available plugin updates on app launch (enabled
        // plugins only) and when a plugin gets enabled.
        var autoUpdatePlugins: Boolean = true
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
            return runCatching {
                    settingsJson.decodeFromString(Settings.serializer(), migrateJson(json))
                }
                .getOrNull()
                ?.also {
                    // Pre-0.2.3 installs predate the info/controls tabs —
                    // enable them so the new tabs appear without a manual
                    // settings round-trip (user order is preserved).
                    val canonical =
                        listOf("info", "controls", "comments", "chapters", "recommended", "queue", "dot")
                    it.dualScreenVideoTabs += canonical.filter { key -> key !in it.dualScreenVideoTabs }
                }
        }

        /**
         * Migrate the pre-split gesture settings: installs saved a single
         * flat slot map (unified mode) under [playerGestures] with the slot
         * keys directly inside. Wrap it into both the new fullscreen and
         * normal sections so the user's customizations survive the split.
         */
        private fun migrateJson(raw: String): String {
            return try {
                val root = Json.parseToJsonElement(raw).jsonObject
                val pg = root["playerGestures"]?.jsonObject
                if (pg != null && pg.containsKey("top") && !pg.containsKey("fullscreen")) {
                    val wrapped =
                        JsonObject(
                            mapOf(
                                "fullscreen" to pg,
                                "normal" to pg,
                            ),
                        )
                    val updated = root.toMutableMap()
                    updated["playerGestures"] = wrapped
                    return JsonObject(updated).toString()
                } else {
                    raw
                }
            } catch (e: Exception) {
                raw
            }
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

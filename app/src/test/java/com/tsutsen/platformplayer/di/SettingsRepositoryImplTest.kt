package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.PlaybackPreferences
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime

/**
 * Each setter on the Settings-backed repository must persist to the legacy
 * [Settings] singleton (the app's single source of truth) and emit the new
 * value on [com.tsutsen.platformplayer.core.data.repository.SettingsRepository.preferences].
 */
class SettingsRepositoryImplTest {
    private lateinit var repo: SettingsRepositoryImpl

    @Before
    fun resetSettings() {
        val s = Settings.instance
        s.appearance.themeMode = "AUTO"
        s.appearance.dynamicColor = true
        s.playback.autoplay = true
        s.playback.defaultPlaybackSpeed = 1f
        s.playback.subtitleFont = "default"
        s.playback.subtitleFontSize = 16
        s.playback.subtitleBottomPadding = 20
        s.notifications.enabled = true
        s.advancedSettings = false
        s.feed.gridColumns = 3
        s.recommendations.probCut1 = 33
        s.recommendations.probCut2 = 66
        // Fields the legacy-group tests mutate; reset so test order never
        // leaks state through the shared Settings singleton.
        s.playback.preferOriginalAudio = false
        s.playback.restartPlaybackAfterLoss = 0
        s.other.watchLaterAddStart = false
        s.other.playlistAllowDups = false
        s.backup.didAskAutoBackup = false
        s.notifications.plannedContentNotification = false
        s.downloads.byteRangeDownload = true
        s.didFirstStart = false
        s.storage.storage_general = null
        s.storage.storage_download = null
        repo = SettingsRepositoryImpl()
    }

    @Test
    fun initialPreferencesReflectDefaults() =
        runTest {
            val prefs = repo.preferences.first()
            assertEquals(ThemeMode.AUTO, prefs.appearance.themeMode)
            assertTrue(prefs.appearance.dynamicColor)
            assertTrue(prefs.playback.autoPlay)
            assertEquals(1f, prefs.defaultPlaybackSpeed)
            assertEquals("default", prefs.subtitle.font)
            assertEquals(16, prefs.subtitle.size)
            assertEquals(20, prefs.subtitle.bottomPadding)
            assertFalse(prefs.enableDeveloperOptions)
            assertEquals(3, prefs.gridColumns)
        }

    @Test
    fun updateAppearancePersistsAndEmits() =
        runTest {
            val newAppearance =
                AppearancePreferences(
                    themeMode = ThemeMode.DARK,
                    dynamicColor = false,
                )
            repo.updateAppearance(newAppearance)

            val s = Settings.instance
            assertEquals("DARK", s.appearance.themeMode)
            assertFalse(s.appearance.dynamicColor)

            val prefs = repo.preferences.value
            assertEquals(ThemeMode.DARK, prefs.appearance.themeMode)
            assertFalse(prefs.appearance.dynamicColor)
        }

    @Test
    fun updatePlaybackPersistsAndEmits() =
        runTest {
            repo.updatePlayback(PlaybackPreferences(autoPlay = false))

            assertFalse(Settings.instance.playback.autoplay)
            assertFalse(repo.preferences.value.playback.autoPlay)
        }

    @Test
    fun updateGeneralSubtitleFontPersistsAndEmits() =
        runTest {
            repo.updateGeneral("subtitleFont", "serif")
            assertEquals("serif", Settings.instance.playback.subtitleFont)
            assertEquals("serif", repo.preferences.value.subtitle.font)
        }

    @Test
    fun updateGeneralSubtitleSizePersistsAndEmits() =
        runTest {
            repo.updateGeneral("subtitleFontSize", 22)
            assertEquals(22, Settings.instance.playback.subtitleFontSize)
            assertEquals(22, repo.preferences.value.subtitle.size)
        }

    @Test
    fun updateGeneralSubtitleBottomPaddingPersistsAndEmits() =
        runTest {
            repo.updateGeneral("subtitleBottomPadding", 40)
            assertEquals(40, Settings.instance.playback.subtitleBottomPadding)
            assertEquals(40, repo.preferences.value.subtitle.bottomPadding)
        }

    @Test
    fun updateGeneralDefaultPlaybackSpeedPersistsAndEmits() =
        runTest {
            repo.updateGeneral("defaultPlaybackSpeed", 1.5f)
            assertEquals(1.5f, Settings.instance.playback.defaultPlaybackSpeed)
            assertEquals(1.5f, repo.preferences.value.defaultPlaybackSpeed)
        }

    @Test
    fun updateGeneralDeveloperOptionsPersistsAndEmits() =
        runTest {
            repo.updateGeneral("enableDeveloperOptions", true)
            assertTrue(Settings.instance.advancedSettings)
            assertTrue(repo.preferences.value.enableDeveloperOptions)
        }

    @Test
    fun updateGeneralDynamicColorPersistsAndEmits() =
        runTest {
            repo.updateGeneral("dynamicColor", false)
            assertFalse(Settings.instance.appearance.dynamicColor)
            assertFalse(repo.preferences.value.appearance.dynamicColor)
        }

    @Test
    fun updateGeneralGridColumnsPersistsAndEmits() =
        runTest {
            repo.updateGeneral("gridColumns", 2)
            assertEquals(2, Settings.instance.feed.gridColumns)
            assertEquals(2, repo.preferences.value.gridColumns)
        }

    @Test
    fun typedSettersPersistAndEmit() =
        runTest {
            repo.setGridColumns(5)
            repo.setSubtitleFont("serif")
            repo.setDefaultPlaybackSpeed(1.25f)
            repo.setUiRounding(60)
            repo.setGettingStartedCompleted(true)

            val s = Settings.instance
            assertEquals(5, s.feed.gridColumns)
            assertEquals("serif", s.playback.subtitleFont)
            assertEquals(1.25f, s.playback.defaultPlaybackSpeed)
            assertEquals(60, s.appearance.uiRounding)
            assertTrue(s.gettingStartedCompleted)

            val prefs = repo.preferences.value
            assertEquals(5, prefs.gridColumns)
            assertEquals("serif", prefs.subtitle.font)
            assertEquals(1.25f, prefs.defaultPlaybackSpeed)
            assertEquals(60, prefs.appearance.uiRounding)
            assertTrue(prefs.gettingStartedCompleted)
        }

    @Test
    fun probCutsSetterPersistsCoercesAndEmits() =
        runTest {
            repo.setProbCuts(10, 90)

            var s = Settings.instance
            assertEquals(10, s.recommendations.probCut1)
            assertEquals(90, s.recommendations.probCut2)
            assertEquals(10, repo.preferences.value.probCut1)
            assertEquals(90, repo.preferences.value.probCut2)

            // Out-of-range / inverted input is coerced to 0..100, cut1 <= cut2.
            repo.setProbCuts(120, -5)
            s = Settings.instance
            assertEquals(100, s.recommendations.probCut1)
            assertEquals(100, s.recommendations.probCut2)
            assertEquals(100, repo.preferences.value.probCut1)
            assertEquals(100, repo.preferences.value.probCut2)
        }

    @Test
    fun resetToDefaultsRestoresPersistedAndEmits() =
        runTest {
            repo.updateAppearance(AppearancePreferences(themeMode = ThemeMode.LIGHT))
            repo.updateGeneral("gridColumns", 4)
            repo.updateGeneral("defaultPlaybackSpeed", 2f)

            repo.resetToDefaults()

            val s = Settings.instance
            assertEquals("AUTO", s.appearance.themeMode)
            assertTrue(s.appearance.dynamicColor)
            assertFalse(s.advancedSettings)
            assertEquals(1f, s.playback.defaultPlaybackSpeed)
            assertEquals("default", s.playback.subtitleFont)
            assertEquals(16, s.playback.subtitleFontSize)
            assertEquals(20, s.playback.subtitleBottomPadding)
            assertEquals(3, s.feed.gridColumns)

            val prefs = repo.preferences.value
            assertEquals(ThemeMode.AUTO, prefs.appearance.themeMode)
            assertEquals(3, prefs.gridColumns)
            assertEquals(1f, prefs.defaultPlaybackSpeed)
        }

    @Test
    fun legacyGroupDefaultsMapFromSettings() =
        runTest {
            val prefs = repo.preferences.first()
            assertTrue(prefs.downloads.byteRangeDownload)
            assertTrue(prefs.downloads.shouldDownload)
            assertEquals(4, prefs.downloads.byteRangeThreadCount)
            assertNull(prefs.storage.storageGeneralUri)
            assertTrue(prefs.storage.isStorageMainValid)
            assertTrue(prefs.subscriptions.fetchOnAppBoot)
            assertTrue(prefs.subscriptions.peekChannelContents)
            assertFalse(prefs.subscriptions.useSubscriptionExchange)
            assertEquals(60, prefs.subscriptions.backgroundIntervalMinutes)
            assertEquals(4, prefs.subscriptions.concurrency)
            assertTrue(prefs.autoUpdate.isAutoUpdateEnabled)
            assertTrue(prefs.autoUpdate.shouldBackgroundDownload)
            assertTrue(prefs.browsing.videoCache)
            assertFalse(prefs.browsing.useDownloadedCABundle)
            assertFalse(prefs.notifications.plannedContentNotification)
            assertFalse(prefs.other.watchLaterAddStart)
            assertFalse(prefs.other.playlistAllowDups)
            assertFalse(prefs.playback.preferOriginalAudio)
            assertEquals(0, prefs.playback.restartPlaybackAfterLoss)
            assertFalse(prefs.didFirstStart)
        }

    @Test
    fun newTypedSettersPersistAndEmit() =
        runTest {
            val time = OffsetDateTime.now()
            repo.setStorageGeneral("content://general")
            repo.setStorageDownload("content://downloads")
            repo.setDidAskAutoBackup(true)
            repo.setDidFirstStart(true)
            repo.setLastAutoBackupTime(time)

            val s = Settings.instance
            assertEquals("content://general", s.storage.storage_general)
            assertEquals("content://downloads", s.storage.storage_download)
            assertTrue(s.backup.didAskAutoBackup)
            assertTrue(s.didFirstStart)
            assertEquals(time, s.backup.lastAutoBackupTime)

            val prefs = repo.preferences.value
            assertEquals("content://general", prefs.storage.storageGeneral)
            assertEquals("content://downloads", prefs.storage.storageDownload)
            assertTrue(prefs.backup.didAskAutoBackup)
            assertTrue(prefs.didFirstStart)
            assertEquals(time, prefs.backup.lastAutoBackupTime)
        }

    @Test
    fun reloadFromLegacyReEmitsExternalSettingsSwap() =
        runTest {
            // The backup import path swaps the legacy singleton directly;
            // reloadFromLegacy must re-map the prefs snapshot.
            Settings.instance.playback.preferOriginalAudio = true
            Settings.instance.other.watchLaterAddStart = true
            repo.reloadFromLegacy()

            val prefs = repo.preferences.value
            assertTrue(prefs.playback.preferOriginalAudio)
            assertTrue(prefs.other.watchLaterAddStart)
        }

    @Test
    fun encodeLegacySettingsReturnsSettingsJson() =
        runTest {
            // Default-valued fields are omitted (Json.encodeDefaults=false),
            // so flip one first to prove real settings JSON comes out.
            Settings.instance.downloads.byteRangeDownload = false
            val json = repo.encodeLegacySettings()
            assertTrue(json.contains("\"byteRangeDownload\":false"))
        }

    @Test
    fun hiddenContentProbabilitiesPersistAndEmit() =
        runTest {
            // Isolate from any state a previous run persisted.
            Settings.instance.content.hiddenVideoEntries = emptyList()
            Settings.instance.content.hiddenCreatorEntries = emptyList()

            repo.setVideoProbability("https://v/1", "Video one", 0)
            repo.setCreatorProbability("https://ch/1", "Channel", 50)
            repo.setCreatorProbability("https://ch/1", "Channel", 25) // same url updates

            val s = Settings.instance
            assertEquals(listOf("https://v/1"), s.content.hiddenVideoEntries.map { it.url })
            assertEquals("Video one", s.content.hiddenVideoEntries[0].name)
            assertEquals(0, s.content.hiddenVideoEntries[0].probability)
            assertEquals(listOf("https://ch/1"), s.content.hiddenCreatorEntries.map { it.url })
            assertEquals(25, s.content.hiddenCreatorEntries[0].probability)
            assertEquals(listOf("https://ch/1"), repo.hiddenCreators.value.map { it.url })
            assertEquals(25, repo.hiddenCreators.value[0].probability)

            // 100 removes the entry (fully shown again).
            repo.setVideoProbability("https://v/1", "Video one", 100)
            repo.setCreatorProbability("https://ch/1", "Channel", 100)
            assertTrue(Settings.instance.content.hiddenVideoEntries.isEmpty())
            assertTrue(Settings.instance.content.hiddenCreatorEntries.isEmpty())
            assertTrue(repo.hiddenVideos.value.isEmpty())
            assertTrue(repo.hiddenCreators.value.isEmpty())
        }
}

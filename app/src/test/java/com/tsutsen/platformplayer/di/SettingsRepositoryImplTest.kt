package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
import com.tsutsen.platformplayer.core.datastore.model.ContrastLevel
import com.tsutsen.platformplayer.core.datastore.model.PlaybackPreferences
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        s.appearance.contrastLevel = "STANDARD"
        s.playback.autoplay = true
        s.playback.enableBackgroundPlayback = true
        s.playback.enablePictureInPicture = true
        s.notifications.enabled = true
        s.confirmExit = false
        s.advancedSettings = false
        s.language = "en"
        s.feed.gridColumns = 3
        repo = SettingsRepositoryImpl()
    }

    @Test
    fun initialPreferencesReflectDefaults() =
        runTest {
            val prefs = repo.preferences.first()
            assertEquals(ThemeMode.AUTO, prefs.appearance.themeMode)
            assertTrue(prefs.appearance.dynamicColor)
            assertEquals(ContrastLevel.STANDARD, prefs.appearance.contrastLevel)
            assertTrue(prefs.playback.autoPlay)
            assertTrue(prefs.enableBackgroundPlayback)
            assertTrue(prefs.enablePictureInPicture)
            assertTrue(prefs.enableNotifications)
            assertFalse(prefs.confirmExit)
            assertFalse(prefs.enableDeveloperOptions)
            assertEquals("en", prefs.language)
            assertEquals(3, prefs.gridColumns)
        }

    @Test
    fun updateAppearancePersistsAndEmits() =
        runTest {
            val newAppearance =
                AppearancePreferences(
                    themeMode = ThemeMode.DARK,
                    contrastLevel = ContrastLevel.HIGH,
                    dynamicColor = false,
                )
            repo.updateAppearance(newAppearance)

            val s = Settings.instance
            assertEquals("DARK", s.appearance.themeMode)
            assertEquals("HIGH", s.appearance.contrastLevel)
            assertFalse(s.appearance.dynamicColor)

            val prefs = repo.preferences.value
            assertEquals(ThemeMode.DARK, prefs.appearance.themeMode)
            assertEquals(ContrastLevel.HIGH, prefs.appearance.contrastLevel)
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
    fun updateGeneralLanguagePersistsAndEmits() =
        runTest {
            repo.updateGeneral("language", "de")
            assertEquals("de", Settings.instance.language)
            assertEquals("de", repo.preferences.value.language)
        }

    @Test
    fun updateGeneralEnableNotificationsPersistsAndEmits() =
        runTest {
            repo.updateGeneral("enableNotifications", false)
            assertFalse(Settings.instance.notifications.enabled)
            assertFalse(repo.preferences.value.enableNotifications)
        }

    @Test
    fun updateGeneralBackgroundPlaybackPersistsAndEmits() =
        runTest {
            repo.updateGeneral("enableBackgroundPlayback", false)
            assertFalse(Settings.instance.playback.enableBackgroundPlayback)
            assertFalse(repo.preferences.value.enableBackgroundPlayback)
        }

    @Test
    fun updateGeneralPictureInPicturePersistsAndEmits() =
        runTest {
            repo.updateGeneral("enablePictureInPicture", false)
            assertFalse(Settings.instance.playback.enablePictureInPicture)
            assertFalse(repo.preferences.value.enablePictureInPicture)
        }

    @Test
    fun updateGeneralConfirmExitPersistsAndEmits() =
        runTest {
            repo.updateGeneral("confirmExit", true)
            assertTrue(Settings.instance.confirmExit)
            assertTrue(repo.preferences.value.confirmExit)
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
    fun resetToDefaultsRestoresPersistedAndEmits() =
        runTest {
            repo.updateAppearance(AppearancePreferences(themeMode = ThemeMode.LIGHT))
            repo.updateGeneral("confirmExit", true)
            repo.updateGeneral("gridColumns", 4)

            repo.resetToDefaults()

            val s = Settings.instance
            assertEquals("AUTO", s.appearance.themeMode)
            assertTrue(s.appearance.dynamicColor)
            assertEquals("STANDARD", s.appearance.contrastLevel)
            assertTrue(s.playback.enableBackgroundPlayback)
            assertTrue(s.playback.enablePictureInPicture)
            assertTrue(s.notifications.enabled)
            assertFalse(s.confirmExit)
            assertFalse(s.advancedSettings)
            assertEquals("en", s.language)
            assertEquals(3, s.feed.gridColumns)

            val prefs = repo.preferences.value
            assertEquals(ThemeMode.AUTO, prefs.appearance.themeMode)
            assertFalse(prefs.confirmExit)
            assertEquals(3, prefs.gridColumns)
        }
}

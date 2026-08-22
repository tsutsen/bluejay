package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences
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
        s.playback.autoplay = true
        s.playback.defaultPlaybackSpeed = 1f
        s.playback.subtitleFont = "default"
        s.playback.subtitleFontSize = 16
        s.playback.subtitleBottomPadding = 20
        s.notifications.enabled = true
        s.advancedSettings = false
        s.feed.gridColumns = 3
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
            assertEquals("standard", prefs.subtitle.size)
            assertEquals("standard", prefs.subtitle.bottomPadding)
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
}

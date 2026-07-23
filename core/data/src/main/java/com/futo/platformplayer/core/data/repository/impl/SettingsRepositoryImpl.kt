package com.futo.platformplayer.core.data.repository.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.futo.platformplayer.core.data.repository.SettingsRepository
import com.futo.platformplayer.core.datastore.model.AppPreferences
import com.futo.platformplayer.core.datastore.model.AppearancePreferences
import com.futo.platformplayer.core.datastore.model.PlaybackPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

/**
 * SettingsRepository implementation backed by DataStore preferences.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val _preferences = MutableStateFlow(AppPreferences())
    override val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    override suspend fun updateAppearance(prefs: AppearancePreferences) {
        _preferences.update { it.copy(appearance = prefs) }
        saveToDataStore()
    }

    override suspend fun updatePlayback(prefs: PlaybackPreferences) {
        _preferences.update { it.copy(playback = prefs) }
        saveToDataStore()
    }

    override suspend fun updateGeneral(key: String, value: Any) {
        _preferences.update {
            when (key) {
                "language" -> it.copy(language = value as String)
                "enableNotifications" -> it.copy(enableNotifications = value as Boolean)
                "enableBackgroundPlayback" -> it.copy(enableBackgroundPlayback = value as Boolean)
                "enablePictureInPicture" -> it.copy(enablePictureInPicture = value as Boolean)
                "confirmExit" -> it.copy(confirmExit = value as Boolean)
                "enableDeveloperOptions" -> it.copy(enableDeveloperOptions = value as Boolean)
                else -> it
            }
        }
        saveToDataStore()
    }

    override suspend fun resetToDefaults() {
        _preferences.value = AppPreferences()
        clearDataStore()
    }

    private suspend fun saveToDataStore() {
        // Persist to DataStore
    }

    private suspend fun clearDataStore() {
        // Clear DataStore
    }
}

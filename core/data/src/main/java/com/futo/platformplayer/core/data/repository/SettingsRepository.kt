package com.futo.platformplayer.core.data.repository

import com.futo.platformplayer.core.datastore.model.AppPreferences
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {

    val preferences: StateFlow<AppPreferences>

    suspend fun updateAppearance(prefs: com.futo.platformplayer.core.datastore.model.AppearancePreferences)
    suspend fun updatePlayback(prefs: com.futo.platformplayer.core.datastore.model.PlaybackPreferences)
    suspend fun updateGeneral(key: String, value: Any)
    suspend fun resetToDefaults()
}

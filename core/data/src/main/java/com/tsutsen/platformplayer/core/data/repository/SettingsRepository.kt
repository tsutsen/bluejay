package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {

    val preferences: StateFlow<AppPreferences>

    suspend fun updateAppearance(prefs: com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences)
    suspend fun updatePlayback(prefs: com.tsutsen.platformplayer.core.datastore.model.PlaybackPreferences)
    suspend fun updateGeneral(key: String, value: Any)
    suspend fun resetToDefaults()
}

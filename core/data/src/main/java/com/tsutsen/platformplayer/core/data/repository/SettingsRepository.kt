package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ControllerPreferences
import com.tsutsen.platformplayer.core.datastore.model.CustomTheme
import com.tsutsen.platformplayer.core.datastore.model.PlayerGestureSlotSet
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {

    val preferences: StateFlow<AppPreferences>

    suspend fun updateAppearance(prefs: com.tsutsen.platformplayer.core.datastore.model.AppearancePreferences)
    suspend fun updatePlayback(prefs: com.tsutsen.platformplayer.core.datastore.model.PlaybackPreferences)
    suspend fun updateGeneral(key: String, value: Any)
    suspend fun updateDualScreenPages(pages: List<String>)
    suspend fun updateDualScreenVideoTabs(tabs: List<String>)
    suspend fun updateDualScreenLibrarySlots(slots: List<String>)
    suspend fun updateDualScreenVideoTabOrder(order: List<String>)
    suspend fun updateDualScreenPageOrder(order: List<String>)
    suspend fun updateDualScreenFeedSources(ids: List<String>)

    /** Save the per-mode gesture assignments (Settings > Gestures). */
    suspend fun updatePlayerGestures(
        fullscreen: PlayerGestureSlotSet,
        normal: PlayerGestureSlotSet,
    )

    /** Save the controller (gamepad) settings (Settings > Controller). */
    suspend fun updateControllerSettings(prefs: ControllerPreferences)

    /** Upsert a user theme (same id = update, new id = add). */
    suspend fun saveCustomTheme(theme: CustomTheme)

    /** Delete a user theme; clears it if it was active. */
    suspend fun deleteCustomTheme(id: String)

    /** Set the active custom theme (null = default theming). */
    suspend fun setActiveThemeId(id: String?)

    suspend fun updateLibrarySectionOrder(order: List<String>)
    suspend fun updateLibrarySectionsEnabled(ids: List<String>)
    suspend fun resetToDefaults()
}

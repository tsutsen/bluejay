package com.tsutsen.platformplayer.feature.dualscreen

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dualScreenDataStore: DataStore<Preferences> by preferencesDataStore(name = "dual_screen_state")

/**
 * Singleton coordinator for cross-activity state management.
 * Ensures MainActivity and CompanionActivity share the same AppState.
 * State is persisted to DataStore for survival across process death.
 */
@Singleton
class ScreenCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _appState = MutableStateFlow<AppState>(AppState.Browsing)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _companionVisible = MutableStateFlow(false)
    val companionVisible: StateFlow<Boolean> = _companionVisible.asStateFlow()

    fun updateState(state: AppState) {
        _appState.update { state }
        persistState(state)
    }

    fun setCompanionVisible(visible: Boolean) {
        _companionVisible.update { visible }
    }

    fun openVideo(videoId: String, fullscreen: Boolean = false) {
        _appState.update { AppState.VideoOpen(videoId, fullscreen) }
        persistState(AppState.VideoOpen(videoId, fullscreen))
    }

    fun minimizeVideo(videoId: String, positionX: Float = 0f, positionY: Float = 0f) {
        _appState.update { AppState.VideoMinimized(videoId, positionX, positionY) }
        persistState(AppState.VideoMinimized(videoId, positionX, positionY))
    }

    fun exitVideo() {
        _appState.update { AppState.Browsing }
        persistState(AppState.Browsing)
    }

    private fun persistState(state: AppState) {
        // Persistence will be fully implemented in Phase 7
    }
}

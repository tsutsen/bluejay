package com.tsutsen.platformplayer.di

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.tsutsen.platformplayer.BuildConfig
import com.tsutsen.platformplayer.UpdateDownloadService
import com.tsutsen.platformplayer.api.http.ManagedHttpClient
import com.tsutsen.platformplayer.core.data.repository.UpdateCheckState
import com.tsutsen.platformplayer.core.data.repository.UpdateRepository
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StateUpdate
import com.tsutsen.platformplayer.states.UpdateUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngineUpdateRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : UpdateRepository {

    private val _checkState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    override val checkState: StateFlow<UpdateCheckState> = _checkState.asStateFlow()

    /** The release currently offered (tag, versionCode); survives cancel → re-offer. */
    @Volatile
    private var lastOffer: Pair<String, Int>? = null

    override val currentVersionName: String
        get() =
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "unknown"

    init {
        // Mirror the download service's UI state so a notification cancel
        // (StateUpdate.clearUi → NONE) reverts us to the offered state
        // instead of being stuck on "downloading…".
        CoroutineScope(Dispatchers.IO).launch {
            StateUpdate.instance.onUiChanged.subscribe {
                when (StateUpdate.instance.uiState) {
                    UpdateUiState.DOWNLOADING ->
                        lastOffer?.let { (tag, code) -> _checkState.value = UpdateCheckState.Downloading(tag, code) }
                    UpdateUiState.READY ->
                        lastOffer?.let { (tag, _) -> _checkState.value = UpdateCheckState.Ready(tag) }
                    UpdateUiState.FAILED ->
                        _checkState.value = UpdateCheckState.Failed(StateUpdate.instance.uiError ?: "Download failed")
                    UpdateUiState.NONE -> {
                        val offer = lastOffer
                        _checkState.value =
                            if (offer != null) UpdateCheckState.Available(offer.first, offer.second)
                            else UpdateCheckState.Idle
                    }
                    UpdateUiState.AVAILABLE -> Unit // set by checkForUpdates itself
                }
            }
        }
    }

    override fun checkForUpdates() {
        if (_checkState.value is UpdateCheckState.Checking) return
        _checkState.value = UpdateCheckState.Checking
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val release = StateUpdate.fetchLatestRelease(ManagedHttpClient.shared)
                if (release == null) {
                    _checkState.value = UpdateCheckState.Failed("Could not reach GitHub releases")
                    return@launch
                }
                Logger.i(TAG, "Manual check: current=${BuildConfig.VERSION_CODE} latest=${release.versionCode} (${release.tag})")
                if (release.versionCode > BuildConfig.VERSION_CODE) {
                    // Offer only — the user confirms via downloadUpdate().
                    lastOffer = release.tag to release.versionCode
                    StateUpdate.instance.setUiAvailable(release.versionCode)
                    _checkState.value = UpdateCheckState.Available(release.tag, release.versionCode)
                } else {
                    lastOffer = null
                    _checkState.value = UpdateCheckState.UpToDate(currentVersionName)
                }
            } catch (t: Throwable) {
                Logger.w(TAG, "Manual update check failed", t)
                _checkState.value = UpdateCheckState.Failed(t.message ?: "Unknown error")
            }
        }
    }

    override fun downloadUpdate() {
        val offer = lastOffer ?: return
        val (tag, versionCode) = offer
        _checkState.value = UpdateCheckState.Downloading(tag, versionCode)
        // Same path as UpdateCheckWorker: download in the foreground service.
        val intent =
            Intent(context, UpdateDownloadService::class.java).apply {
                putExtra(UpdateDownloadService.EXTRA_VERSION, versionCode)
            }
        ContextCompat.startForegroundService(context, intent)
    }

    companion object {
        private const val TAG = "EngineUpdateRepository"
    }
}

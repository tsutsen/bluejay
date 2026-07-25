package com.tsutsen.platformplayer

import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.states.StateApp
import com.tsutsen.platformplayer.states.StateDeveloper
import kotlinx.serialization.Serializable

/**
 * Minimal stub SettingsDev class.
 * The original SettingsDev.kt was heavily dependent on XML-based settings UI
 * components that have been deleted during the Compose migration.
 * This stub provides the minimal API needed for the app to compile.
 */
@Serializable
class SettingsDev {
    var developerMode: Boolean = false
    
    val networking = NetworkingSettings()
    val browsing = BrowsingSettings()
    
    val devServerSettings = DeveloperServerFields()
    val experimentalSettings = ExperimentalFields()
    val cache = Cache()
    
    @Serializable
    class NetworkingSettings {
        var allowAllCertificates: Boolean = false
    }
    
    @Serializable
    class BrowsingSettings {
        var useDownloadedCABundle: Boolean = false
    }
    
    @Serializable
    class DeveloperServerFields {
        var devServerOnBoot: Boolean = false
        
        fun startServer() {
            StateDeveloper.instance.runServer()
            UIDialogs.toast(StateApp.instance.contextOrNull, "Dev Started")
        }
    }
    
    @Serializable
    class ExperimentalFields {
        var backgroundSubscriptionFetching: Boolean = false
    }
    
    @Serializable
    class Cache {
        fun subscriptionsCache5000() {
            Logger.i("SettingsDev", "Started caching 5000 sub items")
            UIDialogs.toast(StateApp.instance.contextOrNull, "Started caching 5000 sub items")
        }
    }
    
    companion object {
        @Volatile
        private var _instance: SettingsDev? = null
        
        val instance: SettingsDev
            get() = _instance ?: synchronized(this) {
                _instance ?: SettingsDev().also { _instance = it }
            }
    }
}

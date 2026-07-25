package com.futo.platformplayer.api.media.platforms.js

import com.futo.platformplayer.R
import com.futo.platformplayer.constructs.Event0
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.AnnouncementType
import com.futo.platformplayer.states.StateAnnouncement
import com.futo.platformplayer.states.StateApp
import com.futo.platformplayer.states.StateHistory
import com.futo.platformplayer.states.StatePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
class SourcePluginDescriptor {
    val config: SourcePluginConfig;
    var settings: HashMap<String,String?> = hashMapOf();

    var appSettings: AppPluginSettings = AppPluginSettings();

    var authEncrypted: String? = null
        private set;
    var captchaEncrypted: String? = null
        private set;

    val flags: List<String>;

    @kotlinx.serialization.Transient
    val onAuthChanged = Event0();
    @kotlinx.serialization.Transient
    val onCaptchaChanged = Event0();

    constructor(config :SourcePluginConfig, authEncrypted: String? = null, captchaEncrypted: String? = null, settings: HashMap<String, String?>? = null) {
        this.config = config;
        this.authEncrypted = authEncrypted;
        this.captchaEncrypted = captchaEncrypted;
        this.flags = listOf();
        this.settings = settings ?: hashMapOf();
    }
    constructor(config :SourcePluginConfig, authEncrypted: String? = null, captchaEncrypted: String? = null, flags: List<String>,  settings: HashMap<String, String?>? = null) {
        this.config = config;
        this.authEncrypted = authEncrypted;
        this.captchaEncrypted = captchaEncrypted;
        this.flags = flags;
        this.settings = settings ?: hashMapOf();
    }

    fun getSettingsWithDefaults(): HashMap<String, String?> {
        val map = HashMap(settings);
        for(field in config.settings) {
            if(!map.containsKey(field.variableOrName) || map[field.variableOrName] == null)
                map.put(field.variableOrName, field.default);
        }
        return map;
    }

    fun updateCaptcha(captcha: SourceCaptchaData?) {
        captchaEncrypted = captcha?.toEncrypted();
        onCaptchaChanged.emit();
    }
    fun getCaptchaData(): SourceCaptchaData? {
        try {
            return SourceCaptchaData.fromEncrypted(captchaEncrypted);
        }
        catch(ex: Throwable) {
            Logger.e("SourcePluginDescriptor", "Captcha decode failed, disabling auth.", ex);
            StateAnnouncement.instance.registerAnnouncement("CAP_BROKEN_" + config.id,
                "Captcha corrupted for plugin [${config.name}]",
                "Something went wrong in the stored captcha, you'll have to login again", AnnouncementType.SESSION);
            return null;
        }
    }

    fun updateAuth(str: SourceAuth?) {
        authEncrypted = str?.toEncrypted();
        onAuthChanged.emit();
    }
    fun getAuth(): SourceAuth? {
        try {
            return SourceAuth.fromEncrypted(authEncrypted);
        }
        catch(ex: Throwable) {
            Logger.e("SourcePluginDescriptor", "Authentication decode failed, disabling auth.", ex);
            StateAnnouncement.instance.registerAnnouncement("AUTH_BROKEN_" + config.id,
                "Authentication corrupted for plugin [${config.name}]",
                "Something went wrong in the stored authentication, you'll have to login again", AnnouncementType.SESSION);
            return null;
        }
    }

    @Serializable
    class AppPluginSettings {

        var checkForUpdates: Boolean = true;
        var automaticUpdate: Boolean = true;

        var tabEnabled = TabEnabled();
        @Serializable
        class TabEnabled {
            var enableHome: Boolean? = null;

            var enableSearch: Boolean? = null;

            var enableShorts: Boolean? = null;
        }

        var sync = Sync();
        @Serializable
        class Sync {
            var enableHistorySync: Boolean? = null;
        }

        var rateLimit = RateLimit();
        @Serializable
        class RateLimit {
            var rateLimitSubs: Int = 0;

            fun getSubRateLimit(): Int {
                return when(rateLimitSubs) {
                    0 -> -1
                    1 -> 25
                    2 -> 50
                    3 -> 75
                    4 -> 100
                    5 -> 125
                    6 -> 150
                    7 -> 200
                    else -> -1
                }
            }

        }



        var allowDeveloperSubmit: Boolean = false;


        fun loadDefaults(config: SourcePluginConfig) {
            if(tabEnabled.enableHome == null)
                tabEnabled.enableHome = config.enableInHome
            if(tabEnabled.enableSearch == null)
                tabEnabled.enableSearch = config.enableInSearch
            if(tabEnabled.enableShorts == null)
                tabEnabled.enableShorts = config.enableInShorts
        }
    }

    companion object {
        const val FLAG_EMBEDDED = "EMBEDDED";
    }
}

package com.tsutsen.platformplayer.backup

import android.net.Uri
import com.tsutsen.platformplayer.Settings
import com.tsutsen.platformplayer.core.database.dao.SavedVideoDao
import com.tsutsen.platformplayer.readBytes
import com.tsutsen.platformplayer.feature.settings.impl.BackupProvider
import com.tsutsen.platformplayer.feature.settings.impl.BackupSummary
import com.tsutsen.platformplayer.states.StateApp
import com.tsutsen.platformplayer.states.StateBackup
import com.tsutsen.platformplayer.states.StateSubscriptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Backup & restore" seam on top of the app's existing backup engine
 * ([StateBackup]): export writes the engine's zip via SAF, restore feeds a
 * picked zip back into the engine's import flow (which shows its own
 * selection + progress UI). [summary] computes the tile counts on demand —
 * a single IO pass, no background work.
 */
@Singleton
class AppBackupProvider @Inject constructor(private val savedVideoDao: SavedVideoDao) :
    BackupProvider {

    override suspend fun summary(): BackupSummary = withContext(Dispatchers.IO) {
        val subscriptions = StateSubscriptions.instance.getSubscriptions()
        val sources =
            subscriptions
                .mapNotNull { sub ->
                    runCatching {
                        Uri.parse(sub.channel.url).host?.substringAfterLast('.')
                    }.getOrNull()
                }
                .distinct()
                .size
        // The save-type lists (Watch Later, Liked, Disliked, Favourites)
        // live in Room, not in the legacy playlist stores.
        BackupSummary(
            subscriptions = subscriptions.size,
            sources = sources,
            savedVideos = savedVideoDao.countAll(),
            savedPlaylists = savedVideoDao.countTypesWithVideos(),
            customSettings = countCustomSettings(),
            sections = SECTION_COUNT,
        )
    }

    override fun exportBackup() {
        val activity = StateApp.instance.activity ?: return
        StateBackup.saveExternalBackup(activity)
    }

    override fun importBackup() {
        val activity = StateApp.instance.activity ?: return
        val context = StateApp.instance.contextOrNull ?: return
        StateApp.instance.requestFileReadAccess(activity, null, "application/zip") { doc ->
            val bytes = doc?.readBytes(context) ?: return@requestFileReadAccess
            StateBackup.importZipBytes(context, StateApp.instance.scope, bytes)
        }
    }

    // How many leaf settings values differ from a fresh default Settings.
    // dualScreenVideoTabs is skipped: Settings.load() normalises it at
    // startup, so it would count as "custom" on every install.
    private fun countCustomSettings(): Int {
        val current =
            runCatching { Json.parseToJsonElement(Settings.instance.encode()).jsonObject }
                .getOrNull()
            ?: return 0
        val defaults =
            runCatching { Json.parseToJsonElement(Settings().encode()).jsonObject }.getOrNull()
                ?: return 0
        return diffLeaves(current, defaults)
    }

    private fun diffLeaves(current: JsonElement, defaults: JsonElement): Int =
        when {
            current is JsonObject && defaults is JsonObject ->
                current.keys.sumOf { key ->
                    if (key == "dualScreenVideoTabs") 0
                    else diffLeaves(current[key] ?: JsonNull, defaults[key] ?: JsonNull)
                }

            current == defaults -> 0
            else -> 1
        }

    private companion object {
        // Sections on the settings master page (Appearance, Content,
        // Playback, Gestures, Controller, Dual screen, Backup & restore,
        // About).
        private const val SECTION_COUNT = 8
    }
}

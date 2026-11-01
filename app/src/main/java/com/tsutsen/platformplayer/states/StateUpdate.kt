package com.tsutsen.platformplayer.states

import android.content.Context
import android.os.Build
import com.tsutsen.platformplayer.BuildConfig
import com.tsutsen.platformplayer.UIDialogs
import com.tsutsen.platformplayer.api.http.ManagedHttpClient
import com.tsutsen.platformplayer.constructs.Event0
import com.tsutsen.platformplayer.copyToOutputStream
import com.tsutsen.platformplayer.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

enum class UpdateUiState { NONE, AVAILABLE, DOWNLOADING, READY, FAILED }

class StateUpdate {

    @Volatile var uiState: UpdateUiState = UpdateUiState.NONE
        private set
    @Volatile var uiVersion: Int = 0
        private set
    @Volatile var uiProgress: Int = 0
        private set
    @Volatile var uiIndeterminate: Boolean = true
        private set
    @Volatile var uiApkFile: File? = null
        private set
    @Volatile var uiError: String? = null
        private set

    val onUiChanged = Event0()

    fun setUiAvailable(version: Int) {
        uiState = UpdateUiState.AVAILABLE
        uiVersion = version
        uiError = null
        onUiChanged.emit()
    }

    fun setUiDownloading(version: Int, progress: Int, indeterminate: Boolean) {
        uiState = UpdateUiState.DOWNLOADING
        uiVersion = version
        uiProgress = progress
        uiIndeterminate = indeterminate
        uiError = null
        onUiChanged.emit()
    }

    fun setUiReady(version: Int, apkFile: File) {
        uiState = UpdateUiState.READY
        uiVersion = version
        uiApkFile = apkFile
        uiError = null
        onUiChanged.emit()
    }

    fun setUiFailed(version: Int, error: String?) {
        uiState = UpdateUiState.FAILED
        uiVersion = version
        uiError = error
        onUiChanged.emit()
    }

    fun clearUi() {
        uiState = UpdateUiState.NONE
        uiVersion = 0
        uiProgress = 0
        uiIndeterminate = true
        uiApkFile = null
        uiError = null
        onUiChanged.emit()
    }

    fun seedUiFromDisk(context: Context) {
        if (uiState != UpdateUiState.NONE) return
        try {
            val dir = File(context.filesDir, "updates")
            if (!dir.exists()) return
            val abi = try { DESIRED_ABI } catch (t: Throwable) { return }
            val prefix = "app-$abi-"
            val suffix = ".apk"
            val candidates = dir.listFiles { f ->
                f.isFile && f.name.startsWith(prefix) && f.name.endsWith(suffix)
            } ?: return
            var bestVersion = BuildConfig.VERSION_CODE
            var bestFile: File? = null
            for (f in candidates) {
                val versionStr = f.name.removePrefix(prefix).removeSuffix(suffix)
                val v = versionStr.toIntOrNull() ?: continue
                if (v > bestVersion && f.length() > 0L) {
                    bestVersion = v
                    bestFile = f
                }
            }
            val ready = bestFile
            if (ready != null) {
                Logger.i(TAG, "Seeding UI ready from disk: v=$bestVersion file=${ready.absolutePath}")
                setUiReady(bestVersion, ready)
            }
        } catch (t: Throwable) {
            Logger.w(TAG, "Failed to seed UI from disk", t)
        }
    }

    suspend fun checkForUpdates(context: Context, showUpToDateToast: Boolean, hideExceptionButtons: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val client = ManagedHttpClient.shared;
            val latestVersion = downloadVersionCode(client);

            if (latestVersion != null) {
                val currentVersion = BuildConfig.VERSION_CODE;
                Logger.i(TAG, "Current version ${currentVersion} latest version ${latestVersion}.");

                if (latestVersion > currentVersion) {
                    val label = latestGithubRelease?.tag?.removePrefix("v") ?: latestVersion.toString();
                    withContext(Dispatchers.Main) {
                        try {
                            UIDialogs.showUpdateAvailableDialog(context, label, hideExceptionButtons);
                        } catch (e: Throwable) {
                            UIDialogs.toast(context, "Failed to show update dialog");
                            Logger.w(TAG, "Error occurred in update dialog.");
                        }
                    }
                } else {
                    if (showUpToDateToast) {
                        withContext(Dispatchers.Main) {
                            UIDialogs.toast(context, "Already on latest version");
                        }
                    }
                }
            } else {
                Logger.w(TAG, "Failed to retrieve version from version URL.");

                withContext(Dispatchers.Main) {
                    UIDialogs.toast(context, "Failed to retrieve version");
                }
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to check for updates.", e);
            android.util.Log.e(TAG, "Failed to check for updates.", e);
            withContext(Dispatchers.Main) {
                UIDialogs.toast(context, "Failed to check for updates\n" + e.message);
            }
        }
    }

    /** Fetches the latest GitHub release and returns its versionCode (semver tag → code). */
    fun downloadVersionCode(client: ManagedHttpClient): Int? {
        val release = fetchLatestRelease(client) ?: return null;
        latestGithubRelease = release;
        return release.versionCode;
    }

    fun downloadChangelog(client: ManagedHttpClient, version: Int): String? {
        return fetchReleaseByVersion(client, version)?.changelog;
    }

    companion object {
        private val TAG = "StateUpdate";

        private var _instance : StateUpdate? = null;
        val instance : StateUpdate
            get(){
            if(_instance == null)
                _instance = StateUpdate();
            return _instance!!;
        };

        val APP_SUPPORTED_ABIS = arrayOf("x86", "x86_64", "arm64-v8a", "armeabi-v7a");
        val DESIRED_ABI: String get() {
            for (i in 0 until Build.SUPPORTED_ABIS.size) {
                val abi = Build.SUPPORTED_ABIS[i];
                if (APP_SUPPORTED_ABIS.contains(abi)) {
                    return abi;
                }
            }

            throw Exception("App is not compatible. Supported ABIS: ${Build.SUPPORTED_ABIS.joinToString()}}.");
        };
        /** Latest GitHub release fetched by the last check — reused for APK URL/changelog. */
        @Volatile
        var latestGithubRelease: GithubRelease? = null
            private set

        const val GITHUB_REPO = "tsutsen/bluejay"
        private val GITHUB_API = "https://api.github.com/repos/$GITHUB_REPO/releases"
        private fun githubHeaders(): MutableMap<String, String> = mutableMapOf(
            "User-Agent" to "bluejay-android",
            "Accept" to "application/vnd.github+json",
        )

        /** A parsed GitHub release. */
        class GithubRelease(
            val tag: String,
            val versionCode: Int,
            val name: String?,
            val changelog: String?,
            val apkUrl: String?,
            val apkSize: Long,
        )

        /** Semver tag (vX.Y.Z) → versionCode, same formula as app/build.gradle. */
        fun versionCodeFromTag(tag: String): Int {
            val semver = tag.removePrefix("v").split('-')[0].split('.')
            return if (semver.size == 3 && semver.all { it.toIntOrNull() != null }) {
                semver[0].toInt() * 1_000_000 + semver[1].toInt() * 1_000 + semver[2].toInt()
            } else {
                1
            }
        }

        /** Picks the best APK asset for this device: ABI match beats universal. */
        private fun pickApkAsset(assets: JSONArray): Pair<String, Long>? {
            val abi = try { DESIRED_ABI } catch (t: Throwable) { return null }
            var best: Pair<String, Long>? = null
            var bestScore = 0
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                if (!name.endsWith(".apk")) continue
                val url = a.optString("browser_download_url").takeIf { it.isNotEmpty() } ?: continue
                var score = 0
                if (name.contains(abi)) score += 4
                if (name.contains("universal")) score += 1
                if (name.contains("stable")) score += 1
                if (score > bestScore) {
                    bestScore = score
                    best = url to a.optLong("size", 0L)
                }
            }
            return best
        }

        private fun parseRelease(json: JSONObject): GithubRelease? {
            val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
            val apk = pickApkAsset(json.optJSONArray("assets") ?: JSONArray())
            return GithubRelease(
                tag = tag,
                versionCode = versionCodeFromTag(tag),
                name = json.optString("name").takeIf { it.isNotBlank() },
                changelog = json.optString("body").takeIf { it.isNotBlank() },
                apkUrl = apk?.first,
                apkSize = apk?.second ?: 0L,
            )
        }

        /** Latest release: /releases/latest for stable builds, newest (incl. prereleases) for unstable. */
        fun fetchLatestRelease(client: ManagedHttpClient): GithubRelease? {
            val unstable = BuildConfig.IS_UNSTABLE_BUILD
            val url = if (unstable) "$GITHUB_API?per_page=1" else "$GITHUB_API/latest"
            val response = client.get(url, githubHeaders())
            if (!response.isOk || response.body == null) return null
            val text = response.body.string()
            return try {
                if (unstable) {
                    val arr = JSONArray(text)
                    if (arr.length() > 0) parseRelease(arr.getJSONObject(0)) else null
                } else {
                    parseRelease(JSONObject(text))
                }
            } catch (t: Throwable) {
                Logger.w(TAG, "Failed to parse GitHub release", t)
                null
            }
        }

        /** Finds a release by versionCode: cached latest first, then the recent releases list. */
        fun fetchReleaseByVersion(client: ManagedHttpClient, version: Int): GithubRelease? {
            latestGithubRelease?.let { if (it.versionCode == version) return it }
            val response = client.get("$GITHUB_API?per_page=100", githubHeaders())
            if (!response.isOk || response.body == null) return null
            return try {
                val arr = JSONArray(response.body.string())
                for (i in 0 until arr.length()) {
                    val r = parseRelease(arr.optJSONObject(i) ?: continue)
                    if (r != null && r.versionCode == version) return r
                }
                null
            } catch (t: Throwable) {
                Logger.w(TAG, "Failed to parse GitHub releases list", t)
                null
            }
        }

        /** APK download URL from the GitHub release assets, or null if no compatible asset. */
        fun getApkUrl(client: ManagedHttpClient, version: Int): String? {
            return fetchReleaseByVersion(client, version)?.apkUrl
        }

        fun getApkFile(context: Context, version: Int): File {
            val dir = File(context.filesDir, "updates");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            val result = File(dir, "app-${DESIRED_ABI}-${version}.apk");
            //if(result.exists())
            //    result.delete();
            return result;
        }

        fun getPartialApkFile(context: Context, version: Int): File {
            val dir = File(context.filesDir, "updates");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            val result = File(dir, "app-${DESIRED_ABI}-${version}.apk.part");
            //if(result.exists())
            //    result.delete();
            return result;
        }

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}

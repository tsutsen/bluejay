package com.tsutsen.platformplayer.feature.settings.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.tsutsen.platformplayer.core.datastore.model.CustomTheme
import java.io.File
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Theme export/import (Settings > Appearance > Custom themes): a custom theme
 * round-trips through the same [CustomTheme] serializer the settings JSON uses,
 * wrapped in a small envelope so files are recognizable and versioned.
 */
@Serializable
private data class ThemeTransferEnvelope(
    /** Always [THEME_TYPE]; rejected on import if it differs. */
    @SerialName("type") val type: String = THEME_TYPE,
    /** Envelope version; only 1 exists today. */
    @SerialName("version") val version: Int = 1,
    /** The exported theme, exactly as it is persisted. */
    @SerialName("theme") val theme: CustomTheme,
)

private const val THEME_TYPE = "bluejay-theme"
private const val THEME_VERSION = 1

private val themeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Serialize a theme into its shareable JSON envelope. */
internal fun encodeThemeEnvelope(theme: CustomTheme): String =
    themeJson.encodeToString(ThemeTransferEnvelope(theme = theme))

/**
 * Parse an exported theme from JSON. Accepts the enveloped format and, as a
 * fallback, a bare [CustomTheme] object (round-trip of older/manual exports).
 * Returns null if the payload is malformed, the envelope type is unknown, or
 * the theme itself is not usable (blank name).
 */
internal fun decodeImportedTheme(text: String): CustomTheme? =
    runCatching {
        val root = themeJson.parseToJsonElement(text)
        require(root is JsonObject)
        // Envelope validation: unknown type or future version is rejected.
        val type = root["type"]?.jsonPrimitive?.content
        val version = root["version"]?.jsonPrimitive?.content?.toIntOrNull()
        if (type != null && type != THEME_TYPE) return null
        if (version != null && version > THEME_VERSION) return null
        val payload = root["theme"] ?: root
        val theme = themeJson.decodeFromJsonElement<CustomTheme>(payload)
        if (theme.name.isBlank()) return null
        theme
    }.getOrNull()

/**
 * Read a theme from a picked document: decode, validate, and give it a fresh
 * id if it collides with an existing one (importing the same file twice keeps
 * both copies).
 */
internal fun readImportedTheme(context: Context, uri: Uri, existingIds: Set<String>): CustomTheme? {
    val text =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().decodeToString()
        } ?: return null
    val theme = decodeImportedTheme(text) ?: return null
    var id = theme.id
    while (id in existingIds) id = UUID.randomUUID().toString()
    return theme.copy(id = id)
}

/**
 * Share a theme as a JSON file: written to the cache "shares" dir (same spot
 * the library backup exporter uses), exposed through the app's FileProvider
 * and handed to the system share sheet.
 */
internal fun exportTheme(context: Context, theme: CustomTheme) {
    val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
    val safeName = theme.name.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "theme" }
    val file = File(shareDir, "bluejay-theme-$safeName.json")
    file.writeText(encodeThemeEnvelope(theme))

    // The FileProvider authority is a build-type resValue in the app module,
    // so resolve it from the merged manifest at runtime instead of hardcoding.
    val authority =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS)
            .providers
            ?.firstOrNull { it.name == FileProvider::class.java.name }
            ?.authority
            ?: return

    val uri = FileProvider.getUriForFile(context, authority, file)
    val intent = ShareCompat.IntentBuilder(context).setType("application/json").setStream(uri).intent
    context.startActivity(Intent.createChooser(intent, null))
}

package com.tsutsen.platformplayer.feature.player.impl

/**
 * Minimal YAML parser for our constrained gesture spec format.
 *
 * Supported structure:
 *   MODE:
 *     ZONE:
 *       GESTURE: action
 *
 * No external dependencies — just indentation-based line parsing.
 */
object GestureSpecParser {

    /** Parse multi-document YAML (sections separated by --- or comments) into a map of MODE → ZONE → GESTURE → action. */
    fun parse(yaml: String): Map<String, Map<String, Map<String, String>>> {
        val result = mutableMapOf<String, MutableMap<String, MutableMap<String, String>>>()
        var currentMode: MutableMap<String, MutableMap<String, String>>? = null

        for (line in yaml.lineSequence()) {
            val trimmed = line.trim()

            // Skip empty lines, --- separators, and full-line comments
            if (trimmed.isEmpty() || trimmed == "---" || trimmed.startsWith("#")) continue

            if (trimmed.endsWith(":") && !line.startsWith(" ") && !line.startsWith("\t")) {
                // Top-level key (MODE)
                currentMode = mutableMapOf()
                result[trimmed.dropLast(1).trim()] = currentMode
            } else if (currentMode != null) {
                val indent = line.takeWhile { it == ' ' }.length
                if (indent >= 2 && indent < 6 && trimmed.endsWith(":")) {
                    // Zone key (indented 2+ spaces, ends with colon, not deep enough for gesture)
                    currentMode[trimmed.dropLast(1).trim()] = mutableMapOf()
                } else if (indent >= 4 && trimmed.contains(":")) {
                    // Gesture: action (indented 4+ spaces)
                    val colonIdx = trimmed.indexOf(":")
                    val key = trimmed.substring(0, colonIdx).trim()
                    val value = trimmed.substring(colonIdx + 1).trim()
                    // Find the last zone added (gestures belong to most recent zone)
                    val zone = currentMode.keys.lastOrNull()
                    if (zone != null) {
                        currentMode[zone]!![key] = value
                    }
                }
            }
        }

        return result
    }
}

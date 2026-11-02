package com.tsutsen.platformplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Read-migration contract for persisted gesture assignments: the legacy flat
 * action ids (written before GestureAction became the single vocabulary)
 * must keep mapping to the same action, and the current enum-name format
 * must round-trip.
 */
class GestureActionMigrationTest {
    /** Every legacy flat id (old PlayerGestures constants) → its action. */
    private val legacyIds =
        mapOf(
            "volume" to GestureAction.VOLUME,
            "brightness" to GestureAction.BRIGHTNESS,
            "speedup" to GestureAction.SPEEDUP,
            "speeddown" to GestureAction.SPEEDDOWN,
            "rewind_back" to GestureAction.REWIND_BACK,
            "rewind_forward" to GestureAction.REWIND_FORWARD,
            "context_menu" to GestureAction.CONTEXT_MENU,
            "morph_floating" to GestureAction.MORPH_TO_FLOATING,
            "morph_fullscreen" to GestureAction.MORPH_TO_FULLSCREEN,
            "morph_normal" to GestureAction.MORPH_TO_NORMAL,
            "morph_vertical" to GestureAction.MORPH_VERTICAL,
            "seek_hold" to GestureAction.SEEK_HOLD,
            "play_pause" to GestureAction.PLAY_PAUSE,
            "pip" to GestureAction.PIP,
        )

    @Test
    fun everyLegacyIdMigratesToItsAction() {
        legacyIds.forEach { (id, action) ->
            assertEquals("legacy id '$id'", action, GestureAction.fromPersisted(id))
        }
    }

    @Test
    fun legacyIdSpaceAndEnumNameSpaceDoNotCollide() {
        // The persisted value space is unambiguous: no legacy id may also be
        // an enum name (and vice versa), or fromPersisted could not tell the
        // two formats apart.
        val enumNames = GestureAction.entries.mapTo(HashSet()) { it.name }
        legacyIds.keys.forEach { id ->
            check(id !in enumNames) { "legacy id '$id' collides with an enum name" }
        }
    }

    @Test
    fun enumNamesRoundTrip() {
        GestureAction.entries.forEach { action ->
            assertEquals(action, GestureAction.fromPersisted(action.name))
        }
        // NONE's name is accepted explicitly too (was the legacy "" empty id).
        assertEquals(GestureAction.NONE, GestureAction.fromPersisted("NONE"))
    }

    @Test
    fun unknownValuesFallBackToNone() {
        assertEquals(GestureAction.NONE, GestureAction.fromPersisted("garbage"))
        assertEquals(GestureAction.NONE, GestureAction.fromPersisted(""))
        assertEquals(GestureAction.NONE, GestureAction.fromPersisted(null))
    }
}

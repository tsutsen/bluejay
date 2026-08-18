package com.tsutsen.platformplayer

import com.tsutsen.platformplayer.helpers.FileHelper.Companion.sanitizeFileName
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExtensionsFileTests {
    @Test
    fun test_sanitizeFileName1() {
        // Legacy contract (shared with Grayjay): with allowSpace=false every
        // space/illegal char becomes "_"; allowSpace=true collapses to one space.
        assertEquals("Hello_world", "Hello world".sanitizeFileName())
        assertEquals("Hello world", "Hello world".sanitizeFileName(true))
        assertEquals("漫漫听-点唱-_公主冠", "漫漫听-点唱- 公主冠".sanitizeFileName())
        assertEquals("食べ_る", "食べ る".sanitizeFileName()); // Hiragana
        assertEquals("テレ_ビ", "テレ ビ".sanitizeFileName()); // Katakana
        assertEquals("ي_خبر", "ي خبر".sanitizeFileName()); // Arabic
        assertEquals(".._testing", "../testing".sanitizeFileName()); // Escaping
    }
}

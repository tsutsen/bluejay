package com.tsutsen.platformplayer

import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePluginConfigTest {

    @Test
    fun isAdvancedStringTrueIsParsed() {
        val json =
            """
            {"name":"t","scriptUrl":"s","settings":[
              {"name":"A","description":"d","type":"Boolean","default":"false","isAdvanced":"true"},
              {"name":"B","description":"d","type":"Boolean","default":"false"}
            ]}
            """
                .trimIndent()
        val config = SourcePluginConfig.fromJson(json)
        assertEquals(true, config.settings[0].isAdvanced)
        assertEquals(null, config.settings[1].isAdvanced)
    }
}

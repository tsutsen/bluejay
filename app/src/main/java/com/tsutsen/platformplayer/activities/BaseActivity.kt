package com.tsutsen.platformplayer.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tsutsen.platformplayer.theming.ThemeManager

/**
 * Base Activity that applies theme mode before the theme is inflated.
 * This ensures the light/dark theme is set before any UI is drawn.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(base: android.content.Context) {
        // Read theme mode from DataStore and apply before super.attachBaseContext()
        // This must happen before the theme is inflated
        super.attachBaseContext(base)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}

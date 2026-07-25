package com.tsutsen.platformplayer.activities

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Stub for InstallUpdateActivity.
 * The original InstallUpdateActivity was an XML-based activity for installing updates.
 * It has been removed during the Compose migration.
 */
object InstallUpdateActivity {
    fun createIntent(context: Context, version: Int, apkPath: String): Intent {
        return Intent(context, InstallUpdateActivity::class.java).apply {
            putExtra("version", version)
            putExtra("apk_path", apkPath)
        }
    }
}

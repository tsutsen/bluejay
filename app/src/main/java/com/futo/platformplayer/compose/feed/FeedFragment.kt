package com.futo.platformplayer.compose.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment

class FeedFragment : MainFragment() {
    override val isMainView: Boolean = true
    override val isOverlay: Boolean = false
    override val isHistory: Boolean = false
    override val hasBottomBar: Boolean = true

    @Composable
    override fun ComposeContent() {
        MaterialTheme(colorScheme = MainFragment.getComposeColorScheme()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Feed Screen (Compose)\n\nComing soon...",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

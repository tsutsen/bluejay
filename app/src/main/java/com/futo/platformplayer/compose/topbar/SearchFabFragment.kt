package com.futo.platformplayer.compose.topbar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.fragment.mainactivity.main.CreatorsFragment
import com.futo.platformplayer.fragment.mainactivity.main.LibraryFragment
import com.futo.platformplayer.fragment.mainactivity.main.LibrarySearchFragment
import com.futo.platformplayer.fragment.mainactivity.main.PlaylistFragment
import com.futo.platformplayer.fragment.mainactivity.main.PlaylistsFragment
import com.futo.platformplayer.fragment.mainactivity.main.SuggestionsFragment
import com.futo.platformplayer.fragment.mainactivity.main.SuggestionsFragmentData
import com.futo.platformplayer.models.SearchType
import com.futo.platformplayer.theming.AppearancePreferencesManager
import com.futo.platformplayer.theming.ThemeMode

class SearchFabFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(requireContext()).apply {
            setId(android.R.id.content)
            setContent {
                val context = requireContext()
                val themeMode = try {
                    val prefs = com.futo.platformplayer.theming.AppearancePreferences()
                    prefs.themeMode
                } catch (_: Exception) {
                    ThemeMode.AUTO
                }
                val isDark = when (themeMode) {
                    ThemeMode.AUTO -> {
                        context.resources.configuration.uiMode and
                                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                                android.content.res.Configuration.UI_MODE_NIGHT_YES
                    }
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    else -> false
                }
                val colorScheme = if (isDark) {
                    androidx.compose.material3.darkColorScheme(
                        primary = androidx.compose.ui.graphics.Color(0xFFBB86FC),
                        primaryContainer = androidx.compose.ui.graphics.Color(0xFF3700B3),
                        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE0B0FF),
                    )
                } else {
                    androidx.compose.material3.lightColorScheme(
                        primary = androidx.compose.ui.graphics.Color(0xFF6750A4),
                        primaryContainer = androidx.compose.ui.graphics.Color(0xFFEADDFF),
                        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF21005D),
                    )
                }
                MaterialTheme(colorScheme = colorScheme) {
                    Box(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { openSearch() },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openSearch() {
        val activity = activity as? com.futo.platformplayer.activities.MainActivity ?: return
        val current = activity.fragCurrent
        when {
            current is CreatorsFragment -> {
                activity.navigate(SuggestionsFragment(), SuggestionsFragmentData("", SearchType.CREATOR))
            }
            current is PlaylistsFragment || current is PlaylistFragment -> {
                activity.navigate(SuggestionsFragment(), SuggestionsFragmentData("", SearchType.PLAYLIST))
            }
            current is LibraryFragment -> {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                    UIDialogs.toast("Your Android version is too old for Mediastore search", true)
                } else {
                    activity.navigate(LibrarySearchFragment(), null)
                }
            }
            else -> {
                activity.navigate(SuggestionsFragment(), SuggestionsFragmentData("", SearchType.VIDEO))
            }
        }
    }

    companion object {
        fun newInstance() = SearchFabFragment().apply { }
    }
}

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.UIDialogs
import com.futo.platformplayer.fragment.mainactivity.main.CreatorsFragment
import com.futo.platformplayer.fragment.mainactivity.main.LibraryFragment
import com.futo.platformplayer.fragment.mainactivity.main.LibrarySearchFragment
import com.futo.platformplayer.fragment.mainactivity.main.PlaylistFragment
import com.futo.platformplayer.fragment.mainactivity.main.PlaylistsFragment
import com.futo.platformplayer.fragment.mainactivity.main.SuggestionsFragment
import com.futo.platformplayer.fragment.mainactivity.main.SuggestionsFragmentData
import com.futo.platformplayer.fragment.mainactivity.topbar.TopFragment
import com.futo.platformplayer.models.SearchType
import com.futo.platformplayer.theming.AppearancePreferencesManager
import com.futo.platformplayer.theming.ThemeMode

class SearchFabFragment : TopFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return ComposeView(requireContext()).apply {
            setId(android.R.id.content)
            setContent {
                val context = requireContext()
                val themeMode by rememberThemeMode()
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            FloatingActionButton(
                                onClick = { openSearch() },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
    }

    private fun openSearch() {
        val current = currentMain
        when {
            current is CreatorsFragment -> {
                navigate<SuggestionsFragment>(SuggestionsFragmentData("", SearchType.CREATOR))
            }
            current is PlaylistsFragment || current is PlaylistFragment -> {
                navigate<SuggestionsFragment>(SuggestionsFragmentData("", SearchType.PLAYLIST))
            }
            current is LibraryFragment -> {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                    UIDialogs.toast("Your Android version is too old for Mediastore search", true)
                } else {
                    navigate<LibrarySearchFragment>()
                }
            }
            else -> {
                navigate<SuggestionsFragment>(SuggestionsFragmentData("", SearchType.VIDEO))
            }
        }
    }

    companion object {
        fun newInstance() = SearchFabFragment().apply { }
    }
}

package com.futo.platformplayer.fragment.mainactivity.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.compose.theme.ComposeThemeMode
import com.futo.platformplayer.compose.theme.rememberComposeThemeState
import com.futo.platformplayer.theming.AppearancePreferences
import com.futo.platformplayer.theming.AppearancePreferencesManager
import com.futo.platformplayer.constructs.Event1
import com.futo.platformplayer.fragment.mainactivity.MainActivityFragment
import com.futo.platformplayer.fragment.mainactivity.topbar.TopFragment

abstract class MainFragment : MainActivityFragment() {
    open val isMainView: Boolean = false;
    open val isOverlay: Boolean = false;
    open val isHistory: Boolean = true;
    open val hasBottomBar: Boolean = true;
    var topBar: TopFragment? = null;

    /**
     * Set to true for Compose-based fragments.
     * When true, [createContent] is used to build the UI with automatic theming.
     * When false (default), [onCreateMainView] is used for XML-based UI.
     */
    open val isComposeMode: Boolean = false;

    val onShownEvent = Event1<MainFragment>();
    val onHideEvent = Event1<MainFragment>();
    val onCloseEvent = Event1<MainFragment>();

    private val _fragmentLock = Object();
    private var _mainView: View? = null;
    private var _lastOnShownParameters: Pair<Any?, Boolean>? = null;

    open fun onShown(parameter: Any?, isBack: Boolean) {
        onShownEvent.emit(this);

        if (_mainView == null) {
            synchronized(_fragmentLock) {
                _lastOnShownParameters = Pair(parameter, isBack);
            }
        } else {
            synchronized(_fragmentLock) {
                _lastOnShownParameters = null;
            }

            onShownWithView(parameter, isBack);
        }
    }

    open fun onShownWithView(parameter: Any?, isBack: Boolean) {

    }

    open fun onBackPressed(): Boolean {
        return false;
    }

    open fun onHide() {
        onHideEvent.emit(this);
    }

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = onCreateMainView(inflater, container, savedInstanceState);
        _mainView = view;

        val lastOnShownParameters = synchronized(_fragmentLock) {
            val value = _lastOnShownParameters;
            _lastOnShownParameters = null;
            return@synchronized value;
        };

        if (lastOnShownParameters != null)
            onShownWithView(lastOnShownParameters.first, lastOnShownParameters.second);

        return view;
    }

    final override fun onDestroyView() {
        super.onDestroyView();
        onDestroyMainView();
        _mainView = null;
    }

    /**
     * Main entry point for creating the fragment's view.
     *
     * If [isComposeMode] is true, automatically creates a ComposeView
     * wrapped in MaterialTheme with the correct light/dark color scheme.
     * Override [createContent] to provide the Compose UI.
     *
     * If [isComposeMode] is false, returns a FrameLayout placeholder.
     * XML-based fragments should override this method directly.
     */
    open fun onCreateMainView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (isComposeMode) {
            return ComposeView(requireContext()).apply {
                setId(android.R.id.content)
                setContent {
                    val colors = getComposeColorScheme()
                    MaterialTheme(colorScheme = colors, typography = Typography()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(WindowInsets.safeDrawing.asPaddingValues())
                        ) {
                            ComposeContent()
                        }
                    }
                }
            }
        }
        // Default: return empty FrameLayout for XML fragments that override this method
        return androidx.constraintlayout.widget.ConstraintLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    /**
     * Provide Compose UI content. Automatically wrapped in MaterialTheme
     * with the correct light/dark color scheme when [isComposeMode] is true.
     *
     * Usage:
     *   class MyFragment : MainFragment() {
     *       override val isComposeMode = true
     *       override fun ComposeContent() { MyScreen() }
     *   }
     */
    @Composable
    protected open fun ComposeContent() {
        // Default: do nothing. Compose fragments override this.
    }

    companion object {
        /**
         * Creates a Material3 ColorScheme based on the saved theme preference.
         * Respects the user's theme choice (light/dark/auto) from AppearancePreferences.
         */
        @Composable
        fun getComposeColorScheme(): ColorScheme {
            val context = LocalContext.current
            val themeState = rememberComposeThemeState().value
            val isDark = when (themeState.themeMode) {
                ComposeThemeMode.AUTO -> {
                    context.resources.configuration.uiMode and
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
                ComposeThemeMode.LIGHT -> false
                ComposeThemeMode.DARK -> true
            }

            return if (isDark) darkColorScheme(
                primary = Color(0xFFBB86FC),
                onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF3700B3),
                secondary = Color(0xFF03DAC6),
                onSecondary = Color(0xFF000000),
                secondaryContainer = Color(0xFF018786),
                tertiary = Color(0xFF935BA0),
                onTertiary = Color(0xFFFFD8E4),
                tertiaryContainer = Color(0xFF7D5260),
                background = Color(0xFF121212),
                onBackground = Color(0xFFE0E0E0),
                surface = Color(0xFF121212),
                onSurface = Color(0xFFE0E0E0),
                surfaceVariant = Color(0xFF49454F),
                onSurfaceVariant = Color(0xFFCAC4D0),
                error = Color(0xFFFFB4AB),
            ) else lightColorScheme(
                primary = Color(0xFF6750A4),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFEADDFF),
                secondary = Color(0xFF625B71),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFE8DEF8),
                tertiary = Color(0xFF7D5260),
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color(0xFFFFD8E4),
                background = Color(0xFFFFFBFE),
                onBackground = Color(0xFF1C1B1F),
                surface = Color(0xFFFFFBFE),
                onSurface = Color(0xFF1C1B1F),
                surfaceVariant = Color(0xFFE7E0EC),
                onSurfaceVariant = Color(0xFF49454F),
                error = Color(0xFFBA1A1A),
            )
        }
    }

    open fun onDestroyMainView() {}

    override fun onDestroy() {
        super.onDestroy();
        onShownEvent.clear();
        onHideEvent.clear();
        onCloseEvent.clear();
    }

    fun close(withNavigate: Boolean = false) {
        isValidMainActivity();
        onCloseEvent.emit(this);
        if (withNavigate)
            (activity as MainActivity).closeSegment(this);
    }

    /**
     * Navigate back to the previous screen. Called from Compose content.
     */
    fun navigateBack() {
        close(true)
    }
}

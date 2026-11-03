package com.tsutsen.platformplayer.activities

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.ThemeMode
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTheme
import com.tsutsen.platformplayer.core.designsystem.theme.ThemeEngine
import com.tsutsen.platformplayer.feature.player.impl.HistoryTracker
import com.tsutsen.platformplayer.logging.Logger
import java.lang.reflect.Proxy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


/**
 * The second-screen UI, hosted in a Presentation window on the rear display
 * — the same mechanism Cemu uses for its external display. A Presentation is
 * a window, not a task, so the AYN shell's rear-display task management (which
 * hid/evicted a companion *activity* during front-display transitions, killing
 * the second screen) never touches it.
 *
 * Three fixed pages you flick between vertically; everything inside a page
 * scrolls horizontally so the gestures never conflict:
 *  0. current video — controls, title block, comments/recommended strips
 *  1. library — up to four horizontal slots (Watch Later, Liked, ...)
 *  2. home — two horizontal rows of feed cards
 *
 * All data comes from the shared repositories, so the screen stays in sync
 * with the main app without a second ViewModel. Comments and recommendations
 * are read from the shared [PlayerState] — the main player's ViewModel fetches
 * them once and pushes them there, so nothing is fetched twice.
 */
class CompanionPresentation(
    context: Context,
    display: Display,
    private val playerRepository: PlayerRepository,
    private val libraryRepository: LibraryRepository,
    private val homeRepository: HomeRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadsRepository: com.tsutsen.platformplayer.core.data.repository.DownloadsRepository,
    private val playbackQueueRepository: com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository,
    private val liveChatRepository: com.tsutsen.platformplayer.core.data.repository.LiveChatRepository,
    private val channelRepository: ChannelRepository,
    private val historyTracker: HistoryTracker,
    private val subscriptionDao: com.tsutsen.platformplayer.core.database.dao.SubscriptionDao,
    private val onChannelClick: (String) -> Unit,
    private val onPlaylistClick: (String) -> Unit,
    private val onWatchStats: () -> Unit,
) : Presentation(context, display) {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ComposeView (not the internal AndroidComposeView) — the only
        // public Compose host view, and it's a plain View so it can live in
        // a Presentation window.
        @Suppress("DEPRECATION")
        val composeView = ComposeView(context)
        // A Presentation window carries no ViewTreeLifecycleOwner (unlike an
        // activity window), but Compose requires one in the hierarchy — bind
        // the owning activity's lifecycle (always present: MainActivity
        // creates us), falling back to a permanently-resumed owner.
        val lifecycleOwner =
            (context as? LifecycleOwner) ?: object : LifecycleOwner {
                private val registry = LifecycleRegistry(this)

                init {
                    registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                }

                override val lifecycle: Lifecycle
                    get() = registry
            }
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        attachSavedStateOwner(composeView)

        composeView.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        setContentView(composeView)
        composeView.setContent {
            // Follow the user's theme settings — same computation as
            // MainActivity, so the second screen matches the main app.
            // Gated on the real prefs: a default initial would render one
            // frame of default tokens, then the radius springs would animate
            // default→saved and overshoot into negative corner radii.
            val prefsState by settingsRepository.preferences.collectAsState(initial = null)
            val prefs = prefsState ?: return@setContent
            val darkTheme =
                when (prefs.appearance.themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.AUTO -> isSystemInDarkTheme()
                }
            val appearance = prefs.appearance
            val activeTheme =
                appearance.customThemes.firstOrNull { it.id == appearance.activeThemeId }
            val customSchemes =
                remember(activeTheme) {
                    activeTheme?.let {
                        ThemeEngine.generate(
                            it.primary,
                            it.secondary,
                            it.tertiary,
                            it.paletteStyle,
                            background = it.background,
                            contrast = it.contrast,
                        )
                    }
                }

            BluejayTheme(
                darkTheme = darkTheme,
                dynamicColor = appearance.dynamicColor,
                uiRounding = appearance.uiRounding,
                colorScheme = customSchemes?.let { if (darkTheme) it.dark else it.light },
            ) {
                CompanionContent(
                    playerRepository = playerRepository,
                    libraryRepository = libraryRepository,
                    homeRepository = homeRepository,
                    downloadsRepository = downloadsRepository,
                    playbackQueueRepository = playbackQueueRepository,
                    settingsRepository = settingsRepository,
                    liveChatRepository = liveChatRepository,
                    channelRepository = channelRepository,
                    historyTracker = historyTracker,
                    subscriptionDao = subscriptionDao,
                    companionWindow = window,
                    onChannelClick = onChannelClick,
                    onPlaylistClick = onPlaylistClick,
                    onWatchStats = onWatchStats,
                )
            }
        }
    }

    /**
     * Compose requires a ViewTreeSavedStateRegistryOwner in the hierarchy.
     * The androidx.savedstate classes ship in the APK but are not exposed to
     * this module's Kotlin compile classpath (KMP variant quirk), so the
     * owner is wired up reflectively — it is plain, stable API.
     */
    private fun attachSavedStateOwner(view: android.view.View) {
        // Direct typed API (the old reflection proxy broke on newer savedstate,
        // leaving the Presentation window without an owner -> Compose crash).
        val owner = object : androidx.savedstate.SavedStateRegistryOwner {
            private val registry = LifecycleRegistry(this)
            private val controller = androidx.savedstate.SavedStateRegistryController.create(this)
            override val savedStateRegistry get() = controller.savedStateRegistry
            override val lifecycle: Lifecycle get() = registry
            init {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                controller.performRestore(null)
            }
        }
        androidx.savedstate.ViewTreeSavedStateRegistryOwner.set(view, owner)
    }

    private companion object {
        const val TAG = "CompanionPresentation"
    }
}


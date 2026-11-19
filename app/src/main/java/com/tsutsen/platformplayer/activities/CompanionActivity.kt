package com.tsutsen.platformplayer.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import com.tsutsen.platformplayer.compose.BluejayAppThemeRoot
import com.tsutsen.platformplayer.core.data.repository.ChannelRepository
import com.tsutsen.platformplayer.core.data.repository.DownloadsRepository
import com.tsutsen.platformplayer.core.data.repository.HomeRepository
import com.tsutsen.platformplayer.core.data.repository.LibraryRepository
import com.tsutsen.platformplayer.core.data.repository.LiveChatRepository
import com.tsutsen.platformplayer.core.data.repository.PlaybackQueueRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.database.dao.SubscriptionDao
import com.tsutsen.platformplayer.feature.player.impl.HistoryTracker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The second-screen UI as a real activity on the DEFAULT display — used when
 * "Swap screens" moved MainActivity onto the non-default display. A
 * Presentation cannot show on the default display (show() throws
 * InvalidDisplayException by design), so the swapped configuration needs an
 * activity host; the unswapped path keeps using [CompanionPresentation]
 * (see the class doc there for why an activity is avoided in the normal
 * case — the rear-display task management that hid/evicted a companion
 * activity only bites on the non-default display).
 *
 * Renders the same [CompanionContent] inside [BluejayAppThemeRoot], with the
 * same Hilt-injected repositories MainActivity uses, so no data is duplicated.
 * Cross-screen navigation goes through [CompanionNavBus] instead of the
 * navigator, which lives in another activity/task.
 */
@OptIn(ExperimentalFoundationApi::class)
@AndroidEntryPoint
class CompanionActivity : ComponentActivity() {
    @Inject
    lateinit var playerRepository: PlayerRepository

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var homeRepository: HomeRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var downloadsRepository: DownloadsRepository

    @Inject
    lateinit var playbackQueueRepository: PlaybackQueueRepository

    @Inject
    lateinit var liveChatRepository: LiveChatRepository

    @Inject
    lateinit var channelRepository: ChannelRepository

    @Inject
    lateinit var historyTracker: HistoryTracker

    @Inject
    lateinit var subscriptionDao: SubscriptionDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register with the bus so MainActivity can dedupe relaunches and
        // finish us when the companion is no longer wanted; registration
        // also clears any stale requestFinish from a previous session.
        CompanionNavBus.companionActivityRef = java.lang.ref.WeakReference(this)
        // Edge-to-edge would draw the content under the status bar. The
        // bottom/main screen has no status bar, but the display hosting THIS
        // activity (default display) usually does — make the decor fit so
        // CompanionContent starts below it. (MainActivity is edge-to-edge
        // and handles insets itself; CompanionContent has no inset handling.)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            // Follow the user's theme settings — same computation as
            // MainActivity/CompanionPresentation, so all screens match.
            BluejayAppThemeRoot(settingsRepository) { prefs ->
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
                    // The activity's own window: per-display brightness and
                    // click routing work against it exactly as with the
                    // Presentation window.
                    companionWindow = window,
                    onChannelClick = { url -> CompanionNavBus.onChannelClick?.invoke(url) },
                    onPlaylistClick = { url -> CompanionNavBus.onPlaylistClick?.invoke(url) },
                    onWatchStats = { CompanionNavBus.onWatchStats?.invoke() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // MainActivity asks us to go away when the companion is no longer
        // wanted (dual-screen off, no display, app backgrounded). Also
        // self-finish if the callbacks are gone — MainActivity died and
        // nobody would route navigation anymore.
        if (CompanionNavBus.requestFinish || CompanionNavBus.onChannelClick == null) {
            finish()
        }
    }

    override fun onDestroy() {
        if (CompanionNavBus.companionActivityRef?.get() === this) {
            CompanionNavBus.companionActivityRef = null
        }
        super.onDestroy()
    }
}

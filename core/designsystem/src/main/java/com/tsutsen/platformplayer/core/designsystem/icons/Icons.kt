package com.tsutsen.platformplayer.core.designsystem.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icon style configuration for Material Symbols.
 * Supports Rounded (default), Sharp, and Outlined styles.
 */
enum class IconStyle {
    ROUNDED, SHARP, OUTLINED
}

/**
 * Get the appropriate ImageVector based on the configured icon style.
 * Currently returns standard Material Icons (Rounded).
 * Sharp and Outlined variants can be added when assets are available.
 */
object GrayjayIcons {

    val Home: ImageVector get() = Icons.Filled.Home
    val Subscriptions: ImageVector get() = Icons.Filled.Subscriptions
    val LibraryMusic: ImageVector get() = Icons.Filled.LibraryMusic
    val Notifications: ImageVector get() = Icons.Filled.Notifications
    val Search: ImageVector get() = Icons.Filled.Search
    val Settings: ImageVector get() = Icons.Filled.Settings
    val PlayArrow: ImageVector get() = Icons.Filled.PlayArrow
    val Pause: ImageVector get() = Icons.Filled.Pause
    val SkipNext: ImageVector get() = Icons.Filled.SkipNext
    val SkipPrevious: ImageVector get() = Icons.Filled.SkipPrevious
    val Fullscreen: ImageVector get() = Icons.Filled.Fullscreen
    val FullscreenExit: ImageVector get() = Icons.Filled.FullscreenExit
    val Minimize: ImageVector get() = Icons.Filled.Minimize
    val VolumeUp: ImageVector get() = Icons.Filled.VolumeUp
    val VolumeOff: ImageVector get() = Icons.Filled.VolumeOff
    val BrightnessHigh: ImageVector get() = Icons.Filled.BrightnessHigh
    val BrightnessLow: ImageVector get() = Icons.Filled.BrightnessLow
    val Speed: ImageVector get() = Icons.Filled.Speed
    val Close: ImageVector get() = Icons.Filled.Close
    val MoreVert: ImageVector get() = Icons.Filled.MoreVert
    val MoreHoriz: ImageVector get() = Icons.Filled.MoreHoriz
    val Add: ImageVector get() = Icons.Filled.Add
    val Delete: ImageVector get() = Icons.Filled.Delete
    val Edit: ImageVector get() = Icons.Filled.Edit
    val Share: ImageVector get() = Icons.Filled.Share
    val Bookmark: ImageVector get() = Icons.Filled.Bookmark
    val BookmarkBorder: ImageVector get() = Icons.Filled.BookmarkBorder
    val ThumbUp: ImageVector get() = Icons.Filled.ThumbUp
    val ThumbDown: ImageVector get() = Icons.Filled.ThumbDown
    val Sort: ImageVector get() = Icons.Filled.Sort
    val FilterList: ImageVector get() = Icons.Filled.FilterList
    val Clear: ImageVector get() = Icons.Filled.Clear
    val ChevronLeft: ImageVector get() = Icons.Filled.ChevronLeft
    val ChevronRight: ImageVector get() = Icons.Filled.ChevronRight
    val ExpandMore: ImageVector get() = Icons.Filled.ExpandMore
    val ExpandLess: ImageVector get() = Icons.Filled.ExpandLess
    val Check: ImageVector get() = Icons.Filled.Check
    val Info: ImageVector get() = Icons.Filled.Info
    val Error: ImageVector get() = Icons.Filled.Error
    val Warning: ImageVector get() = Icons.Filled.Warning
    val Refresh: ImageVector get() = Icons.Filled.Refresh
    val Download: ImageVector get() = Icons.Filled.Download
    val Upload: ImageVector get() = Icons.Filled.Upload
    val Link: ImageVector get() = Icons.Filled.Link
    val Copy: ImageVector get() = Icons.Filled.ContentCopy
    val Person: ImageVector get() = Icons.Filled.Person
    val PersonAdd: ImageVector get() = Icons.Filled.PersonAdd
    val Visibility: ImageVector get() = Icons.Filled.Visibility
    val VisibilityOff: ImageVector get() = Icons.Filled.VisibilityOff
    val Lock: ImageVector get() = Icons.Filled.Lock
    val Unlock: ImageVector get() = Icons.Filled.LockOpen
    val Palette: ImageVector get() = Icons.Filled.Palette
    val DarkMode: ImageVector get() = Icons.Filled.DarkMode
    val LightMode: ImageVector get() = Icons.Filled.LightMode
    val FontDownload: ImageVector get() = Icons.Filled.FontDownload
    val Star: ImageVector get() = Icons.Filled.Star
    val StarBorder: ImageVector get() = Icons.Filled.StarBorder
    val PlayCircle: ImageVector get() = Icons.Filled.PlayCircle
    val Stop: ImageVector get() = Icons.Filled.Stop
    val Replay10: ImageVector get() = Icons.Filled.Replay10
    val Forward10: ImageVector get() = Icons.Filled.Forward10
    val Subtitles: ImageVector get() = Icons.Filled.Subtitles
    val ClosedCaption: ImageVector get() = Icons.Filled.ClosedCaption
    val PictureInPictureAlt: ImageVector get() = Icons.Filled.PictureInPictureAlt
    val cast: ImageVector get() = Icons.Filled.Cast
    val castConnected: ImageVector get() = Icons.Filled.CastConnected
    val QueuePlayNext: ImageVector get() = Icons.Filled.QueuePlayNext
    val PlaylistAdd: ImageVector get() = Icons.Filled.PlaylistAdd
    val History: ImageVector get() = Icons.Filled.History
    val Menu: ImageVector get() = Icons.Filled.Menu
    val Dashboard: ImageVector get() = Icons.Filled.Dashboard
    val Analytics: ImageVector get() = Icons.Filled.Analytics
    val Code: ImageVector get() = Icons.Filled.Code
    val Extension: ImageVector get() = Icons.Filled.Extension
    val Help: ImageVector get() = Icons.Filled.Help
}

package com.tsutsen.platformplayer.feature.player.impl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.LiveChatEntry
import com.tsutsen.platformplayer.core.model.LiveChatUiState
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.feature.player.impl.formatViewCount
import kotlinx.coroutines.delay

/**
 * Live chat for Twitch streams: self-scrolling message list styled like the
 * comments cards (surfaceContainer cards, avatar, bold username), with
 * emote URLs rendered inline via Coil. Shared by the main player and the
 * companion (second-screen) video page.
 *
 * [listHeight] caps the message list for bounded layouts (main player);
 * null fills the parent (companion tab area).
 */
@Composable
fun LiveChatPanel(
    state: LiveChatUiState?,
    modifier: Modifier = Modifier,
    listHeight: Dp? = 380.dp,
    onLinkClick: ((String) -> Unit)? = null,
) {
    Column(modifier = modifier) {
        if (state?.viewCount != null) {
            Text(
                text = "${formatViewCount(state?.viewCount ?: 0)} watching",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (state == null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(listHeight ?: 200.dp)
                        .padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
            return
        }
        val error = state.error
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            return
        }
        ChatMessageList(
            state = state,
            modifier =
                if (listHeight != null) {
                    Modifier.fillMaxWidth().height(listHeight)
                } else {
                    Modifier.fillMaxSize()
                },
            onLinkClick = onLinkClick,
        )
    }
}

@Composable
private fun ChatMessageList(
    state: LiveChatUiState,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val listState = rememberLazyListState()

    // Follow new messages only while the user is at the bottom; someone
    // scrolled up through history must not be yanked back on every
    // message.
    val nearBottom =
        remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.totalItemsCount - 1
                last <= 0 || info.visibleItemsInfo.any { it.index >= last - 3 }
            }
        }

    // The size key keeps the scroll off the frame clock while chat is idle.
    LaunchedEffect(state.entries.size) {
        delay(120)
        if (nearBottom.value && state.entries.isNotEmpty()) {
            listState.animateScrollToItem(state.entries.size - 1)
        }
    }

    val emotes = state.emoteUrls

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // Same horizontal inset as the comment cards on the main screen
        // (and the companion, which reuses this panel).
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
    ) {
        items(state.entries, key = { it.uid }) { entry ->
            when (entry) {
                is LiveChatEntry.ChatMessage ->
                    ChatMessageCard(entry, emotes, onLinkClick)

                is LiveChatEntry.Raid ->
                    SystemChatCard(
                        text =
                            if (entry.isOutgoing) {
                                "You are raiding ${entry.targetName}"
                            } else {
                                "${entry.targetName} is raiding this channel"
                            },
                    )

                is LiveChatEntry.Donation ->
                    SystemChatCard(
                        text =
                            buildString {
                                append(entry.name)
                                append(" donated: ")
                                append(entry.message)
                            },
                    )
            }
        }
    }
}

/**
 * One live chat message as a card, matching the comments [CommentCard]:
 * surfaceContainer card, avatar circle, bold colored username, badge chip.
 */
@Composable
private fun ChatMessageCard(
    entry: LiveChatEntry.ChatMessage,
    emotes: Map<String, String>,
    onLinkClick: ((String) -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.RadiusMd),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val badge = entry.badge
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Plugins may not carry a timestamp (default -1) — only
                // show it when one was provided.
                if (entry.timeMs > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatChatTime(entry.timeMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            EmoteText(
                text = entry.text,
                emotes = emotes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                onLinkClick = onLinkClick,
            )
        }
    }
}

@Composable
private fun SystemChatCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.RadiusMd),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Splits [text] into plain runs and emote runs (longest emote name wins)
 * and renders emotes as inline images via Coil.
 */
@Composable
private fun EmoteText(
    text: String,
    emotes: Map<String, String>,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    onLinkClick: ((String) -> Unit)? = null,
    size: TextUnit = 16.sp,
) {
    val parts = remember(text, emotes) { splitEmotes(text, emotes.keys) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        parts.forEach { (isEmote, part) ->
            if (isEmote) {
                emotes[part]?.let { url ->
                    AsyncImage(
                        url = url,
                        contentDescription = part,
                        modifier = Modifier.height(size.value.dp),
                        contentScale = ContentScale.Fit,
                    )
                } ?: Text(
                    text = part,
                    style = style,
                    color = color,
                )
            } else {
                if (onLinkClick != null) {
                    com.tsutsen.platformplayer.core.designsystem.component.LinkifiedText(
                        text = part,
                        style = style,
                        color = color,
                        onTimestampClick = {},
                        onLinkClick = onLinkClick,
                    )
                } else {
                    Text(
                        text = part,
                        style = style,
                        color = color,
                    )
                }
            }
        }
    }
}

private fun splitEmotes(text: String, emoteNames: Set<String>): List<Pair<Boolean, String>> {
    if (emoteNames.isEmpty() || text.isEmpty()) return listOf(false to text)
    val regex =
        Regex(
            emoteNames
                .filter { it.isNotEmpty() }
                .sortedByDescending { it.length }
                .joinToString("|") { Regex.escape(it) }
        )
    val out = mutableListOf<Pair<Boolean, String>>()
    var last = 0
    for (m in regex.findAll(text)) {
        if (m.range.first > last) out += false to text.substring(last, m.range.first)
        out += true to m.value
        last = m.range.last + 1
    }
    if (last < text.length) out += false to text.substring(last)
    return out
}

private fun formatChatTime(timeMs: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timeMs))

package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/** Matches "m:ss" / "mm:ss" or "h:mm:ss" time tokens (not part of a longer number). */
private val TIMESTAMP_REGEX = Regex("""(?<![\d:])(\d{1,3}:)?\d{1,2}:\d{2}(?![\d:])""")

/** Matches http(s) URLs. */
private val LINK_REGEX = Regex("""https?://[^\s,]+""")

/**
 * Builds an [AnnotatedString] from [text] with clickable timestamps
 * ("3:45", "1:02:33" — delivered in milliseconds via [onTimestampClick])
 * and http(s) links (delivered via [onLinkClick]).
 *
 * The callbacks are captured at build time, so callers must pass stable
 * lambdas (e.g. ones reading live state from a ViewModel).
 */
fun buildLinkifiedText(
    text: String,
    linkColor: Color,
    onTimestampClick: (Long) -> Unit,
    onLinkClick: (String) -> Unit,
): AnnotatedString {
    data class Segment(val start: Int, val end: Int, val action: () -> Unit)

    val segments = mutableListOf<Segment>()
    val taken = BooleanArray(text.length)

    fun overlaps(start: Int, end: Int): Boolean {
        for (i in start until end) if (taken[i]) return true
        return false
    }

    // Links first — they win overlap disputes (timestamps inside URLs are
    // not seek positions).
    for (m in LINK_REGEX.findAll(text)) {
        val start = m.range.first
        val end = m.range.last + 1
        if (overlaps(start, end)) continue
        for (i in start until end) taken[i] = true
        val url = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']')
        segments += Segment(start, end) { onLinkClick(url) }
    }

    for (m in TIMESTAMP_REGEX.findAll(text)) {
        val ms = parseTimestampMs(m.value) ?: continue
        val start = m.range.first
        val end = m.range.last + 1
        if (overlaps(start, end)) continue
        for (i in start until end) taken[i] = true
        segments += Segment(start, end) { onTimestampClick(ms) }
    }

    if (segments.isEmpty()) return AnnotatedString(text)
    segments.sortBy { it.start }

    val linkStyle = SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)
    val builder = AnnotatedString.Builder()
    var pos = 0
    var id = 0
    for (s in segments) {
        if (s.start > pos) builder.append(text, pos, s.start)
        val segmentAction = s.action
        builder.withLink(
            LinkAnnotation.Clickable(
                "linkified_$id",
                linkInteractionListener = LinkInteractionListener { segmentAction() },
            ),
        ) {
            builder.withStyle(linkStyle) { builder.append(text, s.start, s.end) }
        }
        id++
        pos = s.end
    }
    if (pos < text.length) builder.append(text, pos, text.length)
    return builder.toAnnotatedString()
}

/** "3:45" / "1:02:33" → milliseconds, or null if the token isn't a valid time. */
private fun parseTimestampMs(token: String): Long? {
    val parts = token.split(':').mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        2 -> {
            if (parts[1] < 60) parts[0].toLong() * 60_000 + parts[1] * 1_000L else null
        }
        3 -> {
            if (parts[1] < 60 && parts[2] < 60) {
                (parts[0].toLong() * 3_600 + parts[1] * 60 + parts[2]) * 1_000L
            } else null
        }
        else -> null
    }
}

/**
 * [Text] with clickable timestamps and links. [onTimestampClick] receives
 * the seek target in milliseconds; [onLinkClick] receives the URL.
 *
 * Callers must pass stable lambdas — they are captured once per [text].
 */
@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onTimestampClick: (Long) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val annotated =
        remember(text, primary) {
            buildLinkifiedText(text, primary, onTimestampClick, onLinkClick)
        }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

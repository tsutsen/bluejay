package com.futo.platformplayer.compose.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp

/**
 * Compose port of the XML TagView (view_tag.xml).
 *
 * A small pill-shaped badge/chip for displaying short text labels.
 * This is the "trivial view" chosen for Phase 0 pipeline verification:
 * - Simple structure (FrameLayout → Box)
 * - Single text child (TextView → Text)
 * - Pill background with rounded corners
 * - No animations, no gesture handling, no custom drawing
 *
 * Porting decisions (per the skill's "Porting thin custom views" guidance):
 * - XML attributes (padding, radius, text size) → composable parameters with defaults
 * - Material3 building blocks over from-scratch Canvas implementation
 * - Accepts and applies an incoming `modifier: Modifier` for composability
 * - Background uses MaterialTheme's colorSurfaceVariant (matches XML ?attr/colorSurfaceVariant)
 */

/**
 * A pill-shaped tag badge.
 *
 * @param text The display text
 * @param onClick Optional click handler
 * @param modifier Compose modifier chain
 * @param textColor Text color (defaults to colorOnSurface from current theme)
 * @param backgroundColor Background color (defaults to colorSurfaceVariant)
 * @param fontSize Text size in sp (defaults to 11sp, matching the XML)
 */
@Composable
fun TagBadge(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    fontSize: TextUnit = TextUnit(11f, TextUnitType.Sp)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(500.dp)) // 500dp radius = full pill (matches XML)
            .background(backgroundColor) // apply the background color
            .padding(vertical = 6.dp, horizontal = 18.dp) // matches XML padding
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * A pill-shaped tag badge with a value payload.
 * Mirrors TagView's API: setInfo(text, value) → onClick emits (text, value).
 *
 * @param text The display text
 * @param value The associated value (used in onClick callback)
 * @param onClick Click handler receiving (text, value) pair
 * @param modifier Compose modifier chain
 */
@Composable
fun TagBadgeWithValue(
    text: String,
    value: Any? = null,
    onClick: ((text: String, value: Any) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    TagBadge(
        text = text,
        onClick = if (onClick != null && value != null) {
            { onClick(text, value) }
        } else {
            null
        },
        modifier = modifier
    )
}

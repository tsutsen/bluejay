package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.SurfaceKeys
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceBorder
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceColor
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceContentColor
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceSkin
import kotlin.math.roundToInt
import kotlin.ranges.ClosedFloatingPointRange

@Composable
fun groupShape(position: GroupPosition): Shape {
    val r = BluejayTokens().radius
    return when (position) {
        GroupPosition.Single -> RoundedCornerShape(r.md)
        GroupPosition.First -> RoundedCornerShape(topStart = r.md, topEnd = r.md)
        GroupPosition.Middle -> RoundedCornerShape(0.dp)
        GroupPosition.Last -> RoundedCornerShape(bottomStart = r.md, bottomEnd = r.md)
    }
}

@Composable
private fun iconTileShape() = RoundedCornerShape(BluejayTokens().radius.sm)

/** Skin-aware container color for all settings rows. */
@Composable
private fun settingsCardColors() =
    CardDefaults.cardColors(
        containerColor = surfaceColor(SurfaceKeys.SETTINGS_ROW, MaterialTheme.colorScheme.surfaceContainer),
    )

/**
 * Shared settings-row content: skin-aware text color, icon tile, title +
 * subtitle, and the caller's trailing control.
 */
@Composable
private fun SettingsContent(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconUrl: String? = null,
    content: @Composable () -> Unit,
) {
    val skinText = surfaceSkin(SurfaceKeys.SETTINGS_ROW).contentColor
    CompositionLocalProvider(
        LocalContentColor provides
            surfaceContentColor(SurfaceKeys.SETTINGS_ROW, MaterialTheme.colorScheme.onSurface),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Tokens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(Tokens.AvatarMd)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = iconTileShape(),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (iconUrl != null) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.small),
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(Tokens.IconMd),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = skinText?.copy(alpha = 0.65f) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/**
 * Settings card that opens a sub-screen or dialog. [enabled] = false greys
 * the card out and disables the tap (e.g. rows under a master toggle).
 */
@Composable
fun SettingsOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    groupPosition: GroupPosition = GroupPosition.Single,
    enabled: Boolean = true,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.5f)
                .clickable(enabled = enabled, onClick = onClick),
        shape = groupShape(groupPosition),
        colors = settingsCardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = surfaceBorder(SurfaceKeys.SETTINGS_ROW),
    ) {
        SettingsContent(icon = icon, title = title, subtitle = subtitle) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Settings card with a toggle switch. [enabled] = false greys the card out
 * and disables the switch (e.g. source toggles under a master toggle).
 */
@Composable
fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    groupPosition: GroupPosition = GroupPosition.Single,
    enabled: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = groupShape(groupPosition),
        colors = settingsCardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = surfaceBorder(SurfaceKeys.SETTINGS_ROW),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                surfaceContentColor(SurfaceKeys.SETTINGS_ROW, MaterialTheme.colorScheme.onSurface),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (enabled) 1f else 0.5f)
                        .padding(Tokens.SpaceMd),
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(Tokens.AvatarMd)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = iconTileShape(),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(Tokens.IconMd),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                )
            }
        }
    }
}

/**
 * Settings card with a slider, for user-tunable numeric settings
 * (e.g. UI rounding in Appearance).
 */
@Composable
fun SettingsSliderCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    groupPosition: GroupPosition = GroupPosition.Single,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = groupShape(groupPosition),
        colors = settingsCardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = surfaceBorder(SurfaceKeys.SETTINGS_ROW),
    ) {
        // No outer padding: SettingsContent already insets the icon/title;
        // double padding made the icon sit deeper than in regular rows.
        Column {
            SettingsContent(icon = icon, title = title, subtitle = subtitle) {
                Text(
                    text = value.roundToInt().toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier =
                    Modifier
                        .padding(top = Tokens.SpaceXs)
                        .padding(horizontal = Tokens.SpaceMd),
            )
        }
    }
}

/**
 * Settings card with both a toggle switch and navigation (chevron):
 * tapping the card opens a sub-screen, the switch toggles a boolean.
 */
@Composable
fun SettingsSwitchOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    iconUrl: String? = null,
    groupPosition: GroupPosition = GroupPosition.Single,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = groupShape(groupPosition),
        colors = settingsCardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = surfaceBorder(SurfaceKeys.SETTINGS_ROW),
    ) {
        SettingsContent(icon = icon, title = title, subtitle = subtitle, iconUrl = iconUrl) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

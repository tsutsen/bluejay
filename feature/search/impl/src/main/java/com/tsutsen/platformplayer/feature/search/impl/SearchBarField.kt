package com.tsutsen.platformplayer.feature.search.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens

/**
 * Docked search bar in the PixelPlayer style: translucent primary container,
 * leading search icon, transparent field, and a clear button that appears
 * when there's text. Search submits via the IME search action.
 */
@Composable
fun SearchBarField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = BluejayTokens()
    val motion = tokens.motion
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(tokens.radius.md))
                .background(scheme.primaryContainer.copy(alpha = 0.3f)),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Search",
            tint = scheme.primary,
            modifier =
                Modifier
                    .size(24.dp)
                    .padding(start = 16.dp, end = 8.dp),
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).then(fieldModifier),
            placeholder = {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.primary,
                )
            },
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = scheme.onSurface,
                    unfocusedTextColor = scheme.onSurface.copy(alpha = 0.8f),
                    cursorColor = scheme.primary,
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        AnimatedVisibility(
            visible = value.isNotBlank(),
            enter = fadeIn(motion.stateSpec<Float>()) + expandHorizontally(motion.stateSpec<IntSize>()),
            exit = fadeOut(motion.stateSpec<Float>()) + shrinkHorizontally(motion.stateSpec<IntSize>()),
        ) {
            IconButton(
                onClick = { onValueChange("") },
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(scheme.primaryContainer.copy(alpha = 0.2f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear",
                    tint = scheme.primary,
                )
            }
        }
    }
}

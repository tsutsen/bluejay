package com.tsutsen.platformplayer.feature.search.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens

/**
 * Search field for [SearchScreen] — a verbatim port of PixelPlayer's
 * [DockedSearchBar] (M3 Expressive): translucent primary-tinted
 * container, search icon, and a clear button that appears while the
 * query is non-blank.
 *
 * The corner radius follows the user's UI-rounding setting
 * ([BluejayTokens].radius.lg); PixelPlayer hard-codes 28dp.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val inputFieldColors =
        SearchBarDefaults.inputFieldColors(
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface.copy(alpha = 0.8f),
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = scheme.primary,
        )

    DockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                modifier = fieldModifier,
                query = value,
                onQueryChange = onValueChange,
                onSearch = { onSearch() },
                expanded = false,
                onExpandedChange = {},
                placeholder = {
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.primary,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                },
                trailingIcon = {
                    if (value.isNotBlank()) {
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
                },
                colors = inputFieldColors,
            )
        },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier.clip(RoundedCornerShape(tokens.radius.lg)),
        colors =
            SearchBarDefaults.colors(
                containerColor = scheme.primaryContainer.copy(alpha = 0.3f),
                dividerColor = scheme.primary.copy(alpha = 0.2f),
                inputFieldColors = inputFieldColors,
            ),
        content = {},
    )
}

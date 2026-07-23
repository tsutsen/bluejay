package com.futo.platformplayer.core.designsystem.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Underlined indicator tab row with horizontal scrolling.
 */
@Composable
fun GrayjayTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        indicator = { tabPositions ->
            if (selectedTabIndex in tabPositions.indices) {
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = index == selectedTabIndex,
                onClick = {
                    onTabSelected(index)
                    // Scroll tab into view
                    scope.launch {
                        listState.animateScrollToItem(index)
                    }
                },
                text = {
                    Text(
                        text = title,
                        style = if (index == selectedTabIndex)
                            MaterialTheme.typography.titleMedium
                        else
                            MaterialTheme.typography.bodyMedium
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

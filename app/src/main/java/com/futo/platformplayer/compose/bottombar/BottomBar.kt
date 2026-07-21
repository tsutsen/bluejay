package com.futo.platformplayer.compose.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A single bottom bar navigation item.
 */
data class BottomNavItem(
    val id: Int,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isActive: Boolean,
    val onClick: () -> Unit
)

/**
 * Bottom bar composable — renders a row of navigation buttons.
 * This is the core UI component, independent of navigation logic.
 */
@Composable
fun BottomBar(
    items: List<BottomNavItem>,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surface),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            BottomBarButton(
                item = item,
                contentColor = contentColor
            )
            if (index < items.size - 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Individual bottom bar button.
 */
@Composable
private fun BottomBarButton(
    item: BottomNavItem,
    contentColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(onClick = item.onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (item.isActive) contentColor else contentColor.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = item.label,
            fontSize = 11.sp,
            color = if (item.isActive) contentColor else contentColor.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

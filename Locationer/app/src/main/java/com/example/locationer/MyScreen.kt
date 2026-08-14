package com.example.locationer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.zIndex

/** “我的”页面，收藏夹和设置子页长期共存。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScreen(
    mapViewModel: MapViewModel,
    favoritesViewModel: FavoritesViewModel,
    currentStyle: FlagStyle,
    onStyleChange: (FlagStyle) -> Unit,
    isActive: Boolean = true,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val navEvent by mapViewModel.navigationEvent.collectAsState()

    LaunchedEffect(navEvent, isActive) {
        if (isActive && navEvent.target == MapViewModel.NavigationTarget.MY_FAVORITES) {
            selectedTab = 0
            mapViewModel.consumedNavEvent()
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(
            title = { Text("我的") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("收藏夹") },
                icon = { Icon(Icons.Filled.Star, contentDescription = null) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("设置") },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (selectedTab == 0) 1f else 0f)
                    .zIndex(if (selectedTab == 0) 1f else 0f),
            ) {
                FavoritesScreen(
                    mapViewModel = mapViewModel,
                    favoritesViewModel = favoritesViewModel,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (selectedTab == 1) 1f else 0f)
                    .zIndex(if (selectedTab == 1) 1f else 0f),
            ) {
                SettingsScreen(
                    currentStyle = currentStyle,
                    onStyleChange = onStyleChange,
                )
            }
        }
    }
}

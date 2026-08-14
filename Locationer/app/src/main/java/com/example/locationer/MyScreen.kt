package com.example.locationer

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    // 从 SharedPreferences 读取上次停留的子页签，默认 0（收藏夹）
    var selectedTab by remember {
        mutableIntStateOf(
            context.getSharedPreferences("my_tab_pref", Context.MODE_PRIVATE)
                .getInt("selected_my_tab", 0)
        )
    }
    // 子页签变化时持久化
    LaunchedEffect(selectedTab) {
        context.getSharedPreferences("my_tab_pref", Context.MODE_PRIVATE)
            .edit().putInt("selected_my_tab", selectedTab).apply()
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
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
                    currentStyle  = currentStyle,
                    onStyleChange = onStyleChange,
                    mapViewModel  = mapViewModel,
                )
            }
        }
    }
}

package com.example.locationer

import android.content.Context
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** "我的"页面，收藏夹和设置子页支持左右滑动平滑切换，含帮助文档入口。 */
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
    val initialTab = remember {
        context.getSharedPreferences("my_tab_pref", Context.MODE_PRIVATE)
            .getInt("selected_my_tab", 0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialTab,
        initialPageOffsetFraction = 0f,
    ) { 2 }

    LaunchedEffect(pagerState.currentPage) {
        context.getSharedPreferences("my_tab_pref", Context.MODE_PRIVATE)
            .edit().putInt("selected_my_tab", pagerState.currentPage).apply()
    }

    var tapTarget by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(tapTarget) {
        tapTarget?.let { page ->
            pagerState.animateScrollToPage(page)
        }
        tapTarget = null
    }

    // 帮助文档可见性
    var showHelp by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { tapTarget = 0 },
                text = { Text("收藏夹") },
                icon = { Icon(Icons.Filled.Star, contentDescription = null) },
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { tapTarget = 1 },
                text = { Text("设置") },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            pageSpacing = 0.dp,
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    0 -> FavoritesScreen(
                        mapViewModel = mapViewModel,
                        favoritesViewModel = favoritesViewModel,
                    )
                    1 -> SettingsScreen(
                        currentStyle  = currentStyle,
                        onStyleChange = onStyleChange,
                        mapViewModel  = mapViewModel,
                        onHelpClick   = { showHelp = true },
                    )
                }
            }
        }
    }

    if (showHelp) {
        HelpScreen(onBack = { showHelp = false })
    }
}

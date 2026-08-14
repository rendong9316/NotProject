package com.example.locationer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.locationer.ui.theme.LocationerTheme

class MainActivity : ComponentActivity() {

    private var lastBackPressTime = 0L
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsManager = SettingsManager(this)
        // 双击返回键退出：间隔不超过 2 秒，否则重置计时
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) {
                    isEnabled = false
                    finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(this@MainActivity, "再按一次退出应用", Toast.LENGTH_SHORT).show()
                }
            }
        })
        setContent {
            var style by remember { mutableStateOf(settingsManager.read()) }
            LocationerTheme(
                themeMode = style.themeMode,
                fontFamilyOption = style.globalFontFamily,
            ) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = density.density,
                        fontScale = style.globalTextScale,
                    )
                ) {
                    LocationerApp(
                        style = style,
                        onStyleChange = { updated ->
                            style = updated
                            settingsManager.write(updated)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun LocationerApp(style: FlagStyle, onStyleChange: (FlagStyle) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val viewModel: MapViewModel = viewModel()
    val favoritesViewModel: FavoritesViewModel = viewModel()
    val flags by viewModel.flags.collectAsState()
    val navigationEvent by viewModel.navigationEvent.collectAsState()

    LaunchedEffect(flags) {
        favoritesViewModel.migrateLegacyFavorites(flags)
    }

    LaunchedEffect(navigationEvent) {
        selectedTab = when (navigationEvent.target) {
            MapViewModel.NavigationTarget.MAP             -> 0
            MapViewModel.NavigationTarget.TOOLS_MEASUREMENT -> 1
            MapViewModel.NavigationTarget.MY_FAVORITES    -> 2
            MapViewModel.NavigationTarget.NONE            -> selectedTab
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 三个页面始终在 composition 中（永不销毁），仅通过 alpha + zIndex 控制显隐。
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (selectedTab == 0) 1f else 0f)
                    .zIndex(if (selectedTab == 0) 1f else 0f),
            ) { MapScreen(viewModel = viewModel, isActive = selectedTab == 0, flagStyle = style) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (selectedTab == 1) 1f else 0f)
                    .zIndex(if (selectedTab == 1) 1f else 0f),
            ) {
                ToolsScreen(
                    mapViewModel = viewModel,
                    favoritesViewModel = favoritesViewModel,
                    isActive = selectedTab == 1,
                    flagStyle = style,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (selectedTab == 2) 1f else 0f)
                    .zIndex(if (selectedTab == 2) 1f else 0f),
            ) {
                MyScreen(
                    mapViewModel = viewModel,
                    favoritesViewModel = favoritesViewModel,
                    currentStyle = style,
                    onStyleChange = onStyleChange,
                    isActive = selectedTab == 2,
                )
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
            Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                TabItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.NearMe,
                    label = "地图",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                )
                TabItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Build,
                    label = "工具",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                )
                TabItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Person,
                    label = "我的",
                    selected = selectedTab == 2,
                    onClick = {
                        viewModel.requestSwitchToFavorites()
                        selectedTab = 2
                    },
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    modifier  : Modifier,
    icon      : androidx.compose.ui.graphics.vector.ImageVector,
    label     : String,
    selected  : Boolean,
    onClick   : () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon, contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label, fontSize = 14.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

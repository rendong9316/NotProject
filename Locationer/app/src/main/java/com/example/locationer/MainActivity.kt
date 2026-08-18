package com.example.locationer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import com.example.locationer.ui.theme.LocationerTheme

class MainActivity : ComponentActivity() {

    private var lastBackPressTime = 0L
    private lateinit var settingsManager: SettingsManager
    // 暂存来自外部（点击 JSON 文件）的 Intent URI，供 MyScreen → FavoritesScreen 消费
    private var pendingRestoreUri by mutableStateOf<Uri?>(null)
    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "未授予存储权限，自动备份不可用，可使用手动导出",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsManager = SettingsManager(this)
        // 优先从 onSaveInstanceState 恢复；若进程未重启则从当前 intent 取
        pendingRestoreUri = savedInstanceState?.let { state ->
            BundleCompat.getParcelable(state, PENDING_RESTORE_URI_KEY, Uri::class.java)
        } ?: intent.data
        handleIncomingIntent(intent)
        // 双击返回键退出
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
                        pendingRestoreUri = pendingRestoreUri,
                        onRestoreUriHandled = { pendingRestoreUri = null },
                    )
                }
            }
        }
        requestLegacyStoragePermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRestoreUri = intent?.data
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW || intent.data == null) return
        pendingRestoreUri = intent.data
    }

    private fun requestLegacyStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    companion object {
        private const val PENDING_RESTORE_URI_KEY = "pending_restore_uri"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingRestoreUri?.let { outState.putParcelable(PENDING_RESTORE_URI_KEY, it) }
    }
}

@Composable
fun LocationerApp(
    style: FlagStyle,
    onStyleChange: (FlagStyle) -> Unit,
    pendingRestoreUri: Uri? = null,
    onRestoreUriHandled: () -> Unit = {},
) {
    var selectedTab by remember { mutableStateOf(0) }
    val viewModel: MapViewModel = viewModel()
    val favoritesViewModel: FavoritesViewModel = viewModel()

    LaunchedEffect(pendingRestoreUri) {
        if (pendingRestoreUri != null) selectedTab = 2
    }

    // 监听 flag 迁移（旧版本 isFavorite → 新收藏快照）
    val flags by viewModel.flags.collectAsState()
    LaunchedEffect(flags) {
        favoritesViewModel.migrateLegacyFavorites(flags)
    }

    // 监听导航事件：从地图/工具页切回对应 tab
    val navigationEvent by viewModel.navigationEvent.collectAsState()
    LaunchedEffect(navigationEvent) {
        when (navigationEvent.target) {
            MapViewModel.NavigationTarget.MAP             -> selectedTab = 0
            MapViewModel.NavigationTarget.TOOLS_MEASUREMENT -> selectedTab = 1
            MapViewModel.NavigationTarget.MY_FAVORITES    -> selectedTab = 2
            MapViewModel.NavigationTarget.MEASUREMENT_HISTORY -> selectedTab = 2
            MapViewModel.NavigationTarget.NONE            -> Unit
        }
        viewModel.consumedNavEvent()
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
                    pendingRestoreUri = pendingRestoreUri,
                    onRestoreUriHandled = onRestoreUriHandled,
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

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.locationer.ui.theme.LocationerTheme

class MainActivity : ComponentActivity() {

    private var lastBackPressTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            LocationerTheme {
                CompositionLocalProvider(FixedTextScaleFactor provides 1f) {
                    LocationerApp()
                }
            }
        }
    }
}

@Composable
fun LocationerApp() {
    var selectedTab by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        // 两个页面始终在 composition 中（永不销毁），仅通过 alpha + zIndex 控制显隐。
        // 激活页 alpha=1 zIndex=1 在上层接收触摸；非激活页 alpha=0 zIndex=0 完全隐藏。
        // 瞬时切换，无动画。
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (selectedTab == 0) 1f else 0f)
                    .zIndex(if (selectedTab == 0) 1f else 0f),
            ) { MapScreen() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (selectedTab == 1) 1f else 0f)
                    .zIndex(if (selectedTab == 1) 1f else 0f),
            ) { ToolsScreen() }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
            Row(modifier = Modifier.fillMaxWidth()) {
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
                    label = "工具箱",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
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
            text = label, fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

package com.example.locationer

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions

/** 固定字体缩放比例，始终为 1f，不跟随系统无障碍字体大小设置 */
private val FixedTextScaleFactor = compositionLocalOf { 1f }

// ---------- 字体缩放感知的 Text ----------
@Composable
private fun FText(
    text      : String,
    fontSize  : Int,
    modifier  : Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color     : androidx.compose.ui.graphics.Color? = null,
    textAlign : TextAlign? = null,
    maxLines  : Int = Int.MAX_VALUE,
    fontFamily: FontFamily? = null,
) {
    val scale = FixedTextScaleFactor.current
    Text(
        text       = text,
        fontSize   = (fontSize * scale).sp,
        modifier   = modifier,
        fontWeight = fontWeight,
        color      = color ?: MaterialTheme.colorScheme.onSurface,
        textAlign  = textAlign,
        maxLines   = maxLines,
        fontFamily = fontFamily,
    )
}

/**
 * 主页面：顶部高德地图 + 可折叠信息面板 + 底部操作栏
 *
 * 使用 FixedTextScaleFactor compositionLocal 锁定字体缩放 = 1x，
 * 不受系统"字体大小"无障碍设置影响。
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(),
) {
    val context          = LocalContext.current
    val lifecycleOwner   = LocalLifecycleOwner.current

    // ================ 地图实例 ================
    val mapView = remember {
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        MapView(context).apply { onCreate(Bundle()) }
    }
    val aMap = remember { mapView.map }

    var currentMarker    by remember(mapView) { mutableStateOf<Marker?>(null) }
    var targetMarker     by remember(mapView) { mutableStateOf<Marker?>(null) }
    var lastJumpId       by remember(mapView) { mutableStateOf(-1L) }
    var mapReady         by remember { mutableStateOf(false) }
    var initialCameraSet by remember(mapView) { mutableStateOf(false) }

    // ================ MapView 生命周期托管 ================
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME   -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE    -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY  -> mapView.onDestroy()
                else                        -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // ================ 状态收集 ================
    val gcj             by viewModel.currentGcj.collectAsState()
    val wgs             by viewModel.currentWgs.collectAsState()
    val jump            by viewModel.jumpTarget.collectAsState()
    val locating        by viewModel.locating.collectAsState()
    val msg             by viewModel.message.collectAsState()
    val lonText         by viewModel.lonText.collectAsState()
    val latText         by viewModel.latText.collectAsState()
    val coordType       by viewModel.coordType.collectAsState()
    val accuracyMeters  by viewModel.accuracyMeters.collectAsState()

    // ================ 面板折叠状态 ================
    var panelExpanded by remember { mutableStateOf(true) }

    // ================ 初始镜头（中国视野） ================
    LaunchedEffect(mapReady) {
        if (mapReady && !initialCameraSet) {
            initialCameraSet = true
            aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.0, 104.0), 4.5f))
        }
    }

    // ================ 当前位置标记（蓝色） ================
    LaunchedEffect(mapReady, gcj) {
        val amap = aMap ?: return@LaunchedEffect
        val g    = gcj ?: return@LaunchedEffect
        val pos  = LatLng(g.lat, g.lon)
        currentMarker?.remove()
        currentMarker = amap.addMarker(
            MarkerOptions().position(pos).title("当前位置")
                .snippet("GCJ02 经度 %.6f 纬度 %.6f".format(g.lon, g.lat))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .anchor(0.5f, 0.5f)
        )
        amap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
    }

    // ================ 目标点位标记（红色） ================
    LaunchedEffect(mapReady, jump) {
        val target = jump ?: return@LaunchedEffect
        if (target.id == lastJumpId) return@LaunchedEffect
        lastJumpId = target.id
        val amap  = aMap ?: return@LaunchedEffect
        val pos   = LatLng(target.gcj.lat, target.gcj.lon)
        targetMarker?.remove()
        targetMarker = amap.addMarker(
            MarkerOptions().position(pos).title("目标点位")
                .snippet("%s 经度 %.6f 纬度 %.6f".format(
                    target.type.name, target.typed.lon, target.typed.lat))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .anchor(0.5f, 0.9f)
        )
        amap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
    }

    // ================ Toast 提示 ================
    LaunchedEffect(msg) {
        msg?.let {
            Toast.makeText(context, it.text, Toast.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }

    // ================ 权限处理 ================
    var showSettingsDialog by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine    = result[Manifest.permission.ACCESS_FINE_LOCATION]    == true
        val coarse  = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            if (!fine)
                Toast.makeText(context, "仅获得粗略定位权限，精度可能受限", Toast.LENGTH_LONG).show()
            viewModel.locate()
        } else {
            val activity = context as? Activity
            val permanentlyDenied = activity == null ||
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            if (permanentlyDenied) showSettingsDialog = true
            else Toast.makeText(context, "定位权限被拒绝，无法获取当前位置", Toast.LENGTH_LONG).show()
        }
    }

    val onClickLocate = {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) viewModel.locate()
        else permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ))
    }

    // 权限引导弹窗
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { FText("需要定位权限", 16, fontWeight = FontWeight.SemiBold) },
            text  = { FText("定位权限已被拒绝且系统不再询问。请前往系统设置手动开启定位权限后重试。", 14) },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")))
                }) { FText("去设置", 14) }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { FText("取消", 14) }
            }
        )
    }

    // ================ 主布局（锁定字体缩放比例 = 1x） ================
    CompositionLocalProvider(FixedTextScaleFactor provides 1f) {
        Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Bottom
            ) {
                // -------- 地图（占满可用区域） --------
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier.fillMaxSize(),
                        update    = { mapReady = true },
                    )
                }

                // -------- 可折叠信息面板 --------
                CollapsibleInfoPanel(
                    expanded       = panelExpanded,
                    gcj            = gcj,
                    wgs            = wgs,
                    accuracyMeters = accuracyMeters,
                    locating       = locating,
                    onToggle       = { panelExpanded = !panelExpanded },
                    onLocate       = { onClickLocate() },
                )

                // -------- 始终可见的底部操作栏 --------
                BottomInputBar(
                    lonText     = lonText,
                    latText     = latText,
                    coordType   = coordType,
                    onLonChange = { viewModel.updateLonText(it) },
                    onLatChange = { viewModel.updateLatText(it) },
                    onCoordType = { viewModel.setCoordType(it) },
                    onJumpTo    = { viewModel.jumpTo() },
                )
            }
        }
    }
}

// ============================================================================
// 可折叠信息面板
// ============================================================================

@Composable
private fun CollapsibleInfoPanel(
    expanded       : Boolean,
    gcj            : CT.Coord?,
    wgs            : CT.Coord?,
    accuracyMeters : Float?,
    locating       : Boolean,
    onToggle       : () -> Unit,
    onLocate       : () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // ---- 面板头部：点击切换展开/折叠 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                FText(
                    "当前位置",
                    14,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                // 快捷定位按钮（圆形图标按钮，等待时显示旋转动画）
                LocateButton(onClick = onLocate, locating = locating)
                // 折叠箭头
                Icon(
                    imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = if (expanded) "收起面板" else "展开面板",
                    modifier = Modifier.size(22.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 展开时显示坐标信息 ----
            if (expanded) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CoordsRow(label = "GCJ02", coord = gcj,
                        color = MaterialTheme.colorScheme.primary)
                    CoordsRow(label = "WGS84", coord = wgs,
                        color = MaterialTheme.colorScheme.tertiary)
                    AccuracyRow(meters = accuracyMeters)
                }
            }
        }
    }
}

// GCJ02 / WGS84 坐标单行：长按复制到剪贴板，格式 (lon, lat)
@Composable
private fun CoordsRow(
    label : String,
    coord : CT.Coord?,
    color : androidx.compose.ui.graphics.Color,
) {
    val context = LocalContext.current
    val text = coord?.let { "(%.6f, %.6f)".format(it.lon, it.lat) }
                   ?: "(--, --)"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("坐标", text))
                Toast.makeText(context, "已复制：$text", Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText(label, 11, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            FText(
                text = text,
                fontSize = 11,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
            )
        }
    }
}

// 精度单行（已修复条件逻辑）
private data class AccuracyInfo(
    val label    : String,
    val subtext  : String,
    val textColor: androidx.compose.ui.graphics.Color,
)

@Composable
private fun AccuracyRow(meters: Float?) {
    val info = when {
        meters == null    -> AccuracyInfo("等待定位", "", MaterialTheme.colorScheme.onSurfaceVariant)
        meters > 50f      -> AccuracyInfo(
            "精度较低",
            "",
            MaterialTheme.colorScheme.error)
        meters > 20f      -> AccuracyInfo(
            "精度一般", "",
            MaterialTheme.colorScheme.onSurfaceVariant)
        else              -> AccuracyInfo(
            "高精度",
            "",
            MaterialTheme.colorScheme.primary)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText("定位精度", 11, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            FText(
                text = "${info.label}  ${meters?.toInt()?.toString() ?: "--"} m",
                fontSize = 11,
                fontWeight = FontWeight.SemiBold,
                color = info.textColor,
            )
        }
    }
    if (info.subtext.isNotEmpty()) {
        Spacer(Modifier.height(2.dp))
        FText(info.subtext, 11, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
    }
}

// ============================================================================
// 准星定位按钮（圆形图标按钮，等待时显示旋转动画）
// ============================================================================

@Composable
private fun LocateButton(onClick: () -> Unit, locating: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(enabled = !locating, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (locating) {
            CircularProgressIndicator(
                modifier   = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color      = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Icon(
                Icons.Filled.NearMe,
                contentDescription = "一键获取当前位置",
                modifier = Modifier.size(22.dp),
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ============================================================================
// 底部操作栏（固定高度，始终可见）
// ============================================================================

@Composable
private fun BottomInputBar(
    lonText     : String,
    latText     : String,
    coordType   : CoordType,
    onLonChange : (String) -> Unit,
    onLatChange : (String) -> Unit,
    onCoordType : (CoordType) -> Unit,
    onJumpTo    : () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .imePadding()
        ) {
            // 坐标输入行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value        = lonText,
                    onValueChange = onLonChange,
                    modifier     = Modifier.weight(1f),
                    label        = { FText("经度", 12) },
                    placeholder  = { FText("116.397428", 13) },
                    singleLine   = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor      = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor    = MaterialTheme.colorScheme.outline,
                        disabledIndicatorColor     = MaterialTheme.colorScheme.outline,
                        focusedContainerColor      = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor    = MaterialTheme.colorScheme.surface,
                    ),
                )
                OutlinedTextField(
                    value        = latText,
                    onValueChange = onLatChange,
                    modifier     = Modifier.weight(1f),
                    label        = { FText("纬度", 12) },
                    placeholder  = { FText("39.90923", 13) },
                    singleLine   = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor      = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor    = MaterialTheme.colorScheme.outline,
                        disabledIndicatorColor     = MaterialTheme.colorScheme.outline,
                        focusedContainerColor      = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor    = MaterialTheme.colorScheme.surface,
                    ),
                )
            }

            Spacer(Modifier.height(6.dp))

            // 坐标类型 + 跳转按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText("坐标类型", 12, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                CoordRadio("GCJ02", coordType == CoordType.GCJ02) {
                    onCoordType(CoordType.GCJ02)
                }
                CoordRadio("WGS84", coordType == CoordType.WGS84) {
                    onCoordType(CoordType.WGS84)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onJumpTo,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = "跳转定位", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CoordRadio(
    label    : String,
    selected : Boolean,
    onClick  : () -> Unit,
) {
    Row(
        modifier = Modifier.selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        FText(label, 12)
        Spacer(Modifier.width(6.dp))
    }
}

package com.example.locationer

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import kotlin.math.abs
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
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
import com.amap.api.maps.model.PolylineOptions

/** 固定字体缩放比例，始终为 1f，不跟随系统无障碍字体大小设置 */
val FixedTextScaleFactor = compositionLocalOf { 1f }

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

// ============================================================================
// 位图工具函数
// ============================================================================

/** 绘制准星图标：红色圆环 + 红色中心点（64x64） */
private fun createReticleBitmap(): Bitmap {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val ring = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 3f }
    val dot  = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
    c.drawCircle(32f, 32f, 20f, ring)
    c.drawCircle(32f, 32f, 6f, dot)
    return bmp
}

/** 绘制标记图标：带标签的圆点 */
private fun createFlagBitmap(color: Int, label: String, size: Int = 64): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    // 圆点
    val circlePaint = Paint().apply { this.color = color; style = Paint.Style.FILL }
    c.drawCircle((size / 2).toFloat(), (size / 2 + 4).toFloat(), 14f, circlePaint)
    // 标签文字
    val textPaint = Paint().apply {
        this.color = Color.WHITE
        this.textSize = 18f
        this.isAntiAlias = true
        this.textAlign = Paint.Align.CENTER
    }
    val metrics = textPaint.descent() - textPaint.ascent()
    c.drawText(label, (size / 2).toFloat(), (size / 2 - 10).toFloat() - metrics / 2, textPaint)
    return bmp
}

// ============================================================================
// 主页面
// ============================================================================

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(),
) {
    val context          = LocalContext.current
    val lifecycleOwner   = LocalLifecycleOwner.current
    val density          = LocalDensity.current

    // ================ 地图实例 ================
    val mapView = remember {
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        MapView(context).apply { onCreate(Bundle()) }
    }
    val aMap = remember { mapView.map }

    // 标记管理
    var currentMarker    by remember(mapView) { mutableStateOf<Marker?>(null) }
    var targetMarker     by remember(mapView) { mutableStateOf<Marker?>(null) }
    var lastJumpId       by remember(mapView) { mutableStateOf(-1L) }
    var reticleMarker    by remember(mapView) { mutableStateOf<Marker?>(null) }
    var mapReady         by remember { mutableStateOf(false) }
    var initialCameraSet by remember(mapView) { mutableStateOf(false) }
    /** 是否已执行过首次镜头平移到当前位置（后续持续定位不再跟相机） */
    var firstLocateDone  by remember(mapView) { mutableStateOf(false) }
    // 所有旗标 marker 列表（key = flag.id）
    var flagMarkers      by remember(mapView) { mutableStateOf<Map<Long, Marker>>(emptyMap()) }

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
    val placeMode       by viewModel.placeMode.collectAsState()
    val reticleCoord    by viewModel.reticleCoord.collectAsState()
    val pickedCoord     by viewModel.lastPickedCoord.collectAsState()
    val flags           by viewModel.flags.collectAsState()
    val bearing         by viewModel.bearing.collectAsState()
    val isTracking      by viewModel.isTracking.collectAsState()

    // ================ 面板折叠状态 ================
    var panelExpanded by remember { mutableStateOf(true) }

    // ================ 初始镜头 ================
    LaunchedEffect(mapReady) {
        if (mapReady && !initialCameraSet) {
            initialCameraSet = true
            aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.0, 104.0), 4.5f))
        }
    }

    // ================ 地图手势锁定（拾取模式） ================
    LaunchedEffect(placeMode) {
        aMap?.setMapCustomEnable(placeMode)
    }

    // ================ 拾取模式触摸监听 ================
    LaunchedEffect(placeMode, aMap) {
        if (aMap == null) return@LaunchedEffect
        if (placeMode) {
            aMap!!.setOnMapTouchListener { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        val latlng = aMap!!.projection?.fromScreenLocation(
                            android.graphics.Point(event.x.toInt(), event.y.toInt())
                        )
                        latlng?.let { viewModel.setReticleCoord(CT.Coord(it.longitude, it.latitude)) }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 松手确认放置
                        val reticle = viewModel.reticleCoord.value
                        if (reticle != null) {
                            viewModel.confirmPlacement(reticle)
                            // 准星回到屏幕中心
                            val centerPt = android.graphics.Point(
                                mapView.width / 2, mapView.height / 2
                            )
                            val centerLatlng = aMap!!.projection?.fromScreenLocation(centerPt)
                            centerLatlng?.let { viewModel.setReticleCoord(CT.Coord(it.longitude, it.latitude)) }
                        }
                        true
                    }
                    else -> false
                }
            }
        } else {
            aMap!!.setOnMapTouchListener(null)
        }
    }

    // ================ 长按地图快捷放置 ================
    LaunchedEffect(aMap) {
        aMap?.setOnMapLongClickListener { latlng ->
            if (!placeMode) {
                // 非模式：直接放置一个旗标
                val gcj = CT.Coord(latlng.longitude, latlng.latitude)
                viewModel.confirmPlacement(gcj)
            }
            true
        }
    }

    // ================ 地图点击监听（兼容旧接口） ================
    LaunchedEffect(aMap) {
        aMap?.setOnMapClickListener { latlng ->
            if (!placeMode) {
                viewModel.onMapClick(latlng.longitude, latlng.latitude)
            }
        }
    }

    // ================ 当前位置标记 ================
    LaunchedEffect(mapReady, gcj, bearing, isTracking) {
        val amap = aMap ?: return@LaunchedEffect
        val g    = gcj ?: return@LaunchedEffect
        val pos  = LatLng(g.lat, g.lon)
        if (currentMarker == null) {
            currentMarker = amap.addMarker(
                MarkerOptions().position(pos).title("当前位置")
                    .snippet("GCJ02 经度 %.6f 纬度 %.6f".format(g.lon, g.lat))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    .anchor(0.5f, 0.5f)
            )
        } else {
            currentMarker?.position = pos
            currentMarker?.snippet = "GCJ02 经度 %.6f 纬度 %.6f".format(g.lon, g.lat)
        }
        if (isTracking && bearing != null) {
            currentMarker?.rotateAngle = ((180f - bearing!!) % 360f + 360f) % 360f
        }
        // 首次定位成功后才平移镜头，后续持续跟踪不再跟随相机
        if (!firstLocateDone) {
            firstLocateDone = true
            amap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
        }
    }

    // ================ 跟踪停止时重置镜头跟随状态 ================
    LaunchedEffect(isTracking) {
        if (!isTracking) firstLocateDone = false
    }

    // ================ 跳转目标标记 ================
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

    // ================ 准星标记（拾取模式中） ================
    LaunchedEffect(reticleCoord, placeMode) {
        if (!placeMode || reticleCoord == null) {
            reticleMarker?.remove()
            reticleMarker = null
            return@LaunchedEffect
        }
        val amap = aMap ?: return@LaunchedEffect
        val pos = LatLng(reticleCoord!!.lat, reticleCoord!!.lon)
        if (reticleMarker == null) {
            reticleMarker = amap.addMarker(
                MarkerOptions().position(pos)
                    .icon(BitmapDescriptorFactory.fromBitmap(createReticleBitmap()))
                    .anchor(0.5f, 0.5f)
            )
        } else {
            reticleMarker?.position = pos
        }
    }

    // ================ 旗标标记渲染 ================
    LaunchedEffect(flags, mapReady) {
        if (!mapReady) return@LaunchedEffect
        val amap = aMap ?: return@LaunchedEffect
        // 移除旧的旗标 marker
        flagMarkers.values.forEach { it.remove() }
        // 创建新的
        val newMap = flags.associate { flag ->
            val pos = LatLng(flag.gcjLat, flag.gcjLon)
            val color = when (flag.type) {
                FlagType.CURRENT  -> Color.BLUE
                FlagType.PICKED   -> Color.rgb(255, 193, 7)   // amber/yellow
                FlagType.JUMPED   -> Color.RED
            }
            val marker = amap.addMarker(
                MarkerOptions().position(pos)
                    .icon(BitmapDescriptorFactory.fromBitmap(
                        createFlagBitmap(color, flag.label, size = 64)))
                    .anchor(0.5f, 0.9f)
            )
            flag.id to marker
        }
        flagMarkers = newMap
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
        firstLocateDone = false  // 每次点击重新开始定位，镜头重新跟随
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) viewModel.locate()
        else permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ))
    }

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

    // ================ 主布局 ================
    CompositionLocalProvider(FixedTextScaleFactor provides 1f) {
        Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Bottom
            ) {
                // -------- 地图区域 --------
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier.fillMaxSize(),
                        update    = { mapReady = true },
                    )
                    // 准星 + 预览 Canvas 叠加层
                    if (placeMode && reticleCoord != null && mapReady) {
                        ReticleOverlay(
                            reticleCoord = reticleCoord!!,
                            density = density,
                            mapSize = { android.graphics.Point(mapView.width, mapView.height) },
                            aMapGetter = { aMap },
                        )
                    }
                    // FAB
                    PickModeFab(
                        placeMode = placeMode,
                        onToggle  = { viewModel.togglePlaceMode() },
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

                // -------- 底部坐标输入栏 --------
                BottomInputBar(
                    lonText     = lonText,
                    latText     = latText,
                    coordType   = coordType,
                    onLonChange = { viewModel.updateLonText(it) },
                    onLatChange = { viewModel.updateLatText(it) },
                    onCoordType = { viewModel.setCoordType(it) },
                    onJumpTo    = { viewModel.jumpTo() },
                )

                // -------- 已拾取坐标展示条 --------
                if (pickedCoord != null) {
                    PickedCoordBanner(
                        coord = pickedCoord,
                        isPickMode = placeMode,
                        onCopy = {
                            pickedCoord?.let {
                                val text = "(%.6f, %.6f)".format(it.lon, it.lat)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("坐标", text))
                                Toast.makeText(context, "已复制：$text", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDismiss = { viewModel.clearPickedCoord() }
                    )
                }
            }
        }
    }
}

// ============================================================================
// 准星 + 预览叠加层（Canvas）
// ============================================================================

@Composable
private fun ReticleOverlay(
    reticleCoord : CT.Coord,
    density      : Density,
    mapSize      : () -> android.graphics.Point,
    aMapGetter   : () -> com.amap.api.maps.AMap?,
) {
    val ctx = LocalContext.current
    val previewOffsetDp = 60.dp
    val previewOffsetPx = with(density) { previewOffsetDp.toPx() }

    // 计算准星的屏幕坐标
    val reticleScreenPt = remember(reticleCoord, mapSize) {
        aMapGetter()?.projection?.toScreenLocation(
            LatLng(reticleCoord.lat, reticleCoord.lon)
        )
    }

    // 计算预览偏移方向
    val previewOffsetDir = remember(reticleScreenPt, mapSize) {
        val mapW = mapSize().x.toFloat()
        val mapH = mapSize().y.toFloat()
        val cx = reticleScreenPt?.x?.toFloat() ?: mapW / 2
        val cy = reticleScreenPt?.y?.toFloat() ?: mapH / 2
        // 判断是否需要翻转
        if (cy < 80f) Offset(0f, previewOffsetPx)        // 靠近顶部 → 向下偏移
        else if (cx < 60f) Offset(previewOffsetPx, 0f)   // 靠近左侧 → 向右偏移
        else if (cx > mapW - 60f) Offset(-previewOffsetPx, 0f)  // 靠近右侧 → 向左偏移
        else Offset(0f, -previewOffsetPx)                 // 默认 → 向上偏移
    }

    val previewScreenX = (reticleScreenPt?.x?.toFloat() ?: mapSize().x / 2f) + previewOffsetDir.x
    val previewScreenY = (reticleScreenPt?.y?.toFloat() ?: mapSize().y / 2f) + previewOffsetDir.y

    Canvas(modifier = Modifier.fillMaxSize()) {
        val rx = (reticleScreenPt?.x?.toFloat() ?: size.width / 2f)
        val ry = (reticleScreenPt?.y?.toFloat() ?: size.height / 2f)

        // 连接线（准星 → 预览）
        drawLine(
            color = UiColor.Yellow.copy(alpha = 0.6f),
            start = Offset(rx, ry),
            end   = Offset(previewScreenX, previewScreenY),
            strokeWidth = 2f,
        )

        // 预览图钉（半透明黄色圆点）
        drawCircle(
            color = UiColor.Yellow.copy(alpha = 0.5f),
            radius = 16f,
            center = Offset(previewScreenX, previewScreenY),
        )
        drawCircle(
            color = UiColor.Yellow.copy(alpha = 0.8f),
            radius = 8f,
            center = Offset(previewScreenX, previewScreenY),
        )
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
                FText("当前位置", 14, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                LocateButton(onClick = onLocate, locating = locating)
                Icon(
                    imageVector = if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = if (expanded) "收起面板" else "展开面板",
                    modifier = Modifier.size(22.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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

@Composable
private fun CoordsRow(
    label : String,
    coord : CT.Coord?,
    color : androidx.compose.ui.graphics.Color,
) {
    val context = LocalContext.current
    val text = coord?.let { "(%.6f, %.6f)".format(it.lon, it.lat) } ?: "(--, --)"
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
            FText(text = text, fontSize = 11, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
        }
    }
}

private data class AccuracyInfo(
    val label    : String,
    val subtext  : String,
    val textColor: androidx.compose.ui.graphics.Color,
)

@Composable
private fun AccuracyRow(meters: Float?) {
    val info = when {
        meters == null    -> AccuracyInfo("等待定位", "", MaterialTheme.colorScheme.onSurfaceVariant)
        meters > 50f      -> AccuracyInfo("精度较低", "", MaterialTheme.colorScheme.error)
        meters > 20f      -> AccuracyInfo("精度一般", "", MaterialTheme.colorScheme.onSurfaceVariant)
        else              -> AccuracyInfo("高精度", "", MaterialTheme.colorScheme.primary)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FText("定位精度", 11, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            FText("${info.label}  ${meters?.toInt()?.toString() ?: "--"} m",
                fontSize = 11, fontWeight = FontWeight.SemiBold, color = info.textColor)
        }
    }
    if (info.subtext.isNotEmpty()) {
        Spacer(Modifier.height(2.dp))
        FText(info.subtext, 11, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun LocateButton(onClick: () -> Unit, locating: Boolean) {
    Box(
        modifier = Modifier.size(40.dp).clickable(enabled = !locating, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (locating) {
            CircularProgressIndicator(
                modifier   = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color      = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Icon(Icons.Filled.NearMe, contentDescription = "一键获取当前位置",
                modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ============================================================================
// 已拾取坐标展示条
// ============================================================================

@Composable
private fun PickedCoordBanner(
    coord      : CT.Coord?,
    isPickMode : Boolean,
    onCopy     : () -> Unit,
    onDismiss  : () -> Unit,
) {
    Surface(
        color = if (isPickMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionContainer {
                FText(
                    text = coord?.let { "(%.6f, %.6f)".format(it.lon, it.lat) } ?: "--",
                    fontSize = 13, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPickMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCopy,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                FText("复制", 11)
            }
            TextButton(onClick = onDismiss,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                FText("关闭", 11)
            }
        }
    }
}

// ============================================================================
// 拾取模式浮动按钮
// ============================================================================

@Composable
private fun PickModeFab(placeMode: Boolean, onToggle: () -> Unit) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier = Modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = onToggle,
            modifier = Modifier
                .padding(16.dp)
                .align(androidx.compose.ui.Alignment.TopEnd),
            containerColor = if (placeMode) MaterialTheme.colorScheme.error
                             else MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary,
        ) {
            if (placeMode)
                Icon(Icons.Filled.Close, contentDescription = "退出拾取")
            else
                Icon(Icons.Filled.Search, contentDescription = "拾取坐标")
        }
    }
}

// ============================================================================
// 底部坐标输入栏
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FText("坐标类型", 12, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                CoordRadio("GCJ02", coordType == CoordType.GCJ02) { onCoordType(CoordType.GCJ02) }
                CoordRadio("WGS84", coordType == CoordType.WGS84) { onCoordType(CoordType.WGS84) }
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
private fun CoordRadio(label    : String, selected : Boolean, onClick  : () -> Unit) {
    Row(
        modifier = Modifier.selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        FText(label, 12)
        Spacer(Modifier.width(6.dp))
    }
}

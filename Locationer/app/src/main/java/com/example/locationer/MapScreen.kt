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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.foundation.isSystemInDarkTheme
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polygon
import com.amap.api.maps.model.PolygonOptions
import com.amap.api.maps.model.Polyline
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

/** 绘制准星图标：红色圆环 + 红色中心点（128x128） */
private fun createReticleBitmap(): Bitmap {
    val size = 128
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val ring = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 5f }
    val dot  = Paint().apply { color = Color.RED; style = Paint.Style.FILL }
    c.drawCircle(64f, 64f, 40f, ring)
    c.drawCircle(64f, 64f, 10f, dot)
    return bmp
}

/** 绘制旗标图标：带标签的圆点（144x144） */
private fun createFlagBitmap(color: Int, label: String): Bitmap {
    val size = 144
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val circlePaint = Paint().apply { this.color = color; style = Paint.Style.FILL }
    c.drawCircle((size / 2).toFloat(), (size / 2 + 8).toFloat(), 28f, circlePaint)
    val textPaint = Paint().apply {
        this.color = Color.WHITE
        this.textSize = 30f
        this.isAntiAlias = true
        this.textAlign = Paint.Align.CENTER
    }
    val metrics = textPaint.descent() - textPaint.ascent()
    c.drawText(label, (size / 2).toFloat(), (size / 2 - 20).toFloat() - metrics / 2, textPaint)
    return bmp
}

// ============================================================================
// 主页面
// ============================================================================

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(),
    isActive: Boolean = true,
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

    // 标记管理
    var currentMarker    by remember(mapView) { mutableStateOf<Marker?>(null) }
    var targetMarker     by remember(mapView) { mutableStateOf<Marker?>(null) }
    var lastJumpId       by remember(mapView) { mutableStateOf(-1L) }
    var reticleMarker    by remember(mapView) { mutableStateOf<Marker?>(null) }
    var mapReady         by remember { mutableStateOf(false) }
    var initialCameraSet by remember(mapView) { mutableStateOf(false) }
    var firstLocateDone  by remember(mapView) { mutableStateOf(false) }
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
    val flags           by viewModel.flags.collectAsState()
    val bearing         by viewModel.bearing.collectAsState()
    val isTracking      by viewModel.isTracking.collectAsState()
    // ================ 测量相关状态 ================
    val measurementMode       by viewModel.measurementMode.collectAsState()
    val measurementState      by viewModel.measurementState.collectAsState()
    val measurementPickMode   by viewModel.measurementPickMode.collectAsState()
    val waypoints             by viewModel.measurementWaypoints.collectAsState()
    val collapsePanelEvent by viewModel.collapsePanelEvent.collectAsState()
    val isMeasuring = measurementState == MapViewModel.MeasurementState.PLACING
    val canCalculate = isMeasurementReady(measurementMode, waypoints.size)

    // ================ 面板折叠状态（统一） ================
    var panelExpanded by remember { mutableStateOf(true) }
    var handledCollapseEvent by remember { mutableStateOf(0L) }

    // ================ 测量模式驱动面板折叠 ================
    LaunchedEffect(isActive, collapsePanelEvent) {
        if (isActive && collapsePanelEvent > handledCollapseEvent) {
            handledCollapseEvent = collapsePanelEvent
            panelExpanded = false
            Toast.makeText(context, "可以开始放点", Toast.LENGTH_SHORT).show()
        }
    }

    // 测量拾取模式开启时自动收起面板，避免遮挡地图触摸
    LaunchedEffect(measurementPickMode) {
        if (measurementPickMode) panelExpanded = false
    }

    // ================ 地图图层切换 + 深色模式适配 ================
    val darkTheme = isSystemInDarkTheme()
    var mapLayer by remember { mutableStateOf(false) } // false=标准，true=卫星
    LaunchedEffect(mapReady, mapLayer, darkTheme) {
        if (!mapReady) return@LaunchedEffect
        aMap?.setMapType(when {
            mapLayer   -> AMap.MAP_TYPE_SATELLITE
            darkTheme  -> AMap.MAP_TYPE_NIGHT
            else       -> AMap.MAP_TYPE_NORMAL
        })
        if (!initialCameraSet) {
            initialCameraSet = true
            aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.0, 104.0), 4.5f))
        }
    }

    // 拾取模式偏移跟踪（分别用于普通拾取和测量拾取）
    var reticleOffset      by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    // 测量拾取：手指与准星的屏幕像素偏移（ACTION_DOWN 时记录，拖动时保持固定）
    var measurePickDelta   by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    // ================ 地图手势：全局禁用旋转；平移在拾取/测量拾取模式下锁定 ================
    LaunchedEffect(aMap) {
        aMap?.uiSettings?.isRotateGesturesEnabled = false
    }
    LaunchedEffect(placeMode, measurementPickMode) {
        aMap?.setMapCustomEnable(placeMode || measurementPickMode)
        aMap?.uiSettings?.setScrollGesturesEnabled(!(placeMode || measurementPickMode))
    }

    // ================ 触摸监听：支持普通拾取和测量拾取双模式 ================
    LaunchedEffect(placeMode, measurementPickMode, aMap) {
        if (aMap == null) return@LaunchedEffect
        val active = placeMode || measurementPickMode
        if (!active) {
            reticleOffset = null
            measurePickDelta = null
            aMap!!.setOnMapTouchListener(null)
            return@LaunchedEffect
        }
        aMap!!.setOnMapTouchListener { event ->
            val projection = aMap!!.projection ?: run { false; return@setOnMapTouchListener }
            val isMeasurePick = measurementPickMode
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isMeasurePick) {
                        // 记录手指初始落点与屏幕中心的偏移，作为准星的固定偏移量
                        measurePickDelta = Pair(event.x - mapView.width / 2f, event.y - mapView.height / 2f)
                    } else {
                        val cx = mapView.width / 2f
                        val cy = mapView.height / 2f
                        reticleOffset = Pair(event.x - cx, event.y - cy)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isMeasurePick) {
                        // 准星始终在手指前方固定距离处，不遮挡视线
                        val delta = measurePickDelta
                        if (delta != null) {
                            val rx = event.x - delta.first
                            val ry = event.y - delta.second
                            val latlng = projection.fromScreenLocation(
                                android.graphics.Point(rx.toInt(), ry.toInt())
                            )
                            latlng?.let { viewModel.setReticleCoord(CT.Coord(it.longitude, it.latitude)) }
                        }
                    } else {
                        val offset = reticleOffset
                        if (offset != null) {
                            val rx = event.x - offset.first
                            val ry = event.y - offset.second
                            val latlng = projection.fromScreenLocation(
                                android.graphics.Point(rx.toInt(), ry.toInt())
                            )
                            latlng?.let { viewModel.setReticleCoord(CT.Coord(it.longitude, it.latitude)) }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val reticle = viewModel.reticleCoord.value
                    if (reticle != null) {
                        if (isMeasurePick) viewModel.addWaypoint(reticle.lon, reticle.lat)
                        else viewModel.confirmPlacement(reticle)
                    }
                    // 准星回中心（与普通拾取模式行为一致）
                    val centerLatlng = projection.fromScreenLocation(
                        android.graphics.Point(mapView.width / 2, mapView.height / 2)
                    )
                    centerLatlng?.let { viewModel.setReticleCoord(CT.Coord(it.longitude, it.latitude)) }
                    if (isMeasurePick) measurePickDelta = null
                    else reticleOffset = null
                    true
                }
                else -> false
            }
        }
    }

    // ================ 长按地图快捷放置（仅非测量、非拾取模式） ================
    LaunchedEffect(aMap, placeMode, measurementPickMode) {
        aMap?.setOnMapLongClickListener { latlng ->
            if (!placeMode && !measurementPickMode) {
                viewModel.confirmPlacement(CT.Coord(latlng.longitude, latlng.latitude))
            }
            true
        }
    }

    // ================ 地图点击监听（非测量、非拾取时传递到旧接口） ================
    LaunchedEffect(aMap, placeMode, measurementPickMode) {
        aMap?.setOnMapClickListener { latlng ->
            when {
                placeMode                                       -> Unit  // 由 OnMapTouchListener 处理
                measurementPickMode                             -> Unit  // 由长按确认，单击忽略
                else                                            -> viewModel.onMapClick(latlng.longitude, latlng.latitude)
            }
            true
        }
    }

    // ================ 当前位置标记（带方向的大图标，不记录到旗标） ================
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
        if (!firstLocateDone) {
            firstLocateDone = true
            amap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
        }
    }

    // ================ 跟踪停止时重置镜头跟随状态 ================
    LaunchedEffect(isTracking) {
        if (!isTracking) firstLocateDone = false
    }

    // ================ 跳转目标（临时红色指示标记 + 镜头移动） ================
    LaunchedEffect(mapReady, jump) {
        val target = jump ?: return@LaunchedEffect
        if (target.id == lastJumpId) return@LaunchedEffect
        lastJumpId = target.id
        val amap = aMap ?: return@LaunchedEffect
        val pos  = LatLng(target.gcj.lat, target.gcj.lon)
        // 移除旧的临时标记
        targetMarker?.remove()
        // 放置新的临时红色指示标记
        targetMarker = amap.addMarker(
            MarkerOptions().position(pos).title("目标点位")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .anchor(0.5f, 0.9f)
        )
        amap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17f))
    }

    // ================ 准星标记（拾取模式 / 测量拾取模式中） ================
    LaunchedEffect(reticleCoord, placeMode, measurementPickMode) {
        if ((!placeMode && !measurementPickMode) || reticleCoord == null) {
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
        flagMarkers.values.forEach { it.remove() }
        val newMap = flags.associate { flag ->
            val pos = LatLng(flag.gcjLat, flag.gcjLon)
            val color = when (flag.type) {
                FlagType.CURRENT  -> Color.CYAN
                FlagType.PICKED   -> Color.rgb(192, 20, 40)
                FlagType.JUMPED   -> Color.RED
            }
            val marker = amap.addMarker(
                MarkerOptions().position(pos)
                    .icon(BitmapDescriptorFactory.fromBitmap(
                        createFlagBitmap(color, flag.customName.ifBlank { flag.label })))
                    .anchor(0.5f, 0.9f)
            )
            flag.id to marker
        }
        flagMarkers = newMap
    }

    // ================ 测量折线 + Waypoint 标记渲染 ================
    var oldMarkers by remember { mutableStateOf<List<Marker>>(emptyList()) }
    var oldPolyline by remember { mutableStateOf<Polyline?>(null) }
    var oldPolygon by remember { mutableStateOf<Polygon?>(null) }
    LaunchedEffect(waypoints, measurementMode, measurementPickMode, mapReady) {
        if (!mapReady) return@LaunchedEffect
        val amap = aMap ?: return@LaunchedEffect
        // 清除旧对象
        oldMarkers.forEach { it.remove() }
        oldPolyline?.remove()
        oldPolygon?.remove()
        if (waypoints.isEmpty()) {
            oldMarkers = emptyList()
            oldPolyline = null
            oldPolygon = null
            return@LaunchedEffect
        }
        val positions = waypoints.map { LatLng(it.gcj.lat, it.gcj.lon) }
        val linePositions = if (
            measurementMode == MapViewModel.MeasurementMode.AREA && positions.size >= 3
        ) positions + positions.first() else positions
        oldPolyline = if (linePositions.size >= 2) {
            amap.addPolyline(PolylineOptions()
                .addAll(linePositions)
                .color(Color.parseColor("#1976D2"))
                .width(6f))
        } else null
        oldPolygon = if (
            measurementMode == MapViewModel.MeasurementMode.AREA && positions.size >= 3
        ) {
            amap.addPolygon(PolygonOptions()
                .addAll(positions)
                .strokeColor(Color.parseColor("#1976D2"))
                .strokeWidth(4f)
                .fillColor(Color.argb(55, 25, 118, 210)))
        } else null
        // 绘制无名称标签的蓝色圆点标记（仅编号）
        oldMarkers = waypoints.mapIndexed { i, wp ->
            amap.addMarker(MarkerOptions()
                .position(LatLng(wp.gcj.lat, wp.gcj.lon))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .anchor(0.5f, 0.5f))
        }
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

    // ================ 启动自动定位 ================
    var hasAutoLocateStarted by remember { mutableStateOf(false) }
    LaunchedEffect(hasAutoLocateStarted, permissionLauncher) {
        if (hasAutoLocateStarted) return@LaunchedEffect
        hasAutoLocateStarted = true
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            viewModel.locate()
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ))
        }
    }

    val onClickLocate = {
        firstLocateDone = false
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
                    // 图层切换按钮（标准 / 卫星），位于右上角
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        LayerToggleButton(
                            isSatellite = mapLayer,
                            onClick     = { mapLayer = !mapLayer },
                        )
                    }
                    // 测量拾取模式：显示 Close 按钮；否则显示 PickModeFab
                    if (measurementPickMode) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            FloatingActionButton(
                                onClick = {
                                    val center = aMap?.cameraPosition?.target?.let {
                                        CT.Coord(it.longitude, it.latitude)
                                    }
                                    viewModel.toggleMeasurementPickMode(initCoord = center)
                                },
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(androidx.compose.ui.Alignment.TopEnd),
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor   = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "退出拾取测点")
                            }
                        }
                    } else {
                        // 非测量拾取时：根据是否在测量中决定按钮行为
                        PickModeFab(
                            placeMode = placeMode,
                            onToggle  = {
                                if (isMeasuring) {
                                    // 测量中：切换为测量拾取模式（放置测量断点）
                                    val center = aMap?.cameraPosition?.target?.let {
                                        CT.Coord(it.longitude, it.latitude)
                                    }
                                    viewModel.toggleMeasurementPickMode(initCoord = center)
                                } else {
                                    val center = aMap?.cameraPosition?.target?.let {
                                        CT.Coord(it.longitude, it.latitude)
                                    }
                                    viewModel.togglePlaceMode(initCoord = center)
                                }
                            },
                        )
                    }
                    if (isMeasuring && canCalculate) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ExtendedFloatingActionButton(
                                onClick = { viewModel.completeMeasurement() },
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(androidx.compose.ui.Alignment.BottomEnd),
                                containerColor = MaterialTheme.colorScheme.primary,
                                icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                                text = { Text("开始计算") },
                            )
                        }
                    }
                }

                // -------- 统一面板（当前位置 + 坐标输入） --------
                UnifiedPanel(
                    expanded       = panelExpanded,
                    onToggle       = { panelExpanded = !panelExpanded },
                    gcj            = gcj,
                    wgs            = wgs,
                    accuracyMeters = accuracyMeters,
                    locating       = locating,
                    onLocate       = { onClickLocate() },
                    lonText        = lonText,
                    latText        = latText,
                    coordType      = coordType,
                    onLonChange    = { viewModel.updateLonText(it) },
                    onLatChange    = { viewModel.updateLatText(it) },
                    onCoordType    = { viewModel.setCoordType(it) },
                    onJumpTo       = { viewModel.jumpTo() },
                )
            }
        }
    }
}

// ============================================================================
// 统一面板：当前位置信息 + 经纬度输入，共用折叠/展开
// ============================================================================

@Composable
private fun UnifiedPanel(
    expanded       : Boolean,
    onToggle       : () -> Unit,
    gcj            : CT.Coord?,
    wgs            : CT.Coord?,
    accuracyMeters : Float?,
    locating       : Boolean,
    onLocate       : () -> Unit,
    lonText        : String,
    latText        : String,
    coordType      : CoordType,
    onLonChange    : (String) -> Unit,
    onLatChange    : (String) -> Unit,
    onCoordType    : (CoordType) -> Unit,
    onJumpTo       : () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()) {
            // -------- 折叠栏（始终显示） --------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
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

            // -------- 展开内容 --------
            if (expanded) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    // 当前位置坐标
                    CoordsRow(label = "GCJ02", coord = gcj,
                        color = MaterialTheme.colorScheme.primary)
                    CoordsRow(label = "WGS84", coord = wgs,
                        color = MaterialTheme.colorScheme.tertiary)
                    AccuracyRow(meters = accuracyMeters)

                    // 分隔线
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ) {}
                    Spacer(Modifier.height(2.dp))

                    // 经纬度输入
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value        = lonText,
                            onValueChange = onLonChange,
                            modifier     = Modifier.weight(1f),
                            label        = { FText("经度", 11) },
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
                            label        = { FText("纬度", 11) },
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

                    // 坐标类型 + 跳转按钮
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
    }
}

@Composable
private fun CoordsRow(
    label : String,
    coord : CT.Coord?,
    color : androidx.compose.ui.graphics.Color,
) {
    val context = LocalContext.current
    val text = coord?.let { "%.6f,%.6f".format(it.lon, it.lat) } ?: "--"
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

// ============================================================================
// 拾取模式浮动按钮
// ============================================================================

@Composable
private fun PickModeFab(placeMode: Boolean, onToggle: () -> Unit) {
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
// 图层切换按钮（标准 / 卫星）
// ============================================================================

@Composable
private fun LayerToggleButton(isSatellite: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor   = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.Tune,
            contentDescription = if (isSatellite) "切换到标准地图" else "切换到卫星地图",
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isSatellite) "标准" else "卫星",
            fontSize = 12.sp,
        )
    }
}

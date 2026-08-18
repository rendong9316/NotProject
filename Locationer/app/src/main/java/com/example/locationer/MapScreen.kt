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
import android.text.StaticLayout
import android.text.TextPaint
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
import androidx.compose.material3.HorizontalDivider
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
import com.amap.api.maps.model.CameraPosition
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
fun FText(
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

/** 绘制旗标图标：带标签的圆点，支持任意长度名称和字号（单行显示） */
private data class FlagBitmap(
    val bitmap: Bitmap,
    val anchorX: Float,
    val anchorY: Float,
)

private fun createFlagBitmap(color: Int, label: String, iconRadius: Float = 28f, textSize: Float = 30f, textColor: Int = Color.WHITE): FlagBitmap {
    // 边距常量
    val PADDING_X = 24f      // 左右边距
    val PADDING_Y_TOP = 16f  // 上方预留
    val PADDING_Y_BOTTOM = 24f // 下方预留（圆圈底部以下）

    // 文字画笔
    val textPaint = TextPaint().apply {
        this.color = textColor
        this.textSize = textSize
        this.isAntiAlias = true
        this.textAlign = Paint.Align.CENTER
    }

    // 计算文字单行宽度
    val textWidth = textPaint.measureText(label)
    // Bitmap 宽度 = 文字宽度 + 左右边距，最小 64px
    val bmpWidth = (textWidth + PADDING_X * 2).toInt().coerceAtLeast(64)
    val circleCenterX = bmpWidth / 2f

    // 文字高度
    val textHeight = textPaint.descent() - textPaint.ascent()

    // 计算 Bitmap 高度：文字区域 + 圆圈 + 边距
    val circleTopY = PADDING_Y_TOP + textHeight + 8f
    val bmpHeight = (circleTopY + iconRadius * 2 + PADDING_Y_BOTTOM + 8f).toInt()

    val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)

    // 绘制圆圈（居中于文字下方）
    val circlePaint = Paint().apply { this.color = color; style = Paint.Style.FILL }
    c.drawCircle(circleCenterX, circleTopY + iconRadius, iconRadius, circlePaint)

    // 绘制单行文字
    val textY = PADDING_Y_TOP + textHeight - textHeight / 2f
    c.drawText(label, circleCenterX, textY, textPaint)

    return FlagBitmap(
        bitmap = bmp,
        anchorX = circleCenterX / bmpWidth,
        anchorY = (circleTopY + iconRadius) / bmpHeight,
    )
}

// ============================================================================
// 主页面
// ============================================================================

@Composable
fun MapScreen(
    modifier  : Modifier = Modifier,
    viewModel : MapViewModel = viewModel(),
    isActive  : Boolean = true,
    flagStyle : FlagStyle = FlagStyle(),
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
    val accuracyMode    by viewModel.accuracyMode.collectAsState()
    val placeMode       by viewModel.placeMode.collectAsState()
    val reticleCoord    by viewModel.reticleCoord.collectAsState()
    val flags           by viewModel.flags.collectAsState()
    val bearing         by viewModel.bearing.collectAsState()
    val isTracking      by viewModel.isTracking.collectAsState()
    val address         by viewModel.reverseGeocodeAddress.collectAsState()
    val searchQuery     by viewModel.searchQuery.collectAsState()
    val searchResults   by viewModel.searchResults.collectAsState()
    val searching       by viewModel.searching.collectAsState()
    val showPickedFlagLabels by viewModel.showPickedFlagLabels.collectAsState()
    // ================ 测量相关状态 ================
    val measurementMode       by viewModel.measurementMode.collectAsState()
    val measurementState      by viewModel.measurementState.collectAsState()
    val measurementPickMode   by viewModel.measurementPickMode.collectAsState()
    val waypoints             by viewModel.measurementWaypoints.collectAsState()
    val collapsePanelEvent by viewModel.collapsePanelEvent.collectAsState()
    val isReplaying        by viewModel.isReplaying.collectAsState()
    val replayRecord       by viewModel.replayRecord.collectAsState()
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
    var mapLayer by remember {
        mutableStateOf(
            context.getSharedPreferences("map_screen", android.content.Context.MODE_PRIVATE)
                .getBoolean("mapLayer", false)
        )
    }
    LaunchedEffect(mapLayer) {
        context.getSharedPreferences("map_screen", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("mapLayer", mapLayer).apply()
    }
    LaunchedEffect(mapReady, mapLayer, darkTheme) {
        if (!mapReady) return@LaunchedEffect
        // 地图就绪后初始化逆地理编码（SDK内部依赖地图服务）
        viewModel.initGeocodeSearch()
        aMap?.setMapType(when {
            mapLayer     -> AMap.MAP_TYPE_SATELLITE
            darkTheme    -> AMap.MAP_TYPE_NIGHT
            else         -> AMap.MAP_TYPE_NORMAL
        })
        if (!initialCameraSet) {
            initialCameraSet = true
            // 优先恢复上次退出时的相机高度；否则使用默认值 4.5f（全国视角）
            val initZoom = viewModel.cameraZoom.value ?: 4.5f
            aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.0, 104.0), initZoom))
        }
    }

    // 拾取模式偏移跟踪（分别用于普通拾取和测量拾取）
    var reticleOffset      by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    // 测量拾取：手指与准星的屏幕像素偏移（ACTION_DOWN 时记录，拖动时保持固定）
    var measurePickDelta   by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    // 回放模式：是否显示"返回历史"按钮（在 measurement card 收起时不可见）
    var showReturnBtn      by remember { mutableStateOf(false) }

    // ================ 地图手势：全局禁用旋转；平移在拾取/测量拾取模式下锁定 ================
    LaunchedEffect(aMap) {
        aMap?.uiSettings?.isRotateGesturesEnabled = false
        try {
            aMap?.uiSettings?.javaClass?.getMethod("setLogoEnable", Boolean::class.java)?.invoke(aMap.uiSettings, false)
        } catch (_: Exception) {}
    }

    // ================ 相机缩放级别持久化：监听镜头变化，结束时保存当前 zoom ================
    LaunchedEffect(aMap) {
        aMap?.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition?) {}
            override fun onCameraChangeFinish(position: CameraPosition?) {
                position?.let { viewModel.saveCameraZoom(it.zoom) }
            }
        })
    }
    LaunchedEffect(placeMode, measurementPickMode, isReplaying) {
        aMap?.setMapCustomEnable(placeMode || measurementPickMode || isReplaying)
        aMap?.uiSettings?.setScrollGesturesEnabled(!(placeMode || measurementPickMode))
    }

    // ================ 触摸监听：支持普通拾取和测量拾取双模式（回放模式不拦截触摸） ================
    LaunchedEffect(placeMode, measurementPickMode, isReplaying, aMap) {
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
                    viewModel.vibratePick()
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
                    viewModel.vibratePick()
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

    // ================ 长按地图：测量模式下放测点，回放模式禁止操作，否则放普通旗标 ================
    LaunchedEffect(aMap, placeMode, measurementPickMode, measurementState, isReplaying) {
        aMap?.setOnMapLongClickListener { latlng ->
            when {
                isReplaying                                  -> Unit
                placeMode || measurementPickMode             -> Unit  // 拾取模式由 OnMapTouchListener 处理
                measurementState == MapViewModel.MeasurementState.PLACING -> {
                    viewModel.addWaypoint(latlng.longitude, latlng.latitude)
                    viewModel.vibratePick()
                }
                else -> {
                    viewModel.confirmPlacement(CT.Coord(latlng.longitude, latlng.latitude))
                    viewModel.vibratePick()
                }
            }
            true
        }
    }

    // ================ 地图点击监听（回放模式禁止操作） ================
    LaunchedEffect(aMap, placeMode, measurementPickMode, measurementState, isReplaying) {
        aMap?.setOnMapClickListener { latlng ->
            when {
                isReplaying                                    -> Unit
                placeMode                                       -> Unit  // 由 OnMapTouchListener 处理
                measurementPickMode                             -> Unit  // 由长按确认，单击忽略
                measurementState == MapViewModel.MeasurementState.PLACING ->
                    viewModel.addWaypoint(latlng.longitude, latlng.latitude)
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
            // 保持当前相机高度，不主动改变缩放级别；首次启动无持久化值时使用默认 17f
            val currentZoom = amap.cameraPosition?.zoom ?: viewModel.cameraZoom.value ?: 17f
            amap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, currentZoom))
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
                .anchor(0.5f, 0.5f)
        )
        // 保持当前相机高度，不主动改变缩放级别；首次启动无持久化值时使用默认 17f
        val currentZoom = amap.cameraPosition?.zoom ?: viewModel.cameraZoom.value ?: 17f
        amap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, currentZoom))
    }

    // ================ 历史测量回看镜头：移动到所有节点的质心，保持当前缩放级别 ================
    LaunchedEffect(mapReady, isReplaying, replayRecord) {
        if (!mapReady || !isReplaying) return@LaunchedEffect
        val points = replayRecord?.waypoints.orEmpty()
        if (points.isEmpty()) return@LaunchedEffect
        val amap = aMap ?: return@LaunchedEffect
        val center = LatLng(
            points.map { it.lat }.average(),
            points.map { it.lon }.average(),
        )
        val currentZoom = amap.cameraPosition?.zoom ?: viewModel.cameraZoom.value ?: 17f
        amap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, currentZoom))
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

    // ================ 旗标标记渲染（支持自定义样式） ================
    LaunchedEffect(flags, mapReady, flagStyle, showPickedFlagLabels) {
        if (!mapReady) return@LaunchedEffect
        val amap = aMap ?: return@LaunchedEffect
        flagMarkers.values.forEach { it.remove() }
        val newMap = flags.associate { flag ->
            val pos = LatLng(flag.gcjLat, flag.gcjLon)
            val defaultColor = when (flag.type) {
                FlagType.CURRENT  -> Color.CYAN
                FlagType.PICKED   -> Color.rgb(192, 20, 40)
                FlagType.JUMPED   -> Color.RED
            }
            val iconColor = if (flag.type == FlagType.CURRENT) {
                defaultColor
            } else {
                flagStyle.flagIconColor.toInt()
            }
            val flagBitmap = createFlagBitmap(
                color = iconColor,
                // 只对 PICKED 类型旗标受开关影响；CURRENT/JUMPED 始终显示标签
                label = when {
                    flag.type == FlagType.PICKED && !showPickedFlagLabels -> ""
                    else                                                 -> flag.customName.ifBlank { flag.label }
                },
                iconRadius = flagStyle.flagIconSize,
                textSize   = flagStyle.flagTextSize,
                textColor  = flagStyle.flagTextColor.toInt(),
            )
            val marker = amap.addMarker(
                MarkerOptions().position(pos)
                    .icon(BitmapDescriptorFactory.fromBitmap(flagBitmap.bitmap))
                    .anchor(flagBitmap.anchorX, flagBitmap.anchorY)
            )
            flag.id to marker
        }
        flagMarkers = newMap
    }

    // ================ 测量折线 + Waypoint 标记渲染（支持正常测量 + 历史回放） ================
    var oldMarkers by remember { mutableStateOf<List<Marker>>(emptyList()) }
    var oldPolyline by remember { mutableStateOf<Polyline?>(null) }
    var oldPolygon by remember { mutableStateOf<Polygon?>(null) }
    LaunchedEffect(waypoints, measurementMode, measurementPickMode, mapReady, flagStyle, replayRecord, isReplaying) {
        if (!mapReady) return@LaunchedEffect
        val amap = aMap ?: return@LaunchedEffect
        // 清除旧对象
        oldMarkers.forEach { it.remove() }
        oldPolyline?.remove()
        oldPolygon?.remove()

        // 优先使用回放数据，其次使用当前测量数据
        val points = if (isReplaying && replayRecord != null) {
            replayRecord!!.waypoints
        } else {
            waypoints.map { it.gcj }
        }
        if (points.isEmpty()) {
            oldMarkers = emptyList()
            oldPolyline = null
            oldPolygon = null
            return@LaunchedEffect
        }
        val positions = points.map { LatLng(it.lat, it.lon) }
        val linePositions = if (
            (measurementMode == MapViewModel.MeasurementMode.AREA ||
             (isReplaying && replayRecord!!.mode == "AREA")) && positions.size >= 3
        ) positions + positions.first() else positions
        oldPolyline = if (linePositions.size >= 2) {
            amap.addPolyline(PolylineOptions()
                .addAll(linePositions)
                .color(Color.parseColor("#1976D2"))
                .width(6f))
        } else null
        oldPolygon = if (
            (measurementMode == MapViewModel.MeasurementMode.AREA ||
             (isReplaying && replayRecord!!.mode == "AREA")) && positions.size >= 3
        ) {
            amap.addPolygon(PolygonOptions()
                .addAll(positions)
                .strokeColor(Color.parseColor("#1976D2"))
                .strokeWidth(4f)
                .fillColor(Color.argb(55, 25, 118, 210)))
        } else null
        // 绘制与旗标同款的圆形图标标记（含序号标签）
        oldMarkers = points.mapIndexed { i, pt ->
            val label = "${i + 1}"
            val bmp = createFlagBitmap(
                color      = flagStyle.waypointIconColor.toInt(),
                label      = label,
                iconRadius = flagStyle.flagIconSize,
                textSize   = flagStyle.flagTextSize,
                textColor  = flagStyle.flagTextColor.toInt(),
            )
            amap.addMarker(MarkerOptions()
                .position(LatLng(pt.lat, pt.lon))
                .icon(BitmapDescriptorFactory.fromBitmap(bmp.bitmap))
                .anchor(bmp.anchorX, bmp.anchorY))
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
        Scaffold(
            modifier = modifier.fillMaxSize(),
            // The activity's bottom navigation owns the navigation-bar inset.
            // Keep only the top inset here so the panel can sit directly above it.
            contentWindowInsets = WindowInsets.statusBars,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Bottom
            ) {
                // -------- 地图区域 --------
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier.fillMaxSize(),
                        update    = { mapReady = true },
                    )
                    // 图层切换按钮（标准 / 卫星 / 离线），位于右上角
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        LayerToggleButton(
                            isSatellite = mapLayer,
                            onClick     = { mapLayer = !mapLayer },
                        )
                    }
                    // 右上垂直按钮组：拾取（放大镜/关闭）+ 隐藏/显示旗标标签（始终两按钮共存）
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.TopEnd)
                                .padding(end = 16.dp, top = 16.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.End,
                        ) {
                            PickModeFab(
                                placeMode     = placeMode,
                                measureMode   = measurementPickMode,
                                onTogglePlace = {
                                    val center = aMap?.cameraPosition?.target?.let {
                                        CT.Coord(it.longitude, it.latitude)
                                    }
                                    // 测距模式下优先开启测量拾取，而非旗标拾取
                                    if (isMeasuring && !measurementPickMode) {
                                        viewModel.toggleMeasurementPickMode(initCoord = center)
                                    } else {
                                        viewModel.togglePlaceMode(initCoord = center)
                                    }
                                },
                                onToggleMeasure = {
                                    val center = aMap?.cameraPosition?.target?.let {
                                        CT.Coord(it.longitude, it.latitude)
                                    }
                                    viewModel.toggleMeasurementPickMode(initCoord = center)
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                            VisibilityToggleFab(
                                visible = showPickedFlagLabels,
                                onClick = { viewModel.toggleShowPickedFlagLabels() },
                            )
                        }
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
                    // 回放模式下显示"返回历史"按钮
                    if (isReplaying) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    viewModel.stopReplay()
                                    viewModel.requestSwitchToMeasurementHistory()
                                },
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(androidx.compose.ui.Alignment.BottomEnd),
                                containerColor = MaterialTheme.colorScheme.secondary,
                                icon = { Icon(Icons.Filled.Straighten, contentDescription = null) },
                                text = { Text("返回历史") },
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
                    accuracyMode   = accuracyMode,
                    locating       = locating,
                    onLocate       = { onClickLocate() },
                    lonText        = lonText,
                    latText        = latText,
                    coordType      = coordType,
                    onLonChange    = { viewModel.updateLonText(it) },
                    onLatChange    = { viewModel.updateLatText(it) },
                    onPasteCoordParsed = { lon, lat -> viewModel.showPasteSplitTip(lon, lat) },
                    onCoordType    = { viewModel.setCoordType(it) },
                    onJumpTo       = { viewModel.jumpTo() },
                    address        = address,
                    jumpTarget     = jump,
                    onReverseGeocode = {
                        val gcjForAddr = jump?.gcj ?: gcj
                        if (gcjForAddr == null) {
                            Toast.makeText(context, "请先输入坐标并跳转定位", Toast.LENGTH_SHORT).show()
                            return@UnifiedPanel
                        }
                        viewModel.fetchReverseGeocode(gcjForAddr.lon, gcjForAddr.lat)
                    },
                    searchQuery    = searchQuery,
                    searchResults  = searchResults,
                    searching      = searching,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onSearch = { viewModel.searchAddress() },
                    onSelectResult = { viewModel.selectSearchResult(it) },
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
    accuracyMode   : String?,
    locating       : Boolean,
    onLocate       : () -> Unit,
    lonText        : String,
    latText        : String,
    coordType      : CoordType,
    onLonChange    : (String) -> Unit,
    onLatChange    : (String) -> Unit,
    onPasteCoordParsed: (lon: Double, lat: Double) -> Unit = { _, _ -> },
    onCoordType    : (CoordType) -> Unit,
    onJumpTo       : () -> Unit,
    jumpTarget     : JumpTarget?,
    address        : String?,
    onReverseGeocode: () -> Unit,
    searchQuery    : String,
    searchResults  : List<SearchResult>,
    searching      : Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearch       : () -> Unit,
    onSelectResult : (SearchResult) -> Unit,
) {
    val _jt = jumpTarget
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.imePadding()) {
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
                    // 地址搜索
                    FText("搜索地址", 13, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value        = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier     = Modifier.fillMaxWidth(),
                        label        = { FText("输入地址关键词", 11) },
                        placeholder  = { FText("如：北京市海淀区", 12) },
                        singleLine   = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        trailingIcon = {
                            if (searching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = "搜索",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable(enabled = !searching && searchQuery.isNotBlank(), onClick = onSearch),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor      = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor    = MaterialTheme.colorScheme.outline,
                            disabledIndicatorColor     = MaterialTheme.colorScheme.outline,
                            focusedContainerColor      = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor    = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    // 搜索结果列表（LazyColumn 自管理滚动，避免与外层滚动冲突）
                    if (searchResults.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                itemsIndexed(searchResults, key = { _, result ->
                                    "${result.gcjLon}_${result.gcjLat}"
                                }) { index, result ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = { onSelectResult(result) })
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = result.title,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    if (index < searchResults.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }

                    // 分隔线
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CoordsRow(label = "GCJ02", coord = gcj,
                        color = MaterialTheme.colorScheme.primary)
                    CoordsRow(label = "WGS84", coord = wgs,
                        color = MaterialTheme.colorScheme.tertiary)
                    AccuracyRow(meters = accuracyMeters, mode = accuracyMode)

                    // 分隔线
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ) {}
                    Spacer(Modifier.height(2.dp))

                    // 经纬度输入（本地 TextFieldValue 状态保留光标位置）
                    var lonTFValue by remember { mutableStateOf(TextFieldValue(lonText)) }
                    var latTFValue by remember { mutableStateOf(TextFieldValue(latText)) }
                    LaunchedEffect(lonText) { lonTFValue = TextFieldValue(lonText, lonTFValue.selection) }
                    LaunchedEffect(latText) { latTFValue = TextFieldValue(latText, latTFValue.selection) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value        = lonTFValue,
                            onValueChange = { newV: TextFieldValue ->
                                lonTFValue = newV
                                handleCoordinateInput(
                                    text = newV.text,
                                    onCurrentChange = onLonChange,
                                    onLonChange = onLonChange,
                                    onLatChange = onLatChange,
                                    onParsed = onPasteCoordParsed,
                                )
                            },
                            modifier     = Modifier
                                .weight(1f),
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
                            value        = latTFValue,
                            onValueChange = { newV: TextFieldValue ->
                                latTFValue = newV
                                handleCoordinateInput(
                                    text = newV.text,
                                    onCurrentChange = onLatChange,
                                    onLonChange = onLonChange,
                                    onLatChange = onLatChange,
                                    onParsed = onPasteCoordParsed,
                                )
                            },
                            modifier     = Modifier
                                .weight(1f),
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

                    // 坐标类型 + 跳转按钮 + 地址解析按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FText("坐标类型", 14, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        CoordRadio("GCJ02", coordType == CoordType.GCJ02) { onCoordType(CoordType.GCJ02) }
                        CoordRadio("WGS84", coordType == CoordType.WGS84) { onCoordType(CoordType.WGS84) }
                        Spacer(Modifier.weight(1f))
                        val hasCoordinate = _jt?.gcj != null || gcj != null
                        Button(
                            onClick = onReverseGeocode,
                            enabled = hasCoordinate && (address.isNullOrEmpty() || address == "解析中…"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Filled.LocationOn,
                                contentDescription = "逆地理编码", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            FText("解析地址", 12)
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = onJumpTo,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardReturn,
                                contentDescription = "跳转定位", modifier = Modifier.size(16.dp))
                        }
                    }

                    // 逆地理编码结果（显示在输入框下方）
                    if (address != null) {
                        Spacer(Modifier.height(2.dp))
                        val ctx = LocalContext.current
                        val cm = remember { ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("地址", address))
                                    Toast.makeText(ctx, "已复制地址", Toast.LENGTH_SHORT).show()
                                },
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(6.dp))
                                FText(
                                    text = address,
                                    fontSize = 13,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                )
                            }
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
        FText(label, 14, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            FText(text = text, fontSize = 14, fontFamily = FontFamily.Monospace,
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
private fun AccuracyRow(meters: Float?, mode: String?) {
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
        Column(modifier = Modifier.weight(1f)) {
            FText("定位精度", 14, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!mode.isNullOrBlank()) {
                FText(mode, 12, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        SelectionContainer {
            FText("${info.label}  ${meters?.toInt()?.toString() ?: "--"} m",
                fontSize = 14, fontWeight = FontWeight.SemiBold, color = info.textColor)
        }
    }
    if (info.subtext.isNotEmpty()) {
        Spacer(Modifier.height(2.dp))
        FText(info.subtext, 14, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun CoordRadio(label    : String, selected : Boolean, onClick  : () -> Unit) {
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
// 坐标粘贴自动解析（T5）
// ============================================================================

/**
 * 在原生输入框已经接收到文本后识别完整坐标。
 * 普通输入和单值粘贴继续交给原有输入回调；只有完整坐标才拆分到两个输入框。
 */
private fun handleCoordinateInput(
    text: String,
    onCurrentChange: (String) -> Unit,
    onLonChange: (String) -> Unit,
    onLatChange: (String) -> Unit,
    onParsed: (Double, Double) -> Unit,
) {
    val result = if (hasCoordinatePairSeparator(text)) parseCoordFromPaste(text) else null
    if (result == null) {
        onCurrentChange(text)
        return
    }
    onLonChange("%.6f".format(result.lon))
    onLatChange("%.6f".format(result.lat))
    onParsed(result.lon, result.lat)
}

private fun hasCoordinatePairSeparator(text: String): Boolean =
    text.any { it == ',' || it == '，' || it == ';' || it == '；' || it == '\t' || it == '\n' || it == ' ' } ||
        text.contains(":") || text.contains("：")

/**
 * 从粘贴文本中解析出 (lon, lat)。
 * 支持：逗号 / 中文逗号 / 分号 / 空格 / 制表符 分隔。
 * 优先级：带前缀关键字 > 范围启发式 > 默认顺序（第一值=经度）。
 * 解析成功返回 Coord，失败返回 null。
 */
internal fun parseCoordFromPaste(raw: String): CT.Coord? {
    // 支持逗号、中文逗号、分号、空格、换行，以及“经度: ... 纬度: ...”等标签格式。
    val normalized = raw.trim()
        .replace('，', ',')
        .replace('；', ',')
        .replace(';', ',')
        .replace('\t', ',')
        .replace('\n', ',')
        .replace('\r', ',')
    val withoutCrsLabel = normalized.replace(
        Regex("(?i)(?:gcj|wgs)\\s*[-_]?\\s*\\d+"),
        "",
    )
    val numberPattern = Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")
    val values = numberPattern.findAll(withoutCrsLabel)
        .mapNotNull { it.value.toDoubleOrNull() }
        .take(2)
        .toList()
    if (values.size < 2) return null

    val d0 = values[0]
    val d1 = values[1]

    // 关键字识别：关键词所在文本片段的数字顺序对应经纬度顺序。
    val lower = normalized.lowercase()
    val lonIndex = listOf("经度", "longitude", "lon", "lng", "x")
        .map { keyword -> lower.indexOf(keyword) }
        .filter { it >= 0 }
        .minOrNull() ?: -1
    val latIndex = listOf("纬度", "latitude", "lat", "y")
        .map { keyword -> lower.indexOf(keyword) }
        .filter { it >= 0 }
        .minOrNull() ?: -1

    val (lon, lat) = when {
        lonIndex >= 0 && latIndex >= 0 && lonIndex < latIndex -> d0 to d1
        lonIndex >= 0 && latIndex >= 0 && latIndex < lonIndex -> d1 to d0
        lonIndex >= 0 -> d0 to d1
        latIndex >= 0 -> d1 to d0
        else -> {
            // 范围启发式：0~180 是经度，0~90 是纬度
            val abs0 = kotlin.math.abs(d0)
            val abs1 = kotlin.math.abs(d1)
            val lonOnly0 = abs0 in 0.0..180.0 && abs0 > 90.0
            val latOnly0 = abs0 in 0.0..90.0
            val lonOnly1 = abs1 in 0.0..180.0 && abs1 > 90.0
            val latOnly1 = abs1 in 0.0..90.0
            when {
                lonOnly0 && latOnly1 -> d0 to d1
                latOnly0 && lonOnly1 -> d1 to d0
                else                 -> d0 to d1  // 默认：第一值=经度
            }
        }
    }

    // 4. 合法性校验
    if (kotlin.math.abs(lat) > 90.0) return null
    if (kotlin.math.abs(lon) > 180.0) return null
    return CT.Coord(lon, lat)
}

// ============================================================================
// 拾取模式浮动按钮
// ============================================================================

/** 显示/隐藏拾取旗标名称的按钮，复用 PickModeFab 同款 FloatingAction */
@Composable
private fun VisibilityToggleFab(visible: Boolean, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor   = MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(
            imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
            contentDescription = if (visible) "隐藏旗标名称" else "显示旗标名称",
        )
    }
}

@Composable
private fun PickModeFab(
    placeMode     : Boolean,
    measureMode   : Boolean,
    onTogglePlace : () -> Unit,
    onToggleMeasure: () -> Unit,
) {
    val active  = placeMode || measureMode
    val isError = active
    FloatingActionButton(
        onClick = {
            if (measureMode) onToggleMeasure()
            else onTogglePlace()
        },
        modifier = Modifier.size(48.dp),
        containerColor = if (isError) MaterialTheme.colorScheme.error
                         else MaterialTheme.colorScheme.primary,
        contentColor   = MaterialTheme.colorScheme.onPrimary,
    ) {
        if (active)
            Icon(Icons.Filled.Close, contentDescription = "退出拾取")
        else
            Icon(Icons.Filled.Search, contentDescription = "拾取坐标")
    }
}

// ============================================================================
// 图层切换按钮（标准 / 卫星 / 离线）
// ============================================================================

@Composable
private fun LayerToggleButton(
    isSatellite : Boolean,
    onClick     : () -> Unit,
) {
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
            fontSize = 14.sp,
        )
    }
}

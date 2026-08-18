package com.example.locationer

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.location.ISensorListenerDelegate
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.poisearch.PoiSearchV2
import com.amap.api.services.poisearch.PoiResultV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap

internal fun minimumMeasurementPoints(mode: MapViewModel.MeasurementMode): Int = when (mode) {
    MapViewModel.MeasurementMode.DISTANCE -> 2
    MapViewModel.MeasurementMode.AREA -> 3
}

internal fun isMeasurementReady(mode: MapViewModel.MeasurementMode, pointCount: Int): Boolean =
    pointCount >= minimumMeasurementPoints(mode)

internal fun measurementPolygonAreaMeters(points: List<CT.Coord>): Double {
    if (points.size < 3) return 0.0
    val radius = 6371000.0
    val originLon = points.map { it.lon }.average() / 180.0 * CT.PI
    val originLat = points.map { it.lat }.average() / 180.0 * CT.PI
    val projected = points.map { point ->
        val lon = point.lon / 180.0 * CT.PI
        val lat = point.lat / 180.0 * CT.PI
        Pair(
            (lon - originLon) * kotlin.math.cos(originLat) * radius,
            (lat - originLat) * radius,
        )
    }
    var twiceArea = 0.0
    projected.indices.forEach { index ->
        val current = projected[index]
        val next = projected[(index + 1) % projected.size]
        twiceArea += current.first * next.second - next.first * current.second
    }
    return kotlin.math.abs(twiceArea) / 2.0
}

/** 输入坐标类型：GCJ02（高德火星坐标）/ WGS84（GPS 原始坐标） */
enum class CoordType { GCJ02, WGS84 }

/** 跳转定位指令：携带转换后的 GCJ02 目标坐标，供地图打点与镜头移动 */
data class JumpTarget(
    val id: Long,          // 自增序号，区分同坐标的重复跳转
    val gcj: CT.Coord,     // 目标点位 GCJ02 坐标（地图渲染专用）
    val typed: CT.Coord,   // 用户输入的原始坐标
    val type: CoordType,   // 用户选择的输入坐标类型
)

/** UI 提示消息（Toast 展示） */
data class UiMessage(val text: String)

/** 正向地理编码搜索结果 */
data class SearchResult(
    val title: String,
    val address: String,
    val gcjLon: Double,
    val gcjLat: Double,
)

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val _context = application

    /** 拾取/打点场景震动反馈：按下时 + 放置完成时各触发一次 */
    fun vibratePick() {
        runCatching {
            val vibrator = _context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return@runCatching
            if (!vibrator.hasVibrator()) return@runCatching
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        }
    }

    enum class NavigationTarget { NONE, MAP, TOOLS_MEASUREMENT, MY_FAVORITES, MEASUREMENT_HISTORY }
    data class NavigationEvent(val id: Long = 0L, val target: NavigationTarget = NavigationTarget.NONE)

    private val _currentGcj   = MutableStateFlow<CT.Coord?>(null)
    val currentGcj: StateFlow<CT.Coord?>   = _currentGcj.asStateFlow()

    private val _currentWgs   = MutableStateFlow<CT.Coord?>(null)
    val currentWgs: StateFlow<CT.Coord?>   = _currentWgs.asStateFlow()

    private val _jumpTarget   = MutableStateFlow<JumpTarget?>(null)
    val jumpTarget: StateFlow<JumpTarget?> = _jumpTarget.asStateFlow()

    /** 逆地理编码结果（地址文本），null 表示尚未请求或请求失败 */
    private val _reverseGeocodeAddress = MutableStateFlow<String?>(null)
    val reverseGeocodeAddress: StateFlow<String?> = _reverseGeocodeAddress.asStateFlow()

    /** 记录最后一次已请求的逆地理编码参数，用于缓存判断 */
    private var lastGeocodeKey: String = ""

    /** 记录最后一次搜索的时间，防止请求过于频繁 */
    private var lastSearchTime = 0L
    private val SEARCH_COOLDOWN_MS = 1000L // 搜索间隔至少 1 秒

    /** 搜索缓存：关键词 -> 结果列表，避免重复请求触发限流 */
    private val _searchCache = LinkedHashMap<String, List<SearchResult>>()
    private val SEARCH_CACHE_MAX_SIZE = 20 // 最多缓存 20 条

    /** 正向地理编码搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 搜索结果列表 */
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    /** 搜索是否进行中 */
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _accuracyMeters = MutableStateFlow<Float?>(null)
    val accuracyMeters: StateFlow<Float?> = _accuracyMeters.asStateFlow()

    private val _accuracyMode = MutableStateFlow<String?>(null)
    val accuracyMode: StateFlow<String?> = _accuracyMode.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    /** 当前设备朝向（0-360°，0=正北，顺时针），null 表示尚未获取 */
    private val _bearing = MutableStateFlow<Float?>(null)
    val bearing: StateFlow<Float?> = _bearing.asStateFlow()

    private val _lonText  = MutableStateFlow(loadPref("lonText", ""))
    val lonText: StateFlow<String> = _lonText.asStateFlow()
    private val _latText  = MutableStateFlow(loadPref("latText", ""))
    val latText: StateFlow<String> = _latText.asStateFlow()

    /** 记录每次剪贴板内容的快照，用于 LaunchedEffect 检测真正的粘贴操作 */
    private val _pasteBuffer = MutableStateFlow("")
    val pasteBuffer: StateFlow<String> = _pasteBuffer.asStateFlow()

    private val _coordType = MutableStateFlow(
        runCatching { CoordType.valueOf(loadPref("coordType", "GCJ02")) }.getOrDefault(CoordType.GCJ02)
    )
    val coordType: StateFlow<CoordType> = _coordType.asStateFlow()

    private var jumpId = 0L
    private var locationClient: AMapLocationClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var networkRetryTimes = 0
    private var retryTask: Runnable? = null

    /** 内部状态：是否处于持续跟踪模式（定位后保持更新位置与朝向） */
    private var _internalIsTracking = false
    /** UI 状态：持续跟踪模式开关 */
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    private var firstFixReceived = false
    private var sensorListener: ISensorListenerDelegate? = null

    companion object {
        private const val MAX_NETWORK_RETRY = 2
        private const val RETRY_DELAY_MS    = 1500L
        /** 自动选单位：≥1000m 显示 km，<1000m 显示 m，统一保留两位小数 */
        fun formatDist(meters: Double): String =
            if (meters >= 1000) "%.2f km".format(meters / 1000) else "%.2f m".format(meters)
        /** 自动选单位：≥1km² 显示 km²，<1km² 显示 m²，统一保留两位小数 */
        fun formatArea(sqMeters: Double): String =
            if (sqMeters >= 1_000_000) "%.2f km²".format(sqMeters / 1_000_000) else "%.2f m²".format(sqMeters)
    }

    // ================ 拾取旗标标签显示开关 ================
    private val _showPickedFlagLabels = MutableStateFlow(loadPref("showPickedFlagLabels", "true") == "true")
    val showPickedFlagLabels: StateFlow<Boolean> = _showPickedFlagLabels.asStateFlow()

    fun toggleShowPickedFlagLabels() {
        _showPickedFlagLabels.value = !_showPickedFlagLabels.value
        savePref("showPickedFlagLabels", _showPickedFlagLabels.value.toString())
    }

    // ================ 相机缩放级别持久化（杀死进程后恢复） ================
    private val _cameraZoom = MutableStateFlow<Float?>(loadPref("cameraZoom", "").toFloatOrNull())
    val cameraZoom: StateFlow<Float?> = _cameraZoom.asStateFlow()

    /** 保存当前相机缩放级别（由 MapScreen 在 onCameraChangeFinish 时调用） */
    fun saveCameraZoom(zoom: Float) {
        if (_cameraZoom.value != zoom) {
            _cameraZoom.value = zoom
            savePref("cameraZoom", zoom.toString())
        }
    }

    // ================ 历史测量回放状态 ================
    data class ReplayRecord(
        val label     : String,
        val mode      : String,
        val waypoints : List<CT.Coord>,
        val totalDist : Double,
        val totalArea : Double,
    )
    private val _replayRecord = MutableStateFlow<ReplayRecord?>(null)
    val replayRecord: StateFlow<ReplayRecord?> = _replayRecord.asStateFlow()

    private val _isReplaying = MutableStateFlow(false)
    val isReplaying: StateFlow<Boolean> = _isReplaying.asStateFlow()

    /** 历史测量保存 Store */
    val savedMeasurementsStore = SavedMeasurementsStore(getApplication())

    /** 从历史测量开始回放；同时清除当前正在进行的测量 */
    fun startReplay(record: SavedMeasurementRecord) {
        _measurementState.value = MeasurementState.IDLE
        _measurementWaypoints.value = emptyList()
        _measurementSegments.value = emptyList()
        _measurementTotalDist.value = 0.0
        _measurementTotalArea.value = 0.0
        _measurementPickMode.value = false
        _placeMode.value = false
        _replayRecord.value = ReplayRecord(
            label     = record.label,
            mode      = record.mode,
            waypoints = record.waypoints.map { CT.Coord(it.gcjLon, it.gcjLat) },
            totalDist = record.totalDist,
            totalArea = record.totalArea,
        )
        _isReplaying.value = true
    }

    fun stopReplay() {
        _replayRecord.value = null
        _isReplaying.value = false
        // 回放结束：确保拖拽手势恢复（防止手势状态残留）
        _measurementPickMode.value = false
        _placeMode.value = false
    }

    /** 开始新测量前清除回放状态，避免新旧数据混杂 */
    fun clearReplayBeforeNewMeasurement() {
        _replayRecord.value = null
        _isReplaying.value = false
    }

    /** 将当前已完成测量的结果保存到历史 */
    fun saveMeasurementToHistory(label: String = ""): Boolean {
        if (_measurementState.value != MeasurementState.COMPLETED) return false
        if (_measurementWaypoints.value.isEmpty()) return false
        val gcjWaypoints = _measurementWaypoints.value.map { it.gcj }
        return savedMeasurementsStore.add(
            label = label,
            mode = _measurementMode.value.name,
            waypoints = gcjWaypoints,
            totalDist = _measurementTotalDist.value,
            totalArea = _measurementTotalArea.value,
        )
    }

    fun requestSwitchToMeasurementHistory() {
        _navigationEvent.value = NavigationEvent(
            id = _navigationEvent.value.id + 1,
            target = NavigationTarget.MEASUREMENT_HISTORY,
        )
    }

    // ================ 拾取模式开关 ================
    private val _placeMode = MutableStateFlow(false)
    val placeMode: StateFlow<Boolean> = _placeMode.asStateFlow()

    /** 拾取模式中的准星坐标（GCJ02，实时跟随手指） */
    private val _reticleCoord = MutableStateFlow<CT.Coord?>(null)
    val reticleCoord: StateFlow<CT.Coord?> = _reticleCoord.asStateFlow()

    /** 拾取编号计数器（从持久化 flag 中恢复，避免重启后编号重复） */
    private var _pickCounter = 0L
        get() {
            if (field == 0L && flagStore.flags.value.isNotEmpty()) {
                field = flagStore.flags.value
                    .mapNotNull { it.label.toLongOrNull() }
                    .maxOrNull() ?: 0L
            }
            return field
        }
    /** 跳转编号计数器 */
    private var _jumpCounter = 0L

    // ================ 旗标存储 ================
    private val flagStore = FlagStore(application)

    /** 所有旗标列表 */
    val flags: StateFlow<List<Flag>> = flagStore.flags

    /** 最近一次地图点击拾取的坐标（GCJ02，兼容旧接口） */
    private val _lastPickedCoord = MutableStateFlow<CT.Coord?>(null)
    val lastPickedCoord: StateFlow<CT.Coord?> = _lastPickedCoord.asStateFlow()

    init {
        try {
            locationClient = AMapLocationClient(application).apply {
                val option = AMapLocationClientOption().apply {
                    isOnceLocation = true
                    isOnceLocationLatest = true
                    setNeedAddress(false)
                    setInterval(0)         // 单次定位：结果就绪即刻回调，不设额外等待
                    setSensorEnable(true)  // 启用传感器数据辅助过滤
                    isMockEnable = true
                }
                setLocationOption(option)
                setLocationListener(::onLocationResult)
            }
        } catch (e: Exception) {
            _message.value = UiMessage("定位客户端初始化失败：${e.message}")
        }
    }

    /** 逆地理编码搜索（延迟初始化，确保地图SDK就绪） */
    private var _geocodeSearch: GeocodeSearch? = null
    private val geocodeSearch: GeocodeSearch
        get() = _geocodeSearch ?: throw IllegalStateException("GeocodeSearch未初始化，请调用initGeocodeSearch()")

    /** POI 关键词搜索（延迟初始化，确保地图SDK就绪） */
    private var _poiSearch: PoiSearchV2? = null
    private val poiSearch: PoiSearchV2
        get() = _poiSearch ?: throw IllegalStateException("PoiSearchV2未初始化，请调用initGeocodeSearch()")

    /** 初始化逆地理编码与 POI 搜索（应在地图就绪后调用） */
    fun initGeocodeSearch() {
        if (_geocodeSearch != null) return
        _geocodeSearch = GeocodeSearch(_context).apply {
            setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, statusCode: Int) {
                    val statusStr = when {
                        statusCode == 1000 -> "成功"
                        statusCode == 1001 -> "参数错误"
                        statusCode == 1002 -> "key无效"
                        statusCode == 1003 -> "配额超限"
                        statusCode == 1004 -> "服务不可用"
                        else -> "未知($statusCode)"
                    }
                    if (statusCode != 1000) {
                        _message.value = UiMessage("逆地理编码: $statusStr")
                        _reverseGeocodeAddress.value = null
                        lastGeocodeKey = ""
                        return
                    }
                    if (result == null) {
                        _message.value = UiMessage("逆地理编码: $statusStr, result=null")
                        _reverseGeocodeAddress.value = null
                        lastGeocodeKey = ""
                        return
                    }
                    val addr = result.regeocodeAddress
                    if (addr == null) {
                        _message.value = UiMessage("逆地理编码: $statusStr, regeocodeAddress=null")
                        _reverseGeocodeAddress.value = null
                        lastGeocodeKey = ""
                        return
                    }
                    // 高德通常返回 formatAddress；少数坐标只返回行政区字段，使用字段组合兜底。
                    val fallbackAddress = listOfNotNull(
                        addr.province,
                        addr.city?.takeIf { it != addr.province },
                        addr.district,
                        addr.township,
                        addr.neighborhood,
                        addr.building,
                        addr.streetNumber?.street,
                        addr.streetNumber?.number,
                        addr.pois?.firstOrNull()?.title,
                    ).filter { it.isNotBlank() }.distinct().joinToString("")
                    val displayAddress = addr.formatAddress?.trim().orEmpty().ifEmpty { fallbackAddress }
                    if (displayAddress.isEmpty()) {
                        _message.value = UiMessage("逆地理编码: $statusStr | 地址为空")
                        _reverseGeocodeAddress.value = null
                        lastGeocodeKey = ""
                    } else {
                        _reverseGeocodeAddress.value = displayAddress
                    }
                }
                override fun onGeocodeSearched(result: com.amap.api.services.geocoder.GeocodeResult?, statusCode: Int) {
                    _searching.value = false
                    if (statusCode != 1000 || result == null) {
                        val statusText = geocodeStatusText(statusCode)
                        // 频率限制错误：给出更友好的提示，有缓存时展示缓存结果
                        if (statusCode == 1101 || statusCode == 1115) {
                            _message.value = UiMessage("搜索请求过于频繁，请稍后再试")
                            val searchKey = _searchQuery.value.trim()
                            if (searchKey.isNotEmpty()) {
                                _searchResults.value = _searchCache[searchKey] ?: emptyList()
                            }
                        } else {
                            _message.value = UiMessage("地址搜索失败：$statusText")
                            _searchResults.value = emptyList()
                        }
                        return
                    }
                    // 捕获当前搜索关键词用于缓存
                    val searchKey = _searchQuery.value.trim()
                    val list = result.geocodeAddressList?.mapNotNull { addr ->
                        val loc = addr.latLonPoint ?: return@mapNotNull null
                        SearchResult(
                            title = (addr.formatAddress ?: "").replace("/", " ").trim(),
                            address = addr.formatAddress ?: "",
                            gcjLon = loc.longitude,
                            gcjLat = loc.latitude,
                        )
                    } ?: emptyList()
                    // 更新缓存
                    val sorted = if (_currentGcj.value != null) {
                        val current = _currentGcj.value!!
                        list.sortedBy { result ->
                            CT.Coord(result.gcjLon, result.gcjLat).distanceTo(current)
                        }
                    } else list
                    if (searchKey.isNotEmpty()) {
                        _searchCache[searchKey] = sorted
                        if (_searchCache.size > SEARCH_CACHE_MAX_SIZE) {
                            val firstKey = _searchCache.keys.first()
                            _searchCache.remove(firstKey)
                        }
                    }
                    _searchResults.value = sorted
                }
            })
        }
        try {
            _poiSearch = PoiSearchV2(_context, PoiSearchV2.Query("", "")).apply {
                setOnPoiSearchListener(object : PoiSearchV2.OnPoiSearchListener {
                    override fun onPoiSearched(result: PoiResultV2?, statusCode: Int) {
                        _searching.value = false
                        if (statusCode != 1000 || result == null) {
                            val statusText = geocodeStatusText(statusCode)
                            if (statusCode == 1101 || statusCode == 1115) {
                                _message.value = UiMessage("搜索请求过于频繁，请稍后再试")
                                val searchKey = _searchQuery.value.trim()
                                if (searchKey.isNotEmpty()) {
                                    _searchResults.value = _searchCache[searchKey] ?: emptyList()
                                }
                            } else {
                                _message.value = UiMessage("搜索失败：$statusText")
                                _searchResults.value = emptyList()
                            }
                            return
                        }
                        val searchKey = _searchQuery.value.trim()
                        val list = result.pois?.mapNotNull { poi ->
                            val loc = poi.latLonPoint ?: return@mapNotNull null
                            SearchResult(
                                title = poi.title ?: "",
                                address = poi.snippet ?: "",
                                gcjLon = loc.longitude,
                                gcjLat = loc.latitude,
                            )
                        } ?: emptyList()
                        val sorted = if (_currentGcj.value != null) {
                            val current = _currentGcj.value!!
                            list.sortedBy { r -> CT.Coord(r.gcjLon, r.gcjLat).distanceTo(current) }
                        } else list
                        if (searchKey.isNotEmpty()) {
                            _searchCache[searchKey] = sorted
                            if (_searchCache.size > SEARCH_CACHE_MAX_SIZE) {
                                _searchCache.remove(_searchCache.keys.first())
                            }
                        }
                        _searchResults.value = sorted
                    }
                    override fun onPoiItemSearched(item: com.amap.api.services.core.PoiItemV2?, statusCode: Int) {}
                    override fun onVisualSearched(result: com.amap.api.services.poisearch.VisualSearchResult?, statusCode: Int) {}
                })
            }
        } catch (e: Exception) {
            _message.value = UiMessage("POI 搜索初始化失败：${e.message}")
        }
    }

    private fun onLocationResult(loc: AMapLocation?) {
        _locating.value = false
        when {
            loc == null -> {
                _message.value = UiMessage("定位失败：未获取到定位结果，请重试")
                return
            }
            loc.errorCode != 0 -> {
                if ((loc.errorCode == 4 || loc.errorCode == 13) && networkRetryTimes < MAX_NETWORK_RETRY) {
                    networkRetryTimes++
                    val task = Runnable {
                        _locating.value = true
                        try { locationClient?.startLocation() }
                        catch (e: Exception) {
                            _locating.value = false
                            _message.value = UiMessage("定位启动异常：${e.message}")
                        }
                    }
                    retryTask = task
                    mainHandler.postDelayed(task, RETRY_DELAY_MS)
                    return
                }
                _message.value = UiMessage(locationErrorText(loc))
                return
            }
            loc.longitude == 0.0 && loc.latitude == 0.0 -> {
                _message.value = UiMessage("定位失败：返回坐标无效，请重试")
                return
            }
        }
        networkRetryTimes = 0
        val gcj   = CT.Coord(loc.longitude, loc.latitude)
        val wgs   = CT.gcj02ToWgs84(gcj, precision = CT.HIGH_PRECISION)
        _currentGcj.value = gcj
        _currentWgs.value = wgs
        _accuracyMeters.value = if (loc.accuracy > 0f) loc.accuracy else null
        _accuracyMode.value = when {
            "gps".equals(loc.provider, ignoreCase = true)                         -> "GPS 卫星定位"
            "network".equals(loc.provider, ignoreCase = true)                     -> "网络辅助定位"
            (loc.accuracy ?: 0f) <= 10f                                          -> "GPS 卫星定位"
            (loc.accuracy ?: 0f) <= 50f                                          -> "GPS+网络融合"
            else                                                                 -> "网络辅助定位"
        }

        // 首次定位成功后，若用户在跟踪模式则切换到连续定位
        if (!firstFixReceived) {
            firstFixReceived = true
            if (_internalIsTracking) {
                _isTracking.value = true
                switchToContinuous()
            }
        }
    }

    /** 点击定位按钮：初始单次定位，成功后自动进入持续跟踪 */
    fun locate() {
        locationClient ?: run {
            _message.value = UiMessage("定位客户端未就绪，请重启应用")
            return
        }
        networkRetryTimes = 0
        _internalIsTracking = true
        firstFixReceived = false
        _locating.value = true
        try { locationClient?.startLocation() }
        catch (e: Exception) {
            _locating.value = false
            _message.value = UiMessage("定位启动异常：${e.message}")
        }
    }

    /** 退出持续跟踪模式，恢复单次定位行为 */
    fun stopTracking() {
        _internalIsTracking = false
        _isTracking.value = false
        firstFixReceived = false
        stopContinuous()
    }

    /** 停止时清除朝向和跟踪状态 */
    fun clearBearing() { _bearing.value = null; _isTracking.value = false }

    private fun locationErrorText(loc: AMapLocation): String = when (loc.errorCode) {
        3   -> "定位失败：系统定位服务未开启，请在设置中打开定位"
        4   -> "定位失败：网络连接异常，请检查网络后重试"
        12  -> "定位失败：定位权限被拒绝，请在系统设置中授予定位权限"
        16  -> "定位失败：GPS 未开启，请打开 GPS"
        else -> "定位失败（错误码 ${loc.errorCode}）：${loc.errorInfo}"
    }

    fun jumpTo() {
        val lon = _lonText.value.trim()
        val lat = _latText.value.trim()
        if (lon.isEmpty() || lat.isEmpty()) {
            _message.value = UiMessage("请输入经度和纬度"); return
        }
        val lonNum = lon.toDoubleOrNull()
        val latNum = lat.toDoubleOrNull()
        if (lonNum == null || latNum == null) {
            _message.value = UiMessage("经纬度必须是数字"); return
        }
        if (lonNum < -180.0 || lonNum > 180.0) {
            _message.value = UiMessage("经度超出有效范围 [-180, 180]"); return
        }
        if (latNum < -90.0 || latNum > 90.0) {
            _message.value = UiMessage("纬度超出有效范围 [-90, 90]"); return
        }
        val typed = CT.Coord(lonNum, latNum)
        val gcj = when (_coordType.value) {
            CoordType.GCJ02 -> typed
            CoordType.WGS84 -> CT.wgs84ToGcj02(typed)
        }
        val wgs = CT.gcj02ToWgs84(gcj, precision = CT.HIGH_PRECISION)
        _jumpCounter++
        _jumpTarget.value = JumpTarget(++jumpId, gcj, typed, _coordType.value)
        resetReverseGeocode()
    }

    /** 跳转完成后重置逆地理编码缓存（坐标变了，地址也要重新请求） */
    fun resetReverseGeocode() {
        lastGeocodeKey = ""
        _reverseGeocodeAddress.value = null
    }

    /** 调用高德逆地理编码（SDK 内置，复用现有 Android Key） */
    fun fetchReverseGeocode(gcjLon: Double, gcjLat: Double) {
        val key = "%.6f,%.6f".format(gcjLon, gcjLat)
        if (key == lastGeocodeKey) return  // 已有结果，不重复请求
        lastGeocodeKey = key
        _reverseGeocodeAddress.value = "解析中…"
        try {
            _geocodeSearch?.getFromLocationAsyn(
                // LatLonPoint 的构造顺序是 latitude、longitude（官方 SDK 定义）。
                RegeocodeQuery(LatLonPoint(gcjLat, gcjLon), 200f, GeocodeSearch.AMAP)
            ) ?: throw IllegalStateException("GeocodeSearch未初始化")
        } catch (e: Exception) {
            _message.value = UiMessage("逆地理编码失败：${e.message}")
            _reverseGeocodeAddress.value = null
            lastGeocodeKey = ""
        }
    }

    /** 更新搜索关键词（不自动触发搜索） */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** 手动触发搜索（按钮点击） */
    fun searchAddress() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) {
            _message.value = UiMessage("请输入要搜索的地址")
            return
        }
        // 检查缓存
        val cached = _searchCache[query]
        if (cached != null) {
            _searchResults.value = cached
            return
        }
        // 冷却期检查：防止请求过于频繁
        val now = System.currentTimeMillis()
        if (now - lastSearchTime < SEARCH_COOLDOWN_MS) {
            // 不显示错误，静默忽略
            return
        }
        lastSearchTime = now
        _searching.value = true
        _searchResults.value = emptyList()
        try {
            val poiQuery = com.amap.api.services.poisearch.PoiSearchV2.Query(query, "")
            _poiSearch?.setQuery(poiQuery)
            _poiSearch?.searchPOIAsyn()
                ?: throw IllegalStateException("PoiSearchV2未初始化")
        } catch (e: Exception) {
            _searching.value = false
            _message.value = UiMessage("地址搜索失败：${e.message}")
        }
    }

    /** 将错误码转换为用户友好的提示信息 */
    private fun geocodeStatusText(statusCode: Int): String = when (statusCode) {
        1000 -> "成功"
        1001 -> "参数错误"
        1002 -> "key无效"
        1003 -> "配额超限"
        1004 -> "服务不可用"
        1101 -> "搜索过于频繁，请稍后重试"  // IP 频率限制
        1102 -> "业务类型错误"
        1103 -> "查询内容非法"
        1104 -> "接口维护中"
        1105 -> "内部服务错误"
        1107 -> "URL非法"
        1112 -> "数据不存在"
        1115 -> "用户无正当理由频繁请求"
        1401 -> "不支持的请求类型"
        1601 -> "不支持 HTTP 请求"
        else -> "未知错误($statusCode)"
    }

    /** 选择搜索结果并跳转 */
    fun selectSearchResult(result: SearchResult) {
        val gcj = CT.Coord(result.gcjLon, result.gcjLat)
        val wgs = CT.gcj02ToWgs84(gcj, precision = CT.HIGH_PRECISION)
        val typed = when (_coordType.value) {
            CoordType.GCJ02 -> gcj
            CoordType.WGS84 -> wgs
        }
        _lonText.value = "%.6f".format(typed.lon)
        _latText.value = "%.6f".format(typed.lat)
        _searchResults.value = emptyList()
        // 复用 jumpTo 逻辑
        _jumpCounter++
        _jumpTarget.value = JumpTarget(++jumpId, gcj, typed, _coordType.value)
        resetReverseGeocode()
    }

    // ---------- 拾取模式（连续放置）----------
    /** 切换拾取模式；进入时 initCoord 为屏幕中心坐标，退出时传 null */
    fun togglePlaceMode(initCoord: CT.Coord? = null) {
        if (_placeMode.value) {
            disablePlaceMode()
        } else {
            enablePlaceMode(initCoord)
        }
    }

    /** 进入拾取模式，准星初始位置为当前定位或指定坐标 */
    fun enablePlaceMode(initCoord: CT.Coord? = null) {
        _placeMode.value = true
        _reticleCoord.value = initCoord ?: _currentGcj.value
        _lastPickedCoord.value = initCoord
    }

    /** 退出拾取模式，清除准星 */
    fun disablePlaceMode() {
        _placeMode.value = false
        _reticleCoord.value = null
    }

    /** 更新准星坐标（手指拖动时调用） */
    fun setReticleCoord(gcj: CT.Coord) { _reticleCoord.value = gcj }

    /** 确认放置旗标，返回新建的 Flag */
    fun confirmPlacement(reticle: CT.Coord, customName: String = ""): Flag? {
        val gcj = reticle
        val wgs = CT.gcj02ToWgs84(gcj, precision = CT.HIGH_PRECISION)
        _pickCounter++
        val label = if (customName.isNotBlank()) customName else _pickCounter.toString()
        val flag = Flag(
            id = System.currentTimeMillis(), label = label,
            gcjLon = gcj.lon, gcjLat = gcj.lat,
            wgsLon = wgs.lon, wgsLat = wgs.lat,
            type = FlagType.PICKED, createdAt = System.currentTimeMillis(),
            customName = if (customName.isNotBlank()) customName else "",
        )
        flagStore.insert(flag)
        _lastPickedCoord.value = gcj
        return flag
    }

    /** 删除单个旗标 */
    fun deleteFlag(id: Long) { flagStore.deleteById(id) }

    /** 重命名旗标 */
    fun renameFlag(id: Long, customName: String) { flagStore.updateCustomName(id, customName) }
    fun updateExpanded(id: Long, expanded: Boolean) { flagStore.updateExpanded(id, expanded) }

    /** 手动添加旗标（支持指定类型和自定义名称） */
    fun addFlag(gcj: CT.Coord, wgs: CT.Coord, type: FlagType, customName: String = "") {
        _pickCounter++
        val label = if (customName.isNotBlank()) customName else _pickCounter.toString()
        val flag = Flag(
            id = System.currentTimeMillis(), label = label,
            gcjLon = gcj.lon, gcjLat = gcj.lat,
            wgsLon = wgs.lon, wgsLat = wgs.lat,
            type = type, createdAt = System.currentTimeMillis(),
            customName = if (customName.isNotBlank()) customName else "",
        )
        flagStore.insert(flag)
        _lastPickedCoord.value = gcj
    }

    /** 清除所有旗标 */
    fun clearAllFlags() {
        flagStore.deleteAll()
        _pickCounter = 0L
    }

    /** 清除指定类型的旗标 */
    fun clearFlagsByType(type: FlagType) { flagStore.deleteByType(type) }

    /** 罗马数字转换（1→I, 2→II, 3→III...） */
    fun toRomanNumeral(n: Long): String {
        val ones   = arrayOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")
        val tens   = arrayOf("", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC")
        val hundreds = arrayOf("", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM")
        val thousands = arrayOf("", "M", "MM", "MMM")
        val padded = "0000".takeLast(maxOf(1, 4 - n.toString().length)) + n.toString()
        return StringBuilder().apply {
            append(thousands[padded[0].digitToInt()])
            append(hundreds[padded[1].digitToInt()])
            append(tens[padded[2].digitToInt()])
            append(ones[padded[3].digitToInt()])
        }.toString()
    }

    // ---------- 拾取（旧接口，保留兼容）----------
    fun onMapClick(lon: Double, lat: Double) {
        if (_placeMode.value) return // 新模式下由 OnMapTouchListener 处理
        _lastPickedCoord.value = CT.Coord(lon, lat)
    }
    fun clearPickedCoord() { _lastPickedCoord.value = null }

    // ---------- 折线测量 ----------
    /** 测量模式：DISTANCE（测距）/ AREA（测面积） */
    enum class MeasurementMode { DISTANCE, AREA }

    enum class MeasurementState { IDLE, PLACING, COMPLETED }

    private val _measurementMode = MutableStateFlow(loadMeasurement()?.mode ?: MeasurementMode.DISTANCE)
    private val _measurementWaypoints = MutableStateFlow(loadMeasurement()?.waypoints ?: emptyList())
    private val _measurementSegments = MutableStateFlow(loadMeasurement()?.segments ?: emptyList())
    private val _measurementTotalDist = MutableStateFlow(loadMeasurement()?.totalDist ?: 0.0)
    private val _measurementTotalArea = MutableStateFlow(loadMeasurement()?.totalArea ?: 0.0)
    private val _measurementState = MutableStateFlow(loadMeasurement()?.state ?: MeasurementState.IDLE)
    private val _measurementPickMode = MutableStateFlow(false)
    val measurementMode: StateFlow<MeasurementMode> = _measurementMode.asStateFlow()
    val measurementState: StateFlow<MeasurementState> = _measurementState.asStateFlow()
    data class MeasurementPoint(val gcj: CT.Coord, val wgs: CT.Coord)
    val measurementWaypoints: StateFlow<List<MeasurementPoint>> = _measurementWaypoints.asStateFlow()
    data class Segment(val index: Int, val dist: Double) {
        val distText: String get() = formatDist(dist)
    }
    val measurementSegments: StateFlow<List<Segment>> = _measurementSegments.asStateFlow()
    val measurementTotalDist: StateFlow<Double> = _measurementTotalDist.asStateFlow()
    val measurementTotalArea: StateFlow<Double> = _measurementTotalArea.asStateFlow()
    val measurementPickMode: StateFlow<Boolean> = _measurementPickMode.asStateFlow()

    // ---------- 持久化辅助 ----------
    private fun loadPref(key: String, default: String): String =
        _context.getSharedPreferences("map_state", Context.MODE_PRIVATE).getString(key, default) ?: default

    private fun savePref(key: String, value: String) {
        _context.getSharedPreferences("map_state", Context.MODE_PRIVATE)
            .edit().putString(key, value).apply()
    }

    /** 从 SharedPreferences 加载上次测量的完整数据 */
    private fun loadMeasurement(): SavedMeasurement? {
        val raw = loadPref("measurement", "")
        if (raw.isBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            val waypoints = buildList {
                val arr = obj.optJSONArray("waypoints") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val wp = arr.getJSONObject(i)
                    add(MeasurementPoint(
                        gcj = CT.Coord(wp.optDouble("gcjLon"), wp.optDouble("gcjLat")),
                        wgs = CT.Coord(wp.optDouble("wgsLon"), wp.optDouble("wgsLat")),
                    ))
                }
            }
            val segments = buildList {
                val arr = obj.optJSONArray("segments") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val seg = arr.getJSONObject(i)
                    add(Segment(seg.optInt("index"), seg.optDouble("dist")))
                }
            }
            SavedMeasurement(
                mode = when (obj.optString("mode")) { "AREA" -> MeasurementMode.AREA; else -> MeasurementMode.DISTANCE },
                waypoints = waypoints,
                segments = segments,
                totalDist = obj.optDouble("totalDist"),
                totalArea = obj.optDouble("totalArea"),
                state = when (obj.optString("state")) {
                    "PLACING" -> MeasurementState.PLACING
                    "COMPLETED" -> MeasurementState.COMPLETED
                    else -> MeasurementState.IDLE
                },
            )
        }.getOrNull()
    }

    private fun saveMeasurement() {
        val obj = JSONObject().apply {
            put("mode", _measurementMode.value.name)
            put("state", _measurementState.value.name)
            put("totalDist", _measurementTotalDist.value)
            put("totalArea", _measurementTotalArea.value)
            val wpArr = JSONArray()
            for (wp in _measurementWaypoints.value) {
                wpArr.put(JSONObject()
                    .put("gcjLon", wp.gcj.lon).put("gcjLat", wp.gcj.lat)
                    .put("wgsLon", wp.wgs.lon).put("wgsLat", wp.wgs.lat))
            }
            put("waypoints", wpArr)
            val segArr = JSONArray()
            for (seg in _measurementSegments.value) {
                segArr.put(JSONObject().put("index", seg.index).put("dist", seg.dist))
            }
            put("segments", segArr)
        }
        savePref("measurement", obj.toString())
    }

    private data class SavedMeasurement(
        val mode: MeasurementMode,
        val waypoints: List<MeasurementPoint>,
        val segments: List<Segment>,
        val totalDist: Double,
        val totalArea: Double,
        val state: MeasurementState,
    )

    // ---------- 可重复消费的 UI 事件 ----------
    private val _navigationEvent = MutableStateFlow(NavigationEvent())
    val navigationEvent: StateFlow<NavigationEvent> = _navigationEvent.asStateFlow()

    private val _collapsePanelEvent = MutableStateFlow(0L)
    val collapsePanelEvent: StateFlow<Long> = _collapsePanelEvent.asStateFlow()

    fun requestSwitchToMap() {
        _navigationEvent.value = NavigationEvent(
            id = _navigationEvent.value.id + 1,
            target = NavigationTarget.MAP,
        )
    }
    fun requestSwitchToTools() {
        _navigationEvent.value = NavigationEvent(
            id = _navigationEvent.value.id + 1,
            target = NavigationTarget.TOOLS_MEASUREMENT,
        )
    }
    fun requestSwitchToFavorites() {
        _navigationEvent.value = NavigationEvent(
            id = _navigationEvent.value.id + 1,
            target = NavigationTarget.MY_FAVORITES,
        )
    }
    /** 消费一次导航事件（避免反复触发） */
    fun consumedNavEvent() {
        _navigationEvent.value = NavigationEvent()
    }
    fun triggerCollapsePanel() { _collapsePanelEvent.value++ }

    fun startMeasurement(mode: MeasurementMode) {
        clearReplayBeforeNewMeasurement()
        _placeMode.value = false
        _reticleCoord.value = null
        _measurementPickMode.value = false
        _measurementMode.value = mode
        _measurementWaypoints.value = emptyList()
        _measurementSegments.value = emptyList()
        _measurementTotalDist.value = 0.0
        _measurementTotalArea.value = 0.0
        _measurementState.value = MeasurementState.PLACING
    }

    fun stopMeasurement() {
        _measurementWaypoints.value = emptyList()
        _measurementSegments.value = emptyList()
        _measurementTotalDist.value = 0.0
        _measurementTotalArea.value = 0.0
        _measurementState.value = MeasurementState.IDLE
        _measurementPickMode.value = false
        saveMeasurement()
    }

    /** 切换测量拾取模式：启用后在地图上拖动准星，轻触放置测点 */
    fun toggleMeasurementPickMode(initCoord: CT.Coord? = null) {
        if (_measurementPickMode.value) {
            _measurementPickMode.value = false
            _reticleCoord.value = null
        } else {
            _measurementPickMode.value = true
            _placeMode.value = false
            _reticleCoord.value = initCoord ?: _currentGcj.value
        }
    }

    /** 完成测量：保留 waypoints/结果，通知 UI 跳回工具 */
    fun completeMeasurement() {
        if (_measurementState.value != MeasurementState.PLACING) return
        if (!isMeasurementReady(_measurementMode.value, _measurementWaypoints.value.size)) return
        _measurementState.value = MeasurementState.COMPLETED
        saveMeasurement()
        requestSwitchToTools()
    }

    fun removeLastWaypoint() {
        val list = _measurementWaypoints.value
        if (list.isNotEmpty()) {
            _measurementWaypoints.value = list.dropLast(1)
            _measurementTotalDist.value = 0.0
            _measurementTotalArea.value = 0.0
            recalcSegments()
        }
    }

    fun clearWaypoints() {
        _measurementWaypoints.value = emptyList()
        _measurementSegments.value = emptyList()
        _measurementTotalDist.value = 0.0
        _measurementTotalArea.value = 0.0
        if (_measurementState.value == MeasurementState.COMPLETED) {
            _measurementState.value = MeasurementState.IDLE
        } else if (_measurementState.value == MeasurementState.PLACING) {
            _measurementState.value = MeasurementState.IDLE
        }
        saveMeasurement()
    }

    fun addWaypoint(lon: Double, lat: Double) {
        if (_measurementState.value != MeasurementState.PLACING) return
        val gcj = CT.Coord(lon, lat)
        val wgs = CT.gcj02ToWgs84(gcj, precision = CT.HIGH_PRECISION)
        val list = _measurementWaypoints.value + MeasurementPoint(gcj, wgs)
        _measurementWaypoints.value = list
        recalcSegments()
    }

    private fun recalcSegments() {
        val list = _measurementWaypoints.value
        val segs = mutableListOf<Segment>()
        var totalDist = 0.0
        val n = list.size
        val closed = _measurementMode.value == MeasurementMode.AREA && n >= 3
        for (i in 0 until n - 1) {
            val d = list[i].gcj.distanceTo(list[i + 1].gcj)
            totalDist += d
            segs.add(Segment(i, d))
        }
        if (closed) {
            val d = list.last().gcj.distanceTo(list.first().gcj)
            totalDist += d
            segs.add(Segment(n - 1, d))
        }
        _measurementSegments.value = segs
        _measurementTotalDist.value = totalDist
        _measurementTotalArea.value = measurementPolygonAreaMeters(list.map { it.gcj })
    }

    // ---------- 连续定位 / 传感器 ----------

    /**
     * 首次定位成功后，切换到连续模式：
     * - 每 1s 更新一次位置
     * - 注册传感器监听，实时获取手机朝向（heading）
     */
    private fun switchToContinuous() {
        try {
            locationClient?.let { client ->
                client.setLocationOption(AMapLocationClientOption().apply {
                    isOnceLocation = false        // 连续定位
                    setInterval(1000L)            // 1 秒更新一次
                    setNeedAddress(false)
                    setSensorEnable(true)
                    isMockEnable = true
                })
                // 注册高德传感器监听获取实时朝向
                sensorListener = object : ISensorListenerDelegate {
                    override fun onSetHeading(timestamp: Long, status: Int, heading: Float) {
                        _bearing.value = heading
                    }
                    override fun onSetAccelerometer(timestamp: Long, status: Int,
                                                    x: Float, y: Float, z: Float,
                                                    xv: Float, yv: Float, zv: Float) {}
                    override fun onSetGyroscope(timestamp: Long, status: Int,
                                                x: Float, y: Float, z: Float,
                                                xv: Float, yv: Float, zv: Float) {}
                    override fun onSetMagnetic(timestamp: Long, status: Int,
                                               x: Float, y: Float, z: Float,
                                               xv: Float, yv: Float, zv: Float) {}
                    override fun onSetOrientation(timestamp: Long, status: Int,
                                                  az: Float, roll: Float, pitch: Float) {}
                    override fun onSetPressure(timestamp: Long, status: Int, pressure: Float) {}
                    override fun onSetTemperature(timestamp: Long, status: Int, temperature: Float) {}
                }
                client.addSensorListener(sensorListener!!)
                client.startLocation()
            }
        } catch (e: Exception) {
            _message.value = UiMessage("切换持续定位失败：${e.message}")
            _internalIsTracking = false
        }
    }

    private fun stopContinuous() {
        try {
            sensorListener?.let { listener ->
                locationClient?.removeSensorListener(listener)
                sensorListener = null
            }
            locationClient?.stopLocation()
        } catch (_: Exception) {}
        // 恢复单次定位配置（为下一次 locate() 做准备）
        try {
            locationClient?.setLocationOption(AMapLocationClientOption().apply {
                isOnceLocation = true
                isOnceLocationLatest = true
                setNeedAddress(false)
                setInterval(0)
                setSensorEnable(true)
                isMockEnable = true
            })
        } catch (_: Exception) {}
        firstFixReceived = false
    }

    fun updateLonText(v: String) {
        val filtered = v.filter { it.isDigit() || it == '.' || it == '-' || it == '+' }
        _lonText.value = filtered
        savePref("lonText", filtered)
    }
    fun updateLatText(v: String) {
        val filtered = v.filter { it.isDigit() || it == '.' || it == '-' || it == '+' }
        _latText.value = filtered
        savePref("latText", filtered)
    }
    fun setCoordType(t: CoordType) {
        _coordType.value = t
        savePref("coordType", t.name)
    }
    fun messageShown()            { _message.value = null }

    /** 更新剪贴板缓存，供 UI 层在 LaunchedEffect 中检测粘贴时机 */
    fun updatePasteBuffer(text: String) { _pasteBuffer.value = text }

    /** 通知 UI 显示坐标粘贴拆分成功提示 */
    fun showPasteSplitTip(lon: Double, lat: Double) {
        _message.value = UiMessage("已拆分填入：经度 %.6f  纬度 %.6f".format(lon, lat))
    }

    /** 通知 UI 显示坐标粘贴格式无法识别提示 */
    fun showPasteParseFailTip() {
        _message.value = UiMessage("粘贴格式无法识别，请手动输入")
    }

    /** 通知 UI 显示单值粘贴成功提示 */
    fun showPasteSingleTip() {
        _message.value = UiMessage("已粘贴")
    }

    override fun onCleared() {
        super.onCleared()
        retryTask?.let { mainHandler.removeCallbacks(it) }; retryTask = null
        stopContinuous()
        try { locationClient?.onDestroy(); locationClient = null }
        catch (_: Exception) {}
    }
}

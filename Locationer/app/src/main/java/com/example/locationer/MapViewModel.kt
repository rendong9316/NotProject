package com.example.locationer

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentGcj   = MutableStateFlow<CT.Coord?>(null)
    val currentGcj: StateFlow<CT.Coord?>   = _currentGcj.asStateFlow()

    private val _currentWgs   = MutableStateFlow<CT.Coord?>(null)
    val currentWgs: StateFlow<CT.Coord?>   = _currentWgs.asStateFlow()

    private val _jumpTarget   = MutableStateFlow<JumpTarget?>(null)
    val jumpTarget: StateFlow<JumpTarget?> = _jumpTarget.asStateFlow()

    /** 拾取模式开关 */
    private val _pickMode = MutableStateFlow(false)
    val pickMode: StateFlow<Boolean> = _pickMode.asStateFlow()

    /** 最近一次地图点击拾取的坐标（GCJ02） */
    private val _lastPickedCoord = MutableStateFlow<CT.Coord?>(null)
    val lastPickedCoord: StateFlow<CT.Coord?> = _lastPickedCoord.asStateFlow()

    private val _accuracyMeters = MutableStateFlow<Float?>(null)
    val accuracyMeters: StateFlow<Float?> = _accuracyMeters.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating: StateFlow<Boolean> = _locating.asStateFlow()

    private val _lonText  = MutableStateFlow("")
    val lonText: StateFlow<String> = _lonText.asStateFlow()
    private val _latText  = MutableStateFlow("")
    val latText: StateFlow<String> = _latText.asStateFlow()

    private val _coordType = MutableStateFlow(CoordType.GCJ02)
    val coordType: StateFlow<CoordType> = _coordType.asStateFlow()

    private var jumpId = 0L
    private var locationClient: AMapLocationClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var networkRetryTimes = 0
    private var retryTask: Runnable? = null

    companion object {
        private const val MAX_NETWORK_RETRY = 2
        private const val RETRY_DELAY_MS    = 1500L
    }

    init {
        try {
            locationClient = AMapLocationClient(application).apply {
                val option = AMapLocationClientOption().apply {
                    setGpsFirst(true)      // 优先 GPS 卫星定位（精度最高）
                    isOnceLocation = true
                    isOnceLocationLatest = true
                    setNeedAddress(false)
                    setInterval(0)         // 单次定位：结果就绪即刻回调，不设额外等待
                    setSensorEnable(true)  // 启用传感器数据辅助过滤
                    isMockEnable = true
                    setGpsFirstTimeout(20000L) // GPS 等待超时 20s，超时后回退网络定位
                }
                setLocationOption(option)
                setLocationListener(::onLocationResult)
            }
        } catch (e: Exception) {
            _message.value = UiMessage("定位客户端初始化失败：${e.message}")
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
                if (loc.errorCode == 4 && networkRetryTimes < MAX_NETWORK_RETRY) {
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
    }

    fun locate() {
        locationClient ?: run {
            _message.value = UiMessage("定位客户端未就绪，请重启应用")
            return
        }
        networkRetryTimes = 0
        _locating.value = true
        try { locationClient?.startLocation() }
        catch (e: Exception) {
            _locating.value = false
            _message.value = UiMessage("定位启动异常：${e.message}")
        }
    }

    private fun locationErrorText(loc: AMapLocation): String = when (loc.errorCode) {
        3   -> "定位失败：系统定位服务未开启，请在设置中打开定位"
        4   -> "定位失败：网络连接异常，请检查网络后重试"
        12  -> "定位失败：定位权限被拒绝，请在系统设置中授予定位权限"
        13  -> "定位失败：网络辅助定位失败，请稍后重试"
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
        _jumpTarget.value = JumpTarget(++jumpId, gcj, typed, _coordType.value)
    }

    // ---------- 拾取模式 ----------
    fun togglePickMode() { _pickMode.value = !_pickMode.value }
    fun onMapClick(lon: Double, lat: Double) {
        if (_pickMode.value) {
            _lastPickedCoord.value = CT.Coord(lon, lat)
            _pickMode.value = false
        }
    }
    fun clearPickedCoord() { _lastPickedCoord.value = null }

    // ---------- 折线测量 ----------
    /** 测量模式：DISTANCE（测距）/ AREA（测面积） */
    enum class MeasurementMode { DISTANCE, AREA }

    private val _measurementMode = MutableStateFlow(MeasurementMode.DISTANCE)
    val measurementMode: StateFlow<MeasurementMode> = _measurementMode.asStateFlow()

    private val _measurementWaypoints = MutableStateFlow<List<CT.Coord>>(emptyList())
    val measurementWaypoints: StateFlow<List<CT.Coord>> = _measurementWaypoints.asStateFlow()

    data class Segment(val index: Int, val dist: Double) {
        val distText: String get() = if (dist >= 1000) "%.2f km".format(dist / 1000) else "%.0f m".format(dist)
    }
    private val _measurementSegments = MutableStateFlow<List<Segment>>(emptyList())
    val measurementSegments: StateFlow<List<Segment>> = _measurementSegments.asStateFlow()

    private val _measurementTotalDist = MutableStateFlow(0.0)
    val measurementTotalDist: StateFlow<Double> = _measurementTotalDist.asStateFlow()

    private val _measurementTotalArea = MutableStateFlow(0.0)
    val measurementTotalArea: StateFlow<Double> = _measurementTotalArea.asStateFlow()

    fun startMeasurement(mode: MeasurementMode) {
        _measurementMode.value = mode
        _measurementWaypoints.value = emptyList()
        _measurementSegments.value = emptyList()
        _measurementTotalDist.value = 0.0
        _measurementTotalArea.value = 0.0
    }

    fun stopMeasurement() {
        _measurementWaypoints.value = emptyList()
        _measurementSegments.value = emptyList()
        _measurementTotalDist.value = 0.0
        _measurementTotalArea.value = 0.0
    }

    fun removeLastWaypoint() {
        val list = _measurementWaypoints.value
        if (list.size > 1) {
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
    }

    fun addWaypoint(lon: Double, lat: Double) {
        val coord = CT.Coord(lon, lat)
        val list = _measurementWaypoints.value + coord
        _measurementWaypoints.value = list
        recalcSegments()
    }

    private fun recalcSegments() {
        val list = _measurementWaypoints.value
        val segs = mutableListOf<Segment>()
        var totalDist = 0.0
        val n = list.size; for (i in 0 until n - 1) {
            val d = list[i].distanceTo(list[i + 1])
            totalDist += d
            segs.add(Segment(i, d))
        }
        _measurementSegments.value = segs
        _measurementTotalDist.value = totalDist
        // 面积：用鞋带公式（仅多边形闭合时有效）
        _measurementTotalArea.value = if (list.size >= 3) shooLaceArea(list) else 0.0
    }

    /** 鞋带公式计算多边形面积（平方米，GCJ02 坐标） */
    private fun shooLaceArea(points: List<CT.Coord>): Double {
        val n = points.size
        if (n < 3) return 0.0
        var area = 0.0
        for (i in points.indices) {
            val j = (i + 1) % n
            area += points[i].lon * points[j].lat
            area -= points[j].lon * points[i].lat
        }
        // 粗略估算：1度经度 ≈ 111km * cos(lat)，1度纬度 ≈ 111km
        val midLat = points.map { it.lat }.average()
        val mPerLonDeg = 111320.0 * kotlin.math.cos(midLat / 180.0 * Math.PI)
        val mPerLatDeg = 110540.0
        return kotlin.math.abs(area * 0.5 * mPerLonDeg * mPerLatDeg)
    }

    // ---------- 输入框状态 ----------
    fun updateLonText(v: String)  { _lonText.value = v.filter { it.isDigit() || it == '.' || it == '-' || it == '+' } }
    fun updateLatText(v: String)  { _latText.value = v.filter { it.isDigit() || it == '.' || it == '-' || it == '+' } }
    fun setCoordType(t: CoordType){ _coordType.value = t }
    fun messageShown()            { _message.value = null }

    override fun onCleared() {
        super.onCleared()
        retryTask?.let { mainHandler.removeCallbacks(it) }; retryTask = null
        try { locationClient?.stopLocation(); locationClient?.onDestroy(); locationClient = null }
        catch (_: Exception) {}
    }
}

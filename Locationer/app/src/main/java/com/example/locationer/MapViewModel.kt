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
                    setGpsFirst(true)      // 优先 GPS 卫星定位
                    isOnceLocation = true
                    isOnceLocationLatest = true
                    setNeedAddress(false)
                    setInterval(3000)      // 最小采样间隔 3000ms
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

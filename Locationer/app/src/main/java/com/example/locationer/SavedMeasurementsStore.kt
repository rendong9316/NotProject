package com.example.locationer

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史测量记录的数据模型。
 * 每条记录保存一次完整测量的所有节点（含 GCJ02 / WGS84）以及计算结果，
 * 点击后回放地图并绘制蓝色区域与各节点。
 */
data class SavedMeasurementRecord(
    val id         : Long,
    val label      : String,
    /** DISTANCE 或 AREA */
    val mode       : String,
    val waypoints  : List<SavedWaypoint>,
    val totalDist  : Double,
    val totalArea  : Double,
    /** 创建时间（毫秒时间戳） */
    val timestamp  : Long = id,
    val isExpanded : Boolean = false,
)

data class SavedWaypoint(
    val gcjLon : Double,
    val gcjLat : Double,
    val wgsLon : Double,
    val wgsLat : Double,
)

class SavedMeasurementsStore(private val application: Application) {

    companion object {
        private const val PREFS_NAME    = "saved_measurements"
        private const val KEY_DATA      = "data"
        /** 独立的公共备份文件名，与收藏夹的 Locationer-favorites.json 互不干扰 */
        const val AUTO_BACKUP_FILE_NAME = "Locationer-saved-measurements.json"
        private const val COORD_EPS     = 0.0000001
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _records = MutableStateFlow<List<SavedMeasurementRecord>>(emptyList())
    val records: StateFlow<List<SavedMeasurementRecord>> = _records.asStateFlow()

    init { reload() }

    private fun reload() {
        _records.value = parseRecords(prefs.getString(KEY_DATA, "[]").orEmpty())
    }

    /** 持久化到内存 + SharedPreferences + 公共备份文件（用于数据变更操作） */
    private fun save(records: List<SavedMeasurementRecord>) {
        val json = buildJsonArray(records).toString()
        _records.value = records
        prefs.edit().putString(KEY_DATA, json).apply()
        FavoriteFileStore(application).saveToPublic(json, AUTO_BACKUP_FILE_NAME)
    }

    /** 仅更新内存和 SharedPreferences，不触碰公共备份（用于 clearAll / updateExpanded） */
    private fun saveDataOnly(records: List<SavedMeasurementRecord>) {
        val json = buildJsonArray(records).toString()
        _records.value = records
        prefs.edit().putString(KEY_DATA, json).apply()
    }

    /** 添加一条测量记录；同名且坐标重复则忽略 */
    fun add(
        label     : String,
        mode      : String,
        waypoints : List<CT.Coord>,
        totalDist : Double,
        totalArea : Double,
    ): Boolean {
        val newRecord = SavedMeasurementRecord(
            id        = nextId(),
            label     = label.trim().ifEmpty { formatMeasurementTimestamp() },
            mode      = mode,
            waypoints = waypoints.map { gcj ->
                val wgs = CT.gcj02ToWgs84(gcj, precision = CT.HIGH_PRECISION)
                SavedWaypoint(gcj.lon, gcj.lat, wgs.lon, wgs.lat)
            },
            totalDist = totalDist,
            totalArea = totalArea,
            timestamp = System.currentTimeMillis(),
        )
        if (_records.value.any { existing ->
            existing.label == newRecord.label && existing.mode == newRecord.mode &&
                sameWaypoints(existing.waypoints, newRecord.waypoints)
        }) return false
        save(_records.value + newRecord)
        return true
    }

    fun remove(id: Long) {
        save(_records.value.filterNot { it.id == id })
    }

    /** 清空本地数据，保留公共备份文件供手动恢复（与收藏夹行为一致） */
    fun clearAll() {
        saveDataOnly(emptyList())
    }

    /** 删除公共下载目录中的备份文件 */
    fun deleteBackup(): Boolean =
        FavoriteFileStore(application).deleteFromFile(AUTO_BACKUP_FILE_NAME)

    fun rename(id: Long, newLabel: String) {
        val label = newLabel.trim()
        if (label.isEmpty()) return
        save(_records.value.map { if (it.id == id) it.copy(label = label) else it })
    }

    fun updateExpanded(id: Long, expanded: Boolean) {
        saveDataOnly(_records.value.map { if (it.id == id) it.copy(isExpanded = expanded) else it })
    }

    fun exportAll(): String =
        _records.value.joinToString(separator = "\n\n") { it.formatForClipboard() }

    fun exportToJson(): String = buildJsonArray(_records.value).toString()

    fun restoreFromUri(uri: android.net.Uri): Int {
        val json = runCatching {
            application.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
        }.getOrNull() ?: return 0
        return restoreJson(json)
    }

    private fun restoreJson(json: String): Int {
        val parsed = parseRecords(json)
        if (parsed.isEmpty()) return 0
        save(parsed)
        return parsed.size
    }

    /** 从公共下载目录读取本 Store 的备份文件 */
    fun restoreFromOffline(): Int {
        val json = FavoriteFileStore(application).readFromFile(AUTO_BACKUP_FILE_NAME) ?: return 0
        return restoreJson(json)
    }

    /** 回放记录到地图：更新输入框并跳转 */
    fun jumpTo(record: SavedMeasurementRecord, mapViewModel: MapViewModel) {
        if (record.waypoints.isEmpty()) return
        val first = record.waypoints.first()
        mapViewModel.updateLonText(String.format(java.util.Locale.US, "%.6f", first.gcjLon))
        mapViewModel.updateLatText(String.format(java.util.Locale.US, "%.6f", first.gcjLat))
        mapViewModel.setCoordType(CoordType.GCJ02)
        mapViewModel.startReplay(record)
        mapViewModel.requestSwitchToMap()
    }

    fun countBackupRecords(): Int {
        val json = FavoriteFileStore(application).readFromFile(AUTO_BACKUP_FILE_NAME) ?: return 0
        return parseRecords(json).size
    }

    fun hasAutoSaved(): Boolean = FavoriteFileStore(application).readFromFile(AUTO_BACKUP_FILE_NAME) != null

    private fun parseRecords(json: String): List<SavedMeasurementRecord> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optLong("id", 0L)
                if (id == 0L) continue
                val wpArr = item.optJSONArray("waypoints") ?: continue
                val waypoints = buildList {
                    for (i in 0 until wpArr.length()) {
                        val wp = wpArr.getJSONObject(i)
                        add(
                            SavedWaypoint(
                                gcjLon = wp.optDouble("gcjLon"),
                                gcjLat = wp.optDouble("gcjLat"),
                                wgsLon = wp.optDouble("wgsLon"),
                                wgsLat = wp.optDouble("wgsLat"),
                            )
                        )
                    }
                }
                add(
                    SavedMeasurementRecord(
                        id        = id,
                        label     = item.optString("label"),
                        mode      = item.optString("mode", "DISTANCE"),
                        waypoints = waypoints,
                        totalDist = item.optDouble("totalDist"),
                        totalArea = item.optDouble("totalArea"),
                        timestamp = item.optLong("timestamp", id),
                        isExpanded = item.optBoolean("isExpanded", false),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun buildJsonArray(records: List<SavedMeasurementRecord>): JSONArray {
        val array = JSONArray()
        records.forEach { rec ->
            val wpArr = JSONArray()
            rec.waypoints.forEach { wp ->
                wpArr.put(JSONObject()
                    .put("gcjLon", wp.gcjLon).put("gcjLat", wp.gcjLat)
                    .put("wgsLon", wp.wgsLon).put("wgsLat", wp.wgsLat))
            }
            array.put(
                JSONObject()
                    .put("id", rec.id)
                    .put("label", rec.label)
                    .put("mode", rec.mode)
                    .put("totalDist", rec.totalDist)
                    .put("totalArea", rec.totalArea)
                    .put("timestamp", rec.timestamp)
                    .put("isExpanded", rec.isExpanded)
                    .put("waypoints", wpArr)
            )
        }
        return array
    }

    private fun sameWaypoints(a: List<SavedWaypoint>, b: List<SavedWaypoint>): Boolean {
        if (a.size != b.size) return false
        return a.zip(b).all { (x, y) ->
            kotlin.math.abs(x.gcjLon - y.gcjLon) < COORD_EPS &&
                kotlin.math.abs(x.gcjLat - y.gcjLat) < COORD_EPS &&
                kotlin.math.abs(x.wgsLon - y.wgsLon) < COORD_EPS &&
                kotlin.math.abs(x.wgsLat - y.wgsLat) < COORD_EPS
        }
    }

    private fun nextId(): Long =
        maxOf(System.currentTimeMillis(), (_records.value.maxOfOrNull { it.id } ?: 0L) + 1L)
}

internal fun SavedMeasurementRecord.formatForClipboard(): String {
    val modeLabel = if (mode == "AREA") "测面积" else "测距"
    val sb = StringBuilder()
    sb.append(label).append("\n")
    sb.append(modeLabel).append("\n")
    waypoints.forEachIndexed { i, wp ->
        sb.append("点 ${i + 1}: GCJ02 %.6f,%.6f  WGS84 %.6f,%.6f\n".format(
            wp.gcjLon, wp.gcjLat, wp.wgsLon, wp.wgsLat
        ))
    }
    sb.append("累计距离 ").append(MapViewModel.formatDist(totalDist))
    if (mode == "AREA") sb.append("  面积 ").append(MapViewModel.formatArea(totalArea))
    return sb.toString().trimEnd()
}

internal fun formatMeasurementTimestamp(timestamp: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy年MM月dd日HH时mm分ss秒", Locale.CHINA).format(Date(timestamp))

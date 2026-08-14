package com.example.locationer

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/** 独立于地图旗标的收藏快照。 */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    data class FavoritePoint(
        val id: Long,
        val label: String,
        val gcjLon: Double,
        val gcjLat: Double,
        val wgsLon: Double,
        val wgsLat: Double,
        val timestamp: Long,
    )

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow(load())
    val favorites: StateFlow<List<FavoritePoint>> = _favorites.asStateFlow()

    /**
     * 旧版本把收藏状态存在 Flag.isFavorite 中。这里只复制一次快照，迁移后两套数据不再关联。
     */
    fun migrateLegacyFavorites(flags: List<Flag>) {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return
        val migrated = flags.filter { it.isFavorite }.fold(_favorites.value) { points, flag ->
            val point = flag.toFavorite(nextId(points), flag.createdAt)
            if (points.any { it.sameSnapshot(point) }) points else points + point
        }
        save(migrated)
        prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
    }

    /** 返回 false 表示相同名称和坐标的快照已经存在。 */
    fun addFromFlag(flag: Flag): Boolean {
        val point = flag.toFavorite(nextId(_favorites.value), System.currentTimeMillis())
        if (_favorites.value.any { it.sameSnapshot(point) }) return false
        save(_favorites.value + point)
        return true
    }

    fun remove(id: Long) {
        save(_favorites.value.filterNot { it.id == id })
    }

    fun clearAll() {
        save(emptyList())
    }

    fun rename(id: Long, newLabel: String) {
        val label = newLabel.trim()
        if (label.isEmpty()) return
        save(_favorites.value.map { if (it.id == id) it.copy(label = label) else it })
    }

    fun search(query: String): List<FavoritePoint> {
        val normalized = query.trim()
        return if (normalized.isEmpty()) {
            _favorites.value
        } else {
            _favorites.value.filter { it.label.contains(normalized, ignoreCase = true) }
        }
    }

    fun copyText(id: Long): String? = _favorites.value.find { it.id == id }?.formatForClipboard()

    fun exportAll(): String = _favorites.value.joinToString(separator = "\n\n") { it.formatForClipboard() }

    fun jumpTo(point: FavoritePoint, mapViewModel: MapViewModel) {
        mapViewModel.updateLonText(formatCoordinate(point.gcjLon))
        mapViewModel.updateLatText(formatCoordinate(point.gcjLat))
        mapViewModel.setCoordType(CoordType.GCJ02)
        mapViewModel.jumpTo()
        mapViewModel.requestSwitchToMap()
    }

    private fun Flag.toFavorite(id: Long, savedAt: Long) = FavoritePoint(
        id = id,
        label = customName.ifBlank { label },
        gcjLon = gcjLon,
        gcjLat = gcjLat,
        wgsLon = wgsLon,
        wgsLat = wgsLat,
        timestamp = savedAt,
    )

    private fun FavoritePoint.sameSnapshot(other: FavoritePoint): Boolean =
        label == other.label &&
            abs(gcjLon - other.gcjLon) < COORD_EPSILON &&
            abs(gcjLat - other.gcjLat) < COORD_EPSILON &&
            abs(wgsLon - other.wgsLon) < COORD_EPSILON &&
            abs(wgsLat - other.wgsLat) < COORD_EPSILON

    private fun FavoritePoint.formatForClipboard(): String = String.format(
        Locale.US,
        "%s\nGCJ02: %.6f,%.6f\nWGS84: %.6f,%.6f",
        label,
        gcjLon,
        gcjLat,
        wgsLon,
        wgsLat,
    )

    private fun nextId(points: List<FavoritePoint>): Long =
        maxOf(System.currentTimeMillis(), (points.maxOfOrNull { it.id } ?: 0L) + 1L)

    private fun load(): List<FavoritePoint> {
        val raw = prefs.getString(KEY_DATA, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optLong("id", 0L)
                    if (id == 0L) continue
                    add(
                        FavoritePoint(
                            id = id,
                            label = item.optString("label"),
                            gcjLon = item.optDouble("gcjLon"),
                            gcjLat = item.optDouble("gcjLat"),
                            wgsLon = item.optDouble("wgsLon"),
                            wgsLat = item.optDouble("wgsLat"),
                            timestamp = item.optLong("timestamp", id),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(points: List<FavoritePoint>) {
        val array = JSONArray()
        points.forEach { point ->
            array.put(
                JSONObject()
                    .put("id", point.id)
                    .put("label", point.label)
                    .put("gcjLon", point.gcjLon)
                    .put("gcjLat", point.gcjLat)
                    .put("wgsLon", point.wgsLon)
                    .put("wgsLat", point.wgsLat)
                    .put("timestamp", point.timestamp)
            )
        }
        _favorites.value = points
        prefs.edit().putString(KEY_DATA, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "favorites_manual"
        private const val KEY_DATA = "data"
        private const val KEY_LEGACY_MIGRATED = "legacy_flags_migrated_v1"
        private const val COORD_EPSILON = 0.0000001

        fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)
    }
}

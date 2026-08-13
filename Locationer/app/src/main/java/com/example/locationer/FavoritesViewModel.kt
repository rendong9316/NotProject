package com.example.locationer

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 收藏夹 ViewModel，使用 SharedPreferences 持久化存储。
 * 每条收藏：label（名称）+ GCJ02/WGS84 坐标 + 时间戳。
 */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    data class FavoritePoint(
        val id: Long,
        val label: String,
        val gcjLon: Double,
        val gcjLat: Double,
        val wgsLon: Double,
        val wgsLat: Double,
        val coordType: String, // "GCJ02" or "WGS84"
        val timestamp: Long,
    )

    private val prefs = application.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow<List<FavoritePoint>>(emptyList())
    val favorites: StateFlow<List<FavoritePoint>> = _favorites.asStateFlow()

    init { reload() }

    fun add(label: String, gcj: CT.Coord, wgs: CT.Coord, type: CoordType) {
        val point = FavoritePoint(
            id = System.currentTimeMillis(),
            label = label,
            gcjLon = gcj.lon, gcjLat = gcj.lat,
            wgsLon = wgs.lon, wgsLat = wgs.lat,
            coordType = type.name,
            timestamp = System.currentTimeMillis(),
        )
        val list = _favorites.value + point
        save(list)
    }

    fun remove(id: Long) {
        save(_favorites.value.filter { it.id != id })
    }

    fun clearAll() {
        save(emptyList())
    }

    /** 跳转到收藏点，更新 MapViewModel 的坐标输入和跳转指令 */
    fun jumpTo(point: FavoritePoint, viewModel: MapViewModel) {
        val lonStr = when (point.coordType) {
            "GCJ02" -> "%.6f".format(point.gcjLon)
            else -> "%.6f".format(point.wgsLon)
        }
        val latStr = when (point.coordType) {
            "GCJ02" -> "%.6f".format(point.gcjLat)
            else -> "%.6f".format(point.wgsLat)
        }
        viewModel.updateLonText(lonStr)
        viewModel.updateLatText(latStr)
        viewModel.setCoordType(CoordType.valueOf(point.coordType))
        viewModel.jumpTo()
    }

    private fun reload() {
        val json = prefs.getString("data", "[]") ?: "[]"
        _favorites.value = parseFavorites(json)
    }

    private fun save(list: List<FavoritePoint>) {
        val json = buildString {
            append("[")
            list.forEachIndexed { i, p ->
                if (i > 0) append(",")
                append("{\"id\":${p.id},\"label\":${encodeStr(p.label)},")
                append("\"gcjLon\":${p.gcjLon},\"gcjLat\":${p.gcjLat},")
                append("\"wgsLon\":${p.wgsLon},\"wgsLat\":${p.wgsLat},")
                append("\"coordType\":\"${p.coordType}\",\"timestamp\":${p.timestamp}}")
            }
            append("]")
        }
        prefs.edit().putString("data", json).apply()
        _favorites.value = list
    }

    private fun parseFavorites(json: String): List<FavoritePoint> {
        val list = mutableListOf<FavoritePoint>()
        val trimmed = json.trim().removePrefix("[").removeSuffix("]")
        if (trimmed.isBlank()) return emptyList()
        for (entry in trimmed.split("},{").map { s -> if (s.startsWith("{")) s else "{$s" }.map { s -> if (s.endsWith("}")) s else "$s}" }) {
            try {
                val getId = regexExtract(entry, """"id":(\d+)""")?.toLongOrNull() ?: continue
                val getLabel = regexExtract(entry, """"label":"([^"]*)"""") ?: ""
                val getDbl = { name: String -> regexExtract(entry, """"$name":([^,}]+)""")?.toDoubleOrNull() ?: 0.0 }
                list.add(FavoritePoint(
                    id = getId,
                    label = getLabel,
                    gcjLon = getDbl("gcjLon"), gcjLat = getDbl("gcjLat"),
                    wgsLon = getDbl("wgsLon"), wgsLat = getDbl("wgsLat"),
                    coordType = regexExtract(entry, """"coordType":"([^"]*)"""") ?: "GCJ02",
                    timestamp = getDbl("timestamp").toLong(),
                ))
            } catch (_: Exception) {}
        }
        return list
    }

    private fun regexExtract(text: String, pattern: String): String? {
        val regex = Regex(pattern)
        return regex.find(text)?.groupValues?.getOrNull(1)
    }

    private fun encodeStr(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}

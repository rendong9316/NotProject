package com.example.locationer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.abs

/** A favorites snapshot that is independent from map flags. */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    data class FavoritePoint(
        val id: Long,
        val label: String,
        val gcjLon: Double,
        val gcjLat: Double,
        val wgsLon: Double,
        val wgsLat: Double,
        val timestamp: Long,
        val isExpanded: Boolean = false,
    )

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow(load())
    val favorites: StateFlow<List<FavoritePoint>> = _favorites.asStateFlow()

    fun migrateLegacyFavorites(flags: List<Flag>) {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return
        val legacyFavorites = flags.filter { it.isFavorite }
        if (legacyFavorites.isNotEmpty()) {
            val migrated = legacyFavorites.fold(_favorites.value) { points, flag ->
                val point = flag.toFavorite(nextId(points), flag.createdAt)
                if (points.any { it.sameSnapshot(point) }) points else points + point
            }
            if (migrated != _favorites.value) save(migrated)
        }
        prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
    }

    fun addFromFlag(flag: Flag): Boolean {
        val point = flag.toFavorite(nextId(_favorites.value), System.currentTimeMillis())
        if (_favorites.value.any { it.sameSnapshot(point) }) return false
        save(_favorites.value + point)
        return true
    }

    fun remove(id: Long) {
        save(_favorites.value.filterNot { it.id == id })
    }

    /** Clears local data while retaining the last public backup for explicit recovery. */
    fun clearAll() {
        _favorites.value = emptyList()
        prefs.edit().putString(KEY_DATA, "[]").apply()
    }

    fun clearBackup(): Boolean = FavoriteFileStore(getApplication()).deleteFromFile()

    fun clearLegacyBackups(): Int = FavoriteFileStore(getApplication()).deleteLegacyBackups()

    fun rename(id: Long, newLabel: String) {
        val label = newLabel.trim()
        if (label.isEmpty()) return
        save(_favorites.value.map { if (it.id == id) it.copy(label = label) else it })
    }

    fun updateExpanded(id: Long, expanded: Boolean) {
        save(_favorites.value.map { point ->
            if (point.id == id) point.copy(isExpanded = expanded) else point
        })
    }

    fun exportAll(): String =
        _favorites.value.joinToString(separator = "\n\n") { it.formatForClipboard() }

    fun exportToJson(): String = buildJsonArray(_favorites.value).toString()

    fun jumpTo(point: FavoritePoint, mapViewModel: MapViewModel) {
        mapViewModel.updateLonText(formatCoordinate(point.gcjLon))
        mapViewModel.updateLatText(formatCoordinate(point.gcjLat))
        mapViewModel.setCoordType(CoordType.GCJ02)
        mapViewModel.jumpTo()
        mapViewModel.requestSwitchToMap()
    }

    fun countBackupRecords(): Int {
        val json = FavoriteFileStore(getApplication()).readFromFile() ?: return 0
        return parseJsonArray(json).size
    }

    fun hasAutoSavedToPublic(): Boolean =
        FavoriteFileStore(getApplication()).readFromFile() != null

    fun backupCurrentFavorites(): Boolean {
        if (_favorites.value.isEmpty()) return false
        return FavoriteFileStore(getApplication()).saveToPublic(exportToJson())
    }

    fun restoreFromOffline(): Int {
        val json = FavoriteFileStore(getApplication()).readFromFile() ?: return 0
        return restoreJson(json)
    }

    fun restoreFromUri(uri: Uri): Int {
        val json = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
        }.getOrNull() ?: return 0
        return restoreJson(json)
    }

    private fun restoreJson(json: String): Int {
        val parsed = parseJsonArray(json)
        if (parsed.isEmpty()) return 0
        save(parsed)
        return parsed.size
    }

    private fun load(): List<FavoritePoint> {
        val local = parseJsonArray(prefs.getString(KEY_DATA, "[]").orEmpty())
        if (local.isNotEmpty()) return local

        val legacyJson = FavoriteFileStore.tryReadLegacyFile(getApplication()) ?: return emptyList()
        val legacy = parseJsonArray(legacyJson)
        if (legacy.isNotEmpty()) prefs.edit().putString(KEY_DATA, legacyJson).apply()
        return legacy
    }

    private fun save(points: List<FavoritePoint>) {
        val json = buildJsonArray(points).toString()
        _favorites.value = points
        prefs.edit().putString(KEY_DATA, json).apply()
        FavoriteFileStore(getApplication()).saveToPublic(json)
    }

    private fun parseJsonArray(json: String): List<FavoritePoint> = runCatching {
        val array = JSONArray(json)
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
                        isExpanded = item.optBoolean("isExpanded", false),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun buildJsonArray(points: List<FavoritePoint>): JSONArray {
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
                    .put("isExpanded", point.isExpanded)
            )
        }
        return array
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

    companion object {
        private const val PREFS_NAME = "favorites_manual"
        private const val KEY_DATA = "data"
        private const val KEY_LEGACY_MIGRATED = "legacy_flags_migrated_v1"
        private const val COORD_EPSILON = 0.0000001

        fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.6f", value)
    }
}

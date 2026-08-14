package com.example.locationer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 旗标类型 */
enum class FlagType { CURRENT, PICKED, JUMPED }

/** 一条旗标记录。isFavorite 仅用于一次性迁移旧版本数据，运行时不再参与收藏逻辑。 */
data class Flag(
    val id: Long,
    val label: String,
    val gcjLon: Double,
    val gcjLat: Double,
    val wgsLon: Double,
    val wgsLat: Double,
    val type: FlagType,
    val createdAt: Long,
    val customName: String = "",
    val isFavorite: Boolean = false,
)

/** 旗标持久化存储。 */
class FlagStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("flags", Context.MODE_PRIVATE)
    private val _flags = MutableStateFlow<List<Flag>>(emptyList())
    val flags: StateFlow<List<Flag>> = _flags.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        _flags.value = parseFlags(prefs.getString(KEY_DATA, "[]").orEmpty())
    }

    fun insert(flag: Flag) {
        save(_flags.value + flag)
    }

    fun updateCoordinates(id: Long, gcjLon: Double, gcjLat: Double, wgsLon: Double, wgsLat: Double) {
        save(_flags.value.map { flag ->
            if (flag.id == id) {
                flag.copy(gcjLon = gcjLon, gcjLat = gcjLat, wgsLon = wgsLon, wgsLat = wgsLat)
            } else {
                flag
            }
        })
    }

    fun deleteById(id: Long) {
        save(_flags.value.filterNot { it.id == id })
    }

    fun deleteAll() {
        save(emptyList())
    }

    fun deleteByType(type: FlagType) {
        save(_flags.value.filterNot { it.type == type })
    }

    fun updateCustomName(id: Long, name: String) {
        save(_flags.value.map { flag ->
            if (flag.id == id) flag.copy(customName = name) else flag
        })
    }

    /** 兼容旧数据迁移；新收藏逻辑不调用此方法。 */
    fun updateFavorite(id: Long, favorite: Boolean) {
        save(_flags.value.map { flag ->
            if (flag.id == id) flag.copy(isFavorite = favorite) else flag
        })
    }

    private fun save(list: List<Flag>) {
        val array = JSONArray()
        list.forEach { flag ->
            array.put(
                JSONObject()
                    .put("id", flag.id)
                    .put("label", flag.label)
                    .put("gcjLon", flag.gcjLon)
                    .put("gcjLat", flag.gcjLat)
                    .put("wgsLon", flag.wgsLon)
                    .put("wgsLat", flag.wgsLat)
                    .put("type", flag.type.name)
                    .put("createdAt", flag.createdAt)
                    .put("isFavorite", flag.isFavorite)
                    .put("customName", flag.customName)
            )
        }
        prefs.edit().putString(KEY_DATA, array.toString()).apply()
        _flags.value = list
    }

    private fun parseFlags(json: String): List<Flag> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optLong("id", 0L)
                if (id == 0L) continue
                add(
                    Flag(
                        id = id,
                        label = item.optString("label"),
                        gcjLon = item.optDouble("gcjLon"),
                        gcjLat = item.optDouble("gcjLat"),
                        wgsLon = item.optDouble("wgsLon"),
                        wgsLat = item.optDouble("wgsLat"),
                        type = runCatching {
                            FlagType.valueOf(item.optString("type"))
                        }.getOrDefault(FlagType.PICKED),
                        createdAt = item.optLong("createdAt", id),
                        customName = item.optString("customName"),
                        isFavorite = item.optBoolean("isFavorite", false) ||
                            item.optInt("isFavorite", 0) > 0,
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY_DATA = "data"
    }
}

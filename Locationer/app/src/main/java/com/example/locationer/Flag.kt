package com.example.locationer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 旗标类型 */
enum class FlagType { CURRENT, PICKED, JUMPED }

/** 一条旗标记录 */
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
)

/**
 * 旗标持久化：使用 SharedPreferences + 手动 JSON（无额外依赖）
 */
class FlagStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("flags", Context.MODE_PRIVATE)
    private val _flags = MutableStateFlow<List<Flag>>(emptyList())
    val flags: StateFlow<List<Flag>> = _flags.asStateFlow()

    init { reload() }

    private fun reload() {
        val json = prefs.getString("data", "[]") ?: "[]"
        _flags.value = parseFlags(json)
    }

    fun insert(flag: Flag) {
        val list = _flags.value + flag
        save(list)
    }

    fun updateCoordinates(id: Long, gcjLon: Double, gcjLat: Double, wgsLon: Double, wgsLat: Double) {
        val list = _flags.value.map { f ->
            if (f.id == id) f.copy(gcjLon = gcjLon, gcjLat = gcjLat, wgsLon = wgsLon, wgsLat = wgsLat) else f
        }
        save(list)
    }

    fun deleteById(id: Long) {
        save(_flags.value.filter { it.id != id })
    }

    fun deleteAll() {
        save(emptyList())
    }

    fun deleteByType(type: FlagType) {
        save(_flags.value.filter { it.type != type })
    }

    fun updateCustomName(id: Long, name: String) {
        val list = _flags.value.map { f ->
            if (f.id == id) f.copy(customName = name) else f
        }
        save(list)
    }

    private fun save(list: List<Flag>) {
        val json = buildString {
            append("[")
            list.forEachIndexed { i, f ->
                if (i > 0) append(",")
                append("{\"id\":${f.id},\"label\":\"${encodeStr(f.label)}\",")
                append("\"gcjLon\":${f.gcjLon},\"gcjLat\":${f.gcjLat},")
                append("\"wgsLon\":${f.wgsLon},\"wgsLat\":${f.wgsLat},")
                append("\"type\":\"${f.type}\",\"createdAt\":${f.createdAt}")
                if (f.customName.isNotBlank()) append(",\"customName\":\"${encodeStr(f.customName)}\"")
                append("}")
            }
            append("]")
        }
        prefs.edit().putString("data", json).apply()
        _flags.value = list
    }

    private fun parseFlags(json: String): List<Flag> {
        val list = mutableListOf<Flag>()
        val trimmed = json.trim().removePrefix("[").removeSuffix("]")
        if (trimmed.isBlank()) return emptyList()
        for (entry in trimmed.split("},{").map { s -> if (s.startsWith("{")) s else "{$s" }.map { s -> if (s.endsWith("}")) s else "$s}" }) {
            try {
                val getId = regexExtract(entry, """"id":(\d+)""")?.toLongOrNull() ?: continue
                val getStr = { name: String -> regexExtract(entry, """"$name":"([^"]*)"""") ?: "" }
                val getDbl = { name: String -> regexExtract(entry, """"$name":([^,}]+)""")?.toDoubleOrNull() ?: 0.0 }
                list.add(Flag(
                    id = getId,
                    label = getStr("label"),
                    gcjLon = getDbl("gcjLon"), gcjLat = getDbl("gcjLat"),
                    wgsLon = getDbl("wgsLon"), wgsLat = getDbl("wgsLat"),
                    type = runCatching { FlagType.valueOf(getStr("type")) }.getOrDefault(FlagType.PICKED),
                    createdAt = getDbl("createdAt").toLong(),
                    customName = getStr("customName"),
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

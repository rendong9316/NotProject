package com.example.locationer

import android.content.Context
import android.content.SharedPreferences

// ─────────────────────────────────────────────────────────────────────────────
//  数据模型
// ─────────────────────────────────────────────────────────────────────────────

enum class ThemeMode { FOLLOW_SYSTEM, LIGHT, DARK }
enum class FontFamilyOption { DEFAULT, SERIF, MONOSPACE }

/** 全局样式参数，持久化存储于 SharedPreferences */
data class FlagStyle(
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    // 旗标图标颜色（Hex ARGB，如 0xFFC01428）
    val flagIconColor: Long = 0xFFC01428L,
    // 旗标图标直径（像素），默认 28
    val flagIconSize: Float = 28f,
    // 旗标文字颜色（Hex ARGB）
    val flagTextColor: Long = 0xFFFFFFFFL,
    // 旗标文字字号（像素），默认 30
    val flagTextSize: Float = 30f,
    // 测点图标颜色（Hex ARGB），默认蓝色
    val waypointIconColor: Long = 0xFF1976D2L,
    // 全局字体大小（倍率，1.0 = 正常）
    val globalTextScale: Float = 1.0f,
    // 全局字体族
    val globalFontFamily: FontFamilyOption = FontFamilyOption.DEFAULT,
)

// ─────────────────────────────────────────────────────────────────────────────
//  设置管理器（单例，内部 IO 线程读写）
// ─────────────────────────────────────────────────────────────────────────────

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val FLAG = "flagStyle"

    fun read(): FlagStyle {
        val json = prefs.getString(FLAG, "") ?: ""
        if (json.isBlank()) return FlagStyle()
        return try {
            val parts = json.split("|")
            FlagStyle(
                themeMode         = runCatching { ThemeMode.valueOf(parts[0]) }.getOrDefault(ThemeMode.FOLLOW_SYSTEM),
                flagIconColor     = parts.getOrNull(1)?.toLongOrNull() ?: 0xFFC01428L,
                flagIconSize      = parts.getOrNull(2)?.toFloatOrNull() ?: 28f,
                flagTextColor     = parts.getOrNull(3)?.toLongOrNull() ?: 0xFFFFFFFFL,
                flagTextSize      = parts.getOrNull(4)?.toFloatOrNull() ?: 30f,
                waypointIconColor = parts.getOrNull(5)?.toLongOrNull() ?: 0xFF1976D2L,
                globalTextScale   = parts.getOrNull(6)?.toFloatOrNull() ?: 1.0f,
                globalFontFamily  = runCatching { FontFamilyOption.valueOf(parts.getOrNull(7) ?: "") }
                    .getOrDefault(FontFamilyOption.DEFAULT),
            )
        } catch (_: Exception) {
            FlagStyle()
        }
    }

    fun write(style: FlagStyle) {
        val s = buildString {
            append(style.themeMode.name)
            append("|${style.flagIconColor}")
            append("|${style.flagIconSize}")
            append("|${style.flagTextColor}")
            append("|${style.flagTextSize}")
            append("|${style.waypointIconColor}")
            append("|${style.globalTextScale}")
            append("|${style.globalFontFamily.name}")
        }
        prefs.edit().putString(FLAG, s).apply()
    }
}

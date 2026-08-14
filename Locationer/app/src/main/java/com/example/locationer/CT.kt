package com.example.locationer

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

// China Transformed
object CT {
    /// 克拉索夫斯基椭球参数
    const val GCJ_A = 6378245.0
    const val GCJ_EE = 0.00669342162296594323 // f = 1/298.3; e^2 = 2*f - f**2
    const val PI = 3.14159265358979323846

    data class Coord(val lon: Double, val lat: Double)

    fun outOfChina(coords: Coord): Boolean =
        coords.lat < 0.8293 || coords.lat > 55.8271 ||
            coords.lon < 72.004 || coords.lon > 137.8347

    fun wgs84ToGcj02(wgs: Coord, checkChina: Boolean = true): Coord {
        if (checkChina && outOfChina(wgs)) return wgs

        // 将经度减去 105°，纬度减去 35°，求偏移距离
        val x = wgs.lon - 105
        val y = wgs.lat - 35

        // 将偏移距离转化为在 SK-42 椭球下的经纬度大小
        val dLatM = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x)) +
            (2.0 * sin(x * 6.0 * PI) + 2.0 * sin(x * 2.0 * PI) +
                2.0 * sin(y * PI) + 4.0 * sin(y / 3.0 * PI) +
                16.0 * sin(y / 12.0 * PI) + 32.0 * sin(y / 30.0 * PI)) *
            20.0 / 3.0
        val dLonM = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x)) +
            (2.0 * sin(x * 6.0 * PI) + 2.0 * sin(x * 2.0 * PI) +
                2.0 * sin(x * PI) + 4.0 * sin(x / 3.0 * PI) +
                15.0 * sin(x / 12.0 * PI) + 30.0 * sin(x / 30.0 * PI)) *
            20.0 / 3.0

        val radLat = wgs.lat / 180.0 * PI
        val magic = 1.0 - GCJ_EE * sin(radLat).pow(2.0) // just a common expr

        val latDegArclen = (PI / 180.0) * (GCJ_A * (1.0 - GCJ_EE)) / magic.pow(1.5)
        val lonDegArclen = (PI / 180.0) * (GCJ_A * cos(radLat) / sqrt(magic))

        // 往原坐标加偏移量
        return Coord(wgs.lon + dLonM / lonDegArclen, wgs.lat + dLatM / latDegArclen)
    }

    // 计算两个坐标值的偏差值
    fun diffCoord(a: Coord, b: Coord): Coord = Coord(a.lon - b.lon, a.lat - b.lat)

    // 精度控制（最大误差）
    const val g2wPrecision = 1.0 / 111391.0

    /** 高精度迭代逆向转换精度阈值（度）：~1e-10 度 ≈ 0.01 毫米级，保证面板 6 位小数稳定显示 */
    const val HIGH_PRECISION = 1e-10

    fun gcj02ToWgs84(
        gcj: Coord,
        checkChina: Boolean = true,
        precision: Double = g2wPrecision
    ): Coord {
        // 计算输入 gcj 坐标与将其计算为84坐标的偏差
        // 用当前的 gcj 坐标减去这个偏差，其近似于 gcj 对应的 84 坐标
        // 使用这个近似坐标去计算火星坐标，与输入的 gcj 进行比较，看是否符合精度
        // 如果不符合精度，则将近似坐标加上上面得到的偏差，再进行计算一次

        var wgs = diffCoord(gcj, diffCoord(wgs84ToGcj02(gcj, checkChina), gcj))
        var d = diffCoord(gcj, wgs84ToGcj02(wgs))

        var maxIterations = 10 // 最大迭代次数

        while (maxIterations-- > 0 &&
            (abs(d.lon) > precision || abs(d.lat) > precision)
        ) {
            wgs = Coord(wgs.lon + d.lon, wgs.lat + d.lat)
            d = diffCoord(gcj, wgs84ToGcj02(wgs))
        }
        return wgs
    }
}

// ========== 几何计算（顶层扩展函数） ==========

/** Haversine 球面距离（米） */
fun CT.Coord.distanceTo(other: CT.Coord): Double {
    val R = 6371000.0
    val dLat = (other.lat - lat) / 180.0 * CT.PI
    val dLon = (other.lon - lon) / 180.0 * CT.PI
    val a = sin(dLat / 2).pow(2) +
            cos(lat / 180.0 * CT.PI) * cos(other.lat / 180.0 * CT.PI) * sin(dLon / 2).pow(2)
    return 2 * R * atan2(sqrt(a), sqrt(1.0 - a))
}

/** 方位角（度，0=正北，顺时针 0~360） */
fun CT.Coord.bearingTo(other: CT.Coord): Double {
    val lat1 = lat / 180.0 * CT.PI
    val lat2 = other.lat / 180.0 * CT.PI
    val dLon = (other.lon - lon) / 180.0 * CT.PI
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (atan2(y, x) / CT.PI * 180.0 + 360.0) % 360.0
}

/** 反方位角 B→A */
fun CT.Coord.reverseBearingTo(other: CT.Coord): Double = other.bearingTo(this)

/** 正算：已知起点 + 方位角(度) + 距离(米) → 终点坐标 */
fun CT.Coord.forward(bearingDeg: Double, distanceM: Double): CT.Coord {
    val R = 6371000.0
    val brng = bearingDeg / 180.0 * CT.PI
    val lat1 = lat / 180.0 * CT.PI
    val lon1 = lon / 180.0 * CT.PI
    val lat2 = asin(sin(lat1) * cos(distanceM / R) + cos(lat1) * sin(distanceM / R) * cos(brng))
    val lon2 = lon1 + atan2(sin(brng) * sin(distanceM / R) * cos(lat1),
                             cos(distanceM / R) - sin(lat1) * sin(lat2))
    return CT.Coord(lon2 / CT.PI * 180.0, lat2 / CT.PI * 180.0)
}

/** 罗盘方向字符串（16方位） */
fun bearingCardinal(deg: Double): String {
    val dirs = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                       "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    return dirs[((deg + 11.25).toInt() % 360) / 22]
}

// ============================================================================
// 角度换算：度 ↔ 时分秒（° ↔ DMS）
// ============================================================================

/**
 * 将十进制度数转换为时分秒格式字符串。
 * 示例：116.397428 → "116°23'50.741""
 */
fun CT.Coord.toDmsString(): String =
    "${lon.toDmsString()} ${lat.toDmsString()}"

/** 将单个角度值转换为 DMS 字符串（°′″） */
fun Double.toDmsString(): String {
    val isNegative = this < 0
    val absVal = kotlin.math.abs(this)
    val degrees = absVal.toInt()
    val minutesFloat = (absVal - degrees) * 60.0
    val minutes = minutesFloat.toInt()
    val seconds = (minutesFloat - minutes) * 60.0
    val secStr = "%.3f".format(seconds)
    return "${if (isNegative) "-" else ""}$degrees°$minutes′${secStr}″"
}

/**
 * 将 DMS 字符串解析为十进制度数。
 * 支持格式：
 * - "116°23'50.741""
 * - "116 23 50.741"
 * - "116°23'50.741"N"（带方向字母）
 * - "-116.397428"（纯小数）
 */
fun String.toDecimalDegrees(): Double? {
    val trimmed = trim()
    // 先尝试直接解析为小数
    val direct = trimmed.toDoubleOrNull()
    if (direct != null) return direct

    // 解析 DMS 格式
    val cleaned = trimmed
        .replace("°", " ").replace("′", "'").replace("\"", " ")
        .replace("″", " ").replace("’", "'")
        .replace(Regex("[NSEW]"), " ").trim()

    val parts = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.size < 2) return null

    val deg = parts[0].toDoubleOrNull() ?: return null
    val minStr = parts.getOrNull(1) ?: return null
    val secStr = parts.getOrNull(2) ?: "0"
    val min = minStr.replace(",", ".").toDoubleOrNull() ?: return null
    val sec = secStr.replace(",", ".").toDoubleOrNull() ?: 0.0

    return deg + min / 60.0 + sec / 3600.0
}

/** 将十进制度数格式化为简洁的 DMS 字符串（保留3位秒小数） */
fun Double.formatDms(): String = toDmsString()

/** 将时分秒字符串转为十进制，用于输入验证 */
fun parseDmsToDecimal(dms: String): Double? = dms.toDecimalDegrees()

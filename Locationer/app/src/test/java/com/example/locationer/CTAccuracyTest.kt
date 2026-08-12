package com.example.locationer

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 验证 CT.gcj02ToWgs84 反算算法的准确性
 *
 * 验证手段：
 *  1. 往返误差：WGS84 -> GCJ02 -> WGS84，在中国全境网格采样
 *  2. 权威参考点：天安门(lddgo官方示例)、coordtransform 测试点
 *  3. 高精度反算对照：相同公式收敛到 1e-10 度作为"真值"，评估迭代截断损失
 *  4. 境外点直通检查
 */
class CTAccuracyTest {

    private fun meters(dLat: Double, dLon: Double, latDeg: Double): Double {
        val latRad = Math.toRadians(latDeg)
        val mPerDegLat = 111132.92 - 559.82 * cos(2 * latRad) + 1.175 * cos(4 * latRad)
        val mPerDegLon = 111412.84 * cos(latRad) - 93.5 * cos(3 * latRad) + 0.118 * cos(5 * latRad)
        val dy = dLat * mPerDegLat
        val dx = dLon * mPerDegLon
        return sqrt(dy * dy + dx * dx)
    }

    /** 同一公式收敛到极高精度，作为迭代反算的参考真值 */
    private fun gcj02ToWgs84Exact(gcj: CT.Coord): CT.Coord {
        var wgs = gcj
        val eps = 1e-10
        repeat(200) {
            val d = CT.diffCoord(gcj, CT.wgs84ToGcj02(wgs))
            if (Math.abs(d.lon) <= eps && Math.abs(d.lat) <= eps) return wgs
            wgs = CT.Coord(wgs.lon + d.lon, wgs.lat + d.lat)
        }
        return wgs
    }

    @Test
    fun roundTripErrorAcrossChina() {
        var maxErr = 0.0
        var sumErr = 0.0
        var over1m = 0
        var n = 0
        var worstLon = 0.0
        var worstLat = 0.0

        for (lati in 1..54) {
            val lat = lati.toDouble()
            for (lon in 73..135) {
                val lng = lon.toDouble()
                val wgs = CT.Coord(lng, lat)
                val gcj = CT.wgs84ToGcj02(wgs)
                val back = CT.gcj02ToWgs84(gcj)
                val errM = meters(back.lat - wgs.lat, back.lon - wgs.lon, lat)
                sumErr += errM
                n++
                if (errM > 1.0) over1m++
                if (errM > maxErr) {
                    maxErr = errM
                    worstLon = lng
                    worstLat = lat
                }
            }
        }
        println("=== 往返误差 WGS84->GCJ02->WGS84 (格网 %.1f°x%.1f°, n=$n) ===".format(1.0, 1.0))
        println("平均误差: %.4f m".format(sumErr / n))
        println("最大误差: %.4f m @ lon=%.0f lat=%.0f".format(maxErr, worstLon, worstLat))
        println("误差>1m 的点数: $over1m / $n")

        assertTrue("最大往返误差 ${maxErr}m 应 < 1.5m", maxErr < 1.5)
        assertTrue("平均往返误差 ${sumErr / n}m 应 < 0.5m", sumErr / n < 0.5)
    }

    @Test
    fun referencePointTiananmen() {
        // lddgo 在线工具示例: 天安门 WGS84 -> GCJ02
        val wgs = CT.Coord(116.391349, 39.907375)
        val gcj = CT.wgs84ToGcj02(wgs)
        val expect = CT.Coord(116.39759019123527, 39.90877629414095)
        val errM = meters(gcj.lat - expect.lat, gcj.lon - expect.lon, wgs.lat)
        println("=== 天安门参考点 (lddgo 官方示例) ===")
        println("正算: 期望 (%.6f, %.6f) 实测 (%.6f, %.6f) 偏差 %.4f m"
            .format(expect.lon, expect.lat, gcj.lon, gcj.lat, errM))
        assertTrue("正算偏差 ${errM}m 应 < 3m", errM < 3.0)
    }

    @Test
    fun referencePointCoordtransform() {
        // wandergis/coordtransform 官方测试数据
        val wgs = CT.Coord(116.404, 39.915)
        val gcj = CT.wgs84ToGcj02(wgs)
        val expectGcj = CT.Coord(116.41024449916938, 39.91640428150164)
        val err1 = meters(gcj.lat - expectGcj.lat, gcj.lon - expectGcj.lon, wgs.lat)

        val gcjIn = CT.Coord(116.404, 39.915)
        val wgsOut = CT.gcj02ToWgs84(gcjIn)
        val expectWgs = CT.Coord(116.39775550083061, 39.91359571849836)
        val err2 = meters(wgsOut.lat - expectWgs.lat, wgsOut.lon - expectWgs.lon, gcjIn.lat)

        println("=== coordtransform 测试点 (116.404, 39.915) ===")
        println("正算 wgs->gcj: 偏差 %.4f m".format(err1))
        println("反算 gcj->wgs: 期望(%.6f,%.6f) 实测(%.6f,%.6f) 偏差 %.4f m"
            .format(expectWgs.lon, expectWgs.lat, wgsOut.lon, wgsOut.lat, err2))
        assertTrue("正算偏差 ${err1}m 应 < 3m", err1 < 3.0)
        assertTrue("反算偏差 ${err2}m 应 < 5m", err2 < 5.0)
    }

    @Test
    fun accuracyVsExactInverse() {
        var maxErr = 0.0
        var sumErr = 0.0
        var n = 0
        var maxIterUsed = 0
        var worstLon = 0.0
        var worstLat = 0.0
        var truncated = 0

        for (lati in 1..54) {
            val lat = lati.toDouble()
            for (lon in 73..135) {
                val lng = lon.toDouble()
                val gcj = CT.wgs84ToGcj02(CT.Coord(lng, lat))
                val wgsFast = CT.gcj02ToWgs84(gcj)
                val wgsExact = gcj02ToWgs84Exact(gcj)
                val errM = meters(wgsFast.lat - wgsExact.lat, wgsFast.lon - wgsExact.lon, lat)

                // 复算迭代次数
                var w = CT.Coord(gcj.lon, gcj.lat)
                var d = CT.diffCoord(gcj, CT.wgs84ToGcj02(w))
                var iter = 0
                while (iter < 10 &&
                    (Math.abs(d.lon) > CT.g2wPrecision || Math.abs(d.lat) > CT.g2wPrecision)
                ) {
                    w = CT.Coord(w.lon + d.lon, w.lat + d.lat)
                    d = CT.diffCoord(gcj, CT.wgs84ToGcj02(w))
                    iter++
                }
                if (iter > maxIterUsed) maxIterUsed = iter
                if (iter == 10 && (Math.abs(d.lon) > CT.g2wPrecision || Math.abs(d.lat) > CT.g2wPrecision)) {
                    truncated++
                }

                sumErr += errM
                n++
                if (errM > maxErr) {
                    maxErr = errM
                    worstLon = lng
                    worstLat = lat
                }
            }
        }
        println("=== 与高精度反算(1e-10度)对照 (n=$n) ===")
        println("平均偏差: %.4f m".format(sumErr / n))
        println("最大偏差: %.4f m @ lon=%.0f lat=%.0f".format(maxErr, worstLon, worstLat))
        println("10次迭代未收敛点数: $truncated")
        println("实际最大迭代次数: $maxIterUsed")
        assertTrue("反算最大偏差 ${maxErr}m 应 < 1.5m", maxErr < 1.5)
    }

    @Test
    fun outOfChinaPassthrough() {
        val p = CT.Coord(-77.03687, 38.90719)
        val gcj = CT.wgs84ToGcj02(p)
        val wgs = CT.gcj02ToWgs84(CT.Coord(gcj.lon, gcj.lat))
        println("=== 境外点直通检查 (华盛顿) ===")
        println("正算返回 %s, 反算返回 %s".format(gcj, wgs))
        assertTrue(gcj.lon == p.lon && gcj.lat == p.lat)
        assertTrue(wgs.lon == p.lon && wgs.lat == p.lat)
    }
}
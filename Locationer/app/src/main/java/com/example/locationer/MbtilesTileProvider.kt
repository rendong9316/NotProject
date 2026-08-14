package com.example.locationer

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.amap.api.maps.model.Tile
import com.amap.api.maps.model.TileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * MBTiles 离线卫星瓦片提供者
 *
 * 架构灵感：RadarView (Tauri/Cesium)
 *   - 使用 MBTiles SQLite 标准格式存储离线瓦片
 *   - AMap TileProvider 接口直接读取本地 SQLite，零网络依赖
 *
 * 工作原理：
 *   1. App 启动时将 assets/tiles_china.mbtiles 复制到内部存储（仅首次）
 *   2. 每次 getTile() 查询本地 SQLite，返回 Tile 对象
 *   3. 离线状态下完全可用，联网时可额外下载更高 zoom 瓦片
 *
 * MBTiles 坐标系说明：
 *   - 存储格式：TMS（tile_row=0 对应北纬 85° 线）
 *   - 请求格式：XYZ（tile_y=0 对应南极）
 *   - 转换公式：xyz_y = 2^z - 1 - tms_y
 */
class MbtilesTileProvider private constructor(
    private val dbFile: File,
    private val tileSize: Int = 256,
) : TileProvider {

    companion object {
        private const val TAG = "MbtilesTileProvider"
        private const val ASSETS_MBTILES = "tiles_china.mbtiles"
        private const val DB_FILENAME = "offline_tiles.db"
        private const val MAX_WORKER_THREADS = 3

        /**
         * 创建 TileProvider 实例。
         * 若 assets 中不存在 .mbtiles 文件，将返回始终返回 null 的空实现。
         */
        fun create(context: Context): MbtilesTileProvider {
            val dbFile = extractMbtilesIfNeeded(context)
            if (!dbFile.exists() || dbFile.length() == 0L) {
                Log.w(TAG, "未找到离线瓦片数据库，将返回空白地图")
            } else {
                Log.d(TAG, "使用离线瓦片数据库: ${dbFile.absolutePath} (${dbFile.length()} bytes)")
            }
            return MbtilesTileProvider(dbFile)
        }

        /**
         * 将 assets 中的 .mbtiles 文件复制到内部存储。
         * 仅在目标文件不存在时执行一次，后续启动直接读取。
         */
        private fun extractMbtilesIfNeeded(context: Context): File {
            val target = File(context.filesDir, DB_FILENAME)
            if (target.exists() && target.length() > 0L) return target

            val am = context.assets
            try {
                val input: InputStream = am.open(ASSETS_MBTILES)
                target.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
                Log.d(TAG, "已从 assets 提取瓦片数据库 (${target.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "从 assets 提取瓦片失败: ${e.message}", e)
                // 创建空占位文件，避免重复尝试
                target.createNewFile()
            }
            return target
        }

        /** XYZ Y 坐标 → TMS Y 坐标（MBTiles 存储格式） */
        @JvmStatic
        fun xyzYtoTmsY(z: Int, xyzY: Int): Int = (1 shl z) - 1 - xyzY
    }

    // ─── 后台线程池：用于异步预加载瓦片 ───
    private val executor: ExecutorService = Executors.newFixedThreadPool(MAX_WORKER_THREADS)

    // ─── TileProvider 核心接口 ───
    override fun getTileWidth() = tileSize
    override fun getTileHeight() = tileSize

    override fun getTile(x: Int, y: Int, z: Int): Tile? {
        // 参数合法性校验
        if (z < 0 || z > 22 || x < 0 || y < 0) return null
        val maxCoord = (1 shl z) - 1
        if (x > maxCoord || y > maxCoord) return null

        // XYZ → TMS 坐标转换
        val tmsY = xyzYtoTmsY(z, y)

        return try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null,
                SQLiteDatabase.OPEN_READONLY
            )
            var tileBytes: ByteArray? = null
            var cursor: Cursor? = null
            try {
                cursor = db.query(
                    "tiles",
                    null,
                    "zoom_level = ? AND tile_column = ? AND tile_row = ?",
                    arrayOf(z.toString(), x.toString(), tmsY.toString()),
                    null, null, null
                )
                if (cursor.moveToFirst()) {
                    tileBytes = cursor.getBlob(0)
                }
            } finally {
                cursor?.close()
                db.close()
            }

            if (tileBytes != null && tileBytes.isNotEmpty()) {
                Log.d(TAG, "读取瓦片 z=$z x=$x y=$y (${tileBytes.size} bytes)")
                Tile(tileSize, tileSize, tileBytes)
            } else {
                Log.d(TAG, "瓦片未缓存 z=$z x=$x y=$y（超出数据库范围）")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取瓦片失败 z=$z x=$x y=$y: ${e.message}")
            null
        }
    }

    // ─── 预加载请求（联网时可用于触发远程下载，目前仅作占位） ───
    fun preloadAround(x: Int, y: Int, z: Int) {
        executor.execute {
            val range = 1
            val maxCoord = (1 shl z) - 1
            for (dx in -range..range) {
                for (dy in -range..range) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0..maxCoord && ny in 0..maxCoord) {
                        preload(nx, ny, z)
                    }
                }
            }
        }
    }

    /** 异步预加载单个瓦片（联网时可用于后台下载） */
    private fun preload(x: Int, y: Int, z: Int) {
        // 当前离线模式：预加载仅作日志记录
        Log.d(TAG, "预加载 z=$z x=$x y=$y（离线模式下无操作）")
    }

    /** 获取数据库文件大小（用于 UI 显示） */
    fun getDbSizeBytes(): Long = runCatching { dbFile.length() }.getOrDefault(0L)

    /** 清理 WAL 缓存文件 */
    fun clearCache() {
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()
        Log.d(TAG, "已清理 WAL 缓存文件")
    }

    fun shutdown() {
        executor.shutdown()
    }
}

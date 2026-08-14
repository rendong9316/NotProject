"""
离线卫星瓦片下载器 — 生成 MBTiles SQLite 文件

依赖: pip install requests
注: Pillow 仅用于验证图片，MBTiles 直接存储原始 JPEG 字节

用法:
  python gen_tiles.py                     # 中国全境 zoom 0-8
  python gen_tiles.py --max-zoom 10       # zoom 0-10
  python gen_tiles.py --output my_tiles.mbtiles

输出: tiles_china.mbtiles（MBTiles 标准格式，可直接用于 Android）
"""
import argparse
import math
import os
import sqlite3
import sys
import time
from pathlib import Path

import requests

# ─── 中国范围边界（WGS84 经纬度）───
CHINA_BOUNDS = {
    "lon_min": 73.0,
    "lon_max": 135.0,
    "lat_min": 18.0,
    "lat_max": 54.0,
}

# Esri World Imagery 瓦片服务（免费、无需 Key、全球覆盖）
TILE_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

# 天地图卫星影像（需 Key，质量更高，国内更稳定）
# TIANDITU_URL = "http://t{{0}}.tianditu.gov.cn/img_w/wmts?service=wmts&request=GetTile&version=1.0.0&LAYER=img&tileMatrixSet=w&TileMatrix={{z}}&TileRow={{y}}&TileCol={{x}}&style=default&format=tiles"
# TIANDITU_KEYS = "0123456789abcdef"


def lon_lat_to_tile(lon: float, lat: float, z: int) -> tuple[int, int]:
    """经纬度 → XYZ 瓦片坐标"""
    n = 2 ** z
    x = int((lon + 180.0) / 360.0 * n)
    lat_rad = math.radians(lat)
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return x, y


def ensure_bounds(z: int) -> tuple[int, int, int, int]:
    """计算覆盖中国的瓦片范围（含边界缓冲）"""
    b = CHINA_BOUNDS
    x1, y1 = lon_lat_to_tile(b["lon_min"], b["lat_max"], z)
    x2, y2 = lon_lat_to_tile(b["lon_max"], b["lat_min"], z)
    pad = 1  # 边界缓冲
    return max(0, x1 - pad), min(2 ** z - 1, x2 + pad), \
           max(0, y1 - pad), min(2 ** z - 1, y2 + pad)


def fetch_tile(url: str, max_retries: int = 3) -> bytes | None:
    """下载单个瓦片，失败自动重试"""
    for attempt in range(max_retries):
        try:
            resp = requests.get(url, timeout=15, stream=True)
            resp.raise_for_status()
            data = resp.content
            # Esri 错误时返回 HTML，尺寸很小，跳过
            if len(data) < 500 and b"<html" in data.lower():
                if attempt < max_retries - 1:
                    time.sleep(1)
                    continue
                return None
            return data
        except Exception as e:
            if attempt < max_retries - 1:
                time.sleep(1)
            else:
                print(f"  请求失败: {e}")
    return None


def generate_mbtiles(output_path: Path, max_zoom: int = 8):
    """生成 MBTiles SQLite 文件"""
    print(f"\n{'='*55}")
    print(f"离线卫星瓦片生成器")
    print(f"范围: 中国 ({CHINA_BOUNDS['lon_min']}°E ~ {CHINA_BOUNDS['lon_max']}°E)")
    print(f"         ({CHINA_BOUNDS['lat_min']}°N ~ {CHINA_BOUNDS['lat_max']}°N)")
    print(f"最大层级: zoom {max_zoom}")
    print(f"瓦片服务: Esri World Imagery (免费)")
    print(f"输出文件: {output_path.resolve()}")
    print(f"{'='*55}\n")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists():
        output_path.unlink()

    conn = sqlite3.connect(str(output_path))
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")

    # MBTiles 标准 schema
    conn.executescript("""
        CREATE TABLE metadata (name TEXT, value TEXT);
        CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER,
                            tile_row INTEGER, tile_data BLOB);
        CREATE UNIQUE INDEX idx_tiles ON tiles (zoom_level, tile_column, tile_row);
    """)

    meta = [
        ("name", "Esri World Imagery (China)"),
        ("description", "Offline satellite tiles for mainland China"),
        ("format", "jpeg"),
        ("minzoom", "0"),
        ("maxzoom", str(max_zoom)),
        ("type", "baselayer"),
        ("bounds", f"{CHINA_BOUNDS['lon_min']},{CHINA_BOUNDS['lat_min']},"
                   f"{CHINA_BOUNDS['lon_max']},{CHINA_BOUNDS['lat_max']}"),
    ]
    conn.executemany("INSERT INTO metadata VALUES (?, ?)", meta)

    total_downloaded = 0
    total_skipped = 0
    start_time = time.time()

    for z in range(max_zoom + 1):
        x1, x2, y1, y2 = ensure_bounds(z)
        count = (x2 - x1 + 1) * (y2 - y1 + 1)
        print(f"[Zoom {z:2d}] 计划下载 {count:>7} 张瓦片 "
              f"(x:{x1}~{x2}, y:{y1}~{y2}) ...", flush=True)

        downloaded = 0
        for y in range(y1, y2 + 1):
            for x in range(x1, x2 + 1):
                url = TILE_URL.format(z=z, y=y, x=x)
                data = fetch_tile(url)
                if data:
                    # MBTiles 使用 TMS 坐标系：tms_y = 2^z - 1 - xyz_y
                    tms_y = (2 ** z) - 1 - y
                    conn.execute(
                        "INSERT OR REPLACE INTO tiles VALUES (?, ?, ?, ?)",
                        (z, x, tms_y, data)
                    )
                    downloaded += 1
                else:
                    total_skipped += 1

        conn.commit()
        total_downloaded += downloaded
        elapsed = time.time() - start_time
        speed = total_downloaded / elapsed if elapsed > 0 else 0
        print(f"         已缓存 {downloaded:>4} 张  "
              f"累计 {total_downloaded} 张  "
              f"({elapsed:.0f}s, {speed:.1f} 张/秒)\n", flush=True)

    conn.close()

    size_mb = output_path.stat().st_size / (1024 * 1024)
    print(f"\n{'='*55}")
    print(f"完成!")
    print(f"   总瓦片数: {total_downloaded}")
    print(f"   跳过失败: {total_skipped}")
    print(f"   文件大小: {size_mb:.1f} MB")
    print(f"   输出路径: {output_path.resolve()}")
    print(f"{'='*55}")

    # 验证
    conn = sqlite3.connect(str(output_path))
    print("\n各层级瓦片数量:")
    for row in conn.execute(
        "SELECT zoom_level, COUNT(*) FROM tiles "
        "GROUP BY zoom_level ORDER BY zoom_level"
    ):
        print(f"   zoom {row[0]:2d}: {row[1]:>6} 张")
    conn.close()


def main():
    parser = argparse.ArgumentParser(description="生成离线卫星瓦片 MBTiles")
    parser.add_argument("--max-zoom", type=int, default=8,
                        help="最大 zoom 层级 (默认 8，demo 用; 正式版建议 10)")
    parser.add_argument("--output", "-o", type=Path, default=None,
                        help="输出文件路径 (默认 tiles_china.mbtiles)")
    args = parser.parse_args()

    output = args.output or Path("tiles_china.mbtiles")
    generate_mbtiles(output, args.max_zoom)


if __name__ == "__main__":
    main()

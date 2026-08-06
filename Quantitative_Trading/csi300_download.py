"""
脚本名称: csi300_download.py (v1 - 初始版)
功能: 下载沪深300成分股前复权日线数据
数据源: akshare (底层调用新浪/腾讯财经接口)
运行时间: 2026-08-06
状态: ❌ 已弃用 — 此版本在网络不稳定时失败率极高（仅40/300成功）

用法:
    python csi300_download.py

输出:
    ./csi300_daily_data/ 目录下每只股票一个CSV文件

依赖:
    pip install akshare pandas

缺陷与注意事项:
1. akshare底层接口限流严格，同一IP短时间内请求过多会被远程服务器断开连接
2. 重试策略不够智能，固定等待时间不够灵活
3. 建议改用 v3 版本（Baostock主力版）
"""
import akshare as ak
import pandas as pd
import os
import time
from datetime import datetime

START_DATE = "20200101"
END_DATE = datetime.now().strftime("%Y%m%d")
OUTPUT_DIR = "./csi300_daily_data"
MAX_RETRIES = 5
RETRY_DELAY = 3  # 秒

def ensure_output_dir():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

def get_csi300_components():
    print("正在获取沪深300成分股列表...")
    try:
        df = ak.index_stock_cons_weight_csindex(symbol="000300")
        stocks = df[["成分券代码", "成分券名称"]].dropna()
        stocks.columns = ["code", "name"]
        stocks = stocks[stocks["code"].notna()].copy()
        print(f"  共获取到 {len(stocks)} 只成分股")
        return stocks
    except Exception as e:
        print(f"  [错误] 获取沪深300成分股失败: {e}")
        return pd.DataFrame(columns=["code", "name"])

def download_stock_data(code, name, start_date, end_date):
    """下载单只股票的前复权日线数据，带重试"""
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            df = ak.stock_zh_a_hist(
                symbol=code,
                period="daily",
                start_date=start_date,
                end_date=end_date,
                adjust="qfq"
            )
            if df is None or df.empty:
                return None, f"数据为空"
            col_map = {
                "日期": "date", "开盘": "open", "收盘": "close",
                "最高": "high", "最低": "low", "成交量": "volume", "成交额": "amount"
            }
            df = df[list(col_map.keys())].copy()
            df.columns = col_map.values()
            df["code"] = code
            df["name"] = name
            return df, None
        except Exception as e:
            err_msg = str(e)
            if attempt < MAX_RETRIES:
                wait = RETRY_DELAY * attempt
                print(f"    (第{attempt}次失败，{wait}s后重试...) ", end="", flush=True)
                time.sleep(wait)
            else:
                return None, err_msg
    return None, f"重试{MAX_RETRIES}次后仍失败"

def main():
    ensure_output_dir()

    # 检查已下载的CSV，跳过已完成的
    existing = set()
    if os.path.exists(OUTPUT_DIR):
        for fname in os.listdir(OUTPUT_DIR):
            if fname.endswith(".csv"):
                code = fname.split("_")[0]
                existing.add(code)
    print(f"已有 {len(existing)} 个文件，跳过这些股票\n")

    stocks = get_csi300_components()
    if stocks.empty:
        print("未获取到成分股列表，程序退出。")
        return

    # 过滤掉已下载的
    fail_stocks = stocks[~stocks["code"].astype(str).str.strip().isin(existing)]
    total = len(fail_stocks)
    print(f"待下载: {total} 只 (共{len(stocks)}只，跳过{len(stocks) - total}只)\n")

    success_count = 0
    fail_list = []

    for idx, row in fail_stocks.iterrows():
        code = str(row["code"]).strip()
        name = str(row["name"]).strip()
        progress = (idx - (len(stocks) - total) + 1) / total * 100 if total > 0 else 100
        # 用原始索引计算真实进度
        orig_idx = stocks.index.get_loc(idx)
        progress = (orig_idx + 1) / len(stocks) * 100
        print(f"[{orig_idx+1}/{len(stocks)}] ({progress:5.1f}%) {code} {name} ... ", end="", flush=True)

        df, err = download_stock_data(code, name, START_DATE, END_DATE)
        if err:
            print(f"失败: {err}")
            fail_list.append((code, name, err))
        else:
            csv_path = os.path.join(OUTPUT_DIR, f"{code}_{name}.csv")
            df.to_csv(csv_path, index=False, encoding="utf-8-sig")
            print(f"OK ({len(df)}条记录)")
            success_count += 1

        # 放慢请求速度，避免被封
        time.sleep(1.0)

    print("\n" + "=" * 50)
    print(f"本次补充下载完成！成功: {success_count}/{total}")
    if fail_list:
        print(f"仍失败 {len(fail_list)} 只，详情：")
        for code, name, err in fail_list[:10]:
            print(f"  - {code} {name}: {err}")
        if len(fail_list) > 10:
            print(f"  ... 共 {len(fail_list)} 只失败")
    total_ok = len([f for f in os.listdir(OUTPUT_DIR) if f.endswith(".csv")])
    print(f"文件夹中共有 {total_ok} 个CSV文件")
    print(f"数据保存在: {os.path.abspath(OUTPUT_DIR)}")

if __name__ == "__main__":
    main()

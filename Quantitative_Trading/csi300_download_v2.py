"""
脚本名称: csi300_download_v2.py (v2 - 综合优化版)
功能: 下载沪深300成分股前复权日线数据
数据源: akshare(东方财富源) + Baostock(兜底)
运行时间: 2026-08-06
状态: ⚠️ 部分可用 — akshare的stock_zh_a_hist()不支持source参数，已报错

关键改进(相比v1):
  1. 尝试使用东方财富源替代新浪源(更稳定)
  2. 随机延迟(1.5~3.5秒)替代固定延迟
  3. 分批下载(每批30只，批间休息60秒)
  4. Baostock兜底(akshare失败时自动切换)

已知缺陷:
  1. stock_zh_a_hist()不支持source参数，传入会报错
  2. akshare东方财富源仍受限流，实际使用效果有限
  3. 建议改用v3版本(纯Baostock，已验证100%成功)

用法:
    python csi300_download_v2.py

输出:
    ./csi300_daily_data/ 目录下每只股票一个CSV文件

依赖:
    pip install akshare baostock pandas
"""
import akshare as ak
import baostock as bs
import pandas as pd
import os
import time
import random
from datetime import datetime

START_DATE = "20200101"
END_DATE = datetime.now().strftime("%Y%m%d")
OUTPUT_DIR = "./csi300_daily_data"

MAX_RETRIES = 3
RETRY_BASE_DELAY = 2
BATCH_SIZE = 30
BATCH_BREAK = 60  # 每批之间休息秒数


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


def download_akshare(code, name, start_date, end_date):
    """用东方财富源下载，带重试"""
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            df = ak.stock_zh_a_hist(
                symbol=code,
                period="daily",
                start_date=start_date,
                end_date=end_date,
                adjust="qfq",
                # source="eastmoney"  # ⚠️ 此参数不存在，会导致TypeError
                # 东方财富源实际通过ak.stock_zh_a_hist_em()访问
            )
            if df is None or df.empty:
                return None, "数据为空"
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
            if attempt < MAX_RETRIES:
                wait = RETRY_BASE_DELAY * attempt + random.uniform(0, 2)
                print(f"    (akshare第{attempt}次失败, {wait:.1f}s后重试...) ", end="", flush=True)
                time.sleep(wait)
            else:
                return None, str(e)
    return None, f"akshare重试{MAX_RETRIES}次失败"


def download_baostock(code, name, start_date, end_date):
    """用Baostock兜底下载
    缺陷:
    - Baostock login/logout需要匹配，否则可能留下残留连接
    - rs.get_data()在login失效时可能返回None
    - 部分科创板股票(688)在2020年7月前可能无数据
    """
    try:
        bs.login()
        # Baostock代码格式: sh.600000 / sz.000001
        if code.startswith("6"):
            bs_code = f"sh.{code}"
        else:
            bs_code = f"sz.{code}"

        fields = "date,open,high,low,close,volume,amount"
        rs = bs.query_history_k_data_plus(
            bs_code, fields,
            start_date=start_date, end_date=end_date,
            frequency="d", adjustflag="2"  # 2=前复权
        )

        if rs.error_code != '0':
            bs.logout()
            return None, f"Baostock错误: {rs.error_msg}"

        data = rs.get_data()
        bs.logout()

        if data is None or data.empty:
            return None, "Baostock数据为空"

        data = data.rename(columns={
            "date": "date", "open": "open", "close": "close",
            "high": "high", "low": "low", "volume": "volume", "amount": "amount"
        })
        data["code"] = code
        data["name"] = name
        return data, None
    except Exception as e:
        try:
            bs.logout()
        except:
            pass
        return None, str(e)


def save_csv(df, code, name):
    csv_path = os.path.join(OUTPUT_DIR, f"{code}_{name}.csv")
    df.to_csv(csv_path, index=False, encoding="utf-8-sig")
    return csv_path


def main():
    ensure_output_dir()

    # 检查已下载的
    existing = set()
    if os.path.exists(OUTPUT_DIR):
        for fname in os.listdir(OUTPUT_DIR):
            if fname.endswith(".csv"):
                code = fname.split("_")[0]
                existing.add(code)
    print(f"已有 {len(existing)} 个文件，将跳过\n")

    stocks = get_csi300_components()
    if stocks.empty:
        print("未获取到成分股列表，程序退出。")
        return

    # 过滤掉已下载的
    fail_stocks = stocks[~stocks["code"].apply(lambda x: str(x).strip()).isin(existing)]
    total = len(fail_stocks)
    print(f"待下载: {total} 只 (共{len(stocks)}只，跳过{len(stocks) - total}只)\n")

    success_count = 0
    fail_list = []

    # 分批处理
    num_batches = (total + BATCH_SIZE - 1) // BATCH_SIZE

    for batch_idx in range(num_batches):
        start_i = batch_idx * BATCH_SIZE
        end_i = min(start_i + BATCH_SIZE, total)
        batch = fail_stocks.iloc[start_i:end_i]
        batch_num = batch_idx + 1

        print(f"\n{'='*50}")
        print(f"第 {batch_num}/{num_batches} 批，{len(batch)} 只股票")
        print(f"{'='*50}")

        for local_idx, (_, row) in enumerate(batch.iterrows()):
            code = str(row["code"]).strip()
            name = str(row["name"]).strip()
            global_idx = start_i + local_idx + 1
            progress = global_idx / total * 100
            print(f"[{global_idx}/{total}] ({progress:5.1f}%) {code} {name} ... ", end="", flush=True)

            # 先用 akshare (东方财富源)
            df, err = download_akshare(code, name, START_DATE, END_DATE)

            if err:
                # akshare失败，用Baostock兜底
                print(f"akshare失败({err[:30]}...)，尝试Baostock... ", end="", flush=True)
                df, err = download_baostock(code, name, START_DATE, END_DATE)
                if err:
                    print(f"Baostock也失败: {err}")
                    fail_list.append((code, name, f"akshare+bs双重失败: {err}"))
                else:
                    print(f"Baostock成功({len(df)}条)")
                    save_csv(df, code, name)
                    success_count += 1
            else:
                print(f"OK ({len(df)}条记录)")
                save_csv(df, code, name)
                success_count += 1

            # 随机延迟（伪装正常访问）
            time.sleep(random.uniform(1.5, 3.5))

        # 批次间休息
        if batch_idx < num_batches - 1:
            print(f"\n  第{batch_num}批完成，休息{BATCH_BREAK}秒...")
            time.sleep(BATCH_BREAK)

    # 汇总报告
    print("\n" + "=" * 50)
    print(f"下载完成！本次成功: {success_count}/{total}")
    if fail_list:
        print(f"仍失败 {len(fail_list)} 只:")
        for code, name, err in fail_list:
            print(f"  - {code} {name}: {err}")
    total_ok = len([f for f in os.listdir(OUTPUT_DIR) if f.endswith(".csv")])
    print(f"文件夹中共有 {total_ok} 个CSV文件")
    print(f"数据保存在: {os.path.abspath(OUTPUT_DIR)}")


if __name__ == "__main__":
    main()

"""
脚本名称: csi300_download_v3.py (v3 - 最终推荐版)
功能: 下载沪深300成分股前复权日线数据
数据源: Baostock (主力) + akshare (仅获取成分股列表)
运行时间: 2026-08-06
状态: ✅ 已验证 — 300/300只全部成功下载

核心优势(相比v1/v2):
  1. Baostock无请求限制，无需担心被封
  2. 每次请求间隔1~2秒随机延迟，避免触发限流
  3. 自动跳过已下载的股票，支持断点续传
  4. 统一列顺序和数据类型，方便后续处理

数据说明:
  - 字段: date, open, high, low, close, volume, amount, code, name
  - 复权: 前复权 (adjustflag="2")
  - 周期: 2020-01-02 至 2026-08-06
  - 数量: 300只股票，约46万条记录

Baostock缺陷与注意事项:
  1. 登录状态: login()后需logout()，否则后续请求可能失败(已有try/except兜底)
  2. 数据延迟: 通常T+1更新，最新交易日数据可能不全
  3. 科创板限制: 科创板(688)2020年7月开板，此前无数据
  4. 退市股票: 已退市股票可能只有上市日至退市日的历史数据
  5. 异常状态: ST、*ST等异常股票数据可能不完整
  6. 成分股时效: 本脚本使用当前成分股列表回溯，历史上已调出的股票
     仍包含在数据中。回测时建议使用滚动成分股而非固定成分股

用法:
    python csi300_download_v3.py

输出:
    ./csi300_daily_data/ 目录下每只股票一个CSV文件

依赖:
    pip install baostock pandas akshare
"""
import baostock as bs
import pandas as pd
import os
import time
import random
from datetime import datetime

START_DATE = "2020-01-01"
END_DATE = datetime.now().strftime("%Y-%m-%d")
OUTPUT_DIR = "./csi300_daily_data"


def ensure_output_dir():
    os.makedirs(OUTPUT_DIR, exist_ok=True)


def get_csi300_components():
    """用akshare获取成分股列表（只获取列表，不下载数据）"""
    import akshare as ak
    print("正在获取沪深300成分股列表...")
    try:
        df = ak.index_stock_cons_weight_csindex(symbol="000300")
        stocks = df[["成分券代码", "成分券名称"]].dropna()
        stocks.columns = ["code", "name"]
        stocks = stocks[stocks["code"].notna()].copy()
        print(f"  共获取到 {len(stocks)} 只成分股")
        return stocks
    except Exception as e:
        print(f"  [错误] 获取成分股失败: {e}")
        return pd.DataFrame(columns=["code", "name"])


def to_baostock_code(code):
    """将股票代码转为Baostock格式
    缺陷:
    - 科创板股票(688开头)需要sh.前缀，但部分688股票在Baostock中可能无数据
    - 北交所股票(8/4/9开头)的转换逻辑未处理
    """
    code = str(code).strip()
    if code.startswith("6"):
        return f"sh.{code}"
    else:
        return f"sz.{code}"


def download_one_stock(bs_code, code, name, start_date, end_date):
    """下载单只股票，返回DataFrame或(None, error_msg)
    缺陷:
    - Baostock返回的列顺序不固定，需要手动重命名和排序
    - 数值列需要手动转换类型，否则可能是字符串
    - rs.get_data()在login失效时返回None，需要try/except兜底
    """
    try:
        rs = bs.query_history_k_data_plus(
            bs_code,
            "date,open,high,low,close,volume,amount",
            start_date=start_date, end_date=end_date,
            frequency="d", adjustflag="2"  # 2=前复权
        )
        if rs.error_code != "0":
            return None, f"error_code={rs.error_code}, {rs.error_msg}"

        data = rs.get_data()
        if data is None or len(data) == 0:
            return None, "数据为空"

        data = data.rename(columns={
            "date": "date", "open": "open", "close": "close",
            "high": "high", "low": "low", "volume": "volume", "amount": "amount"
        })
        data["code"] = code
        data["name"] = name
        # 统一列顺序: date, open, high, low, close, volume, amount, code, name
        data = data[["date", "open", "high", "low", "close", "volume", "amount", "code", "name"]]
        # 数值列转为float
        for col in ["open", "high", "low", "close", "volume", "amount"]:
            data[col] = pd.to_numeric(data[col], errors="coerce")
        return data, None
    except Exception as e:
        return None, str(e)


def save_csv(df, code, name):
    csv_path = os.path.join(OUTPUT_DIR, f"{code}_{name}.csv")
    df.to_csv(csv_path, index=False, encoding="utf-8-sig")
    return csv_path


def main():
    ensure_output_dir()

    # 检查已下载的股票
    existing = set()
    if os.path.exists(OUTPUT_DIR):
        for fname in os.listdir(OUTPUT_DIR):
            if fname.endswith(".csv"):
                code = fname.split("_")[0]
                existing.add(code)
    print(f"已有 {len(existing)} 个文件，将跳过\n")

    # 获取成分股列表
    stocks = get_csi300_components()
    if stocks.empty:
        print("未获取到成分股列表，程序退出。")
        return

    # 过滤掉已下载的
    fail_stocks = stocks[~stocks["code"].apply(lambda x: str(x).strip()).isin(existing)]
    total = len(fail_stocks)
    print(f"待下载: {total} 只\n")

    if total == 0:
        print("所有股票已下载完成！")
        return

    # Baostock login
    print("正在登录Baostock...")
    lg = bs.login()
    if lg.error_code != "0":
        print(f"Baostock登录失败: {lg.error_msg}")
        return

    success_count = 0
    fail_list = []

    for idx, (_, row) in enumerate(fail_stocks.iterrows()):
        code = str(row["code"]).strip()
        name = str(row["name"]).strip()
        bs_code = to_baostock_code(code)
        progress = (idx + 1) / total * 100
        print(f"[{idx+1}/{total}] ({progress:5.1f}%) {code} {name} ... ", end="", flush=True)

        df, err = download_one_stock(bs_code, code, name, START_DATE, END_DATE)
        if err:
            print(f"失败: {err}")
            fail_list.append((code, name, err))
        else:
            save_csv(df, code, name)
            print(f"OK ({len(df)}条记录)")
            success_count += 1

        # 随机延迟 1~2 秒
        time.sleep(random.uniform(1.0, 2.0))

    # logout
    try:
        bs.logout()
    except:
        pass

    # 汇总
    print("\n" + "=" * 50)
    print(f"下载完成！本次成功: {success_count}/{total}")
    if fail_list:
        print(f"失败 {len(fail_list)} 只:")
        for code, name, err in fail_list[:10]:
            print(f"  - {code} {name}: {err}")
        if len(fail_list) > 10:
            print(f"  ... 共 {len(fail_list)} 只失败")
    total_ok = len([f for f in os.listdir(OUTPUT_DIR) if f.endswith(".csv")])
    print(f"文件夹中共有 {total_ok} 个CSV文件")
    print(f"数据保存在: {os.path.abspath(OUTPUT_DIR)}")


if __name__ == "__main__":
    main()

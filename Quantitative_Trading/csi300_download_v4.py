"""
csi300_download_v4.py - 修复版
修复内容:
  1. Baostock返回的volume/amount是object类型字符串，需要正确转换
  2. 修复前复权导致NaN价格的问题（春节期间部分股票价格数据缺失）
  3. 确保volume为int64，amount为float64
  4. 修复价格逻辑：high>=low, high>=open/close, low<=open/close
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
    code = str(code).strip()
    if code.startswith("6"):
        return f"sh.{code}"
    else:
        return f"sz.{code}"


def download_one_stock(bs_code, code, name, start_date, end_date):
    """下载单只股票，返回DataFrame或(None, error_msg)
    修复点:
      - Baostock返回的volume/amount是object(字符串)，需正确转换
      - 价格NaN处理：停牌日price全NaN但有volume/amount时，price填0
      - 确保volume为int64(单位为股)，amount为float64(单位为元)
    """
    try:
        rs = bs.query_history_k_data_plus(
            bs_code,
            "date,open,high,low,close,volume,amount",
            start_date=start_date, end_date=end_date,
            frequency="d", adjustflag="2"
        )
        if rs.error_code != "0":
            return None, f"error_code={rs.error_code}, {rs.error_msg}"

        data = rs.get_data()
        if data is None or len(data) == 0:
            return None, "数据为空"

        # Baostock返回的数值列是object类型(字符串)，需要转换
        # open/high/low/close/amount → float64
        # volume → int64 (Baostock返回的是"股"为单位，不需要再乘100)
        price_cols = ["open", "high", "low", "close"]
        for col in price_cols:
            data[col] = pd.to_numeric(data[col], errors="coerce")

        # volume: 先转float再转int (处理可能的空值)
        data["volume"] = pd.to_numeric(data["volume"], errors="coerce")
        # 检查是否有极小值(可能是以"手"为单位的bug)
        # Baostock的volume单位是"股"，正常值应该>=100
        # 如果发现volume < 10000 且 amount很大，说明需要乘100
        vol_min = data["volume"].min()
        vol_max = data["volume"].max()
        amt_median = data["amount"].median()
        close_median = data["close"].median()
        # 如果volume_max < 1000 但 amount很大，说明volume是以"手"为单位
        if vol_max < 1000 and amt_median > 100000000 and close_median > 1:
            data["volume"] = data["volume"] * 100  # 转换为"股"
            print(f"    [注意] volume需乘100 (原为手单位)")

        data["volume"] = data["volume"].fillna(0).astype("int64")

        # amount: float64
        data["amount"] = pd.to_numeric(data["amount"], errors="coerce")
        data["amount"] = data["amount"].fillna(0.0)

        data["code"] = code
        data["name"] = name

        # 统一列顺序
        data = data[["date", "open", "high", "low", "close", "volume", "amount", "code", "name"]]

        # 处理NaN价格: 如果价格全为NaN但有成交量，说明是停牌日，填0
        all_price_nan = data[price_cols].isna().all(axis=1)
        has_vol = data["volume"] > 0
        both = all_price_nan & has_vol
        if both.any():
            data.loc[both, price_cols] = 0.0

        # 前向填充剩余NaN价格
        for col in price_cols:
            before = int(data[col].isna().sum())
            if before > 0:
                data[col] = data[col].ffill()
                data[col] = data[col].bfill()
                after = int(data[col].isna().sum())
                if after > 0:
                    print(f"    [警告] {col}仍有{after}个NaN")

        # 确保float类型
        for col in price_cols + ["amount"]:
            data[col] = data[col].astype("float64")

        return data, None
    except Exception as e:
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

    fail_stocks = stocks[~stocks["code"].apply(lambda x: str(x).strip()).isin(existing)]
    total = len(fail_stocks)
    print(f"待下载: {total} 只\n")

    if total == 0:
        print("所有股票已下载完成！")
        return

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

        time.sleep(random.uniform(1.0, 2.0))

    try:
        bs.logout()
    except:
        pass

    print("\n" + "=" * 50)
    print(f"下载完成！本次成功: {success_count}/{total}")
    if fail_list:
        print(f"失败 {len(fail_list)} 只:")
        for code, name, err in fail_list[:10]:
            print(f"  - {code} {name}: {err}")
    total_ok = len([f for f in os.listdir(OUTPUT_DIR) if f.endswith(".csv")])
    print(f"文件夹中共有 {total_ok} 个CSV文件")
    print(f"数据保存在: {os.path.abspath(OUTPUT_DIR)}")


if __name__ == "__main__":
    main()

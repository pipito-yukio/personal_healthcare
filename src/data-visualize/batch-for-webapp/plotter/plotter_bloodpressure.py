import base64
import logging
from typing import List

from pandas.core.frame import DataFrame

from plotter.common.statistics import BloodPressStatistics
from plotter.common.todaydata import TodayBloodPress
from plotter.dao import (
    COL_MORNING_MAX, COL_MORNING_MIN, COL_EVENING_MAX, COL_EVENING_MIN
)


def calcBloodPressureStatistics(
        df_all: DataFrame, logger: logging.Logger, is_debug: bool = False) -> BloodPressStatistics:
    """
    血圧測定データの統計情報を計算する
    [集計項目]
    (1)AM測定: 最高血圧, (2)AM測定: 最低血圧, (3)PM測定: 最高血圧, (4)PM測定: 最低血圧
    :param df_all: 検索SQLを実行しデータベースから生成したDataFrame
    :param logger:
    :param is_debug:
    :return: 血圧測定データの統計情報
    """
    # 血圧測定データの統計情報は取得したデータ(午前/午後)の平均を算出
    max_mean: float
    min_mean: float
    max_mean = df_all[COL_MORNING_MAX].mean()
    min_mean = df_all[COL_MORNING_MIN].mean()
    if is_debug:
        logger.debug(f"AM Max.mean: {max_mean}")
        logger.debug(f"AM Min.mean: {min_mean}")
    # (A) 午前測定
    am_max_mean: int = round(max_mean)
    am_min_mean: int = round(min_mean)
    # (B) 午後測定
    max_mean = df_all[COL_EVENING_MAX].mean()
    min_mean = df_all[COL_EVENING_MIN].mean()
    if is_debug:
        logger.debug(f"PM Max.mean: {max_mean}")
        logger.debug(f"PM Min.mean: {min_mean}")
    pm_max_mean: int = round(max_mean)
    pm_min_mean: int = round(min_mean)
    statistics: BloodPressStatistics = BloodPressStatistics(
        am_max_mean, am_min_mean, pm_max_mean, pm_min_mean, df_all.shape[0]
    )
    return statistics


def getTodayData(encoded_today_data: str,
                 logger: logging.Logger, is_debug: bool = False) -> TodayBloodPress:
    """
    血圧測定データの当日データを取得する
    :param encoded_today_data: base64でエンコードした文字列
    :param logger:
    :param is_debug:
    :return: TodayBloodPressオブジェクト
    """
    # base64 decode return bytes
    b_data: bytes = base64.b64decode(encoded_today_data)
    # byte to string
    raw_data: str = b_data.decode()
    if logger is not None and is_debug:
        logger.debug(f"raw_data: {raw_data}")
    parts: List = raw_data.split(",")
    # 血圧測定データの当日データ
    result: TodayBloodPress = TodayBloodPress(
        measurement_day=parts[0], morning_max=int(parts[1]),
        morning_min=int(parts[2]), morning_pulse_rate=int(parts[3])
    )
    return result

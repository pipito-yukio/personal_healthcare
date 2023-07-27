import base64
import binascii
import logging
from typing import List, Optional, Tuple

from pandas.core.frame import DataFrame

from plotter.common.statistics import BloodPressStatistics
from plotter.common.todaydata import TodayBloodPress
from plotter.dao import (
    COL_MORNING_MAX, COL_MORNING_MIN, COL_EVENING_MAX, COL_EVENING_MIN
)
from plotter.plotparameter import BloodPressUserTarget
import util.date_util as du
import util.numeric_util as nu

"""
血圧測定データプロットに関する共通関数
"""

# 有効なカラム件数:
VALID_COLUMN_CNT: int = 4

# 血圧値の許容範囲
ALLOW_PRESS_MAX_UPPER: int = 200
ALLOW_PRESS_MAX_LOWER: int = 70
ALLOW_PRESS_MIN_UPPER: int = 140
ALLOW_PRESS_MIN_LOWER: int = 20
# 脈拍のの許容範囲
ALLOW_PULSE_MAX: int = 150
ALLOW_PULSE_MIN: int = 20


def getTodayData(encoded_today_data: str,
                 logger: logging.Logger, is_debug: bool = False) -> TodayBloodPress:
    """
    血圧測定データの当日データを取得する
    ['測定日付(ISO8601形式), AM最高血圧値(mmHg), AM最低血圧値(mmHg), AM脈拍(拍/分)']
    :param encoded_today_data: base64でエンコードした文字列
    :param logger:
    :param is_debug:
    :return: TodayBloodPressオブジェクト
    :raise ValueError: TodayBloodPressオブジェクトに変換できない場合
    """
    # base64 decode return bytes
    try:
        b_data: bytes = base64.b64decode(encoded_today_data)
    except binascii.Error as err:
        if logger is not None:
            logger.warning(f"'{encoded_today_data}' ->  {err}")
        raise ValueError

    # byte to string
    raw_data: str = b_data.decode()
    if logger is not None and is_debug:
        logger.debug(f"raw_data: {raw_data}")
    parts: List = raw_data.split(",")
    # カンマ区切りの件数チェック
    if len(parts) != VALID_COLUMN_CNT:
        raise ValueError

    # 測定日付チェック
    s_date: str = parts[0]
    if not du.check_str_date(s_date):
        raise ValueError

    # 整数値チェック
    # AM最高血圧値
    s_val: str = parts[1]
    press_max: Optional[int] = nu.convert_integer(s_val)
    if press_max is None:
        raise ValueError

    # 範囲チェック
    if press_max < ALLOW_PRESS_MAX_LOWER or press_max > ALLOW_PRESS_MAX_UPPER:
        raise ValueError

    # AM最低血圧値
    s_val = parts[2]
    press_min: Optional[int] = nu.convert_integer(s_val)
    if press_min is None:
        raise ValueError

    if press_min < ALLOW_PRESS_MIN_LOWER or press_min > ALLOW_PRESS_MIN_UPPER:
        raise ValueError

    # AM脈拍
    s_val = parts[3]
    pulse_rate: Optional[int] = nu.convert_integer(s_val)
    if pulse_rate is None:
        raise ValueError

    if pulse_rate < ALLOW_PULSE_MIN or pulse_rate > ALLOW_PULSE_MAX:
        raise ValueError

    # 血圧測定データの当日データ
    result: TodayBloodPress = TodayBloodPress(
        measurement_day=s_date, morning_max=press_max,
        morning_min=press_min, morning_pulse_rate=pulse_rate
    )
    return result


def decideTargetValues(default_max: float, default_min: float,
                       user_target: BloodPressUserTarget = None) -> Tuple[float, float]:
    """
    目標値 (最高血圧, 最低血圧)を決定する ※ユーザー目標値を優先する
    :param default_max: システム設定の最高血圧目標値
    :param default_min: システム設定の最低血圧目標値
    :param user_target: ユーザー目標値
    :return: ユーザー目標値 (最高血圧, 最低血圧)
    """
    result_max: float
    result_min: float
    if user_target is not None:
        if user_target.pressure_max is not None:
            result_max = 1. * user_target.pressure_max
        else:
            result_max = default_max
        if user_target.pressure_min is not None:
            result_min = 1. * user_target.pressure_min
        else:
            result_min = default_min
        return result_max, result_min
    else:
        return default_max, default_min


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

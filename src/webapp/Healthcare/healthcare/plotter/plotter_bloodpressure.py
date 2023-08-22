import base64
import binascii
import logging
from typing import Dict, List, Optional, Tuple

from pandas.core.frame import DataFrame

from healthcare.plotter import plot_bloodpress_conf
from healthcare.plotter.common.statistics import BloodPressStatistics
from healthcare.plotter.common.todaydata import TodayBloodPress
from healthcare.plotter.dao import (
    COL_MORNING_MAX, COL_MORNING_MIN, COL_EVENING_MAX, COL_EVENING_MIN
)
from healthcare.plotter.plotparameter import BloodPressUserTarget
import healthcare.util.date_util as du
import healthcare.util.file_util as fu
import healthcare.util.numeric_util as nu

"""
血圧測定データプロットに関する共通関数
"""

# 血圧測定プロット可変設定
# 血圧値最小値
DEF_BLOOD_PRESS_MAX: float = 180.
DEF_BLOOD_PRESS_MIN: float = 40.
DEF_PULSE_MAX: float = 110.
DEF_PULSE_MIN: float = 40.
# 家庭血圧: 75歳未満 (120 - 75)
# 最高血圧の正常血圧: 115未満
# 最低血圧の正常血圧: 75未満
# (*) 最高血圧の正常高値血圧: 115〜124
# (*) 最低血圧の正常高値血圧: 75未満
DEF_TARGET_PRESS_MAX: float = 125.
DEF_TARGET_PRESS_MIN: float = 75.
# 設定値の上書き

_y_axis_bp: Dict = plot_bloodpress_conf["y_axis"]["blood_press"]
_y_axis_pr: Dict = plot_bloodpress_conf["y_axis"]["pulse_rate"]
_bp_value: Dict = plot_bloodpress_conf["value"]["blood_press"]
BLOOD_PRESS_MAX: float = _y_axis_bp.get('max', DEF_BLOOD_PRESS_MAX)
BLOOD_PRESS_MIN: float = _y_axis_bp.get('min', DEF_BLOOD_PRESS_MIN)
PULSE_MAX: float = _y_axis_pr.get('max', DEF_PULSE_MAX)
PULSE_MIN: float = _y_axis_pr.get('min', DEF_PULSE_MIN)
TARGET_PRESS_MAX: float = _bp_value.get("target_max", DEF_TARGET_PRESS_MAX)
TARGET_PRESS_MIN: float = _bp_value.get("target_min", DEF_TARGET_PRESS_MIN)
# 傾斜角度
X_AXIS_ROTATION: float = plot_bloodpress_conf["x_axis"]["rotation"]

# 有効なカラム件数:
_VALID_COLUMN_CNT: int = 4

# 血圧値の許容範囲
_ALLOW_PRESS_MAX_UPPER: int = 200
_ALLOW_PRESS_MAX_LOWER: int = 70
_ALLOW_PRESS_MIN_UPPER: int = 140
_ALLOW_PRESS_MIN_LOWER: int = 20
# 脈拍のの許容範囲
_ALLOW_PULSE_MAX: int = 150
_ALLOW_PULSE_MIN: int = 20
# ユーザー目標値が未設定
USER_TARGET_NONE: int = -1


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
    if len(parts) != _VALID_COLUMN_CNT:
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
    if press_max < _ALLOW_PRESS_MAX_LOWER or press_max > _ALLOW_PRESS_MAX_UPPER:
        raise ValueError

    # AM最低血圧値
    s_val = parts[2]
    press_min: Optional[int] = nu.convert_integer(s_val)
    if press_min is None:
        raise ValueError

    if press_min < _ALLOW_PRESS_MIN_LOWER or press_min > _ALLOW_PRESS_MIN_UPPER:
        raise ValueError

    # AM脈拍
    s_val = parts[3]
    pulse_rate: Optional[int] = nu.convert_integer(s_val)
    if pulse_rate is None:
        raise ValueError

    if pulse_rate < _ALLOW_PULSE_MIN or pulse_rate > _ALLOW_PULSE_MAX:
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
    :param user_target: ユーザー目標値 ※いずれか一方が未設定(=-1)の場合も許容
    :return: ユーザー目標値 (最高血圧, 最低血圧) ※いずれか一方が未設定未設定の場合はデフォルト値
    """
    result_max: float
    result_min: float
    if user_target is not None:
        if user_target.pressure_max is not None and user_target.pressure_max != USER_TARGET_NONE:
            result_max = 1. * user_target.pressure_max
        else:
            result_max = default_max
        if user_target.pressure_min is not None and user_target.pressure_min != USER_TARGET_NONE:
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


def flattenStatistics(stat: BloodPressStatistics) -> Tuple[str, int]:
    """
    血圧測定統計情報の平均値(カンマ区切り)とレコード件数に分解したものを取得する
    :param stat: 血圧測定統計情報オブジェクト
    :return: Tuple[平均値文字列(カンマ区切り), レコード件数]
    """
    # 平均値をカンマ区切りで連結
    flatten: str = f"{stat.am_max_mean},{stat.am_min_mean},{stat.pm_max_mean},{stat.pm_min_mean}"
    return flatten, stat.record_size

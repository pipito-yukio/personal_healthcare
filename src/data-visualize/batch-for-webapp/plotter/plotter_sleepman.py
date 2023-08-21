import base64
import binascii
import logging
from typing import List, Optional, Tuple

from plotter.common.statistics import SleepManStatistics
from plotter.common.todaydata import TodaySleepMan
import util.date_util as du
import util.numeric_util as nu

"""
睡眠管理データプロットに関する共通関数
"""

# 有効なカラム件数:
VALID_COLUMN_CNT: int = 6
MAX_VISITS: int = 20


def getTodayData(encoded_today_data: str,
                 logger: logging.Logger, is_debug: bool = False) -> TodaySleepMan:
    """
    睡眠管理データの当日データを取得する
     ['測定日付(ISO8601形式), 起床時刻("%H:%M"), 夜間トイレ回数, 睡眠スコア, 睡眠時間("%H:%M"), 深い睡眠("%H:%M")']
    :param encoded_today_data: base64でエンコードした文字列
    :param logger:
    :param is_debug:
    :return: TodaySleepManオブジェクト
    :raise ValueError: TodaySleepManオブジェクトに変換できない場合
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

    # 時刻チェック
    # 起床時刻 ※必須
    s_wakeup_time: str = parts[1]
    # 睡眠時間 ※必須
    s_sleeping_time: str = parts[4]
    # 深い睡眠 ※未設定(測定不能)の場合は "00:00"
    s_deep_sleeping_time: str = parts[5]
    for s_time in [s_wakeup_time, s_sleeping_time, s_deep_sleeping_time]:
        # 時刻文字列は "時:分"
        if not du.check_str_time(s_time, has_second=False):
            raise ValueError

    # 整数値チェック
    # 夜間トイレ回数 ※一時保存で必須
    s_toilet_visits: str = parts[2]
    toilet_visits: Optional[int] = nu.convert_integer(s_toilet_visits)
    if toilet_visits is None:
        raise ValueError

    # 夜間トイレ回数チェック範囲: 0-20
    if toilet_visits < 0 or toilet_visits > MAX_VISITS:
        raise ValueError

    # 睡眠スコア ※未設定の場合があり得る
    s_sleep_score: str = parts[3]
    sleep_score: Optional[int] = nu.convert_integer(s_sleep_score)
    if sleep_score is None:
        raise ValueError

    # 睡眠スコア範囲: 0-100
    if sleep_score == -1:
        # 2023-08-17: 未設定の場合 Androidアプリが -1 を設定する
        sleep_score = None
    else:
        if sleep_score < 0 or sleep_score > 100:
            raise ValueError

    result: TodaySleepMan = TodaySleepMan(
        measurement_day=s_date, wakeup_time=s_wakeup_time,
        midnight_toilet_visits=toilet_visits, sleep_score=sleep_score,
        sleeping_time=s_sleeping_time, deep_sleeping_time=s_deep_sleeping_time
    )
    return result


def flattenStatistics(stat: SleepManStatistics) -> Tuple[str, int]:
    """
    睡眠管理統計情報の平均値(カンマ区切り)とレコード件数に分解したものを取得する
    :param stat: 睡眠管理統計情報オブジェクト
    :return: Tuple[平均値文字列(カンマ区切り), レコード件数]
    """
    # 平均値をカンマ区切りで連結
    flatten: str = f"{stat.sleeping_mean},{stat.deep_sleeping_mean}"
    return flatten, stat.record_size

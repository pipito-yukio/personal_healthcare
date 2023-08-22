from datetime import datetime, timedelta
from typing import Optional

import pandas as pd

from healthcare.plotter.common.funcs import toMinute
import healthcare.util.date_util as du

"""
睡眠管理データプロット用の関数群
"""


def calcBedTime(
        s_date: str, s_wakeup_time: str, s_sleeping_time: str) -> Optional[datetime]:
    """
    就寝時刻(前日)を計算する
    :param s_date: 測定日付文字列(ISO8601) ※必須
    :param s_wakeup_time: 起床時刻文字列 ("%H:%M") ※必須
    :param s_sleeping_time: 睡眠時間 ("%H:%M") ※任意 欠損値 None
    :return: (測定日付+起床時刻) - 睡眠時間
    """
    if pd.isnull(s_sleeping_time) is None:
        return None

    day_time: datetime = datetime.strptime(f"{s_date} {s_wakeup_time}", du.FMT_DATETIME_HM)
    val_minutes: int = toMinute(s_sleeping_time)
    return day_time - timedelta(minutes=val_minutes)


def calcBedTimeToMinute(
        s_curr_date: str, s_wakeup_time: str, s_sleeping_time: str) -> Optional[int]:
    """
    当日０時を基準とした就寝時刻(分)を計算する ※前日に就寝した場合は負の分
    :param s_curr_date: 測定日付文字列(ISO8601) ※必須
    :param s_wakeup_time: 起床時刻文字列 ("%H:%M") ※必須
    :param s_sleeping_time: 睡眠時間 ("%H:%M") ※任意 欠損値 None
    :return: 前日なら24時(1440分)をマイナスした経過時間(分), 当日なら0時からの経過時間(分)
    """
    if pd.isnull(s_sleeping_time):
        return None

    bed_time: Optional[datetime] = calcBedTime(s_curr_date, s_wakeup_time, s_sleeping_time)
    if bed_time is None:
        return None

    # 時刻部分のみ取り出す
    f_time: str = bed_time.strftime("%H:%M")
    bed_time_minutes: int = toMinute(f_time)
    # 測定日
    curr_time: datetime = datetime.strptime(s_curr_date, du.FMT_ISO8601)
    result: int
    if bed_time < curr_time:
        # 前日に就寝 (負の値) ※ 1440 = 24H*60
        result = bed_time_minutes - 1440
    else:
        # 当日に就寝 (正の値) 00:00 からの経過(分)
        result = bed_time_minutes
    return result


def minuteToFormatTime(val_minutes: int, trim_hour_zero: bool = False) -> str:
    """
    分を時刻文字列("%H:%M")に変換する
    :param val_minutes: 分
    :param trim_hour_zero: 時の先頭のゼロをトリムする
    :return: 時刻文字列("%H:%M")
    """
    if not trim_hour_zero:
        return f"{val_minutes // 60:#02d}:{val_minutes % 60:#02d}"
    else:
        return f"{val_minutes // 60:#d}:{val_minutes % 60:#02d}"

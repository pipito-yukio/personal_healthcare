from datetime import datetime, timedelta
from typing import Optional

import pandas as pd

from plotter.common.funcs import toMinute
import util.date_util as du

"""
睡眠管理データプロット用の関数群
"""


def calcBedTime(s_date: datetime, s_wakeup_time: str, s_sleeping_time: str
                ) -> Optional[datetime]:
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


def minuteToFormatTime(val_minutes: int) -> str:
    """
    分を時刻文字列("%H:%M")に変換する
    :param val_minutes: 分
    :return: 時刻文字列("%H:%M")
    """
    return f"{val_minutes // 60:#02d}:{val_minutes % 60:#02d}"

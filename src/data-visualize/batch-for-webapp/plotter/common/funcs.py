from typing import List, Optional

import pandas as pd

"""
健康管理アプリ共通関数
for pandas
"""


def toMinute(s_time: str) -> Optional[int]:
    """
    時刻文字列("時:分")を分に変換する ※pandasのみで使用
    :param s_time: 時刻文字列("時:分") ※欠損値有り(None)
    :return: 分(整数), NoneならNone
    """
    if s_time is None or pd.isna(s_time):  # pandasでは nan のチェックが必要
        return None

    times: List[str] = s_time.split(":")
    return int(times[0]) * 60 + int(times[1])

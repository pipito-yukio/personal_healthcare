import enum
from datetime import date, datetime, timedelta
from typing import List

"""
日付処理ユーティリティ
"""

# デフォルトの日付フォーマット (ISO8601形式)
FMT_ISO8601 = "%Y-%m-%d"
FMT_DATETIME: str = '%Y-%m-%d %H:%M:%S'
FMT_DATETIME_HM: str = '%Y-%m-%d %H:%M'

# 日本語の曜日
JP_WEEK_DAY_NAMES: List[str] = ["月", "火", "水", "木", "金", "土", "日"]


class DateCompEnum(enum.Enum):
    """ 日付比較結果 """
    EQ = 0
    GT = 1
    LT = -1


def add_day_string(s_date: str, add_days=1, fmt_date=FMT_ISO8601) -> str:
    """
    指定された日付文字列に指定された日数を加減算する
    :param s_date: 日付文字列
    :param add_days: 加算(n)または減算(-n)する日数
    :param fmt_date: デフォルト ISO8601形式
    :return: 加減算した日付文字列
    """
    dt = datetime.strptime(s_date, fmt_date)
    dt += timedelta(days=add_days)
    s_next = dt.strftime(fmt_date)
    return s_next


def check_str_date(s_date, fmt_date=FMT_ISO8601) -> bool:
    """
    日付文字列チェック
    :param s_date: 日付文字列
    :param fmt_date: デフォルト ISO8601形式
    :return: 日付文字列ならTrue, それ以外はFalse
    """
    try:
        datetime.strptime(s_date, fmt_date)
        return True
    except ValueError:
        return False


def dateCompare(s_date1: str, s_date2: str) -> DateCompEnum:
    """
    s_date1(小さい想定の日付文字列) と s_date2(大きい想定の日付文字列)を比較する
    :param s_date1: 小さい想定の日付文字列
    :param s_date2: 大きい想定の日付文字列
    :return: s_date2が大きい場合 DateCompare.GT, 小さい場合 DateCompare.LT, 等しい場合 DateCompare.EQ
    """
    d1 = datetime.strptime(s_date1, FMT_ISO8601)
    d2 = datetime.strptime(s_date2, FMT_ISO8601)
    if d1 == d2:
        return DateCompEnum.EQ
    elif d1 < d2:
        return DateCompEnum.GT
    else:
        return DateCompEnum.LT


def diffInDays(s_date1: str, s_date2: str) -> int:
    """
    2つの日付の差分(日数)を求める
    :param s_date1: 小さい想定の日付文字列
    :param s_date2: 大きい想定の日付文字列
    :return: 差分(日数)
    """
    d1 = datetime.strptime(s_date1, FMT_ISO8601)
    d2 = datetime.strptime(s_date2, FMT_ISO8601)
    # Differential value is datetime.timedelta
    diff_days: timedelta = d2 - d1
    return diff_days.days


def calcEndOfMonth(s_year_month: str) -> int:
    """
    年月(文字列)の末日を計算する
    :param s_year_month: 年月(文字列, "-"区切り)
    :return: 末日
    """
    parts = s_year_month.split("-")
    val_year, val_month = int(parts[0]), int(parts[1])
    if val_month == 12:
        val_year += 1
        val_month = 1
    else:
        val_month += 1
    # 月末日の翌月の1日
    next_year_month = date(val_year, val_month, 1)
    # 月末日の計算: 次の月-1日
    result = next_year_month - timedelta(days=1)
    return result.day


def makeDateTextWithJpWeekday(iso_date: str, has_month: bool = False) -> str:
    """
    X軸の日付ラベル文字列を生成する\n
    [形式] '日 (曜日)' | '月/日 (曜日)' ※日は前ゼロ
    :param iso_date: ISO8601 日付文字列
    :param has_month: 月を表示
    :return: 日付ラベル文字列
    """
    val_date: datetime = datetime.strptime(iso_date, FMT_ISO8601)
    weekday_name = JP_WEEK_DAY_NAMES[val_date.weekday()]
    if not has_month:
        return f"{val_date.day} ({weekday_name})"
    else:
        return f"{val_date.month}/{val_date.day:#02d} ({weekday_name})"

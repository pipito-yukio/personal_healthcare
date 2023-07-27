from typing import Optional

"""
数値変換関数
"""


def convert_integer(s_value: str) -> Optional[int]:
    """
    数値文字列を整数値に変換する
    :param s_value: 数値文字列
    :return: 整数の場合はその値, それ以外はNone
    """
    result: int
    try:
        result = int(s_value)
        return result
    except ValueError:
        return None


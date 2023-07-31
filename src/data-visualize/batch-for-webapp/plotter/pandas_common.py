from typing import Optional, Tuple

import pandas as pd
from pandas.core.frame import DataFrame

"""
pandasを利用する共通関数
"""


def rebuildIndex(
        df_org: DataFrame, index_name: str,
        s_start_date: str, s_end_date: str) -> Tuple[bool, Optional[DataFrame]]:
    """
    DataFrameのインデックス再構築が必要なら再構築する
    :param df_org: オリジナルのDataFrame
    :param index_name: インデックス名 ※必須
    :param s_start_date: 検索開始日 ※必須
    :param s_end_date: 最終日 (検索終了日 | 当日) ※必須
    :return: 再構築ならTuple[True, 再構築後のDataFrame], それ以外[False, None]
    """
    df_size: int = len(df_org)
    date_range: pd.DatetimeIndex = pd.date_range(s_start_date, s_end_date)
    range_size: int = len(date_range)
    if df_size < range_size:
        # 欠損データ有りの場合はインデックスを振り直す
        result: pd.DataFrame = df_org.reindex(pd.date_range(date_range, name=index_name))
        return True, result
    else:
        return False, None


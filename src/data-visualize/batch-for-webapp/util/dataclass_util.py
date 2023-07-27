from dataclasses import dataclass, asdict, is_dataclass
from typing import Dict, List


def splitTodayData(data: dataclass):
    """
    データオブジェクトをフィールド名(キー)リストと値リストに分割する
    :param data: データオブジェクト
    :return:Tuple(フィールド名(キー)リスト, 値リスト)
    """
    if not is_dataclass(data):
        raise ValueError

    data_dict: Dict = asdict(data)
    keys: List = [k for k in data_dict.keys()]
    datas: List = [d for d in data_dict.values()]
    return keys, datas

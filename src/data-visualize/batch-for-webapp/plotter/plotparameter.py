from dataclasses import dataclass
from typing import List, Optional

"""
プロットに関するパラメタークラス定義
"""


@dataclass(frozen=True)
class PhoneImageInfo:
    """ 携帯端末の画像領域サイズ情報 """
    # 画像表示幅 (pixel)
    px_width: int
    # 画像表示高さ (pixel)
    px_height: int
    # 端末密度
    density: float


@dataclass(frozen=True)
class BloodPressUserTarget:
    """ 血圧測定プロット時の目標基準値 """
    # 最高血圧の基準値
    pressure_max: int
    # 最低血圧の基準値
    pressure_min: int


def getPhoneImageInfoFromHeader(s_info: str) -> Optional[PhoneImageInfo]:
    """
    携帯端末の画像領域サイズ情報(横幅,高さ,密度)を取得する ※画像プロットリクエストでは必須
     [形式] "x" 区切り: (例) '1280x1800x2.0'
    :param s_info: ヘッダーから取得した文字列 ※呼び出し元で長さチェック済みであること
    :return: エラーがない場合は携帯端末の画像領域サイズ情報オブジェクト
    :exception: ValueError
    """
    parts: List[str] = s_info.split("x")
    if len(parts) != 3:
        raise ValueError

    img_width: int = int(parts[0])
    img_height: int = int(parts[1])
    density: float = float(parts[2])
    return PhoneImageInfo(img_width, img_height, density)


def getBloodPressUserTargetFromParameter(s_target: str) -> Optional[BloodPressUserTarget]:
    """
    ユーザー指定の目標基準値文字列から血圧測定プロット用の目標基準値を取得する
    [形式] カンマ区切り (例) '130,80'
    :param s_target: リクエストパラメータから取得した目標基準値文字列 "最高血圧,最低血圧" 
    :return: エラーがなければ血圧測定プロット時の目標基準値オブジェクト
    :exception: ValueError
    """
    parts: List[str] = s_target.split(",")
    if len(parts) != 2:
        raise ValueError

    val_max: int = int(parts[0])
    val_min: int = int(parts[1])
    return BloodPressUserTarget(val_max, val_min)

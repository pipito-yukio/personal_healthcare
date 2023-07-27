from dataclasses import dataclass

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

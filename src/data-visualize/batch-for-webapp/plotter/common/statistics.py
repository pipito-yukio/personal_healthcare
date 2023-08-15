from dataclasses import dataclass

"""
各テーブルの統計情報データクラス
生成以外更新できないイミュータブルクラスとする
(1) 睡眠管理テータ
(2) 血圧測定テータ
"""


@dataclass(frozen=True)
class SleepManStatistics:
    # 平均睡眠時間 (分)
    sleeping_mean: int
    # 平均深い睡眠時間 (分)
    deep_sleeping_mean: int
    # レコード件数
    record_size: int


@dataclass(frozen=True)
class BloodPressStatistics:
    # 午前の平均最高血圧
    am_max_mean: int
    # 午前の平均最低血圧
    am_min_mean: int
    # 午後の平均最高血圧
    pm_max_mean: int
    # 午後の平均最低血圧
    pm_min_mean: int
    # レコード件数
    record_size: int

from dataclasses import dataclass

"""
各テーブルの当日データクラス
生成以外更新できないイミュータブルクラスとする
(1) 睡眠管理テータ
(2) 血圧測定テータ
"""


@dataclass(frozen=True)
class TodayBase:
    measurement_day: str


# 当日睡眠管理データクラス
@dataclass(frozen=True)
class TodaySleepMan(TodayBase):
    wakeup_time: str
    sleep_score: int
    sleeping_time: str
    deep_sleeping_time: str
    midnight_toilet_visits: int


# 当日血圧測定データ(AM測定データのみ)クラス
@dataclass(frozen=True)
class TodayBloodPress(TodayBase):
    morning_max: int
    morning_min: int
    morning_pulse_rate: int

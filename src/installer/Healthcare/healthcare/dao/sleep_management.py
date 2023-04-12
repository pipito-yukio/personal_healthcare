from datetime import date, time
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

from healthcare.dao import HealthcareBase

# 健康管理データベース
# 睡眠管理テーブル


class SleepManagement(HealthcareBase):
    __tablename__ = 'sleep_management'

    # データベースにテーブルは定義済みなので ForeignKeyはつけない。
    # pid: Mapped[SmallInteger] = mapped_column(ForeignKey("person.id"), name='pid',  primary_key=True)
    # クラスのPrimary keyの定義は必要
    pid: Mapped[int] = mapped_column(name='pid', primary_key=True)
    measurementDay: Mapped[date] = mapped_column(name='measurement_day', primary_key=True)
    wakeupTime: Mapped[time] = mapped_column(name='wakeup_time')
    sleepScore: Mapped[int] = mapped_column(name='sleep_score')
    sleepingTime: Mapped[time] = mapped_column(name='sleeping_time')
    deepSleepingTime: Mapped[time] = mapped_column(name='deep_sleeping_time')

    def __repr__(self):
        return f"SleepManagement(pid={self.pid!r}, " \
               f"measurementDay={self.measurementDay!r}, " \
               f"wakeupTime={self.wakeupTime!r}," \
               f"sleepScore={self.sleepScore!r}," \
               f"sleepingTime={self.sleepingTime!r}," \
               f"deepSleepingTime={self.deepSleepingTime!r})"

from datetime import date
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

from healthcare.dao import HealthcareBase

# 歩数管理テーブルテータクラス定義


class WalkingCount(HealthcareBase):
    __tablename__ = 'walking_count'

    # pid: Mapped[SmallInteger] = mapped_column(ForeignKey("person.id"), name='pid',  primary_key=True)
    pid: Mapped[int] = mapped_column(name='pid', primary_key=True)
    measurementDay: Mapped[date] = mapped_column(name='measurement_day', primary_key=True)
    counts: Mapped[int] = mapped_column(name='counts')

    def __repr__(self):
        return f"WalkingCount(pid={self.pid!r}, " \
               f"measurementDay={self.measurementDay!r}, " \
               f"counts={self.counts!r})"

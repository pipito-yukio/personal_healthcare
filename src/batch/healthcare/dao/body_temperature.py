from datetime import date
from sqlalchemy import Float
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

from dao import HealthcareBase

# 健康管理データベース
# 体温管理テーブルデータクラス


class BodyTemperature(HealthcareBase):
    __tablename__ = 'body_temperature'

    # pid: Mapped[SmallInteger] = mapped_column(ForeignKey("person.id"), name='pid',  primary_key=True)
    pid: Mapped[int] = mapped_column(name='pid', primary_key=True)
    measurementDay: Mapped[date] = mapped_column(name='measurement_day', primary_key=True)
    temperature: Mapped[float] = mapped_column(Float, name='temperature')

    def __repr__(self):
        return f"BodyTemperature(pid={self.pid!r}, " \
               f"measurementDay={self.measurementDay!r}, " \
               f"temperature={self.temperature!r})"

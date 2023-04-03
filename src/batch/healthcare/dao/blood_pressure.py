from datetime import date, time
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

from dao import HealthcareBase

# 血圧管理テーブルデータクラス


class BloodPressure(HealthcareBase):
    __tablename__ = 'blood_pressure'

    # pid: Mapped[SmallInteger] = mapped_column(ForeignKey("person.id"), name='pid',  primary_key=True)
    pid: Mapped[int] = mapped_column(name='pid', primary_key=True)
    measurementDay: Mapped[date] = mapped_column(name='measurement_day', primary_key=True)
    morningMeasurementTime: Mapped[time] = mapped_column(name='morning_measurement_time')
    morningMax: Mapped[int] = mapped_column(name='morning_max')
    morningMin: Mapped[int] = mapped_column(name='morning_min')
    morningPulseRate: Mapped[int] = mapped_column(name='morning_pulse_rate')
    eveningMeasurementTime: Mapped[time] = mapped_column(name='evening_measurement_time')
    eveningMax: Mapped[int] = mapped_column(name='evening_max')
    eveningMin: Mapped[int] = mapped_column(name='evening_min')
    eveningPulseRate: Mapped[int] = mapped_column(name='evening_pulse_rate')

    def __repr__(self):
        return f"BloodPressure(pid={self.pid!r}, " \
               f"measurementDay={self.measurementDay!r}, " \
               f"morningMeasurementTime={self.morningMeasurementTime!r}," \
               f"morningMax={self.morningMax!r}," \
               f"morningMin={self.morningMin!r}," \
               f"morningPulseRate={self.morningPulseRate!r}," \
               f"eveningMeasurementTime={self.eveningMeasurementTime!r}," \
               f"eveningMax={self.eveningMax!r}," \
               f"eveningMin={self.eveningMin!r}," \
               f"eveningPulseRate={self.eveningPulseRate!r})"


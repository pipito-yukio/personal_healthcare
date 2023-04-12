from datetime import date
from sqlalchemy import String
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

# 2nd schema:
#  ypeError: 'MetaData' object is not callable
# from dao_weather import WeatherBase

# 気象センサーデータベース
#  天気状態
from sqlalchemy.orm import declarative_base

Base = declarative_base()


class WeatherCondition(Base):
    __tablename__ = 'weather_condition'
    __table_args__ = {'schema': 'weather'}

    measurementDay: Mapped[date] = mapped_column(name='measurement_day', primary_key=True)
    condition: Mapped[str] = mapped_column(String(60))

    def __repr__(self):
        return f"WeatherCondition(measurementDay={self.measurementDay!r}, " \
               f"condition={self.condition!r})"

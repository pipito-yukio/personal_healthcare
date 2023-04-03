from datetime import date
from sqlalchemy.orm import Mapped
from sqlalchemy.orm import mapped_column

from dao import HealthcareBase

# 健康管理データベース
# 夜間頻尿要因管理テーブルデータクラス


class NocturiaFactors(HealthcareBase):
    __tablename__ = 'nocturia_factors'

    # pid: Mapped[SmallInteger] = mapped_column(ForeignKey("person.id"), name='pid',  primary_key=True)
    pid: Mapped[int] = mapped_column(name='pid', primary_key=True)
    measurementDay: Mapped[date] = mapped_column(name='measurement_day', primary_key=True)
    midnightToiletVisits: Mapped[int] = mapped_column(name='midnight_toilet_visits')
    hasCoffee: Mapped[bool] = mapped_column(name='has_coffee')
    hasTea: Mapped[bool] = mapped_column(name='has_tea')
    hasAlcohol: Mapped[bool] = mapped_column(name='has_alcohol')
    hasNutritionDrink: Mapped[bool] = mapped_column(name='has_nutrition_drink')
    hasSportsDrink: Mapped[bool] = mapped_column(name='has_sports_drink')
    hasDiuretic: Mapped[bool] = mapped_column(name='has_diuretic')
    takeMedicine: Mapped[bool] = mapped_column(name='take_medicine')
    takeBathing: Mapped[bool] = mapped_column(name='take_bathing')
    conditionMemo: Mapped[str] = mapped_column(name='condition_memo')

    def __repr__(self):
        return f"NocturiaFactors(pid={self.pid!r}, " \
               f"measurementDay={self.measurementDay!r}, " \
               f"midnightToiletVisits={self.midnightToiletVisits!r})" \
               f"hasTea={self.hasTea!r}" \
               f"hasAlcohol={self.hasAlcohol!r}" \
               f"hasNutritionDrink={self.hasNutritionDrink!r}" \
               f"hasSportsDring={self.hasSportsDring!r}" \
               f"hasDiuretic={self.hasDiuretic!r}" \
               f"takeMedicine={self.takeMedicine!r}" \
               f"takeBathing={self.takeBathing!r}" \
               f"conditionMemo={len(self.conditionMemo)!r})"


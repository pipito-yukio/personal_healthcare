import enum
import logging
from typing import Dict, List, Optional

from pandas.core.frame import DataFrame
from sqlalchemy.orm import scoped_session
from plotter.dao import getDataFrameFromQuery

"""
血圧測定データ検索DAO
"""

# 公開カラム名定義
COL_MORNING_TIME: str = 'morning_measurement_time'
COL_MORNING_MAX: str = 'morning_max'
COL_MORNING_MIN: str = 'morning_min'
COL_MORNING_PULSE: str = 'morning_pulse_rate'
COL_EVENING_TIME: str = 'evening_measurement_time'
COL_EVENING_MAX: str = 'evening_max'
COL_EVENING_MIN: str = 'evening_min'
COL_EVENING_PULSE: str = 'evening_pulse_rate'


class SelectColumnsType(enum.Enum):
    # 血圧・脈拍測定値 ※測定時刻なし
    PRESSURE_PULSE = 0
    # 血圧値のみ ※測定時刻なし
    PRESSURE_ONLY = 1
    # 全カラム取得
    FULL = 2


class BloodPressureDao:
    # 測定データ(血圧・脈拍) ※AM/PM測定時刻不要
    # デフォルトの取得カラム
    # 血圧測定の可視化では測定時刻は使わない
    _SELECT_PRESS_PULSE = ",morning_max,morning_min,morning_pulse_rate,evening_max,evening_min,evening_pulse_rate"
    # 血圧データのみ ※AM/PM測定時刻不要
    # 血圧測定の可視化では測定時刻は使わない
    _SELECT_PRESS_ONLY = ",morning_max,morning_min,evening_max,evening_min"
    _SELECT_FULL = """  ,to_char(morning_measurement_time,'HH24:MI') as morning_measurement_time
  ,morning_max
  ,morning_min
  ,morning_pulse_rate
  ,to_char(evening_measurement_time, 'HH24:MI') as evening_measurement_time
  ,evening_max
  ,evening_min
  ,evening_pulse_rate"""

    _RAW_QUERY = """
SELECT
  to_char(bp.measurement_day,'YYYY-MM-DD') as measurement_day
  {}
FROM
  bodyhealth.person p
  INNER JOIN bodyhealth.blood_pressure bp ON p.id = bp.pid
WHERE
  email=:emailAddress
  AND
  measurement_day BETWEEN :startDay AND :endDay
  ORDER BY measurement_day
"""
    # 取得カラムのタイプ別リスト
    _COLUMNS_TYPE_LIST: List[str] = [_SELECT_PRESS_PULSE, _SELECT_PRESS_ONLY, _SELECT_FULL]

    def __init__(self, email_address: str, start_day: str, end_day: str,
                 parse_dates: Optional[List[str]] = None,
                 logger: logging.Logger = None, is_debug=False):
        """
        クエリーパラメータ生成
        :param email_address:
        :param start_day: 検索開始日 (ISO8601形式)
        :param end_day: 検索開始日 (ISO8601形式)
        :param parse_dates: 日付としてパースする項目リスト, default None
        :param logger: default None
        :param is_debug: default False
        """
        self.params: Dict = {
            'emailAddress': email_address, 'startDay': start_day, 'endDay': end_day
        }
        self.parse_dates = parse_dates
        self.logger = logger
        self.is_debug = is_debug
        if logger is not None and is_debug:
            logger.debug(self.params)

    def execute(self, sess: scoped_session,
                columns_type: SelectColumnsType = SelectColumnsType.PRESSURE_PULSE) -> DataFrame:
        """
        検索結果のDataFrameを取得する
        :param sess: scoped_session
        :param columns_type: 取得カラム型 (血圧・脈拍, 血圧値のみ, AM/PM測定時刻を含む全ての測定データ)
        :return: DataFrame
        :raise Exception
        """
        select_columns: str = self._COLUMNS_TYPE_LIST[columns_type.value]
        query: str = self._RAW_QUERY.format(select_columns)
        if self.logger is not None and self.is_debug:
            self.logger.debug(query)
        result: DataFrame = getDataFrameFromQuery(
            sess,
            raw_query=query,
            query_params=self.params,
            parse_dates=self.parse_dates,
            logger=self.logger
        )
        return result

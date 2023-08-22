import logging
from typing import Dict, List, Optional

from pandas.core.frame import DataFrame
from sqlalchemy.orm import scoped_session
from healthcare.plotter.dao import getDataFrameFromQuery

"""
睡眠管理データ検索DAO
"""

# 公開カラム名定義
COL_WAKEUP: str = 'wakeup_time'
COL_SLEEP_SCORE: str = 'sleep_score'
COL_SLEEPING: str = 'sleeping_time'
COL_DEEP_SLEEPING: str = 'deep_sleeping_time'
COL_TOILET_VISITS: str = 'midnight_toilet_visits'


class SleepManDao:
    # 睡眠管理テーブルのみ対象
    _RAW_QUERY = """
SELECT
  to_char(sm.measurement_day,'YYYY-MM-DD') as measurement_day
  ,to_char(wakeup_time,'HH24:MI') as wakeup_time
  ,sleep_score
  ,to_char(sleeping_time, 'HH24:MI') as sleeping_time
  ,to_char(deep_sleeping_time, 'HH24:MI') as deep_sleeping_time
FROM
  bodyhealth.person p
  INNER JOIN bodyhealth.sleep_management sm ON p.id = sm.pid
WHERE
  email=:emailAddress
  AND
  sm.measurement_day BETWEEN :startDay AND :endDay
  ORDER BY sm.measurement_day
"""

    # 睡眠管理テーブルと頻尿要因テーブルの結合
    _RAW_QUERY_JOINED = """
SELECT
  to_char(sm.measurement_day,'YYYY-MM-DD') as measurement_day
  ,to_char(wakeup_time,'HH24:MI') as wakeup_time
  ,sleep_score
  ,to_char(sleeping_time, 'HH24:MI') as sleeping_time
  ,to_char(deep_sleeping_time, 'HH24:MI') as deep_sleeping_time
  ,midnight_toilet_visits
FROM
  bodyhealth.person p
  INNER JOIN bodyhealth.sleep_management sm ON p.id = sm.pid
  INNER JOIN bodyhealth.nocturia_factors nf ON p.id = nf.pid
WHERE
  email=:emailAddress
  AND
  sm.measurement_day BETWEEN :startDay AND :endDay
  AND
  sm.measurement_day = nf.measurement_day
  ORDER BY sm.measurement_day
"""

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
        if logger is not None and is_debug:
            logger.debug(self._RAW_QUERY)
            logger.debug(self.params)

    def execute(self, sess: scoped_session, has_toilet_visits=False) -> DataFrame:
        """
        検索結果のDataFrameを取得する
        :param sess: scoped_session
        :param has_toilet_visits: defaykt False,
            join nocturia_factors and get midnight_toilet_visits
        :return: DataFrame
        :raise Exception
        """
        result: DataFrame = getDataFrameFromQuery(
            sess,
            self._RAW_QUERY_JOINED if has_toilet_visits else self._RAW_QUERY,
            query_params=self.params,
            parse_dates=self.parse_dates,
            logger=self.logger
        )
        return result

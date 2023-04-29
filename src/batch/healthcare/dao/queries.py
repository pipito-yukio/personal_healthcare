from collections import namedtuple
from datetime import date, time
from logging import Logger
from typing import Dict, Optional

from sqlalchemy.engine import Result
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import scoped_session
from sqlalchemy.sql import text

"""
健康管理データベースと気象データデータベースからクエリを実行して辞書オブジェクトを生成するクラス

https://chartio.com/resources/tutorials/how-to-execute-raw-sql-in-sqlalchemy/
  How to Execute Raw SQL in SQLAlchemy
"""


def _datetime_to_str(src_dict: dict) -> Dict:
    """
    ソース辞書の中のdatetime.date型とdatetime.time型のデータを文字列型に変換する
    :param src_dict: ソース辞書
    :return: 変換後の新たな辞書
    """
    conv_dict = {}
    for key, val in zip(src_dict.keys(), src_dict.values()):
        if isinstance(val, date):
            # "年(4桁)-月(2桁)-日(2桁)'
            # (例)  {date} 2023-03-01 -> {str} '2023-03-01'
            conv_dict[key] = val.isoformat()
        elif isinstance(val, time):
            # "時:分" ※秒は不要: Androidアプリ側の精度が分まで
            # (例) {time} 05:55:00 -> {str} '05:55'
            conv_dict[key] = val.strftime("%H:%M")
        else:
            conv_dict[key] = val
    return conv_dict


class Selector:
    # 1.健康管理データ
    # 1-1.睡眠管理データ
    _SleepManagement = namedtuple('SleepManagement', [
        'wakeupTime','sleepScore','sleepingTime','deepSleepingTime']
    )
    # 1-2.血圧測定データ
    _BloodPressure = namedtuple('BloodPressure', [
        'morningMeasurementTime','morningMax','morningMin','morningPulseRate',
        'eveningMeasurementTime', 'eveningMax', 'eveningMin', 'eveningPulseRate']
    )
    # 1-3.頻尿要因データ
    _NocturiaFactors = namedtuple('NocturiaFactors', [
        'midnightToiletVisits','hasCoffee','hasTea','hasAlcohol','hasNutritionDrink',
        'hasSportsDrink','hasDiuretic','takeMedicine','takeBathing','conditionMemo']
    )
    # 1-4.歩数データ
    _WalkingCount = namedtuple('WalkingCount', ['counts'])
    # 1-5.体温管理データ
    _BodyTemperature = namedtuple('_BodyTemperature', ['measurementTime', 'temperature'])

    # 2.気象データペース.天候データ
    _WeatherCondition = namedtuple('WeatherCondition', ['condition'])

    _QRY_GET_HEALTHCARE: str = """
SELECT
  sm.measurement_day
  ,wakeup_time --必ず入力する
  ,sleep_score --任意
  ,sleeping_time --任意
  ,deep_sleeping_time --任意
  ,morning_measurement_time --任意
  ,morning_max --任意
  ,morning_min --任意
  ,morning_pulse_rate --任意
  ,evening_measurement_time--任意
  ,evening_max --任意
  ,evening_min --任意
  ,evening_pulse_rate --任意
  ,midnight_toilet_visits --必ず入力する
  ,has_coffee
  ,has_tea
  ,has_alcohol
  ,has_nutrition_drink
  ,has_sports_drink
  ,has_diuretic
  ,take_medicine
  ,take_bathing
  ,condition_memo --任意
  ,counts --必ず入力する
  ,measurement_time --BLEデバイス取得(任意)
  ,temperature -- BLEデバイス取得(任意)
FROM
  bodyhealth.person p
  INNER JOIN bodyhealth.sleep_management sm ON p.id = sm.pid
  INNER JOIN bodyhealth.blood_pressure bp ON p.id = bp.pid
  INNER JOIN bodyhealth.nocturia_factors nf ON p.id = nf.pid
  INNER JOIN bodyhealth.walking_count wc ON p.id = wc.pid
  INNER JOIN bodyhealth.body_temperature bt ON p.id = bt.pid
WHERE
  email=:emailAddress
  AND
  sm.measurement_day=:measurementDay
  AND  
  bp.measurement_day=:measurementDay
  AND 
  nf.measurement_day=:measurementDay
  AND
  wc.measurement_day=:measurementDay
  AND
  bt.measurement_day=:measurementDay
"""

    _QRY_GET_WEATHER: str = """
SELECT condition FROM weather.weather_condition WHERE measurement_day=:measurementDay
"""

    def __init__(self, cls_sess_healthcare, cls_sess_sensors, logger: Logger=None):
        # 健康管理DB用セッションオブジェクト生成
        self.sess_healthcare: scoped_session = cls_sess_healthcare()
        # 気象センサーDB用セッションオブジェクト生成
        self.sess_sensors: scoped_session = cls_sess_sensors()
        self.logger = logger

    def get_healthcare_asdict(self, email: str, measurement: str) -> Optional[dict]:
        """
        健康管理DBから指定された主キー項目(メールアドレス,測定日付)のデータの辞書オブジェクトを取得する
        :param email: メールアドレス
        :param measurement: 測定日付
        :return: 健康管理データの辞書オブジェクト, 存在しない場合はNone
        """
        # パラメータ辞書生成: 主キー
        # https://docs.sqlalchemy.org/en/20/orm/session_transaction.html
        # will automatically begin again
        # result = session.execute("< some select statement >")
        # session.add_all([more_objects, ...])
        # session.commit()
        params = {"emailAddress": email, "measurementDay": measurement}
        row = None
        try:
            rs: Result = self.sess_healthcare.execute(text(self._QRY_GET_HEALTHCARE), params)
            if rs:
                row = rs.fetchone()
            self.sess_healthcare.commit()
        except SQLAlchemyError as err:
            self.sess_healthcare.rollback()
            if self.logger:
                self.logger.warning(err.args)
            return None
        finally:
            self.sess_healthcare.close()

        if row is None:
            return None

        # 取得したTupleデータを位置引数で引き渡す
        # 先頭の測定日付はスキップする
        # 睡眠管理データ: 4項目
        sleepman = self._SleepManagement(*row[1:5])
        # 血圧データ: 8項目
        bloodpress = self._BloodPressure(*row[5:13])
        # 頻尿要因データ: 10項目
        factors = self._NocturiaFactors(*row[13:23])
        # 歩数データ: 1項目
        walkingcnt = self._WalkingCount(*row[23:24])
        # 体温データ: 2項目
        bodytemper = self._BodyTemperature(*row[24:])
        # 時刻データが含まれるデータ変換
        sleepman_dict: Dict = _datetime_to_str(sleepman._asdict())
        bloodpress_dict: Dict = _datetime_to_str(bloodpress._asdict())
        bodytemper_dict: Dict = _datetime_to_str(bodytemper._asdict())
        # それ以外は namedtupleからDictオブジェクトに変換する
        factors_dict: Dict = factors._asdict()
        walkingcnt_dict: Dict = walkingcnt._asdict()
        # 各辞書オブジェクトを健康管理データコンテナー用辞書に格納する
        container_dict: Dict = {}
        container_dict["sleepManagement"] = sleepman_dict
        container_dict["bloodPressure"] = bloodpress_dict
        container_dict["nocturiaFactors"] = factors_dict
        container_dict["walkingCount"] = walkingcnt_dict
        container_dict["bodyTemperature"] = bodytemper_dict
        # コンテナーオブジェクトを健康管理データ用辞書に格納する
        return {"healthcareData": container_dict}

    def get_weather_asdict(self, measurement: str) -> Optional[dict]:
        """
        指定された日付(主キー)の天候を取得し辞書オブジェクトとして返却する
        :param measurement_day: 日付
        :return: 天候データの辞書オブジェクト, 存在しない場合はNone
        """
        params = {"measurementDay": measurement}
        row = None
        try:
            rs: Result = self.sess_sensors.execute(text(self._QRY_GET_WEATHER), params)
            if rs:
                row = rs.fetchone()
            self.sess_sensors.commit()
        except SQLAlchemyError as err:
            self.sess_sensors.rollback()
            if self.logger:
                self.logger.warning(err.args)
            return None
        finally:
            self.sess_sensors.close()

        if row is None:
            return None

        weather_condition = self._WeatherCondition(*row)
        container_dict = {"weatherCondition": weather_condition._asdict()}
        return container_dict

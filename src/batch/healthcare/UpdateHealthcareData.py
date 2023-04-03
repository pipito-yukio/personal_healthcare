import argparse
import json
import logging
import os
import socket
from typing import Optional, Dict

import sqlalchemy
from sqlalchemy.engine.url import URL
from sqlalchemy import create_engine, Select, select
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.orm.exc import NoResultFound

from dao.person import Person
from dao.sleep_management import SleepManagement
from dao.blood_pressure import BloodPressure
from dao.nocturia_factors import NocturiaFactors
from dao.walking_count import WalkingCount
from dao.body_temperature import BodyTemperature
from dao.weather_condition import WeatherCondition

"""
健康管理データベースまたは気象センサーデータベース(天候状態)の更新処理
"""

# 健康管理デーベース接続情報: [DB] healthcare_db [PORT] 5433
DB_HEALTHCARE_CONF: str = os.path.join("conf", "db_healthcare.json")
# 気象センサーデータベース: [DB] sensors_pgdb [PORT] 5432
DB_SENSORSE_CONF: str = os.path.join("conf", "db_sensors.json")
# ログフォーマット
LOG_FMT = '%(asctime)s %(filename)s %(funcName)s %(levelname)s %(message)s'
# デバックログ有効
app_logger_debug: bool = True


def get_conn_dict(filePath: str) -> dict:
    with open(filePath, 'r') as fp:
        db_conf: json = json.load(fp)
        hostname = socket.gethostname()
        db_conf["host"] = db_conf["host"].format(hostname=hostname)
    return db_conf


def load_json(filePath: str) -> dict:
    with open(filePath, 'r') as fp:
        json_text = json.load(fp)
    return json_text


def _has_dict_in_data(dict_key: str, data:Dict) -> Optional[Dict]:
    try:
        result: Dict = data[dict_key]
        return result
    except KeyError as err:
        # データ無し
        return None


def _get_personid(session: Session, email_address: str) -> Optional[int]:
    """
    メールアドレスに対応するPersion.idを取得する
    :email_address: メールアドレス
    """
    try:
        with session.begin():
            stmt: Select = select(Person).where(Person.email == email_address)
            person: Person = session.scalars(stmt).one()
        return person.id
    except NoResultFound as notFound:
        print(f"NoResultFound: {notFound}")
        return None


# 健康管理データ更新
def _update_healthdata(sess: Session, person_id: int, measurement_day: str, data: Dict) -> None:
    # 健康管理データコンテナ: 必須
    healthcare_data: Optional[Dict] = _has_dict_in_data("healthcareData", data)
    if healthcare_data is None:
        app_logger.error("Required healthcareData!")
        exit(1)

    # 更新用データは更新されたテーブルのデータのみが存在する
    update_table_count: int = 0
    # (1) 睡眠管理
    sleep_man: Optional[Dict] = _has_dict_in_data("sleepManagement", healthcare_data)
    if app_logger_debug:
        app_logger.debug(f"sleepManagement: {sleep_man}")
    if sleep_man is not None:
        update_table_count += 1

    # (2) 血圧測定
    blood_press: Optional[Dict] = _has_dict_in_data("bloodPressure", healthcare_data)
    if app_logger_debug:
        app_logger.debug(f"bloodPressure: {blood_press}")
    if blood_press is not None:
        update_table_count += 1

    # (3) 夜中トイレ回数要因
    nocturia_factors: Optional[Dict] = _has_dict_in_data("nocturiaFactors", healthcare_data)
    if app_logger_debug:
        app_logger.debug(f"nocturiaFactors: {nocturia_factors}")
    if nocturia_factors is not None:
        update_table_count += 1

    # (4) 歩数
    walking_count: Optional[Dict] = _has_dict_in_data("walkingCount", healthcare_data)
    if app_logger_debug:
        app_logger.debug(f"walkingCount: {walking_count}")
    if walking_count is not None:
        update_table_count += 1

    # (5) 体温データ ※現状テータを運用していないが主キーのみ追加
    body_temper: Optional[Dict] = _has_dict_in_data("bodyTemperature", healthcare_data)
    if app_logger_debug:
        app_logger.debug(f"bodyTemperature: {body_temper}")
    if body_temper is not None:
        update_table_count += 1

    if update_table_count == 0:
        app_logger.info("Update data is None!")
        return

    try:
        sess.begin()
        if sleep_man is not None:
            stmt = (
                sqlalchemy.update(SleepManagement).
                where(SleepManagement.pid==person_id, SleepManagement.measurementDay==measurement_day).
                values(**sleep_man)
            )
            sess.execute(stmt)
        if blood_press is not None:
            stmt = (
                sqlalchemy.update(BloodPressure).
                where(BloodPressure.pid==person_id, BloodPressure.measurementDay==measurement_day).
                values(**blood_press)
            )
            sess.execute(stmt)
        if nocturia_factors is not None:
            stmt = (
                sqlalchemy.update(NocturiaFactors).
                where(NocturiaFactors.pid==person_id, NocturiaFactors.measurementDay==measurement_day).
                values(**nocturia_factors)
            )
            sess.execute(stmt)
        if walking_count is not None:
            stmt = (
                sqlalchemy.update(WalkingCount).
                where(WalkingCount.pid==person_id, WalkingCount.measurementDay==measurement_day).
                values(**walking_count)
            )
            sess.execute(stmt)
        if body_temper is not None:
            stmt = (
                sqlalchemy.update(BodyTemperature).
                where(BodyTemperature.pid==person_id, BodyTemperature.measurementDay==measurement_day).
                values(**body_temper)
            )
            sess.execute(stmt)
        sess.commit()
        if app_logger_debug:
            app_logger.debug(f"Updated[HealthcareData]: Person.id: {person_id}, MeasuremtDay: {measurement_day}")
    except sqlalchemy.exc.SQLAlchemyError as err:
        sess.rollback()
        app_logger.warning(err.args)


def _update_weather(sess: Session, measurement_day: str, data: Dict) -> None:
    """
    天候状態の更新処理
    :sess Session
    :param measurement_day: 測定日
    :data 更新用データ (任意)
    """
    try:
        # 天候データコンテナは必ずある
        weather_data: Dict = data["weatherData"]
        # 天候状態
        weather_condition: Dict = weather_data["weatherCondition"]
        if app_logger_debug:
            app_logger.debug(f"weather_condition: {weather_condition}")
    except KeyError as err:
        app_logger.warning(err)
        return

    if weather_condition is None:
        # 更新データ無し
        return

    # 気象センサDB用セッションオブジェクト取得
    try:
        sess.begin()
        stmt = (
            sqlalchemy.update(WeatherCondition).
            where(WeatherCondition.measurementDay == measurement_day).
            values(**weather_condition)
        )
        sess.execute(stmt)
        sess.commit()
        if app_logger_debug:
            app_logger.debug(f"Updated[WeatherData]: MeasuremtDay: {measurement_day}")
    except sqlalchemy.exc.SQLAlchemyError as err:
        app_logger.warning(err.args)
        sess.rollback()


if __name__ == '__main__':
    logging.basicConfig(format=LOG_FMT, level=logging.DEBUG)
    app_logger = logging.getLogger(__name__)

    parser: argparse.ArgumentParser = argparse.ArgumentParser()
    # JSONファイルパス: 必須
    # (例) ~/Documents/Healthcare/json/healthcare_data_20230213.json"
    parser.add_argument("--json-path", type=str, required=True,
                        help="Healthcare JSON file path.")
    args: argparse.Namespace = parser.parse_args()
    # JSONファイルロード
    healthcare_data: Dict = load_json(os.path.expanduser(args.json_path))
    if app_logger_debug:
        app_logger.debug(healthcare_data)

    # 健康管理データベス接続情報
    url_dict: dict = get_conn_dict(DB_HEALTHCARE_CONF)
    conn_url: URL = URL.create(**url_dict)
    engine_healthcare: sqlalchemy.Engine = create_engine(conn_url, echo=True)
    Session_healthcare = sessionmaker(bind=engine_healthcare)
    # 気象センサーデータベース接続状ワウ
    url_dict: dict = get_conn_dict(DB_SENSORSE_CONF)
    conn_url: URL = URL.create(**url_dict)
    engine_sensors: sqlalchemy.Engine = create_engine(conn_url, echo=True)
    Session_sensors = sessionmaker(bind=engine_sensors)

    # 健康管理テーブルの主キー
    emailAddress: str = healthcare_data["emailAddress"]
    person_id: int = _get_personid(Session_healthcare(), emailAddress)
    if person_id is None:
        app_logger.warning("Person not found.")
        exit(0)

    # 測定日付: 健康管理テーブルと天候状態テーブルの主キー
    measurementDay: str = healthcare_data["measurementDay"]

    # 健康管理データ更新
    _update_healthdata(Session_healthcare(), person_id, measurementDay, healthcare_data)
    # 天候状態
    _update_weather(Session_sensors(), measurementDay, healthcare_data)


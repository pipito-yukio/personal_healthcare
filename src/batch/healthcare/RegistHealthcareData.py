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
import sqlalchemy.exc

from dao.person import Person
from dao.sleep_management import SleepManagement
from dao.blood_pressure import BloodPressure
from dao.nocturia_factors import NocturiaFactors
from dao.walking_count import WalkingCount
from dao.body_temperature import BodyTemperature
from dao.weather_condition import WeatherCondition

"""
健康管理データベースまたは気象センサーデータベース(天候状態)の登録処理
"""

# 健康管理デーベース接続情報: [DB] healthcare_db [PORT] 5432
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
        app_logger.warning(f"NoResultFound: {notFound}")
        return None
    except Exception as excption:
        app_logger.error(f"Exception: {excption}")
        raise excption


# 健康管理データ更新
def _insert_healthdata(sess: Session, person_id: int, measurement_day: str, data: Dict) -> None:
    # JSONキーチェック
    sleep_man = {}
    blood_press = {}
    nocturia_factors = {}
    walking_count = {}
    body_temper = {}
    try:
        # 健康管理データコンテナ
        healthcare_data: Dict = data["healthcareData"]
        # (1) 睡眠管理
        sleep_man: Dict = healthcare_data["sleepManagement"]
        # (2) 血圧測定
        blood_press: Dict = healthcare_data["bloodPressure"]
        # (3) 夜中トイレ回数要因
        nocturia_factors: Dict = healthcare_data["nocturiaFactors"]
        # (4) 歩数
        walking_count: Dict = healthcare_data["walkingCount"]
        # (5) 体温データ
        body_temper: Dict = healthcare_data["bodyTemperature"]
    except KeyError as err:
        app_logger.warning(err)
        exit(1)

    # 主キー値を設定
    sleep_man["pid"] = person_id
    sleep_man["measurementDay"] = measurement_day
    blood_press["pid"] = person_id
    blood_press["measurementDay"] = measurement_day
    nocturia_factors["pid"] = person_id
    nocturia_factors["measurementDay"] = measurement_day
    walking_count["pid"] = person_id
    walking_count["measurementDay"] = measurement_day
    body_temper["pid"] = person_id
    body_temper["measurementDay"] = measurement_day
    # 登録用の各クラスにデータを設定
    sleepMan: SleepManagement = SleepManagement(**sleep_man)
    if app_logger_debug:
        app_logger.debug(sleepMan)
    bloodPressure: BloodPressure = BloodPressure(**blood_press)
    if app_logger_debug:
        app_logger.debug(bloodPressure)
    factors: NocturiaFactors = NocturiaFactors(**nocturia_factors)
    if app_logger_debug:
        app_logger.debug(factors)
    walking: WalkingCount = WalkingCount(**walking_count)
    if app_logger_debug:
        app_logger.debug(walking)
    bodyTemper: BodyTemperature = BodyTemperature(**body_temper)
    if app_logger_debug:
        app_logger.debug(bodyTemper)

    # 健康管理DB用セッションオブジェクト取得
    try:
        sess.begin()
        sess.add_all(
            [sleepMan, bloodPressure, factors, walking, bodyTemper]
        )
        sess.commit()
    except sqlalchemy.exc.IntegrityError as err:
        app_logger.warning(f"IntegrityError: {err.args}")
        sess.rollback()
        raise err
    except sqlalchemy.exc.SQLAlchemyError as err:
        app_logger.warning(err.args)
        sess.rollback()
        raise err
    finally:
        sess.close()


def _insert_weather(sess: Session,measurement_day: str, data: Dict) -> None:
    """
    天候状態の登録処理
    :param measurement_day: 測定日
    :data 登録用データ (必須)
    """
    try:
        # 天候データコンテナは必ずある
        weather_data: Dict = data["weatherData"]
        # 天候状態は必須項目
        weather_condition: Dict = weather_data["weatherCondition"]
        if app_logger_debug:
            app_logger.debug(f"weather_condition: {weather_condition}")
    except KeyError as err:
        app_logger.warning(err)
        return

    # 主キー設定
    weather_condition["measurementDay"] = measurement_day
    weather: WeatherCondition = WeatherCondition(**weather_condition)
    try:
        sess.begin()
        sess.add(weather)
        sess.commit()
    except sqlalchemy.exc.IntegrityError as err:
        app_logger.warning(f"IntegrityError: {err.args}")
        sess.rollback()
    except sqlalchemy.exc.SQLAlchemyError as err:
        app_logger.warning(err.args)
        sess.rollback()
    finally:
        sess.close()


if __name__ == '__main__':
    logging.basicConfig(format=LOG_FMT, level=logging.DEBUG)
    app_logger = logging.getLogger(__name__)

    parser: argparse.ArgumentParser = argparse.ArgumentParser()
    # JSONファイルパス: 必須
    # (例) ~/Documents/Healthcare/json/healthcare_data_20230313.json"
    parser.add_argument("--json-path", type=str, required=True,
                        help="Healthcare JSON file path.")
    args: argparse.Namespace = parser.parse_args()
    # JSONファイルロード
    healthcare_data: Dict = load_json(os.path.expanduser(args.json_path))
    if app_logger_debug:
        app_logger.debug(healthcare_data)

    # 健康管理データベース接続情報
    url_dict: dict = get_conn_dict(DB_HEALTHCARE_CONF)
    conn_url: URL = URL.create(**url_dict)
    engine_healthcare: sqlalchemy.Engine = create_engine(conn_url, echo=False)
    Session_healthcare = sessionmaker(bind=engine_healthcare)
    # 気象センサーデータベース接続情報
    url_dict: dict = get_conn_dict(DB_SENSORSE_CONF)
    conn_url: URL = URL.create(**url_dict)
    engine_sensors: sqlalchemy.Engine = create_engine(conn_url, echo=False)
    Session_sensors = sessionmaker(bind=engine_sensors)

    # メールアドレス取得
    emailAddress: str = healthcare_data["emailAddress"]
    # メールアドレスに対応する個人ID取得: 健康管理テーブルの主キー
    person_id: int = _get_personid(Session_healthcare(), emailAddress)
    if person_id is None:
        app_logger.warning("Person not found.")
        exit(0)

    # 測定日付: 健康管理テーブルと天候状態テーブルの主キー
    measurementDay: str = healthcare_data["measurementDay"]

    # 健康管理データベースの全テーブル一括登録
    _insert_healthdata(Session_healthcare(), person_id, measurementDay, healthcare_data)
    # 天候状態テーブル登録
    _insert_weather(Session_sensors(), measurementDay, healthcare_data)


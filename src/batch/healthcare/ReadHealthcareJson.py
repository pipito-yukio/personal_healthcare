import json
import os
import socket

import sqlalchemy
from sqlalchemy.engine.url import URL
from sqlalchemy import create_engine, select, Select
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.orm.exc import NoResultFound

from dao.person import Person
from dao.sleep_management import SleepManagement
from dao.blood_pressure import BloodPressure
from dao.nocturia_factors import NocturiaFactors
from dao.walking_count import WalkingCount
from dao.weather_condition import WeatherCondition

DB_HEALTHCARE_CONF: str = os.path.join("conf", "db_healthcare.json")
DB_SENSORS_CONF: str = os.path.join("conf", "db_sensors.json")
JSON_DATA = os.path.join("json_datas", "healthcare_data_latest.json")


def load_json(filePath: str) -> dict:
    with open(filePath, 'r') as fp:
        json_text = json.load(fp)
    return json_text


def get_conn_dict(filePath: str) -> dict:
    with open(filePath, 'r') as fp:
        db_conf: json = json.load(fp)
        hostname = socket.gethostname()
        # host in /etc/hosts
        db_conf["host"] = db_conf["host"].format(hostname=hostname)
    return db_conf


if __name__ == '__main__':
    # 健康管理データベース: postgresql[5433]
    conn_dict: dict = get_conn_dict(DB_HEALTHCARE_CONF)
    conn_url: URL = URL.create(**conn_dict)
    engine_healthcare: sqlalchemy.Engine = create_engine(conn_url, echo=True)

    # Perssonテーブル
    email: str = "yoshida@webriverside.com"
    try:
        # https://docs.sqlalchemy.org/en/20/orm/session_basics.html#
        #   opening-and-closing-a-session
        session: Session = Session(engine_healthcare)
        with session.begin():
            stmt: Select = select(Person).where(Person.email == email)
            person: Person = session.scalars(stmt).one()
    except NoResultFound as notFound:
        print(f"NoResultFound: {notFound}")
        exit(1)

    # データベース登録用JSON読み込み ※Android健康アプリからのリクエストで出力される
    json_data: dict = load_json(JSON_DATA)
    # 登録日
    measurement_day: str = json_data.get("measurementDay")
    # 1.健康管理データベース
    healthcare_data: dict = json_data["healthcareData"]
    # 1-1.睡眠管理
    sleep_man: dict = healthcare_data["sleepManagement"]
    sleep_man["pid"] = person.id
    sleep_man["measurementDay"] = measurement_day
    # 1-2.血圧測定
    blood_press: dict = healthcare_data["bloodPressure"]
    blood_press["pid"] = person.id
    blood_press["measurementDay"] = measurement_day
    # 1-3.夜中トイレ回数要因
    nocturia_factors = healthcare_data["nocturiaFactors"]
    nocturia_factors["pid"] = person.id
    nocturia_factors["measurementDay"] = measurement_day
    # 1-4.歩数
    walking_count = healthcare_data["walkingCount"]
    walking_count["pid"] = person.id
    walking_count["measurementDay"] = measurement_day

    sleepMan: SleepManagement = SleepManagement(**sleep_man)
    bloodPressure: BloodPressure = BloodPressure(**blood_press)
    factors: NocturiaFactors = NocturiaFactors(**nocturia_factors)
    walking: WalkingCount = WalkingCount(**walking_count)

    Session = sessionmaker(bind=engine_healthcare)
    try:
        with Session.begin() as session:
            session.add_all(
                [sleepMan, bloodPressure, factors, walking]
            )
    except sqlalchemy.exc.IntegrityError as err:
        print(f"IntegrityError: {err.args}")
        exit(1)
    except sqlalchemy.exc.SQLAlchemyError as err:
        print(err.args)
        exit(1)

    # 2.気象センサデータベース
    # 気象センサーデータベース: postgresql[5432]
    conn_sensors: dict = get_conn_dict(DB_SENSORS_CONF)
    conn_url: URL = URL.create(**conn_sensors)
    engine_sensors: sqlalchemy.Engine = create_engine(conn_url, echo=True)
    Session = sessionmaker(bind=engine_sensors)
    weather_condition: dict = json_data.get("weatherCondition")
    weather_condition["measurementDay"] = measurement_day
    weather: WeatherCondition = WeatherCondition(**weather_condition)
    try:
        with Session.begin() as session:
            session.add(weather)
    except sqlalchemy.exc.IntegrityError as err:
        print(f"IntegrityError: {err.args}")
    except sqlalchemy.exc.SQLAlchemyError as err:
        print(err.args)

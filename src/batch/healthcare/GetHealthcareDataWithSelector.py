import logging
from typing import Dict
import json
import os
import socket

import sqlalchemy
from sqlalchemy.engine.url import URL
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, scoped_session

from dao.queries import Selector

DB_HEALTHCARE_CONF: str = os.path.join("conf", "db_healthcare.json")
DB_SENSORS_CONF: str = os.path.join("conf", "db_sensors.json")
SAVE_JSON = os.path.join("json_datas", 'out_healthcare_data.json')
LOG_FMT = '%(asctime)s %(filename)s %(funcName)s %(levelname)s %(message)s'
# デバックログ有効
app_logger_debug: bool = True


def save_text(filePath: str, text) -> None:
    with open(filePath, 'w') as fp:
        fp.write(text)


def get_conn_dict(filePath: str) -> dict:
    with open(filePath, 'r') as fp:
        db_conf: json = json.load(fp)
        hostname = socket.gethostname()
        # host in /etc/hosts
        db_conf["host"] = db_conf["host"].format(hostname=hostname)
    return db_conf


if __name__ == '__main__':
    logging.basicConfig(format=LOG_FMT, level=logging.DEBUG)
    app_logger = logging.getLogger(__name__)

    # 健康管理データベース: postgresql[5433]
    conn_dict: dict = get_conn_dict(DB_HEALTHCARE_CONF)
    conn_url: URL = URL.create(**conn_dict)
    engine_healthcare: sqlalchemy.Engine = create_engine(conn_url, echo=False)
    Cls_sess_healthcare: scoped_session = scoped_session(
        sessionmaker(bind=engine_healthcare)
    )
    app_logger.info(f"Cls_sess_healthcare: {Cls_sess_healthcare}")

    # 2.気象センサデータベース
    # 気象センサーデータベース: postgresql[5432]
    conn_sensors: dict = get_conn_dict(DB_SENSORS_CONF)
    conn_url: URL = URL.create(**conn_sensors)
    engine_sensors: sqlalchemy.Engine = create_engine(conn_url, echo=False)
    Cls_sess_sensors: scoped_session = scoped_session(
        sessionmaker(bind=engine_sensors)
    )
    app_logger.info(f"Cls_sess_sensors: {Cls_sess_sensors}")

    emailAddress: str = "user1@examples.com"
    measurementDay: str = "2023-03-14"
    selector = Selector(Cls_sess_healthcare, Cls_sess_sensors, logger=app_logger)
    healthcare_dict: Dict = selector.get_healthcare_asdict(emailAddress, measurementDay)
    if healthcare_dict:
        app_logger.info(healthcare_dict)

        weather_dict = selector.get_weather_asdict(measurementDay)
        if weather_dict:
            app_logger.info(weather_dict)

            healthcare_dict["emailAddress"] = emailAddress
            healthcare_dict["measurementDay"] = measurementDay
            # 天気状態取得
            weather_dict = selector.get_weather_asdict(measurementDay)
            if app_logger_debug:
                app_logger.debug(f"Weather: {weather_dict}")
            if weather_dict:
                healthcare_dict["weatherData"] = weather_dict
            else:
                # 天候がなければ未設定
                healthcare_dict["weatherData"] = None
            # 日本語が含まれる: ensure_ascii=False
            json_str = json.dumps(healthcare_dict, indent=3, ensure_ascii=False)
            save_text(SAVE_JSON, json_str)

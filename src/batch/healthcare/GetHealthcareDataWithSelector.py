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
JSON_DATA = os.path.join("json_datas", "healthcare_data_latest.json")

JSON_TEMPL = os.path.join("json_datas", 'healthcare_data_templ.txt')
SAVE_JSON = os.path.join("json_datas", 'out_healthcare_data.json')

LOG_FMT = '%(asctime)s %(filename)s %(funcName)s %(levelname)s %(message)s'


def read_text(filePath: str) -> str:
    with open(filePath, 'r') as fp:
        text = fp.read()
    return text


def save_text(filePath: str, text) -> None:
    with open(filePath, 'w') as fp:
        fp.write(text)


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
    logging.basicConfig(format=LOG_FMT, level=logging.DEBUG)
    logger = logging.getLogger(__name__)
    # テンプレート
    tmpl_json = read_text(JSON_TEMPL)
    logger.debug(tmpl_json)

    # 健康管理データベース: postgresql[5433]
    conn_dict: dict = get_conn_dict(DB_HEALTHCARE_CONF)
    conn_url: URL = URL.create(**conn_dict)
    engine_healthcare: sqlalchemy.Engine = create_engine(conn_url, echo=False)
    Cls_sess_healthcare: scoped_session = scoped_session(
        sessionmaker(bind=engine_healthcare)
    )
    logger.info(f"Cls_sess_healthcare: {Cls_sess_healthcare}")

    # 2.気象センサデータベース
    # 気象センサーデータベース: postgresql[5432]
    conn_sensors: dict = get_conn_dict(DB_SENSORS_CONF)
    conn_url: URL = URL.create(**conn_sensors)
    engine_sensors: sqlalchemy.Engine = create_engine(conn_url, echo=False)
    Cls_sess_sensors: scoped_session = scoped_session(
        sessionmaker(bind=engine_sensors)
    )
    logger.info(f"Cls_sess_sensors: {Cls_sess_sensors}")

    emailAddress: str = "user1@examples.com"
    measurementDay: str = "2023-03-01"
    selector = Selector(Cls_sess_healthcare, Cls_sess_sensors, logger=logger)
    healthcare_dict: Dict = selector.get_healthcare_asdict(emailAddress, measurementDay)
    if healthcare_dict:
        logger.info(healthcare_dict)

        weather_dict = selector.get_weather_asdict(measurementDay)
        if weather_dict:
            logger.info(weather_dict)

            healthcare_dict["emailAddress"] = emailAddress
            healthcare_dict["measurementDay"] = measurementDay
            healthcare_dict["weatherCondition"] = weather_dict
            # https://stackoverflow.com/questions/64560044/reading-template-json-file-and-creating-new-file-with-substitutions-in-bash-or-p
            #  reading template JSON file and creating new file with substitutions in bash or Python
            logger.debug(weather_dict)
            # 日本語が含まれる: ensure_ascii=False
            json_str = json.dumps(healthcare_dict, indent=3, ensure_ascii=False)
            save_text(SAVE_JSON, json_str)

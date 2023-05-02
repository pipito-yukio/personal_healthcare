import argparse
import logging
from typing import Dict, Optional
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
    parser: argparse.ArgumentParser = argparse.ArgumentParser()
    # メールアドレス (例) user1@examples.com
    parser.add_argument("--mail-address", type=str, required=True,
                        help="Healthcare Databae Person mailAddress.")
    # 測定日付 (例) 2023-03-14
    parser.add_argument("--measurement-day", type=str, required=True,
                        help="測定日付 yyyy-MM-dd.")
    # JSON保存パス
    parser.add_argument("--save-path", type=str,
                        help="保存パス ~/Documents/Healthcare/json/output")
    args: argparse.Namespace = parser.parse_args()
    emailAddress: str = args.mail_address
    measurementDay: str = args.measurement_day
    # 保存ファイル名
    jsonName: str = f"out_healthcare_data_{measurementDay}.json"
    # 保存パスは任意
    savePath: Optional[str] = args.save_path
    saveJsonFile = None
    if savePath is not None:
        saveJsonFile = os.path.join(os.path.expanduser(savePath), jsonName)
    else:
        # プロジェクト内のjson_datas
        saveJsonFile = os.path.join("json_datas", jsonName)
    app_logger.info(f"saveJsonFile: {saveJsonFile}")

    # 1-1. 健康管理データベース
    conn_dict: dict = get_conn_dict(DB_HEALTHCARE_CONF)
    conn_url: URL = URL.create(**conn_dict)
    engine_healthcare: sqlalchemy.Engine = create_engine(conn_url, echo=False)
    # Flaskアプリで使うscoped_sessionに合わせるためバッチでもscoped_sessionクラスを生成する
    Cls_sess_healthcare: scoped_session = scoped_session(
        sessionmaker(bind=engine_healthcare)
    )
    app_logger.info(f"Cls_sess_healthcare: {Cls_sess_healthcare}")

    # 1-2. 気象センサデータベース
    conn_sensors: dict = get_conn_dict(DB_SENSORS_CONF)
    conn_url: URL = URL.create(**conn_sensors)
    engine_sensors: sqlalchemy.Engine = create_engine(conn_url, echo=False)
    Cls_sess_sensors: scoped_session = scoped_session(
        sessionmaker(bind=engine_sensors)
    )
    app_logger.info(f"Cls_sess_sensors: {Cls_sess_sensors}")

    # 2. 登録済みデータ検索クラスの検索オブジェクトを生成する
    selector = Selector(Cls_sess_healthcare, Cls_sess_sensors, logger=app_logger)
    # 2-1. 検索オブジェクトの健康管理データ(辞書オブジェクト)取得関数を呼び出す
    healthcare_dict: Dict = selector.get_healthcare_asdict(emailAddress, measurementDay)
    if healthcare_dict:
        # 健康管理データ辞書オブジェクトが存在すれば天候状態を取得する
        app_logger.info(healthcare_dict)

        # 2-2. 検索オブジェクトの天候状態データ取得関数を呼び出す
        weather_dict = selector.get_weather_asdict(measurementDay)
        if weather_dict:
            # 天候データは必ずある想定
            app_logger.info(weather_dict)
            healthcare_dict["weatherData"] = weather_dict

            # 3. 辞書オブジェクトを登録データ取得レスポンス形式(JSON)でファイル保存
            # ※ Flaskアプリでのレスポンスの生成を模倣
            healthcare_dict["emailAddress"] = emailAddress
            healthcare_dict["measurementDay"] = measurementDay
            # 日本語が含まれるため: ensure_ascii=False
            # 3-1. 辞書オブジェクトをJSON形式文字列に変換
            json_str = json.dumps(healthcare_dict, indent=3, ensure_ascii=False)
            # 3-2. JSON文字列をファイル保存
            # ※保存されるJSONはFlaskアプリのレスポンスと同一となる
            save_text(saveJsonFile, json_str)

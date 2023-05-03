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
    if app_logger_debug:
        app_logger.debug(f"Healthcare: {healthcare_dict}")
    if healthcare_dict:
        # 2-2. レスポンス用に入力パラメータのメールアドレスと測定日付を辞書オブジェクトに追加する
        healthcare_dict["emailAddress"] = emailAddress
        healthcare_dict["measurementDay"] = measurementDay

        # 2-3. 検索オブジェクトの天候状態データ取得関数を呼び出す
        weather_dict: Dict = selector.get_weather_asdict(measurementDay)
        if app_logger_debug:
            app_logger.debug(f"Weather: {weather_dict}")
        if weather_dict:
            # 天候状態データを天候データにラップして辞書オブジェクトに追加
            healthcare_dict["weatherData"] = weather_dict
        else:
            # 天候がなければ未設定
            # ※登録時に気象センサーデータベースに障害があった場合に可能性が有るが
            # 通常ここにくることは想定していない
            healthcare_dict["weatherData"] = None

        # ※ FlaskアプリのOKレスポンスと同じ辞書オブジェクトを生成
        resp_obj: Dict = {
            "data": healthcare_dict,
            "status": {"code": 0, "message": "OK"}
        }
        # 3-1. 辞書オブジェクトをJSON形式文字列に変換
        # 日本語が含まれるため: ensure_ascii=False
        json_str = json.dumps(resp_obj, indent=2, ensure_ascii=False)
        # 3-2. JSON文字列をファイル保存
        # ※保存されるJSONはFlaskアプリのレスポンスと同一
        save_text(saveJsonFile, json_str)

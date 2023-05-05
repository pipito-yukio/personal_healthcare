import json
import logging
import os
import socket
import uuid

import sqlalchemy
from flask import Flask
from sqlalchemy import create_engine
from sqlalchemy.engine.url import URL
from sqlalchemy.orm import scoped_session, sessionmaker

from healthcare.log import logsetting


def getdict_forurl(filePath: str) -> dict:
    with open(filePath, 'r') as fp:
        db_conf: json = json.load(fp)
        hostname = socket.gethostname()
        # host in /etc/hosts
        db_conf["host"] = db_conf["host"].format(hostname=hostname)
    return db_conf

# PostgreSQL connection information json file.
CONF_PATH: str = os.path.expanduser("~/bin/conf")
DB_HEALTHCARE_CONF: str = os.path.join(CONF_PATH, "db_healthcare.json")
DB_SENSORS_CONF: str = os.path.join(CONF_PATH, "db_sensors.json")

app: Flask = Flask(__name__)
# ロガーを本アプリ用のものに設定する
app_logger: logging.Logger = logsetting.get_logger("app_main")
app_logger_debug: bool = (app_logger.getEffectiveLevel() <= logging.DEBUG)
app.config.from_object("healthcare.config")
# セッション用の秘密キー
app.secret_key = uuid.uuid4().bytes

# サーバホストとセッションのドメインが一致しないとブラウザにセッションIDが設定されない
IP_HOST: str = os.environ.get("IP_HOST", "localhost")
FLASK_PROD_PORT: str = os.environ.get("FLASK_PROD_PORT", "8080")
has_prod: bool = os.environ.get("FLASK_ENV", "development") == "production"
SERVER_HOST: str
if has_prod:
    # Production mode
    SERVER_HOST = IP_HOST + ":" + FLASK_PROD_PORT
else:
    SERVER_HOST = IP_HOST + ":5000"
app_logger.info("SERVER_HOST: {}".format(SERVER_HOST))

app.config["SERVER_NAME"] = SERVER_HOST
app.config["APPLICATION_ROOT"] = "/healthcare"
# use flask jsonify with japanese message
app.config["JSON_AS_ASCII"] = False

# SQLAlchemy engine
# 1.健康管理データベース: postgresql[5433]
conn_dict: dict = getdict_forurl(DB_HEALTHCARE_CONF)
conn_url: URL = URL.create(**conn_dict)
app_logger.info(f"Healthcare database URL: {conn_url}")
engine_healthcare: sqlalchemy.Engine = create_engine(conn_url, echo=False)
# 個人テーブルチェック用
Session_healthcare = sessionmaker(bind=engine_healthcare)
app_logger.info(f"Session_healthcare: {Session_healthcare}")
# セッションクラス定義
Cls_sess_healthcare: sqlalchemy.orm.scoping.scoped_session = scoped_session(
    sessionmaker(bind=engine_healthcare)
    )
app_logger.info(f"Cls_sess_healthcare: {Cls_sess_healthcare}")

# 2.気象センサーデータベース: postgresql[5432]
conn_dict: dict = getdict_forurl(DB_SENSORS_CONF)
conn_url: URL = URL.create(**conn_dict)
app_logger.info(f"Sensors database URL: {conn_url}")
engine_sensors: sqlalchemy.Engine = create_engine(conn_url, echo=False)
Cls_sess_sensors: sqlalchemy.orm.scoping.scoped_session = scoped_session(
    sessionmaker(bind=engine_sensors)
    )
app_logger.info(f"Cls_sess_sensors: {Cls_sess_sensors}")
if app_logger_debug:
    app_logger.debug(f"{app.config}")

# Application main program
from healthcare.views import app_main

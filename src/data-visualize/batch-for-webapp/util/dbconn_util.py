import json
import socket
from typing import Dict

"""
データベース接続情報読み込みユーティリティ関数
"""


def getSQLAlchemyConnWithDict(path: str, hostname: str = None) -> Dict:
    """
    SQLAlchemyの接続URL用の辞書オブジェクトを取得する
    :param path: 接続設定ファイルパス (JSON形式)
    :param hostname: ホスト名 ※未設定なら実行PCのホスト名
    :return: SQLAlchemyのURL用辞書オブジェクトDB_HEALTHCARE_CONF
    """
    with open(path, 'r') as fp:
        db_conf: json = json.load(fp)
        if hostname is None:
            hostname = socket.gethostname()
        # host in /etc/hostname: "hostname.local"
        db_conf["host"] = db_conf["host"].format(hostname=hostname)
    return db_conf


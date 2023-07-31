import argparse
import logging
import os
from typing import List

from sqlalchemy.engine import Engine
from sqlalchemy.engine.url import URL
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, scoped_session

import plotter.plotter_sleepmanhistdual as sm_hist
from plotter.plotter_sleepmanbar import SleepManStatistics
from plotter.plotparameter import PhoneImageInfo, getPhoneImageInfoFromHeader
import util.date_util as du
import util.file_util as fu
from util.dbconn_util import getSQLAlchemyConnWithDict

"""
特定期間の睡眠スコアが下記条件に対応する並列のヒストグラムを描画する
[結合するテーブル]
  (A) 睡眠管理テーブル
  (B) 夜間頻尿要因テーブル
[フィルター条件]
  (A) 睡眠スコア >=80
  (B) 睡眠スコア <75
[プロット列]
  (1) 夜間トイレ回数 (SQLで取得)
  (2) 睡眠時刻 (計算項目): 起床時刻(SQLで取得) - 睡眠時間(SQLで取得)
  (3) 深い睡眠時間 (SQLで取得): 分に変換
  (4) 睡眠時間 (SQLで取得)
"""

# スクリプト名
script_name = os.path.basename(__file__)
# ログフォーマット
LOG_FMT = '%(levelname)s %(message)s'

# 健康管理データベース接続情報
DB_CONF: str = os.path.join(os.path.expanduser("~/bin/conf"), "db_healthcare.json")

OUT_HTML = """
<!DOCTYPE html>
<html lang="ja">
<body>
<img src="{}"/>
</body>
</html>
"""

# スマートフォンの描画領域サイズ (ピクセル): Google pixel 4a
# '1064x1704x2.75'


if __name__ == '__main__':
    logging.basicConfig(format=LOG_FMT)
    app_logger = logging.getLogger(__name__)
    app_logger.setLevel(level=logging.DEBUG)

    parser: argparse.ArgumentParser = argparse.ArgumentParser()
    # メールアドレス (例) user1@examples.com
    parser.add_argument("--mail-address", type=str, required=True,
                        help="Healthcare Database Person mailAddress.")
    # 検索開始日
    parser.add_argument("--start-date", type=str, required=True,
                        help="2023-04-01")
    # 検索終了日
    parser.add_argument("--end-date", type=str, required=True,
                        help="2023-04-30")
    # スマートフォンの描画領域サイズ ※必須
    parser.add_argument("--phone-image-info", type=str, required=True,
                        help="スマートフォンの描画領域サイズ['幅,高さ,密度'] (例) '1064x1704x2.75'")
    # ホスト名 ※任意 (例) raspi-4
    parser.add_argument("--db-host", type=str, help="Other database hostname.")
    args: argparse.Namespace = parser.parse_args()

    # 検索範囲
    start_date = args.start_date
    end_date = args.end_date
    # 日付文字列チェック
    for i_date in [start_date, end_date]:
        if not du.check_str_date(i_date):
            app_logger.warning(f"Invalid date format ('YYYY-mm-dd'): {i_date}")
            exit(1)

    # 携帯巻末の画像領域サイズチェック
    phone_image_info: PhoneImageInfo
    try:
        phone_image_info = getPhoneImageInfoFromHeader(args.phone_image_info)
        app_logger.info(f"{phone_image_info}")
    except ValueError as err:
        app_logger.warning(f"Invalid phone_image_info: {err}")
        exit(1)

    # 選択クエリーの主キー: メールアドレス
    mail_address: str = args.mail_address

    # データベースホスト ※未指定ならローカル
    db_host = args.db_host
    connDict: dict = getSQLAlchemyConnWithDict(DB_CONF, hostname=db_host)
    # データベース接続URL生成
    connUrl: URL = URL.create(**connDict)
    # SQLAlchemyデータベースエンジン
    db_engine: Engine = create_engine(connUrl, echo=False)
    sess_factory = sessionmaker(bind=db_engine)
    app_logger.info(f"session_factory: {sess_factory}")
    # Sessionクラスは sqlalchemy.orm.scoping.scoped_session
    Cls_sess = scoped_session(sess_factory)
    app_logger.info(f"Session class: {Cls_sess}")
    # Sessionオブジェクト生成
    sess_obj: scoped_session = Cls_sess()
    app_logger.info(f"scoped_session: {sess_obj}")

    # 指定条件の統計情報
    statistics: SleepManStatistics
    # 指定条件のグラフ生成
    html_img_src: str
    try:
        statistics, html_img_src = sm_hist.plot(
            sess_obj, args.mail_address, start_date, end_date,
            phone_image_info,
            logger=app_logger, is_debug=True
        )
    except Exception as err:
        app_logger.error(err)
        exit(1)

    app_logger.info(f"{statistics}")
    html: str = OUT_HTML.format(html_img_src)

    # プロット結果をPNG形式でファイル保存
    script_names: List[str] = script_name.split(".")
    save_name = f"{script_names[0]}.html"
    save_path = os.path.join("output", save_name)
    app_logger.info(save_path)
    fu.save_text(save_path, html)

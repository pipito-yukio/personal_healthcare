import argparse
import logging
import os
from typing import List, Optional

from sqlalchemy.engine import Engine
from sqlalchemy.engine.url import URL
from sqlalchemy import create_engine
from sqlalchemy.orm import scoped_session, sessionmaker

import plotter.common.constants as plotter_const
from plotter.common.todaydata import TodaySleepMan
from plotter.plotter_sleepman import getTodayData
from plotter.plotter_sleepmanbar import plot, SleepManStatistics
from plotter.plotparameter import PhoneImageInfo, getPhoneImageInfoFromHeader
import util.date_util as du
import util.file_util as fu
from util.dbconn_util import getSQLAlchemyConnWithDict

"""
■ Webアプリ用部品クラス作成用バッチスクリプト
(1) 睡眠管理データの棒グラフプロットスクリプト
 [期間] 指定日を除く(当日: 未登録)過去2週間 + 当日のデータ(base64エンコード済み)
  Webアプリ用部品クラスを呼び出す
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


if __name__ == '__main__':
    logging.basicConfig(format=LOG_FMT)
    app_logger = logging.getLogger(__name__)
    app_logger.setLevel(level=logging.DEBUG)

    parser: argparse.ArgumentParser = argparse.ArgumentParser()
    # メールアドレス (例) user1@examples.com
    parser.add_argument("--mail-address", type=str, required=True,
                        help="Healthcare Database Person mailAddress.")
    # 検索最終日 ※登録済みデータの最終レコードを想定
    parser.add_argument("--end-date", type=str, required=True,
                        help="検索終了日 ISO-8601形式")
    # スマートフォンの描画領域サイズ ※必須
    parser.add_argument("--phone-image-info", type=str, required=True,
                        help="スマートフォンの描画領域サイズ['幅,高さ,密度'] (例) '1064x1704x2.75'")
    # 当日データ (base64エンコード): 未登録データで任意
    parser.add_argument("--today-data", type=str, required=False,
                        help="当日データのbase64エンコード済み文字列")
    # ホスト名 ※任意 (例) raspi-4
    parser.add_argument("--db-host", type=str, help="Other database hostname.")
    args: argparse.Namespace = parser.parse_args()
    app_logger.info(args)

    end_date: str = args.end_date
    # 日付文字列チェック
    if not du.check_str_date(end_date):
        app_logger.warning("Invalid day format!")
        exit(1)

    # 携帯巻末の画像領域サイズチェック
    phone_image_info: PhoneImageInfo
    try:
        phone_image_info = getPhoneImageInfoFromHeader(args.phone_image_info)
        app_logger.info(f"{phone_image_info}")
    except ValueError as err:
        app_logger.warning(f"Invalid phone_image_info: {err}")
        exit(1)

    # リクエストの当日データ
    encoded_today_data: str = args.today_data
    app_logger.info(f"encoded_today_data: {encoded_today_data}")
    today_data: Optional[TodaySleepMan]
    if encoded_today_data is not None:
        try:
            today_data = getTodayData(encoded_today_data, logger=app_logger, is_debug=True)
        except ValueError:
            app_logger.warning("Encoded todayData is invalid format!")
            exit(1)
    else:
        today_data = None
    app_logger.info(f"today_data: {today_data}")

    # 14日前の開始日を求める
    start_date: str = du.add_day_string(
        end_date, add_days=plotter_const.BEFORE_2WEEK_PERIODS)
    app_logger.info(f"start_date: {start_date}, end_date: {end_date}")

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
        statistics, html_img_src = plot(
            sess_obj, args.mail_address, start_date, end_date,
            phone_image_info,
            today_data=today_data,
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
import argparse
import logging
import os
from typing import List, Optional
from urllib import parse

from sqlalchemy.engine import Engine
from sqlalchemy.engine.url import URL
from sqlalchemy import create_engine
from sqlalchemy.orm import scoped_session, sessionmaker

from plotter.plotter_bloodpressureline import plot, BloodPressStatistics
from plotter.plotparameter import (
    PhoneImageInfo, BloodPressUserTarget,
    getPhoneImageInfoFromHeader, getBloodPressUserTargetFromParameter
)
import util.date_util as du
import  util.file_util as fu
from util.dbconn_util import getSQLAlchemyConnWithDict

"""
■ Webアプリ用部品クラス作成用バッチスクリプト
(2) 血圧測定データの折れ線グラフプロットスクリプト
 [期間] 月間
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
    # 年月
    parser.add_argument("--year-month", type=str, required=True,
                        help="年月 (例) 2023-04")
    # スマートフォンの描画領域サイズ ※必須
    parser.add_argument("--phone-image-info", type=str, required=True,
                        help="スマートフォンの描画領域サイズ['幅,高さ,密度'] (例) '1064x1704x2.75'")
    # 携帯端末のユーザー目標血圧値 ※urlエンコード済み文字列
    parser.add_argument("--user-target", type=str, required=False,
                        help="ユーザー目標血圧値 ※urlエンコード済み")
    # ホスト名 ※任意 (例) raspi-4
    parser.add_argument("--db-host", type=str, help="Other database hostname.")
    # 基準値オーバー数値の出力を抑止するか
    # action - コマンドラインにこの引数があったときの基本のアクション
    # 引数があれば True, なければ False
    parser.add_argument("--suppress-show-over", action="store_true", help="Suppress value with standard over.")
    parser.add_argument("--warning-over", action="store_false", help="Show value with warning over.")
    args: argparse.Namespace = parser.parse_args()
    app_logger.info(args)

    year_month: str = args.year_month
    # 指定年月の開始日
    start_date: str = f"{year_month}-01"
    # 日付文字列チェック
    if not du.check_str_date(start_date):
        app_logger.warning("Invalid day format!")
        exit(1)

    # 携帯巻末の画像領域サイズチェック ※必須
    phone_image_info: PhoneImageInfo
    try:
        phone_image_info = getPhoneImageInfoFromHeader(args.phone_image_info)
        app_logger.info(f"{phone_image_info}")
    except ValueError as err:
        # 必須項目のため端末側にリクエストエラーを通知する必要がある
        app_logger.warning(f"Invalid phone_image_info: {err}")
        exit(1)

    # ユーザ目標値 ※任意
    encoded_user_target: str = args.user_target
    user_target: Optional[BloodPressUserTarget]
    if encoded_user_target is not None:
        # urldecode
        s_user_target: str = parse.unquote_plus(encoded_user_target)
        try:
            user_target = getBloodPressUserTargetFromParameter(s_user_target)
        except ValueError as err:
            # 値がある場合は端末側にリクエストエラーを通知する必要がある
            app_logger.warning(f"Invalid user_target: {err}")
            exit(1)
    else:
        user_target = None
    app_logger.info(f"{user_target}")

    # その他オプション
    suppress_show_over: bool = args.suppress_show_over
    # 検索年月の月末日
    end_day: int = du.calcEndOfMonth(year_month)
    # 検索年月の終了日文字列
    end_date: str = f"{year_month}-{end_day:#02d}"

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
    statistics: BloodPressStatistics
    # 指定条件のグラフ生成
    html_img_src: str
    try:
        statistics, html_img_src = plot(
            sess_obj, args.mail_address, start_date, end_date,
            phone_image_info,
            is_yearmonth=True,
            today_data=None,
            user_target=user_target,
            suppress_show_over=suppress_show_over,
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

import argparse
import logging
import os
from typing import List, Optional
from urllib import parse

from sqlalchemy.engine import Engine
from sqlalchemy.engine.url import URL
from sqlalchemy import create_engine
from sqlalchemy.orm import scoped_session, sessionmaker

from plotter.common.todaydata import TodayBloodPress
from plotter.plotparameter import (
    PhoneImageInfo, BloodPressUserTarget,
    getPhoneImageInfoFromHeader, getBloodPressUserTargetFromParameter
)
from plotter.plotter_bloodpressure import getTodayData
from plotter.plotter_bloodpressurebar import plot, BloodPressStatistics
import util.date_util as du
import util.file_util as fu
from util.dbconn_util import getSQLAlchemyConnWithDict

"""
■ Webアプリ用部品クラス作成用バッチスクリプト
(2) 血圧測定データの棒グラフプロットスクリプト
 [期間] 検索終了日を含む14日間前
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
    # 検索最終日
    parser.add_argument("--end-date", type=str, required=False,
                        help="検索終了日 ISO-8601形式")
    # スマートフォンの描画領域サイズ ※必須
    parser.add_argument("--phone-image-info", type=str, required=True,
                        help="スマートフォンの描画領域サイズ['幅,高さ,密度'] (例) '1064x1704x2.75'")
    # 当日データ (base64エンコード) ※未登録
    parser.add_argument("--today-data", type=str, required=False,
                        help="当日データのbase64エンコード済み文字列")
    # 携帯端末のユーザー目標血圧値 ※urlエンコード済み文字列
    parser.add_argument("--user-target", type=str, required=False,
                        help="ユーザー目標血圧値 ※urlエンコード済み")
    # ホスト名 ※任意 (例) raspi-4
    parser.add_argument("--db-host", type=str, help="Other database hostname.")
    # 基準値オーバー数値の出力を抑止するか
    # action - コマンドラインにこの引数があったときの基本のアクション
    # 引数があれば True, なければ False
    parser.add_argument("--suppress-show-over", action="store_true", help="Suppress value with standard over.")
    args: argparse.Namespace = parser.parse_args()
    app_logger.info(args)

    # 指定年月日を含む14日前
    end_date: str = args.end_date
    # 日付文字列チェック
    if not du.check_str_date(end_date):
        app_logger.warning(f"{end_date} is invalid date.")
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

    # リクエストの当日データはカンマ区切り
    encoded_today_data: str = args.today_data
    app_logger.info(f"encoded_today_data: {encoded_today_data}")
    today_data: Optional[TodayBloodPress]
    if encoded_today_data is not None:
        today_data = getTodayData(encoded_today_data, logger=app_logger, is_debug=True)
    else:
        today_data = None
    app_logger.info(f"today_data: {today_data}")

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
            sess_obj, args.mail_address, end_date,
            phone_image_info,
            today_data=today_data,
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

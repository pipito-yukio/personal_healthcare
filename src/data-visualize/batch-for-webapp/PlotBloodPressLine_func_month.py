import argparse
import logging
import os
from typing import List

from sqlalchemy.engine import Engine
from sqlalchemy.engine.url import URL
from sqlalchemy import create_engine
from sqlalchemy.orm import scoped_session, sessionmaker

from plotter.plotter_bloodpressureline import plot, BloodPressStatistics
from plotter.plotparameter import PhoneImageInfo, BloodPressUserTarget
import util.date_util as du
from util.file_util import save_text
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

# スマートフォンの描画領域サイズ (ピクセル): Google pixel 4a
PHONE_PX_WIDTH: int = 1020
PHONE_PX_HEIGHT: int = 1700
PHONE_DENSITY: float = 2.75

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
    # 携帯端末のユーザー目標最大血圧値、最低血圧値
    parser.add_argument("--user-target-max", type=int, required=False,
                        help="ユーザー目標最大血圧値")
    parser.add_argument("--user-target-min", type=int, required=False,
                        help="ユーザー目標最低血圧値")
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

    # 携帯巻末の画像領域サイズ
    phone_image_info: PhoneImageInfo = PhoneImageInfo(
        px_width=PHONE_PX_WIDTH, px_height=PHONE_PX_HEIGHT, density=PHONE_DENSITY
    )
    # ユーザ目標値
    user_target: BloodPressUserTarget = BloodPressUserTarget(args.user_target_max, args.user_target_min)
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
    save_text(save_path, html)

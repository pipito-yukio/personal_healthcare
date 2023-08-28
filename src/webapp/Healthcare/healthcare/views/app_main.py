import json
from typing import Dict, Optional, Tuple, Union

import sqlalchemy
from flask import Response, abort, g, jsonify, make_response, request
from sqlalchemy import Select, select
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.orm import scoped_session
from sqlalchemy.orm.exc import NoResultFound
from werkzeug.exceptions import (BadRequest, Conflict, Forbidden,
                                 HTTPException, InternalServerError, NotFound)
from werkzeug.datastructures import Headers

from healthcare import (Cls_sess_healthcare, Cls_sess_sensors,
                        Session_healthcare, app, app_logger, app_logger_debug,
                        )
from healthcare.dao.blood_pressure import BloodPressure
from healthcare.dao.body_temperature import BodyTemperature
from healthcare.dao.nocturia_factors import NocturiaFactors
from healthcare.dao.person import Person
from healthcare.dao.queries import Selector
from healthcare.dao.query_registerdays import UserRegisterDays
from healthcare.dao.sleep_management import SleepManagement
from healthcare.dao.walking_count import WalkingCount
from healthcare.dao.weather_condition import WeatherCondition

import healthcare.plotter.common.constants as plotter_const
import healthcare.plotter.common.todaydata as td
import healthcare.plotter.plotter_bloodpressure as bp_common
import healthcare.plotter.plotter_bloodpressurebar as bp_bar
import healthcare.plotter.plotter_bloodpressureline as bp_line
import healthcare.plotter.plotter_sleepman as sm_common
import healthcare.plotter.plotter_sleepmanbar as sm_bar
import healthcare.plotter.plotter_sleepmanhistdual as sm_histdual
from healthcare.plotter.plotparameter import (
    PhoneImageInfo, BloodPressUserTarget,
    getPhoneImageInfoFromHeader, getBloodPressUserTargetFromParameter
)
import healthcare.util.date_util as du


MSG_DESCRIPTION: str = "error_message"
ABORT_DICT_BLANK_MESSAGE: Dict[str, str] = {MSG_DESCRIPTION: ""}
ABORT_DICT_UNMATCH_TOKEN: Dict[str, str] = {"error_message": app.config["UNMATCH_TOKEN"]}
ABORT_DICT_REQURE_PHONE_IMGSIZE: Dict[str, str] = {
    "error_message": app.config["REQUIRE_PHONE_IMG_SIZE"]
}
# アプリケーションルートパス
APP_ROOT: str = app.config["APPLICATION_ROOT"]

# 共通リクエストパラメータ
PARAM_EMAIL: str = "emailAddress"
# 画像取得時のリクエストパラメータ
# 年月データ取得 ※必須
PARAM_KEY_YM: str = "yearMonth"
# 2週間前データ取得, 睡眠管理ヒストグラム取得 ※必須
PARAM_KEY_END_DATE: str = "endDay"
# 睡眠管理ヒストグラム取得 ※必須
PARAM_KEY_START_DATE: str = "startDay"
# 2週間前データ取得(血圧測定, 睡眠管理) ※任意
PARAM_KEY_TODAY_DATA: str = "todayData"
# 血圧リクエスト時のユーザ目標
PARAM_KEY_USER_TARGET: str = "userTarget"
# リクエストパラメータエラー時のコード
NOT_FOUND_EMAIL: int = 461
ERR_REQUIRE_EMAIL: int = 462
ERR_REQUIRE_YM: int = 471
ERR_INVALID_YM: int = 472
ERR_REQUIRE_START_DATE: int = 473
ERR_INVALID_START_DATE: int = 474
ERR_REQUIRE_END_DATE: int = 475
ERR_INVALID_END_DATE: int = 476
ERR_INVALID_TODAY_BP: int = 480
ERR_INVALID_TODAY_SM: int = 481
ERR_INVALID_USER_TARGET: int = 482


def get_healthcare_session() -> scoped_session:
    """
    健康管理DB用セッションオブジェクトを取得する
    """
    if 'healthcare_session' not in g:
        # 健康管理DB用セッションオブジェクト生成
        g.healthcare_session: scoped_session = Cls_sess_healthcare()
        if app_logger_debug:
            app_logger.debug(f"g.healthcare_session:{g.healthcare_session}")
    return g.healthcare_session


def get_sensors_session() -> scoped_session:
    """
    気象センサDB用セッションオブジェクトを取得する
    """
    if 'sensors_session' not in g:
        # 気象センサDB用セッションオブジェクト生成
        g.sensors_session: scoped_session = Cls_sess_sensors()
        if app_logger_debug:
            app_logger.debug(f"g.sensors_session:{g.sensors_session}")
    return g.sensors_session


@app.teardown_appcontext
def close_sessions(exception=None) -> None:
    """
    各データベース用セッションのクリーンアップ
    """
    # 健康管理DB用セッション
    sess: scoped_session = g.pop('healthcare_session', None)
    app_logger.debug(f"healthcare_session:{sess}")
    if sess is not None:
        # クラスのremoveメソッド呼び出し
        Cls_sess_healthcare.remove()

    # 気象センサDB用セッション
    sess: scoped_session = g.pop('sensors_session', None)
    app_logger.debug(f"sensors_session:{sess}")
    if sess is not None:
        Cls_sess_sensors.remove()
       

@app.route(APP_ROOT + "/getcurrentdata", methods=["GET"])
def getcurrentdata():
    """メールアドレスと測定日付から健康管理データを取得するリクエスト

    :param: request parameter: ?emailAddress=user1@examples.com&measurementDay=2023-0-03
    :return: JSON形式(健康管理DBから取得したデータ + 気象センサーDBから取得した天候)
    """
    if app_logger_debug:
        app_logger.debug(request.path)
        app_logger.debug(request.args.to_dict())

    # 1.リクエストパラメータチェック
    # 1-1.メールアドレス
    emailAddress: str = _checkEmailAddress(request)
    # 1-2.測定日付
    measurementDay = request.args.get("measurementDay")
    if measurementDay is None:
        abort(BadRequest.code, _set_errormessage("463,Required MeasurementDay."))

    # 健康管理DBと気象センサーDBからデータ取得する
    sess_healthcare: scoped_session = get_healthcare_session()
    sess_sensors: scoped_session = get_sensors_session()
    selector = Selector(sess_healthcare, sess_sensors, logger=app_logger)
    # 健康管理データ取得
    healthcare_dict: Dict = selector.get_healthcare_asdict(emailAddress, measurementDay)
    if app_logger_debug:
        app_logger.debug(f"Healthcare: {healthcare_dict}")    
    if healthcare_dict:
        healthcare_dict["emailAddress"] = emailAddress
        healthcare_dict["measurementDay"] = measurementDay
        # 天気状態取得
        weather_dict = selector.get_weather_asdict(measurementDay)
        if app_logger_debug:
            app_logger.debug(f"Weather: {weather_dict}")    
        if weather_dict:
            healthcare_dict["weatherData"] = weather_dict
        else:
            # 天候がなければ未設定
            healthcare_dict["weatherData"] = None
        
        return make_getdata_success(healthcare_dict)
    else:
        abort(NotFound.code, _set_errormessage("Data is not found."))


@app.route(APP_ROOT + "/register", methods=["POST"])
def register():
    """
    健康管理データ(必須)と天候データ(必須)の登録
    """
    if app_logger_debug:
        app_logger.debug(request.path)

    # 登録データチェック
    personId, emailAddress, measurementDay, data = _check_postdata(request)
    
    # 健康管理データ登録 (必須)
    _insert_healthdata(personId, measurementDay, data)
    # 気象データ登録 (必須) ※気象センサーデータベース
    _insert_weather(measurementDay, data)

    # ここまでエラーがなければOKレスポンス
    return make_register_success(emailAddress, measurementDay)


@app.route(APP_ROOT + "/update", methods=["POST"])
def update():
    """
    健康管理データ(任意)または天候データ(任意)の更新
    """
    if app_logger_debug:
        app_logger.debug(request.path)

    # 更新データチェック
    personId, emailAddress, measurementDay, data = _check_postdata(request)

    # 健康管理データの更新
    _update_healthdata(personId, measurementDay, data)
    # 天候状態(気象データベース)の更新
    _update_weather(measurementDay, data)

    # ここまでエラーがなければOKレスポンス
    return make_register_success(emailAddress, measurementDay)


@app.route(APP_ROOT + "/get_registerdays", methods=["GET"])
def getRegisterDays():
    """
    メールアドレスからユーザーの登録開始日と登録最終日を取得するリクエスト
    :param: request parameter: ?emailAddress=user1@examples.com
    :return: JSON形式(登録開始日と登録最終日)
    """
    if app_logger_debug:
        app_logger.debug(request.path)
        app_logger.debug(request.args.to_dict())

    # メールアドレス ※必須
    email_address: str = _checkEmailAddress(request)
    
    # 登録開始日と登録最終日を取得 ※ユーザーが存在してもレコードなしのケースもあり得る
    sess_healthcare: scoped_session = get_healthcare_session()
    register_days: UserRegisterDays = UserRegisterDays(
        sess_healthcare, email_address, logger=app_logger)
    first_day: str = None
    last_day: str = None
    try:
        first_day, last_day = register_days.get_days()
    except Exception as err:
        app_logger.error(err)
        abort(InternalServerError.code, _set_errormessage(f"559,{err}"))

    return make_registerdays_success(first_day, last_day)


@app.route(APP_ROOT + "/getplot_bloodpress_line_ym_forphone", methods=["GET"])
def getPlotBloodPressLineYearMonthForPhone() -> Response:
    """
    月間の血圧測定データの折れ線グラフプロット画像取得リクエスト
    request header: 
        トークン, 携帯端末の領域サイズ情報
    request parameter: 
        メールアドレス(必須), 年月(必須), 血圧基準値のユーザー目標(任意)
    (例) 'emailAddress=user1%40examples.com&yearMonth=2023-06&userTarget=130%2C85'
    :return: JSON形式{可視化画像(base64エンコード文字列), 血圧測定統計情報(カンマ区切り)}
    """
    if app_logger_debug:
        app_logger.debug(request.path)
        app_logger.debug(request.args.to_dict())

    # 共通の必須チェック
    mail_address: str
    phone_image_info: PhoneImageInfo
    mail_address, phone_image_info = _checkRequireCommonForGetImage(request)
    # 検索対象年月から検索開始日付と終了日付を取得 ※必須
    start_date: str
    end_date: str
    start_date, end_date = _checkYearMonth(request)
    # ユーザ目標血圧基準値 ※任意
    user_target: BloodPressUserTarget = _checkUserTarget(request)

    # 血圧測定データレスポンス
    # 統計情報 (カンマ区切り)
    statistics: bp_common.BloodPressStatistics
    # プロット画像 (base64エンコード済み)
    str_stat: str
    rec_count: int
    html_img_src: str
    sess_obj: scoped_session = get_healthcare_session()
    try:
        statistics, html_img_src = bp_line.plot(
            sess_obj, mail_address, start_date, end_date,
            phone_image_info,
            is_yearmonth=True,
            today_data=None,
            user_target=user_target,
            suppress_show_over=False,
            logger=app_logger, is_debug=app_logger_debug
        )
        # 統計情報をレスポンス用に分解する
        str_stat, rec_count = bp_common.flattenStatistics(statistics)
    except Exception as err:
        # ここにくるのはDBエラー・バグなど想定
        app_logger.error(err)
        abort(InternalServerError.code, _set_errormessage(f"559,{err}"))

    return make_image_success(str_stat, rec_cnt=rec_count, img_src=html_img_src)


@app.route(APP_ROOT + "/getplot_bloodpress_line_2w_forphone", methods=["GET"])
def getPlotBloodPressLine2wForPhone() -> Response:
    """
    前日から2週間前+当日AM血圧測定データの折れ線グラフプロット画像画像取得リクエスト
    request header: トークン, 携帯端末の領域サイズ情報
    request parameter: 
        メールアドレス(必須), 検索最終日(通常は前日), 当日AM血圧測定データ(任意), 
        血圧基準値のユーザー目標(任意)
    (例) 'emailAddress=user1%40examples.com&endDay=2023-06-30&todayData=[base64encoded]&userTarget=130%2C85'
    :return: JSON形式{可視化画像(base64エンコード文字列), 血圧測定統計情報(カンマ区切り)}
    """
    if app_logger_debug:
        app_logger.debug(request.path)
        app_logger.debug(request.args.to_dict())

    # 共通の必須チェック
    mail_address: str
    phone_image_info: PhoneImageInfo
    mail_address, phone_image_info = _checkRequireCommonForGetImage(request)
    # 検索最終日付チェック ※必須
    end_date = _checkRequireDate(
        request, PARAM_KEY_END_DATE, ERR_REQUIRE_END_DATE, ERR_INVALID_END_DATE)
    # 当日AM測定時血圧データ ※任意
    today_data: td.TodayBloodPress = _getBloodPressTodayData(request)
    # ユーザ目標血圧基準値 ※任意
    user_target: BloodPressUserTarget = _checkUserTarget(request)

    # 14日前の開始日を求める
    start_date: str = du.add_day_string(
        end_date, add_days=plotter_const.BEFORE_2WEEK_PERIODS)
    if app_logger_debug:
        app_logger.debug(f"start_date: {start_date}")
    # 血圧測定データレスポンス
    statistics: bp_common.BloodPressStatistics
    str_stat: str
    rec_count: int
    html_img_src: str
    sess_obj: scoped_session = get_healthcare_session()
    try:
        statistics, html_img_src = bp_line.plot(
            sess_obj, mail_address, start_date, end_date,
            phone_image_info,
            is_yearmonth=False,
            today_data=today_data,
            user_target=user_target,
            suppress_show_over=False,
            logger=app_logger, is_debug=app_logger_debug
        )
        str_stat, rec_count = bp_common.flattenStatistics(statistics)
    except Exception as err:
        app_logger.error(err)
        abort(InternalServerError.code, _set_errormessage(f"559,{err}"))

    return make_image_success(str_stat, rec_cnt=rec_count, img_src=html_img_src)


@app.route(APP_ROOT + "/getplot_bloodpress_bar_2w_forphone", methods=["GET"])
def getPlotBloodPressBar2wForPhone() -> Response:
    """
    前日から2週間前+当日AM血圧測定データの棒グラフプロット画像取得リクエスト
    request header: トークン, 携帯端末の領域サイズ情報
    request parameter: 
        メールアドレス(必須), 検索最終日(通常は前日), 当日AM血圧測定データ(任意), 
        血圧基準値のユーザー目標(任意)
    (例) 'emailAddress=user1%40examples.com&endDay=2023-06-30&todayData=[base64encoded]&userTarget=130%2C85'
    :return: JSON形式{可視化画像(base64エンコード文字列), 血圧測定統計情報(カンマ区切り)}
    """
    if app_logger_debug:
        app_logger.debug(request.path)
        app_logger.debug(request.args.to_dict())

    # 共通の必須チェック
    mail_address: str
    phone_image_info: PhoneImageInfo
    mail_address, phone_image_info = _checkRequireCommonForGetImage(request)
    # 検索終了日付 ※必須
    end_date:str = _checkRequireDate(
        request, PARAM_KEY_END_DATE, ERR_REQUIRE_END_DATE, ERR_INVALID_END_DATE)
    # 当日AM測定時血圧データ ※任意
    today_data: td.TodayBloodPress = _getBloodPressTodayData(request)
    # ユーザ目標血圧基準値 ※任意
    user_target: BloodPressUserTarget = _checkUserTarget(request)

    # 14日前の開始日を求める
    start_date: str = du.add_day_string(
        end_date, add_days=plotter_const.BEFORE_2WEEK_PERIODS)
    if app_logger_debug:
        app_logger.debug(f"start_date: {start_date}")
    # 血圧測定データレスポンス
    statistics: bp_common.BloodPressStatistics
    str_stat: str
    rec_count: int
    html_img_src: str
    sess_obj: scoped_session = get_healthcare_session()
    try:
        statistics, html_img_src = bp_bar.plot(
            sess_obj, mail_address, start_date, end_date,
            phone_image_info,
            today_data=today_data,
            user_target=user_target,
            suppress_show_over=False,
            logger=app_logger, is_debug=True
        )
        str_stat, rec_count = bp_common.flattenStatistics(statistics)
    except Exception as err:
        app_logger.error(err)
        abort(InternalServerError.code, _set_errormessage(f"559,{err}"))

    return make_image_success(str_stat, rec_cnt=rec_count, img_src=html_img_src)


@app.route(APP_ROOT + "/getplot_sleepman_bar_ym_forphone", methods=["GET"])
def getPlotSleepManBarYearMonthForPhone() -> Response:
    """
    月間の睡眠管理データの可視化画像取得リクエスト
    request header: 
        トークン, 携帯端末の領域サイズ情報
    request parameter: 
        メールアドレス(必須), 年月(必須)
    (例) 'emailAddress=user1%40examples.com&yearMonth=2023-06'
    :return: JSON形式{可視化画像(base64エンコード文字列), 睡眠管理統計情報(カンマ区切り)}
    """
    if app_logger_debug:
        app_logger.debug(request.path)
        app_logger.debug(request.args.to_dict())

    # 共通の必須チェック
    mail_address: str
    phone_image_info: PhoneImageInfo
    mail_address, phone_image_info = _checkRequireCommonForGetImage(request)
    # 検索対象年月から検索開始日付と終了日付を取得 ※必須
    start_date: str
    end_date: str
    start_date, end_date = _checkYearMonth(request)

    # 月間の睡眠管理データレスポンス
    statistics: sm_common.SleepManStatistics
    str_stat: str
    rec_count: int
    html_img_src: str
    sess_obj: scoped_session = get_healthcare_session()
    try:
        statistics, html_img_src = sm_bar.plot(
            sess_obj, mail_address, start_date, end_date,
            phone_image_info,
            today_data=None,
            logger=app_logger, is_debug=app_logger_debug
        )
        # 統計情報をレスポンス用に分解する
        str_stat, rec_count = sm_common.flattenStatistics(statistics)
    except Exception as err:
        app_logger.error(err)
        abort(InternalServerError.code, _set_errormessage(f"559,{err}"))

    return make_image_success(str_stat, rec_cnt=rec_count, img_src=html_img_src)


@app.route(APP_ROOT + "/getplot_sleepman_bar_2w_forphone", methods=["GET"])
def getPlotSleepManBar2wForPhone() -> Response:
    """
    前日から2週間前+当日睡眠管理データの棒グラフプロット画像取得リクエスト
    request header: 
        トークン, 携帯端末の領域サイズ情報
    request parameter: 
        メールアドレス(必須), 検索最終日(通常は前日), 当日睡眠管理データ(任意)
    (例) 'emailAddress=user1%40examples.com&endDay=2023-06-30&todayData=[base64encoded]'
    :return: JSON形式{可視化画像(base64エンコード文字列), 睡眠管理統計情報(カンマ区切り)}
    """
    if app_logger_debug:
        app_logger.debug(request.path)
        app_logger.debug(request.args.to_dict())

    # 共通の必須チェック
    mail_address: str
    phone_image_info: PhoneImageInfo
    mail_address, phone_image_info = _checkRequireCommonForGetImage(request)
    # 検索最終日付をチェック ※必須
    end_date:str = _checkRequireDate(
        request, PARAM_KEY_END_DATE, ERR_REQUIRE_END_DATE, ERR_INVALID_END_DATE)
    # 当日睡眠管理データ ※任意
    today_data: td.TodaySleepMan = _getSleepManTodayData(request)

    # 14日前の開始日を求める
    start_date: str = du.add_day_string(
        end_date, add_days=plotter_const.BEFORE_2WEEK_PERIODS)
    if app_logger_debug:
        app_logger.debug(f"start_date: {start_date}")
    # 睡眠管理データレスポンス
    statistics: sm_common.SleepManStatistics
    str_stat: str
    rec_count: int
    html_img_src: str
    sess_obj: scoped_session = get_healthcare_session()
    try:
        statistics, html_img_src = sm_bar.plot(
            sess_obj, mail_address, start_date, end_date,
            phone_image_info,
            today_data=today_data,
            logger=app_logger, is_debug=app_logger_debug
        )
        str_stat, rec_count = sm_common.flattenStatistics(statistics)
    except Exception as err:
        app_logger.error(err)
        abort(InternalServerError.code, _set_errormessage(f"559,{err}"))

    return make_image_success(str_stat, rec_cnt=rec_count, img_src=html_img_src)


@app.route(APP_ROOT + "/getplot_sleepman_histdual_range_forphone", methods=["GET"])
def getPlotSleepManHistDualRangeForPhone() -> Response:
    """
    指定検索期間(開始日から終了日)の睡眠管理データのヒストグラム画像取得リクエスト
    request header: 
        トークン, 携帯端末の領域サイズ情報
    request parameter: 
        メールアドレス(必須), 検索開始日, 検索最終日 ※1ヶ月, 3ヶ月, 6ヶ月, 1年を想定
    (例) 'emailAddress=user1%40examples.com&startDay=2023-01-01&endDay=2023-03-31'
    :return: JSON形式{可視化画像(base64エンコード文字列), 睡眠管理統計情報(カンマ区切り)}
    """
    if app_logger_debug:
        app_logger.debug(request.path)
        app_logger.debug(request.args.to_dict())

    # 共通の必須チェック
    mail_address: str
    phone_image_info: PhoneImageInfo
    mail_address, phone_image_info = _checkRequireCommonForGetImage(request)
    # 検索開始日チェック ※必須
    start_date:str = _checkRequireDate(
        request, PARAM_KEY_START_DATE, ERR_REQUIRE_START_DATE, ERR_INVALID_START_DATE)
    # 検索最終日チェック ※必須
    end_date:str = _checkRequireDate(
        request, PARAM_KEY_END_DATE, ERR_REQUIRE_END_DATE, ERR_INVALID_END_DATE)

    # 睡眠管理データレスポンス
    statistics: sm_common.SleepManStatistics
    str_stat: str
    rec_count: int
    html_img_src: str
    sess_obj: scoped_session = get_healthcare_session()
    try:
        statistics, html_img_src = sm_histdual.plot(
            sess_obj, mail_address, start_date, end_date,
            phone_image_info,
            logger=app_logger, is_debug=True
        )
        str_stat, rec_count = sm_common.flattenStatistics(statistics)
    except Exception as err:
        app_logger.error(err)
        abort(InternalServerError.code, _set_errormessage(f"559,{err}"))

    return make_image_success(str_stat, rec_cnt=rec_count, img_src=html_img_src)


def _checkEmailAddress(req: request) -> str:
    """
    可視化画像取得リクエスト共通の必須のリクエストパラメータチェック
    :param req: request
    :return emailAddress
    """
    emailAddress: str = request.args.get(PARAM_EMAIL)
    if emailAddress is None:
        app_logger.warning(f"Required: {ERR_REQUIRE_EMAIL}")
        abort(BadRequest.code,
              _set_errormessage(f"{ERR_REQUIRE_EMAIL},Required {PARAM_EMAIL}."))

    # PersonテーブルにemailAddressが存在するか
    personId: int = _get_personid(emailAddress)
    if personId is None:
        abort(BadRequest.code, 
              _set_errormessage(f"{NOT_FOUND_EMAIL},User is not found."))
    
    return emailAddress    


def _checkYearMonth(req: request) -> Tuple[str, str]:
    """
    月間データリクエストのパラメターチェック ※必須
    :param req: request, key="yearMonth"
    :return Tuple[検索開始日付文字列, 検索終了日付文字列] 
    """
    year_month: Optional[str] = request.args.get(PARAM_KEY_YM)
    if app_logger_debug:
        app_logger.debug(f"{PARAM_KEY_YM}: {year_month}")
    if year_month is None:
        app_logger.warning(f"Required: {ERR_REQUIRE_YM}")
        abort(BadRequest.code,
              _set_errormessage(f"{ERR_REQUIRE_YM},Required {PARAM_KEY_YM}."))

    # 年月の妥当性チェック
    start_date: str = f"{year_month}-01"
    # 日付文字列チェック
    if not du.check_str_date(start_date):
        app_logger.warning(f"Invalid: {ERR_INVALID_YM}")
        abort(BadRequest.code, 
              _set_errormessage(f"{ERR_INVALID_YM},Invalid format {PARAM_KEY_YM}."))
    
    # 検索年月の月末日
    end_day: int = du.calcEndOfMonth(year_month)
    # 検索年月の終了日文字列
    end_date: str = f"{year_month}-{end_day:#02d}"
    return start_date, end_date


def _checkRequireDate(req: request, key: str, err_require: int, err_invalid) -> str:
    """
    必須の日付パラメターチェック
    :param req: request
    :param key: parameter key
    :param err_require: 必須エラーコード
    :param err_invalid: 形式エラーコード
    :return 正しい日付ならTrue, NGならabort()
    """
    s_date: Optional[str] = request.args.get(key)
    if app_logger_debug:
        app_logger.debug(f"{key}: {s_date}")
    if s_date is None:
        app_logger.warning(f"Required: {err_require}")
        abort(BadRequest.code, _set_errormessage(f"{err_require},Required {key}."))

    # 日付形式チェック
    if not du.check_str_date(s_date):
        app_logger.warning(f"Invalid: {err_invalid}")
        abort(BadRequest.code, _set_errormessage(f"{err_invalid},Invalid format {key}."))

    return s_date


def _checkUserTarget(req: request) -> Optional[BloodPressUserTarget]:
    """
    血圧測定値のユーザ目標基準値を取得する ※任意項目
    :param req: request, key="userTarget" ※urlencodeなのでここでは復元済み
    :return リクエストパラメータに存在しチェックエラーがなければ BloodPressUserTarget
    """
    s_target: Optional[str] = request.args.get(PARAM_KEY_USER_TARGET)
    if app_logger_debug:
        app_logger.debug(f"{PARAM_KEY_USER_TARGET}: {s_target}")
    if s_target is not None:
        # 存在した場合は形式があっているかチェックする ※不正リクエスト or BUG対応
        try:
            user_target: BloodPressUserTarget = getBloodPressUserTargetFromParameter(
                s_target)
            return user_target
        except ValueError as exp:
            app_logger.warning(f"Invalid: {ERR_INVALID_USER_TARGET}: {s_target}, {exp}")
            abort(
                BadRequest.code,
                _set_errormessage(
                    f"{ERR_INVALID_USER_TARGET},Invalid {PARAM_KEY_USER_TARGET}.")
                )
    else:
        return None    


def _getBloodPressTodayData(req: request) ->Optional[td.TodayBloodPress]:
    """
    当日AM血圧測定データ(base64エンコード文字列)からデコードした当日データ取得 ※任意
    :param req: request key="todayData"
    :return base64デコードした当日AM血圧測定データ
    """
    encoded_data: Optional[str] = request.args.get(PARAM_KEY_TODAY_DATA)
    if app_logger_debug:
        app_logger.debug(f"{PARAM_KEY_TODAY_DATA}: {encoded_data}")

    today_data: Optional[td.TodayBloodPress]
    if encoded_data is not None:
        try:
            today_data = bp_common.getTodayData(
                encoded_data, logger=app_logger, is_debug=app_logger_debug)
        except ValueError as exp:
            app_logger.warning(
                f"Invalid bloodPress {PARAM_KEY_TODAY_DATA}: {encoded_data}, {exp}")
            abort(BadRequest.code, 
                  _set_errormessage(
                      f"{ERR_INVALID_TODAY_BP},Invalid BP[{PARAM_KEY_TODAY_DATA}]."))
    else:
        today_data = None
    return today_data	


def _getSleepManTodayData(req: request) ->Optional[td.TodaySleepMan]:
    """
    当日睡眠管理データ(base64エンコード文字列)からデコードした当日データ取得 ※任意
    :param req: request key="todayData"
    :return base64デコードした当日睡眠管理データ
    """
    encoded_data: Optional[str] = request.args.get(PARAM_KEY_TODAY_DATA)
    if app_logger_debug:
        app_logger.debug(f"{PARAM_KEY_TODAY_DATA}: {encoded_data}")

    today_data: Optional[td.TodaySleepMan]
    if encoded_data is not None:
        try:
            today_data = sm_common.getTodayData(
                encoded_data, logger=app_logger, is_debug=app_logger_debug)
        except ValueError as exp:
            app_logger.warning(
                f"Invalid sleepMan {PARAM_KEY_TODAY_DATA}: {encoded_data}, {exp}")
            abort(BadRequest.code, 
                  _set_errormessage(
                      f"{ERR_INVALID_TODAY_SM},Invalid SM[{PARAM_KEY_TODAY_DATA}]."))
    else:
        today_data = None
    return today_data	


def _matchToken(headers: Headers) -> bool:
    """トークン一致チェック
    :param headers: request header
    :return: if match token True, not False.
    """
    token_value: str = app.config.get("HEADER_REQUEST_PHONE_TOKEN_VALUE", "!")
    req_token_value: Optional[str] = headers.get(
        key=app.config.get("HEADER_REQUEST_PHONE_TOKEN_KEY", "!"),
        type=str,
        default=""
    )
    if req_token_value != token_value:
        app_logger.warning("Invalid request token!")
        return False
    return True


def _checkPhoneImageSize(headers: Headers) -> PhoneImageInfo:
    """
    ヘッダーに表示領域サイズ+密度([width]x[height]x[density])をつけてくる
    ※1.トークンチェックを通過しているのでセットされている前提で処理
    ※2.途中でエラー (Androidアプリ側のBUG) ならExceptionで補足されJSONでメッセージが返却される
    :param headers: request header
    :return: (imageWidth, imageHeight, density)
    """
    img_size: str = headers.get(
        app.config.get("HEADER_REQUEST_IMAGE_SIZE_KEY", ""), type=str, default=""
    )
    if app_logger_debug:
        app_logger.debug(f"Phone imgSize: {img_size}")
    if len(img_size) == 0:
        abort(BadRequest.code, ABORT_DICT_REQURE_PHONE_IMGSIZE)

    phone_img_info: PhoneImageInfo
    try:
        phone_img_info = getPhoneImageInfoFromHeader(img_size)
        if app_logger_debug:
            app_logger.debug(f"phone_img_info: {phone_img_info}")
    except ValueError as exp:
        # ログには例外メッセージ
        app_logger.warning(f"Bad phone image size: {exp}")
        # アボートレスポンスは不正パラメータ
        ABORT_DICT_BLANK_MESSAGE["error_message"] = app.config["INVALID_BEFORE_DAYS"]
        abort(BadRequest.code, ABORT_DICT_BLANK_MESSAGE)

    return phone_img_info


def _checkRequireCommonForGetImage(req: request) -> Tuple[str, PhoneImageInfo]:
    """
    画像取得リクエスト共通の必須チェック
    :param req: request
    :return Tuple[str, PhoneImageInfo]
    """
    # 1.ヘッダー
    # 1-1.トークン ※必須
    headers: Headers = request.headers
    if app_logger_debug:
        app_logger.debug(f"headers: {headers}")
    if not _matchToken(headers):
        abort(Forbidden.code, ABORT_DICT_UNMATCH_TOKEN)
    # 1-2.表示領域サイズ+密度 ※必須
    phone_image_info: PhoneImageInfo = _checkPhoneImageSize(headers)

    # 2.クエストパラメータ
    # 2-1.メールアドレス ※必須
    mail_address: str = _checkEmailAddress(request)
    return mail_address, phone_image_info


def _insert_healthdata(person_id: int, measurement_day: str, data: Dict) -> None:
    """
    健康管理データの登録処理
    :param person_id メールアドレスから取得したPersion.id
    :param measurement_day: 測定日
    :data 登録用データ
    """
    # JSONキーチェック
    try:
        # 健康管理データコンテナ
        healthcare_data: Dict = data["healthcareData"]
        # (1) 睡眠管理
        sleep_man: Dict = healthcare_data["sleepManagement"]
        # (2) 血圧測定
        blood_press: Dict = healthcare_data["bloodPressure"]
        # (3) 夜中トイレ回数要因
        nocturia_factors: Dict = healthcare_data["nocturiaFactors"]
        # (4) 歩数
        walking_count: Dict = healthcare_data["walkingCount"]
        # (5) 体温データ
        body_temper: Dict = healthcare_data["bodyTemperature"]
    except KeyError as err:
        app_logger.warning(err)
        abort(BadRequest.code, _set_errormessage("460,{err}"))

    # 主キー値を設定
    sleep_man["pid"] = person_id
    sleep_man["measurementDay"] = measurement_day
    blood_press["pid"] = person_id
    blood_press["measurementDay"] = measurement_day
    nocturia_factors["pid"] = person_id
    nocturia_factors["measurementDay"] = measurement_day
    walking_count["pid"] = person_id
    walking_count["measurementDay"] = measurement_day
    body_temper["pid"] = person_id
    body_temper["measurementDay"] = measurement_day
    # 登録用の各クラスにデータを設定
    sleepMan: SleepManagement = SleepManagement(**sleep_man)
    if app_logger_debug:
        app_logger.debug(sleepMan)
    bloodPressure: BloodPressure = BloodPressure(**blood_press)
    if app_logger_debug:
        app_logger.debug(bloodPressure)
    factors: NocturiaFactors = NocturiaFactors(**nocturia_factors)
    if app_logger_debug:
        app_logger.debug(factors)
    walking: WalkingCount = WalkingCount(**walking_count)
    if app_logger_debug:
        app_logger.debug(walking)
    bodyTemper: BodyTemperature = BodyTemperature(**body_temper)
    if app_logger_debug:
        app_logger.debug(bodyTemper)

    # 健康管理DB用セッションオブジェクト取得
    sess = get_healthcare_session()
    try:
        sess.begin()
        sess.add_all(
            [sleepMan, bloodPressure, factors, walking, bodyTemper]
        )
        sess.commit()
    except IntegrityError as err:
        app_logger.warning(f"IntegrityError: {err.args}")
        sess.rollback()
        # IntegrityErrorならConfilict(409)を返却 ※Androidアプリ側で"登録済み"を表示する
        abort(Conflict.code, _set_errormessage("Already registered."))
    except SQLAlchemyError as err:
        app_logger.warning(err.args)
        sess.rollback()
        abort(InternalServerError.code, _set_errormessage(f"559,{err}"))
    finally:
        sess.close()
    

def _update_healthdata(person_id: int, measurement_day: str, data: Dict) -> None:
    """
    健康管理データの更新処理
    :param person_id メールアドレスから取得したPersion.id
    :param measurement_day: 測定日
    :data 更新用データ
    """
    # 健康管理データコンテナ: 必須
    healthcare_data: Optional[Dict] = _has_dict_in_data("healthcareData", data)
    if healthcare_data is None:
        abort(BadRequest.code, _set_errormessage("451,Required healthcareData!"))
    
    # 更新用データは更新されたテーブルのデータのみが存在する
    update_table_count: int = 0
    # (1) 睡眠管理
    sleep_man: Optional[Dict] = _has_dict_in_data("sleepManagement", healthcare_data)
    if app_logger_debug:
        app_logger.debug(f"sleepManagement: {sleep_man}")
    if sleep_man is not None:
        update_table_count += 1

    # (2) 血圧測定
    blood_press: Optional[Dict] = _has_dict_in_data("bloodPressure", healthcare_data)
    if app_logger_debug:
        app_logger.debug(f"bloodPressure: {blood_press}")
    if blood_press is not None:
        update_table_count += 1

    # (3) 夜中トイレ回数要因
    nocturia_factors: Optional[Dict] = _has_dict_in_data("nocturiaFactors", healthcare_data)
    if app_logger_debug:
        app_logger.debug(f"nocturiaFactors: {nocturia_factors}")
    if nocturia_factors is not None:
        update_table_count += 1

    # (4) 歩数
    walking_count: Optional[Dict] = _has_dict_in_data("walkingCount", healthcare_data)
    if app_logger_debug:
        app_logger.debug(f"walkingCount: {walking_count}")
    if walking_count is not None:
        update_table_count += 1

    # (5) 体温データ
    body_temper: Optional[Dict] = _has_dict_in_data("bodyTemperature", healthcare_data)
    if app_logger_debug:
        app_logger.debug(f"bodyTemperature: {body_temper}")
    if body_temper is not None:
        update_table_count += 1

    # 更新データ有無
    if update_table_count == 0:
        # 健康管理データの更新なし ※天候データのみの修正の場合があり得る
        return

    # 健康管理DB用セッションオブジェクト取得
    sess = get_healthcare_session()
    # https://docs.sqlalchemy.org/en/20/core/dml.html#sqlalchemy.sql.expression.Update
    #  Insert, Updates, Deletes
    # https://www.educba.com/sqlalchemy-update-object/
    #  SQLAlchemy update object
    try:
        sess.begin()
        if sleep_man is not None:
            stmt = (
                sqlalchemy.update(SleepManagement).
                where(SleepManagement.pid==person_id, SleepManagement.measurementDay==measurement_day).
                values(**sleep_man)
            )
            sess.execute(stmt)
        if blood_press is not None:
            stmt = (
                sqlalchemy.update(BloodPressure).
                where(BloodPressure.pid==person_id, BloodPressure.measurementDay==measurement_day).
                values(**blood_press)
            )
            sess.execute(stmt)
        if nocturia_factors is not None:
            stmt = (
                sqlalchemy.update(NocturiaFactors).
                where(NocturiaFactors.pid==person_id, NocturiaFactors.measurementDay==measurement_day).
                values(**nocturia_factors)
            )
            sess.execute(stmt)
        if walking_count is not None:
            stmt = (
                sqlalchemy.update(WalkingCount).
                where(WalkingCount.pid==person_id, WalkingCount.measurementDay==measurement_day).
                values(**walking_count)
            )
            sess.execute(stmt)
        if body_temper is not None:
            stmt = (
                sqlalchemy.update(BodyTemperature).
                where(BodyTemperature.pid==person_id, BodyTemperature.measurementDay==measurement_day).
                values(**body_temper)
            )
        sess.commit()
        if app_logger_debug:
            app_logger.debug(f"Updated[HealthcareData]: Person.id: {person_id}, MeasuremtDay: {measurement_day}")
    except SQLAlchemyError as err:
        sess.rollback()
        app_logger.warning(err.args)
        abort(InternalServerError.code, _set_errormessage(f"559,{err}"))
    finally:    
        sess.close()



def _insert_weather(measurement_day: str, data: Dict) -> None:
    """
    天候状態の登録処理
    :param measurement_day: 測定日
    :data 登録用データ (必須)
    """
    try:
        # 天候データコンテナは必ずある
        weather_data: Dict = data["weatherData"]
        # 天候状態は必須項目
        weather_condition: Dict = weather_data["weatherCondition"]
    except KeyError as err:
        app_logger.warning(err)
        return

    # 主キー設定
    weather_condition["measurementDay"] = measurement_day
    weather: WeatherCondition = WeatherCondition(**weather_condition)
    # 気象センサDB用セッションオブジェクト取得
    sess = get_sensors_session()
    try:
        sess.begin()
        sess.add(weather)
        sess.commit()
    except IntegrityError as err:
        app_logger.warning(f"IntegrityError: {err.args}")
        sess.rollback()
    except SQLAlchemyError as err:
        app_logger.warning(err.args)
        sess.rollback()
    finally:
        sess.close()
    


def _update_weather(measurement_day: str, data: Dict) -> None:
    """
    天候状態の更新処理
    :param measurement_day: 測定日
    :data 更新用データ (任意)
    """
    try:
        # 天候データコンテナは必須
        weather_data: Dict = data["weatherData"]
    except KeyError as err:
        app_logger.warning(err)
        return
    
    # 天候状態は任意 
    weather_condition: Dict = _has_dict_in_data("weatherCondition", weather_data)
    if app_logger_debug:
        app_logger.debug(f"weather_condition: {weather_condition}")
    if weather_condition is None:
        # 更新データ無し
        return

    # 気象センサDB用セッションオブジェクト取得
    sess = get_sensors_session()
    try:
        sess.begin()
        stmt = (
            sqlalchemy.update(WeatherCondition).
            where(WeatherCondition.measurementDay==measurement_day).
            values(**weather_condition)
        )
        sess.execute(stmt)
        sess.commit()
        if app_logger_debug:
            app_logger.debug(f"Updated[WeatherData]: MeasuremtDay: {measurement_day}")
    except SQLAlchemyError as err:
        app_logger.warning(err.args)
        sess.rollback()
    finally:
        sess.close()


def _check_postdata(request) ->Tuple[int, str, str, Dict]:
    # リクエストヘッダーチェック
    if "application/json" not in request.headers["Content-Type"]:
        abort(BadRequest.code, _set_errormessage("450,Bad request Content-Type."))

    # 登録用データ取得_errorresponse_with_imagere
    data: Dict = json.loads(request.data)
    if app_logger_debug:
        app_logger.debug(data)

    # メールアドレスチェック
    emailAddress = data.get("emailAddress", None)
    if emailAddress is None:
        abort(BadRequest.code, _set_errormessage("462,Required EmailAddress."))

    # 測定日付チェック
    measurementDay = data.get("measurementDay", None)
    if measurementDay is None:
        abort(BadRequest.code, _set_errormessage("463,Required MeasurementDay."))

    # PersonテーブルにemailAddressが存在するか
    personId = _get_personid(emailAddress)
    if personId is None:
        abort(BadRequest.code, _set_errormessage("461,User is not found."))

    return personId, emailAddress, measurementDay, data    


def _get_personid(email_address: str) -> Optional[int]:
    """
    メールアドレスに対応するPersion.idを取得する
    :email_address: メールアドレス
    """
    try:
        # https://docs.sqlalchemy.org/en/20/orm/session_transaction.html
        #   Transactions and Connection Management
        #     Managing Transactions
        person_id: Optional[int]
        with Session_healthcare() as session:
            with session.begin():
                stmt: Select = select(Person).where(Person.email == email_address)
                person: Person = session.scalars(stmt).one()
                if person:
                    person_id = person.id
                else:
                    person_id = None
        if app_logger_debug:    
            app_logger.debug(f"person_id: {person_id}")
        return person_id
    except NoResultFound as notFound:
        app_logger.warning(f"NoResultFound: {notFound}")
        return None
    except Exception as error:
        app_logger.error(f"Error: {error}")
        abort(InternalServerError.code, _set_errormessage(f"{error}"))


def _has_dict_in_data(dict_key: str, data:Dict) -> Optional[Dict]:
    try:
        result: Dict = data[dict_key]
        return result
    except KeyError as err:
        # データ無し
        return None


def make_getdata_success(json_dict: Dict) -> Response:
    """
    データ取得処理OKレスポンス
    :param json_dict: JSON出力用辞書
    :return: Response
    """
    resp_obj: Dict = {
        "status": {
            "code": 0, "message": "OK"},
        "data": json_dict
    }
    return _make_respose(resp_obj, 200)


def make_register_success(email_address: str, measurement_day: str) -> Response:
    """
    登録処理OKレスポンス
    :param email_address: メールアドレス
    :param measurement_day 測定日付
    :return: Response
    """
    resp_obj: Dict = {
        "status":
            {"code": 0, "message": "OK"},
        "data": {
            "emailAddress": email_address,
            "measurementDay": measurement_day
        }
    }
    return _make_respose(resp_obj, 200)


def make_registerdays_success(first_day: str, last_day: str) -> Response:
    """
    ユーザーの登録開始日と登録最終日
    :param first_day: 登録開始日
    :param last_day; 登録最終日
    :return: Response
    """
    resp_obj: Dict = {
        "status":
            {"code": 0, "message": "OK"},
        "data": {
            "firstDay": first_day,
            "lastDay": last_day
        }
    }
    return _make_respose(resp_obj, 200)


def make_image_success(s_statistics: str, rec_cnt: int, img_src: str) -> Response:
    """_
    画像取得処理OKレスポンス
    :param s_statistics: カンマ区切りの統計情報文字列
    :param rec_cnt: レコード件数
    :param img_src: 画像イメージのbase64エンコード文字列
    :return: Json Response
    """
    resp_obj: Dict = {
        "status":
            {"code": 0, "message": "OK"},
        "data": {
            "statistics": s_statistics,
            "rows": rec_cnt,
            "img_src": img_src
        }
    }
    return _make_respose(resp_obj, 200)


def _set_errormessage(message: str) -> Dict:
    ABORT_DICT_BLANK_MESSAGE[MSG_DESCRIPTION] = message
    return ABORT_DICT_BLANK_MESSAGE


def _make_respose(resp_obj: Dict, resp_code) -> Response:
    response = make_response(jsonify(resp_obj), resp_code)
    response.headers["Content-Type"] = "application/json"
    return response


# Flask自体が404をトラップする場合エラーになり、InternalServerErrorとなってしまう
# error_type:<class 'werkzeug.exceptions.InternalServerError'>, 500 Internal Server Error    
@app.errorhandler(BadRequest.code)
@app.errorhandler(Forbidden.code)
@app.errorhandler(NotFound.code)
@app.errorhandler(Conflict.code) # IntegrityError (登録済み)
@app.errorhandler(InternalServerError.code)
def error_handler(error: Union[HTTPException, Dict]) -> Response:
    app_logger.warning(f"error_type:{type(error)}, {error}")
    err_msg: str
    if isinstance(error.description, dict):
        # アプリが呼び出すabort()の場合は辞書オブジェクト
        err_msg = error.description["error_message"]
    else:
        # Flaskが出す場合は HTTPException)
        err_msg = error.description
    resp_obj: Dict[str, Dict[str, Union[int, str]]] = {
        "status": {"code": error.code, "message": err_msg}
    }
    return _make_respose(resp_obj, error.code)

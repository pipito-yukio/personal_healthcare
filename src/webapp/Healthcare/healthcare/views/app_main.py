import json
from typing import Dict, Optional, Union

import sqlalchemy
from flask import Response, abort, g, jsonify, make_response, request
from sqlalchemy import Select, select
from sqlalchemy.orm import Session, scoped_session
from sqlalchemy.orm.exc import NoResultFound
from werkzeug.exceptions import (BadRequest, Conflict, Forbidden,
                                 HTTPException, InternalServerError, NotFound)

from healthcare import (Cls_sess_healthcare, Cls_sess_sensors, app, app_logger,
                        app_logger_debug, engine_healthcare)
from healthcare.dao.blood_pressure import BloodPressure
from healthcare.dao.body_temperature import BodyTemperature
from healthcare.dao.nocturia_factors import NocturiaFactors
from healthcare.dao.person import Person
from healthcare.dao.queries import Selector
from healthcare.dao.sleep_management import SleepManagement
from healthcare.dao.walking_count import WalkingCount
from healthcare.dao.weather_condition import WeatherCondition

MSG_DESCRIPTION: str = "error_message"
ABORT_DICT_BLANK_MESSAGE: Dict[str, str] = {MSG_DESCRIPTION: ""}
# アプリケーションルートパス
APP_ROOT: str = app.config["APPLICATION_ROOT"]


def get_healthcare_session() -> scoped_session:
    """
    健康管理DB用セッションオブジェクトを取得する
    """
    if 'healthcare_session' not in g:
        # 健康管理DB用セッションオブジェクト生成
        g.healthcare_session = Cls_sess_healthcare()
        if app_logger_debug:
            app_logger.debug(f"g.healthcare_session:{g.healthcare_session}")
    return g.healthcare_session


def get_sensors_session() -> scoped_session:
    """
    気象センサDB用セッションオブジェクトを取得する
    """
    if 'sensors_session' not in g:
        # 気象センサDB用セッションオブジェクト生成
        g.sensors_session = Cls_sess_sensors()
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
        try:
            # クラスのremoveメソッド呼び出し
            Cls_sess_healthcare.remove()
        except Exception as err:    
            app_logger.warning(f"Error removed Cls_sess_healthcare :{err}")

    # 気象センサDB用セッション
    sess: scoped_session = g.pop('sensors_session', None)
    app_logger.debug(f"sensors_session:{sess}")
    if sess is not None:
        try:
            Cls_sess_sensors.remove()
        except Exception as err:    
            app_logger.warning(f"Error removed Cls_sess_sensors :{err}")
        

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
    emailAddress = request.args.get("emailAddress")
    if emailAddress is None:
        abort(BadRequest.code, _set_errormessage("462,Required EmailAddress."))
    # 1-2.測定日付
    measurementDay = request.args.get("measurementDay")
    if emailAddress is None:
        abort(BadRequest.code, _set_errormessage("463,Required MeasurementDay."))

    # PersonテーブルにemailAddressが存在するか
    personId = _get_personid(emailAddress)
    if personId is None:
        abort(BadRequest.code, _set_errormessage("461,User is not found."))

    # 健康管理DBと気象センサーDBからデータ取得する
    selector = Selector(Cls_sess_healthcare, Cls_sess_sensors, logger=app_logger)
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
    except sqlalchemy.exc.IntegrityError as err:
        app_logger.warning(f"IntegrityError: {err.args}")
        sess.rollback()
        # IntegrityErrorならConfilict(409)を返却 ※Androidアプリ側で"登録済み"を表示する
        abort(Conflict.code, _set_errormessage("Already registered."))
    except sqlalchemy.exc.SQLAlchemyError as err:
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
    except sqlalchemy.exc.SQLAlchemyError as err:
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
    except sqlalchemy.exc.IntegrityError as err:
        app_logger.warning(f"IntegrityError: {err.args}")
        sess.rollback()
    except sqlalchemy.exc.SQLAlchemyError as err:
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
        # 天候データコンテナは必ずある
        weather_data: Dict = data["weatherData"]
        # 天候状態 
        weather_condition: Dict = weather_data["weatherCondition"]
        if app_logger_debug:
            app_logger.debug(f"weather_condition: {weather_condition}")
    except KeyError as err:
        app_logger.warning(err)
        return
    
    if weather_condition is None:
        # 更新データ無し
        return

    # 主キー設定
    weather_condition["measurementDay"] = measurement_day
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
    except sqlalchemy.exc.SQLAlchemyError as err:
        app_logger.warning(err.args)
        sess.rollback()
    finally:
        sess.close()
    


def _check_postdata(request):
    # リクエストヘッダーチェック
    if "application/json" not in request.headers["Content-Type"]:
        abort(BadRequest.code, _set_errormessage("450,Bad request Content-Type."))

    # 登録用データ取得
    data: dict = json.loads(request.data)
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
        session: Session = Session(engine_healthcare)
        with session.begin():
            stmt: Select = select(Person).where(Person.email == email_address)
            person: Person = session.scalars(stmt).one()
        if app_logger_debug:    
            app_logger.debug(f"person.id: {person.id}")
        return person.id
    except NoResultFound as notFound:
        app_logger.warning(f"NoResultFound: {notFound}")
        return None
    except Exception as error:
        app_logger.warning(f"Error: {error}")
        raise error


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


def _set_errormessage(message: str) -> Dict:
    ABORT_DICT_BLANK_MESSAGE[MSG_DESCRIPTION] = message
    return ABORT_DICT_BLANK_MESSAGE


def _make_respose(resp_obj: Dict, resp_code) -> Response:
    response = make_response(jsonify(resp_obj), resp_code)
    response.headers["Content-Type"] = "application/json"
    return response


@app.errorhandler(BadRequest.code)
@app.errorhandler(Forbidden.code)
@app.errorhandler(NotFound.code)
@app.errorhandler(Conflict.code) # IntegrityError (登録済み)
@app.errorhandler(InternalServerError.code)
def error_handler(error: HTTPException) -> Response:
    app_logger.warning(f"error_type:{type(error)}, {error}")
    resp_obj: Dict[str, Dict[str, Union[int, str]]] = {
        "status": {"code": error.code, "message": error.description["error_message"]}
    }
    return _make_respose(resp_obj, error.code)

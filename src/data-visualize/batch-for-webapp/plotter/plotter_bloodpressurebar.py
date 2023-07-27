import base64
import logging
import os
from datetime import datetime
from io import BytesIO
from typing import Dict, List, Optional, Tuple

import matplotlib.pyplot as plt
from matplotlib.figure import Figure
from matplotlib.axes import Axes
from matplotlib.patches import Patch

import numpy as np
import pandas as pd
from pandas.core.frame import DataFrame, Series
from pandas.core.indexes.datetimes import DatetimeIndex

from sqlalchemy.orm import scoped_session

from plotter.common.todaydata import TodayBloodPress
from plotter.plotter_bloodpressure import calcBloodPressureStatistics
from plotter.common.constants import BEFORE_2WEEK_PERIODS
from plotter.common.statistics import BloodPressStatistics
from plotter.plotter_common import (
    DrawPosition, drawTextOverValue, makeTitleWithMonthRange, pixelToInch
)
from plotter.dao import (
    COL_INDEX, COL_MORNING_MAX, COL_MORNING_MIN, COL_EVENING_MAX, COL_EVENING_MIN,
    BloodPressureDao, SelectColumnsType
)
import util.dataclass_util as dcu
import util.date_util as du
import util.file_util as fu
from util.date_util import DateCompEnum

"""
血圧測定データ棒グラフプロットと統計情報
[期間]
  指定された最終日付を含む過去14日間 + 当日AM測定データ
[特記事項]  
  脈拍はプロットしない
"""

# プロット可変設定
PLOT_CONF: str = os.path.join("plotter", "conf", "plot_bloodpress.json")

# 血圧値最小値
DEF_BLOOD_PRESS_MAX: float = 180.
DEF_BLOOD_PRESS_MIN: float = 40.
DEF_PULSE_MAX: float = 110.
DEF_PULSE_MIN: float = 40.
# 家庭血圧: 75歳未満 (120 - 75)
# 最高血圧の正常血圧: 115未満
# 最低血圧の正常血圧: 75未満
# (*) 最高血圧の正常高値血圧: 115〜124
# (*) 最低血圧の正常高値血圧: 75未満
DEF_TARGET_PRESS_MAX: float = 125.
DEF_TARGET_PRESS_MIN: float = 75.
# 設定値の上書き
plot_conf: Dict = fu.read_json(PLOT_CONF)
y_axis_bp: Dict = plot_conf["y_axis"]["blood_press"]
y_axis_pr: Dict = plot_conf["y_axis"]["pulse_rate"]
x_axis_rotation: float = plot_conf["x_axis"]["rotation"]
bp_value: Dict = plot_conf["value"]["blood_press"]
BLOOD_PRESS_MAX: float = y_axis_bp.get('max', DEF_BLOOD_PRESS_MAX)
BLOOD_PRESS_MIN: float = y_axis_bp.get('min', DEF_BLOOD_PRESS_MIN)
PULSE_MAX: float = y_axis_pr.get('max', DEF_PULSE_MAX)
PULSE_MIN: float = y_axis_pr.get('min', DEF_PULSE_MIN)
TARGET_PRESS_MAX: float = bp_value.get("target_max", DEF_TARGET_PRESS_MAX)
TARGET_PRESS_MIN: float = bp_value.get("target_min", DEF_TARGET_PRESS_MIN)

# 文字列定数定義
Y_PRESSURE_LABEL: str = "血圧値(mmHg)"
# 棒グラフの幅倍率
BAR_WIDTH: float = 0.7
# x軸の左右マージン ※データ1件を1としてその半分
X_LIM_MARGIN: float = -0.5
Y_LIM_MARGIN: float = 10.0
DRAW_POS_MARGIN: float = 0.5
# 血圧データの午前と午後の背景色リスト
BAR_COLORS: List = ['skyblue', 'plum']
COLOR_PRESS_MIN: str = 'white'

# スタイル辞書定数定義
# 棒の線スタイル
BAR_LINE_STYLE: Dict = {'edgecolor': 'black', 'linewidth': 0.7}
# 血圧の基準線の線スタイル
TARGET_LINE_STYLE: Dict = {'linestyle': 'dashdot', 'linewidth': 1.0}
TARGET_MAX_STYLE: Dict = {'color': 'magenta', **TARGET_LINE_STYLE}
TARGET_MIN_STYLE: Dict = {'color': 'crimson', **TARGET_LINE_STYLE}
# 描画領域のグリッド線スタイル: Y方向のグリッド線のみ表示
AXES_GRID_STYLE: Dict = {'axis': 'y', 'linestyle': 'dashed', 'linewidth': 0.7,
                         'alpha': 0.75}
# タイトルフォントスタイル
TITLE_FONT_STYLE: Dict = {'fontsize': 10, 'fontweight': 'medium'}
# X軸のラベル(日+曜日)スタイル
X_TICKS_STYLE: Dict = {'fontsize': 10, 'fontweight': 'bold', 'rotation': x_axis_rotation}
# 軸ラベルスタイル: y軸に設定
Y_TICK_LABEL_PARAMS: Dict = {'axis': 'y', 'labelsize': 9}
# 凡例スタイル
LEGEND_STYLE: Dict = {'loc': 'upper right', 'fontsize': 9}
# カスタム凡例
# https://matplotlib.org/stable/tutorials/intermediate/legend_guide.html
# Legend guide
LEGEND_AM: Patch = Patch(color=BAR_COLORS[0], label='午前 測定')
LEGEND_PM: Patch = Patch(color=BAR_COLORS[1], label='午後 測定')


def addTodayData(df_org: DataFrame, val_today: TodayBloodPress,
                 logger=None, is_debug=False) -> Tuple[bool, Optional[DataFrame]]:
    """
    当日データの測定日が最新ならDataFrameの末尾に追加する
    :param df_org:
    :param val_today: 当日データ is not None
    :param logger:
    :param is_debug:
    :return: 最新ならTuple[True, 当日データをDataFrameの末尾に追加したDataframe], 左記以外 Tuple[False, None]
    """
    df_size: int = len(df_org)
    # 当日データオブジェクトからデータのリストのみを取得する ※項目リストは不要
    _, datas = dcu.splitTodayData(val_today)
    if logger is not None and is_debug:
        logger.debug(f"today.datas: {datas}")

    # 最終レコードの測定日
    p_last_timestamp: pd.Timestamp = df_org[COL_INDEX][df_size - 1]
    last_date: datetime.date = p_last_timestamp.date()
    # 最終レコードの測定日と当日データの測定日を比較する
    d_comp: DateCompEnum = du.dateCompare(str(last_date), val_today.measurement_day)
    if logger is not None and is_debug:
        logger.debug(f"compare: {d_comp}")
    # 当日データの測定日が最新なら既存に追加する
    if d_comp == DateCompEnum.GT:
        # 血圧の当日データはAM測定値しか設定していないので、不足分は全てNaNを設定
        latest_timestamp: pd.Timestamp = pd.to_datetime(
            val_today.measurement_day + " 00:00:00", format=du.FMT_DATETIME
        )
        # 取得項目はAM測定値(最高血圧,最低血圧), PM測定値(最高血圧,最低血圧)のみ ※AM/PM測定時刻なし
        # 測定日(Timestamp), AM最高血圧, AM最低血圧
        append_datas: List = [latest_timestamp, datas[1], datas[2]]
        # PM測定値(最高血圧,最低血圧) はnp.NaNを設定
        append_datas.extend([np.NaN, np.NaN])
        # 当日データをDataFrameの末尾に追加する
        df_org.loc[df_size] = append_datas
        return True, df_org
    else:
        return False, None


def rebuildIndex(df_org: DataFrame, s_start_date: str, s_end_date: str) -> Tuple[bool, Optional[DataFrame]]:
    """
    DataFrameのインデックス再構築が必要なら再構築する
    :param df_org: オリジナルのDataFrame
    :param s_start_date: 検索開始日
    :param s_end_date: 最終日 (検索終了日 | 当日)
    :return: 再構築ならTuple[True, 再構築後のDataFrame], それ以外[False, None]
    """
    df_size: int = len(df_org)
    date_range: DatetimeIndex = pd.date_range(s_start_date, s_end_date)
    range_size: int = len(date_range)
    if df_size < range_size:
        # 欠損データ有りの場合はインデックスを振り直す
        result: DataFrame = df_org.reindex(pd.date_range(date_range, name=COL_INDEX))
        return True, result
    else:
        return False, None


def makeColListForPlotting(df: DataFrame) -> Tuple[List[str], np.ndarray, np.ndarray]:
    """
    X軸用ラベルリスト、AM/PM毎の測定値をマージしたnp.ndarrayを生成する
    :param df:
    :return: X軸用ラベルリスト, 最高血圧値ndarray, 最低血圧値ndarray
    """
    x_ticklers: List[str] = []
    press_max_list: List[np.float] = []
    press_min_list: List[np.float] = []
    # 指定期間の日付リスト
    indexes: Series = df.index
    m_max_ser: Series = df[COL_MORNING_MAX]
    m_min_ser: Series = df[COL_MORNING_MIN]
    e_max_ser: Series = df[COL_EVENING_MAX]
    e_min_ser: Series = df[COL_EVENING_MIN]
    for day, mMax, mMin, eMax, eMin in zip(indexes, m_max_ser, m_min_ser, e_max_ser, e_min_ser):
        x_ticklers.append(du.makeDateTextWithJpWeekday(day.strftime(du.FMT_ISO8601), has_month=True))  # AM軸
        x_ticklers.append("")  # PM軸 ※常に空文字
        press_max_list.append(mMax)  # AM
        press_max_list.append(eMax)  # PM
        press_min_list.append(mMin)
        press_min_list.append(eMin)
    # 測定値リストを np.ndarryに変換
    np_press_maxes = np.array(press_max_list)
    np_press_mines = np.array(press_min_list)
    return x_ticklers, np_press_maxes, np_press_mines


def plot(sess: scoped_session,
         email_address: str, end_date: str,
         phone_pix_width: int, phone_pix_height: int, phone_density: float,
         today_data: TodayBloodPress = None,
         user_target_max: int = None,
         user_target_min: int = None,
         suppress_show_over: bool = False,
         logger: logging.Logger = None, is_debug=False
         ) -> Tuple[BloodPressStatistics, Optional[str]]:
    """
    指定された検索条件の睡眠管理データプロット画像のbase64文字列を取得する
    :param sess: SQLAlchemy scoped_session object
    :param email_address:
    :param end_date: 検索終了年月日
    :param phone_pix_width:
    :param phone_pix_height:
    :param phone_density:
    :param today_data: 当日AM血圧測定データ(テーブル未登録データ), default None
    :param user_target_max: ユーザー目標最高血圧値 ※スマホからリクエストで設定される場合がある (端末で設定している場合)
    :param user_target_min: ユーザー目標最低血圧値 ※スマホからリクエストで設定される場合がある (端末で設定している場合)
    :param suppress_show_over: 基準値オーバー時の値を出力しない ※デフォルトFalseで出力する
    :param logger:
    :param is_debug:
    :return: tuple(統計情報, プロット画像のbase64文字列)
    :raise: DatabaseError
    """
    # 目標値設定: 未設定ならデフォルト値設定
    if user_target_max is None:
        user_target_max = TARGET_PRESS_MAX
    if user_target_min is None:
        user_target_min = TARGET_PRESS_MIN

    # 14日前の開始日を求める
    start_date: str = du.add_day_string(end_date, add_days=BEFORE_2WEEK_PERIODS)
    if logger is not None and is_debug:
        logger.debug(f"start_date: {start_date}, end_date: {end_date}")

    dao: BloodPressureDao = BloodPressureDao(
        email_address, start_date, end_date, parse_dates=[COL_INDEX],
        logger=logger, is_debug=is_debug
    )

    # 棒グラフは血圧データのみ ※M/PM測定時刻と脈拍は取得しない
    df_data: DataFrame = dao.execute(sess, SelectColumnsType.PRESSURE_ONLY)
    if logger is not None and is_debug:
        logger.debug(f"df_data.size: {df_data.shape[0]}")
        logger.debug(f"df_data:\n {df_data.head()}")

    # データ件数
    datasize: int = df_data.shape[0]
    if datasize == 0:
        statistics: BloodPressStatistics = BloodPressStatistics(
            am_max_mean=0, am_min_mean=0, pm_max_mean=0, pm_min_mean=0, record_size=0
        )
        return statistics, None

    # 当日データがあれば追加する
    if today_data is not None:
        is_add: bool
        df_added: DataFrame
        is_add, df_added = addTodayData(df_data, today_data, logger=logger, is_debug=is_debug)
        if is_add:
            # 検索終了日を本日データの測定日で更新
            end_date = today_data.measurement_day
            df_data = df_added
            if logger is not None and is_debug:
                logger.debug(f"df_added: {df_data.tail()}")

    # グラフタイトル (指定期間)
    plot_title: str = makeTitleWithMonthRange(start_date, end_date)
    # 測定日列をインデックスに設定
    df_data.index = df_data[COL_INDEX]

    # 統計情報計算
    statistics: BloodPressStatistics = calcBloodPressureStatistics(df_data, logger, is_debug)

    # 再インデックス処理
    has_rebuild: bool
    rebuild_df: DataFrame
    has_rebuild, rebuild_df = rebuildIndex(df_data, start_date, end_date)
    if has_rebuild:
        df_data = rebuild_df
        logger.debug(f"rebuild.df_data.size: {df_data.shape}")
        logger.debug(f"rebuild.df_data:{df_data.tail()}")
        logger.debug(f"rebuild.df_data.index: {df_data.index}")

    # 最終のレコードサイズ
    df_size: int = len(df_data)
    # 血圧測定データは日当たりAM/PMの各測定値があるためマージする
    # 指定期間のプロット用項目(X軸ラベル, 最高血圧, 最低血圧)リスト生成
    x_tick_labels, press_max_values, press_min_values = makeColListForPlotting(df_data)
    # 棒のカラー配列を作成: AM/PM毎にデータ件数分
    bar_colors: List[str] = BAR_COLORS * df_size
    if logger is not None and is_debug:
        logger.debug(f"x_ticks_labels:\n{x_tick_labels}")
        logger.debug(f"press_max_values:\n{press_max_values}")
        logger.debug(f"press_min_values:\n{press_min_values}")
    # 最高血圧棒グラフ用差分
    bar_diff_values: np.ndarray = press_max_values - press_min_values
    if logger is not None and is_debug:
        logger.debug(f"bar_diff_values:\n{bar_diff_values}")

    # 携帯用の描画領域サイズ(ピクセル)をインチに変換
    fig_width_inch, fig_height_inch = pixelToInch(
        phone_pix_width, phone_pix_height, phone_density,
        logger=logger, is_debug=is_debug
    )

    # 描画領域作成
    fig: Figure
    ax: Axes
    fig, ax = plt.subplots(figsize=(fig_width_inch, fig_height_inch), layout='constrained')
    if is_debug:
        logger.debug(f"fig: {fig}, axes: {ax}")
    # Y方向のグリッド線のみ表示
    ax.grid(**AXES_GRID_STYLE)

    # X軸の作成: データ件数 * 2 (AM + PM)
    plot_size: int = df_size * 2
    x_indexes = np.arange(plot_size)
    # 最低血圧値の棒グラフ: 描画領域色(白色)にして見えないようにする
    ax.bar(x_indexes, press_min_values, BAR_WIDTH, color=COLOR_PRESS_MIN)
    # 最大血圧値(最低血圧値との差分): 棒のカラー(AMカラー/PMカラー交互)
    ax.bar(
        x_indexes, bar_diff_values, BAR_WIDTH,
        bottom=press_min_values, color=bar_colors, **BAR_LINE_STYLE
    )
    # 目標血圧 (正常値): 最高血圧
    ax.axhline(y=user_target_max, **TARGET_MAX_STYLE)
    # 目標血圧 (正常値): 最低血圧
    ax.axhline(y=user_target_min, **TARGET_MIN_STYLE)
    ax.set_ylabel(Y_PRESSURE_LABEL)
    ax.set_title(plot_title, fontdict=TITLE_FONT_STYLE)
    # 最大値を+1することにより最大値が表示される
    ax.set_yticks(np.arange(BLOOD_PRESS_MIN, BLOOD_PRESS_MAX + 1, Y_LIM_MARGIN))
    # 軸ラベルのスタイルを設定する
    # https://matplotlib.org/stable/api/_as_gen/matplotlib.axes.Axes.tick_params.html
    #  matplotlib.axes.Axes.tick_params
    # https://matplotlib.org/stable/gallery/subplots_axes_and_figures/align_labels_demo.html
    #  #sphx-glr-gallery-subplots-axes-and-figures-align-labels-demo-py
    #  Aligning Labels
    # Y軸方向のラベルサイズを設定
    ax.tick_params(**Y_TICK_LABEL_PARAMS)
    ax.set_ylim(BLOOD_PRESS_MIN, BLOOD_PRESS_MAX)
    # X軸方向は下記でフォントサイズを設定
    ax.set_xticks(np.arange(plot_size), x_tick_labels, **X_TICKS_STYLE)
    ax.set_xlim(X_LIM_MARGIN, (plot_size + X_LIM_MARGIN))
    if not suppress_show_over:
        # 基準値をオーバーした値を出力
        # 最高血圧: 基準値を超えた値のみを上端に表示
        drawTextOverValue(ax, press_max_values, user_target_max)
        # 最低血圧: 基準値を超えた値のみを下端に表示
        drawTextOverValue(ax, press_min_values, user_target_min, draw_pos=DrawPosition.TOP)
    # 棒グラフ(AM/PM毎のカラー)の凡例を描画
    ax.legend(handles=[LEGEND_AM, LEGEND_PM], **LEGEND_STYLE)
    # 脈拍はプロットしない

    # 画像をバイトストリームに溜め込みそれをbase64エンコードしてレスポンスとして返す
    buf = BytesIO()
    fig.savefig(buf, format="png", bbox_inches="tight")
    data = base64.b64encode(buf.getbuffer()).decode("ascii")
    if logger is not None and is_debug:
        logger.debug(f"data.len: {len(data)}")
    img_src = f"data:image/png;base64,{data}"
    # 統計情報と画像のTupleを返却
    return statistics, img_src

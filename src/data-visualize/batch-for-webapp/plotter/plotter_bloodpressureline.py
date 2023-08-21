import logging
from datetime import datetime
from typing import Dict, List, Optional, Tuple

from matplotlib.axes import Axes
from matplotlib.figure import Figure

import numpy as np
import pandas as pd
from pandas.core.frame import DataFrame

from sqlalchemy.orm import scoped_session

from plotter.common.statistics import BloodPressStatistics
from plotter.common.todaydata import TodayBloodPress
from plotter.pandas_common import rebuildIndex
from plotter.plotter_bloodpressure import calcBloodPressureStatistics, decideTargetValues
from plotter.plotter_common import (
    DrawPosition, makeTitleWithMonthRange, pixelToInch, getHtmlImgSrcFromFigure
)
from plotter.plotparameter import BloodPressUserTarget, PhoneImageInfo
from plotter.dao import (
    COL_INDEX, COL_MORNING_MAX, COL_MORNING_MIN, COL_MORNING_PULSE,
    COL_EVENING_MAX, COL_EVENING_MIN, COL_EVENING_PULSE,
    BloodPressureDao, SelectColumnsType
)
from plotter.plotter_bloodpressure import (
    BLOOD_PRESS_MAX, BLOOD_PRESS_MIN, TARGET_PRESS_MAX, TARGET_PRESS_MIN, 
    PULSE_MAX, PULSE_MIN, X_AXIS_ROTATION
)
import util.dataclass_util as dcu
import util.date_util as du
from util.date_util import DateCompEnum

"""
血圧測定データを折れ線グラフとしてプロットする
[期間]
 (1) 過去2週間前 + 当日AM血圧測定データ(テーブル未登録): 14+1=15件数
 (2) 月間: 開始測定日, 終了測定日
"""

# X軸の日付間隔: 4日間隔 (1,5,...,25,末日)
PLOT_DAY_INTERVAL: int = 4
# 1番目: '年(前ゼロなし)/月(前ゼロあり)'
FMT_DAY_TICK_LABEL: str = '{}/{:#02d}'
# PM測定データ出力時のX軸加算値 ※1日の半分 (1日は1.0)
X_DIFF_PM: float = 0.5
# ラベル文字列定数定義
Y_LABEL_PRESS: str = "血圧値 (mmHg)"
Y_LABEL_PULSE: str = "脈拍 (回/分)"
LABEL_PRESS_MAX: str = "最高血圧"
LABEL_PRESS_MIN: str = "最低血圧"
LABEL_AM: str = '【午前】'
LABEL_PM: str = '【午後】'
# 凡例文字列
LEGEND_AM_MAX: str = LABEL_AM + LABEL_PRESS_MAX
LEGEND_PM_MAX: str = LABEL_PM + LABEL_PRESS_MAX
LEGEND_AM_MIN: str = LABEL_AM + LABEL_PRESS_MIN
LEGEND_PM_MIN: str = LABEL_PM + LABEL_PRESS_MIN

# X軸の左右マージン ※データ1件を1
# X軸のマージン
X_LIM_LEFT_MARGIN: float = -0.5
# 2週過去 + 当日データ(AMデータのみ)
X_LIM_2W_RIGHT_MARGIN: float = 0.5
# 月間測定データの場合:　PM測定値が半日分(0.5) 右にプロットされるので +0.2
X_LIM_YM_RIGHT_MARGIN: float = X_LIM_2W_RIGHT_MARGIN + 0.2
# Y軸ラベル表示マージン
Y_LIM_MARGIN: float = 10.0
# 目標値以上の数値出力: 縦方向のマージン
DRAW_POS_MARGIN: float = 1.5

# プロットスタイル
# 線色
# '#1f77b4' near blue
COLOR_AM_MAX: str = 'C0'
# '#ff7f03' near orange
COLOR_PM_MAX: str = 'C1'
# '#2ca02c' near green
COLOR_AM_MIN: str = 'C2'
# '#e377c2' near pink
COLOR_PM_MIN: str = 'C6'
PLOT_BASE_STYLE: Dict = {'marker': '.'}
PLOT_AM_MAX_STYLE: Dict = {'color': COLOR_AM_MAX, **PLOT_BASE_STYLE}
PLOT_AM_MIN_STYLE: Dict = {'color': COLOR_AM_MIN, **PLOT_BASE_STYLE}
PLOT_AM_PULSE_STYLE: Dict = {'color': COLOR_AM_MAX, **PLOT_BASE_STYLE}
PLOT_PM_MAX_STYLE: Dict = {'color': COLOR_PM_MAX, **PLOT_BASE_STYLE}
PLOT_PM_MIN_STYLE: Dict = {'color': COLOR_PM_MIN, **PLOT_BASE_STYLE}
PLOT_PM_PULSE_STYLE: Dict = {'color': COLOR_PM_MAX, **PLOT_BASE_STYLE}
# X軸のラベル(日+曜日)スタイル
X_TICKS_STYLE: Dict = {'fontsize': 10, 'fontweight': 'normal', 'rotation': X_AXIS_ROTATION}
Y_TICKS_STYLE: Dict = {'fontsize': 8.5}
# 血圧の基準線の線スタイル
TARGET_LINE_STYLE: Dict = {'linestyle': 'dashdot', 'linewidth': 1.0}
TARGET_MAX_STYLE: Dict = {'color': 'magenta', **TARGET_LINE_STYLE}
TARGET_MIN_STYLE: Dict = {'color': 'crimson', **TARGET_LINE_STYLE}
# 基準値を超えた値の表示文字列スタイル
DRAW_TEXT_BASE_STYLE: Dict = {'color': 'red', 'fontsize': 7, 'fontweight': 'normal',
                              'horizontalalignment': 'center'}
#  (1) 縦揃え: 下段 ※棒の上
DRAW_TEXT_STYLE: Dict = {**DRAW_TEXT_BASE_STYLE, 'verticalalignment': 'bottom'}
#  (2) 縦揃え: 上段 ※棒の下
DRAW_TEXT_TOP_STYLE: Dict = {**DRAW_TEXT_BASE_STYLE, 'verticalalignment': 'top'}
# 描画領域のグリッド線スタイル: Y方向のグリッド線のみ表示
AXES_GRID_STYLE: Dict = {'axis': 'y', 'linestyle': 'dashed', 'linewidth': 0.7,
                         'alpha': 0.75}

# タイトルスタイル
TITLE_STYLE: Dict = {'fontsize': 11, 'fontweight': 'normal'}
# 凡例スタイル
LEGEND_STYLE: Dict = {'loc': 'upper right', 'fontsize': 8}


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
        # 取得項目はAM測定値(最高血圧,最低血圧,脈拍), PM測定値(最高血圧,最低血圧,脈拍)のみ ※AM/PM測定時刻なし
        # 測定日
        append_datas: List = [latest_timestamp]
        # 当日データリストの先頭(当日データの測定日)を除いたデータのリストを追加する
        values_without_day: List = [d for d in datas[1:]]
        append_datas.extend(values_without_day)
        # PM測定値(最高血圧,最低血圧,脈拍) はnp.NaNを設定
        append_datas.extend([np.NaN]*3)
        # 当日データをDataFrameの末尾に追加する
        df_org.loc[df_size] = append_datas
        return True, df_org
    else:
        return False, None


def makeDateTicks(s_start_date: str, s_end_date: str, is_yearmonth: bool = True,
                  logger: logging.Logger = None, is_debug: bool = False
                  ) -> Tuple[List[int], List[str]]:
    """
    X軸整数リストと日付ラベルリストを生成する
    :param s_start_date: 検索開始日
    :param s_end_date: 検索終了日 ※当日データがある場合は当日測定日
    :param is_yearmonth: True=月間データ, デフォルトFalse=2週過去
    :param logger:
    :param is_debug:
    :return: Tuple(X軸整数リスト,軸ラベルリスト)
    """
    # 日付を生成する
    dt_range: pd.DatetimeIndex = pd.date_range(start=s_start_date, end=s_end_date)
    # https://pandas.pydata.org/docs/reference/api/pandas.Series.dt.to_period.html
    period_idx: pd.PeriodIndex = dt_range.to_period()
    if logger is not None and is_debug:
        logger.debug(f"period_idx: {period_idx}")
    period_idx_size: int = len(period_idx)
    ticks: List[int]
    ticks_labels: List[str]
    if is_yearmonth:
        # 月間データの場合: PLOT_DAY_INTERVAL 間隔で間引く
        if period_idx_size < 30:
            # 2月28日 or 29日: [1,5,9,...,25]
            ticks_range = range(0, 26, PLOT_DAY_INTERVAL)
        else:  # 30日, 31日: [1,5,9,...,25,29]
            ticks_range = range(0, 30, PLOT_DAY_INTERVAL)
        # 先頭(1日)から月を取得
        start_idx: pd.Period = period_idx[0]
        val_month: int = start_idx.month
        # 末日
        end_idx: pd.Period = period_idx[period_idx_size - 1]
        ticks = [idx for idx in ticks_range]
        # X軸リストに末日-1を追加 (index 0 start)
        ticks.append(end_idx.day - 1)
        # X軸ラベル: '月 /日[前ゼロパディング)'
        ticks_labels = [FMT_DAY_TICK_LABEL.format(val_month, day+1) for day in ticks_range]
        # 末日の日付を追加
        ticks_labels.append(FMT_DAY_TICK_LABEL.format(end_idx.month, end_idx.day))
    else:
        # 当日データを含むデータの場合: 指定期間: 2週+当日 ※指定範囲の全リスト
        ticks_range: range = range(0, period_idx_size)
        ticks = [idx for idx in ticks_range]
        # https://pandas.pydata.org/docs/reference/api/pandas.PeriodIndex.html#pandas.PeriodIndex
        # pandas.PeriodIndex
        #   month: int, array, or Series, default None
        #   day: int, array, or Series, default None
        ticks_labels: List[str] = [FMT_DAY_TICK_LABEL.format(dt.month, dt.day) for dt in period_idx]
    return ticks, ticks_labels


def makeYTicks(val_min: int, val_max: int, val_interval: int) -> Tuple[range, List[str]]:
    """
    Y軸レンジとY軸ラベルリストを生成する
    :param val_min: 最小値 (整数)
    :param val_max: 最大値 (整数)
    :param val_interval: 間隔 (整数)
    :return: Tuple[Y軸リスト, Y軸ラベルリスト]
    """
    # Y軸レンジ
    tick_range: range = range(val_min, val_max + 1, val_interval)
    # Y軸ラベル
    tick_labels: List[str] = [str(val) for val in tick_range]
    return tick_range, tick_labels


def drawTextOverValue(axes: Axes, values: np.ndarray, std_value: float,
                      measure_pm: bool = False,
                      first_1_index: bool = False,
                      draw_pos: DrawPosition = DrawPosition.BOTTOM,
                      draw_pos_margin: float = DRAW_POS_MARGIN) -> None:
    """1
    基準値を超えた値を対応グラフの上部に表示する\n
    ※ PM測定値の出力位置をAM措定値+1/2分右側にずらす
    :param axes: プロット領域
    :param values: 値のnp.ndarray
    :param measure_pm: PMの測定値がTrueなら出力位置をずらす
    :param first_1_index: インデックスが1で開始するか
    :param std_value: 基準値
    :param draw_pos: 描画位置 (BOTTOM|TOP)
    :param draw_pos_margin:
    """
    add_idx_val: float
    if first_1_index:
        add_idx_val = 1
    else:
        add_idx_val = 0
    x_diff: float
    # 午後測定値なら出力位置を右側にずらす
    if measure_pm:
        x_diff = X_DIFF_PM
    else:
        x_diff = 0.
    for x_idx, val in enumerate(values):
        if not pd.isnull(val) and val > std_value:
            x_pos: float = x_idx + add_idx_val + x_diff
            draw_margin: float
            draw_style: Dict
            if draw_pos == DrawPosition.BOTTOM:
                draw_margin = val + draw_pos_margin
                draw_style = DRAW_TEXT_STYLE
            else:
                draw_margin = val - draw_pos_margin
                draw_style = DRAW_TEXT_TOP_STYLE
            # 数値を小数点なして出力する ※整数でも、浮動小数点数でもOK
            # AM/PM測定値がないデータ(NaN)が含まれる列(Series)は全てfloatになる
            axes.text(x_pos, draw_margin, f"{val:.0f}", **draw_style)


def plot(sess: scoped_session,
         email_address: str, start_date: str, end_date: str,
         phone_image_info: PhoneImageInfo,
         is_yearmonth: bool = True,
         today_data: TodayBloodPress = None,
         user_target: BloodPressUserTarget = None,
         suppress_show_over: bool = False,
         logger: logging.Logger = None, is_debug=False) -> Tuple[BloodPressStatistics, Optional[str]]:
    """
    指定された検索条件の睡眠管理データプロット画像のbase64文字列を取得する
    :param sess: SQLAlchemy scoped_session object
    :param email_address:
    :param start_date: 検索開始年月日
    :param end_date: 検索終了年月日
    :param phone_image_info: 携帯端末の画像領域サイズ情報
    :param is_yearmonth: デフォルトTrue=月間データ, False=2週過去
    :param today_data: 当日AM血圧測定データ(テーブル未登録データ), default None (月間), 2週過去の場合はオプション
    :param user_target: 血圧値のユーザー目標値 ※スマホからリクエストで設定される場合がある (端末で設定している場合)
    :param suppress_show_over: 基準値オーバー時の値を出力しない ※デフォルトFalseで出力する
    :param logger:
    :param is_debug:
    :return: tuple(統計情報, プロット画像のbase64文字列)
    :raise: DatabaseError
    """
    # 目標値設定: 未設定ならデフォルト値設定
    target_max: float
    target_min: float
    target_max, target_min = decideTargetValues(
        TARGET_PRESS_MAX, TARGET_PRESS_MIN, user_target=user_target
    )
    if logger is not None and is_debug:
        logger.debug(f"target_max: {target_max}, target_min: {target_min}")

    dao: BloodPressureDao = BloodPressureDao(
        email_address, start_date, end_date, parse_dates=[COL_INDEX],
        logger=logger, is_debug=is_debug
    )
    # DAO実行: 測定値(血圧と脈拍)のみでAM/PM測定時刻は取得しない
    df_data: DataFrame = dao.execute(sess, SelectColumnsType.PRESSURE_PULSE)
    if is_debug:
        logger.debug(f"df_data.size: {df_data.shape[0]}")
        logger.debug(f"df_data:\n {df_data.head()}")

    # データ件数
    datasize: int = df_data.shape[0]
    if datasize == 0:
        statistics: BloodPressStatistics = BloodPressStatistics(
            am_max_mean=0, am_min_mean=0, pm_max_mean=0, pm_min_mean=0, record_size=0
        )
        return statistics, None

    # 当日データがあればdf_dataに追加する
    if today_data is not None:
        is_add: bool
        df_added: DataFrame
        is_add, df_added = addTodayData(df_data, today_data, logger=logger, is_debug=is_debug)
        if is_add:
            # 検索終了日を本日データの測定日で更新
            end_date = today_data.measurement_day
            df_data = df_added
            if is_debug:
                logger.debug(f"df_added: {df_data.tail()}")

    # グラフタイトル
    plot_title: str = makeTitleWithMonthRange(start_date, end_date)
    # 測定日列をインデックスに設定
    df_data.index = df_data[COL_INDEX]

    # 統計情報計算
    statistics: BloodPressStatistics = calcBloodPressureStatistics(df_data, logger, is_debug)

    # 再インデックス処理
    has_rebuild: bool
    rebuild_df: DataFrame
    has_rebuild, rebuild_df = rebuildIndex(
        df_data, index_name=COL_INDEX, s_start_date=start_date, s_end_date=end_date
    )
    if has_rebuild:
        df_data = rebuild_df
        logger.debug(f"rebuild.df_data.size: {df_data.shape}")
        logger.debug(f"rebuild.df_data:{df_data.tail()}")
        logger.debug(f"rebuild.df_data.index: {df_data.index}")

    # 携帯用の描画領域サイズ(ピクセル)をインチに変換
    fig_width_inch, fig_height_inch = pixelToInch(
        phone_image_info.px_width, phone_image_info.px_height, phone_image_info.density,
        logger=logger, is_debug=is_debug
    )

    # 描画領域作成
    fig: Figure = Figure(figsize=(fig_width_inch, fig_height_inch), constrained_layout=True)
    #  (1)上段描画領域: 血圧測定値
    ax_press: Axes
    #  (2)下段描画領域: 脈拍測定値
    ax_pulse: Axes
    (ax_press, ax_pulse) = fig.subplots(
        2, 1, sharex=True, gridspec_kw={'height_ratios': [7, 3]}
    )
    if is_debug:
        logger.debug(f"fig: {fig}, ax_press: {ax_press}, ax_pulse: {ax_pulse}")
    # Y方向のグリッド線のみ表示
    ax_press.grid(**AXES_GRID_STYLE)
    ax_pulse.grid(**AXES_GRID_STYLE)

    # X軸のインデックス生成
    df_size: int = len(df_data)
    x_indexes = np.arange(df_size)
    if is_debug:
        logger.debug(f"x_indexes: {x_indexes}")

    # 午後測定データは1日の半分(0.5)右側にずらしてプロットする
    x_pm_indexes = x_indexes + X_DIFF_PM
    # Y軸レンジとY軸ラベルリスト
    y_tick_range, y_tick_labels = makeYTicks(int(BLOOD_PRESS_MIN), int(BLOOD_PRESS_MAX), int(Y_LIM_MARGIN))
    # タイトル
    ax_press.set_title(plot_title, **TITLE_STYLE)
    # AM 最高血圧
    ax_press.plot(x_indexes, df_data[COL_MORNING_MAX], label=LEGEND_AM_MAX, **PLOT_AM_MAX_STYLE)
    # AM 最低血圧
    ax_press.plot(x_indexes, df_data[COL_MORNING_MIN], label=LEGEND_AM_MIN, **PLOT_AM_MIN_STYLE)
    # PM 最高血圧
    ax_press.plot(x_pm_indexes, df_data[COL_EVENING_MAX], label=LEGEND_PM_MAX, **PLOT_PM_MAX_STYLE)
    # PM 最低血圧
    ax_press.plot(x_pm_indexes, df_data[COL_EVENING_MIN], label=LEGEND_PM_MIN, **PLOT_PM_MIN_STYLE)
    if not suppress_show_over:
        # 目標値をオーバーした値を出力
        # AM測定値
        drawTextOverValue(ax_press, df_data[COL_MORNING_MAX], target_max)
        drawTextOverValue(ax_press, df_data[COL_MORNING_MIN], target_min)
        # PM測定値
        drawTextOverValue(ax_press, df_data[COL_EVENING_MAX], target_max, measure_pm=True)
        drawTextOverValue(ax_press, df_data[COL_EVENING_MIN], target_min, measure_pm=True)

    # 目標血圧 (正常値): 最高血圧
    # https://matplotlib.org/stable/api/_as_gen/matplotlib.pyplot.hlines.html
    # ax_press.hlines(user_target_max, x_index_min, x_index_max, **TARGET_MAX_STYLE)
    # https://matplotlib.org/stable/api/_as_gen/matplotlib.axes.Axes.axhline.html
    ax_press.axhline(y=target_max, **TARGET_MAX_STYLE)
    # 目標血圧 (正常値): 最低血圧
    # ax_press.hlines(user_target_min, x_index_min, x_index_max, **TARGET_MIN_STYLE)
    ax_press.axhline(y=target_min, **TARGET_MIN_STYLE)
    # Y軸設定
    ax_press.set_yticks(y_tick_range, y_tick_labels, **Y_TICKS_STYLE)
    ax_press.set_ylim(BLOOD_PRESS_MIN, BLOOD_PRESS_MAX)
    ax_press.set_ylabel(Y_LABEL_PRESS)
    # 凡例
    ax_press.legend(**LEGEND_STYLE)

    # 脈拍プロット領域
    # X軸ラベル: '年/月'
    x_ticks, x_ticks_labels = makeDateTicks(
        start_date, end_date, is_yearmonth=is_yearmonth, logger=logger, is_debug=is_debug
    )
    if is_debug:
        logger.debug(f"x_ticks: {x_ticks}")
        logger.debug(f"x_ticks_labels: {x_ticks_labels}")
    # Y軸レンジとY軸ラベルリスト
    y_tick_range, y_tick_labels = makeYTicks(int(PULSE_MIN), int(PULSE_MAX), int(Y_LIM_MARGIN))
    # 脈拍プロット
    ax_pulse.plot(x_indexes, df_data[COL_MORNING_PULSE], label=LABEL_AM, **PLOT_AM_PULSE_STYLE)
    ax_pulse.plot(x_pm_indexes, df_data[COL_EVENING_PULSE], label=LABEL_PM, **PLOT_PM_PULSE_STYLE)
    # X軸設定
    ax_pulse.set_xticks(x_ticks, x_ticks_labels, **X_TICKS_STYLE)
    # X軸: 最大値: (件数-1) + X右側マージン
    ax_pulse.set_xlim(X_LIM_LEFT_MARGIN, (df_size - 1) + X_LIM_YM_RIGHT_MARGIN)
    # ax_pulse.set_xlim(x_index_min, x_index_max)
    # Y軸設定
    ax_pulse.set_ylim(PULSE_MIN, PULSE_MAX)
    ax_pulse.set_yticks(y_tick_range, y_tick_labels, **Y_TICKS_STYLE)
    ax_pulse.set_ylabel(Y_LABEL_PULSE)
    ax_pulse.legend(**LEGEND_STYLE)

    # HTML用のimgSrc(base64エンコード済み)を取得
    img_src: str = getHtmlImgSrcFromFigure(fig)
    # 統計情報と画像のTupleを返却
    return statistics, img_src

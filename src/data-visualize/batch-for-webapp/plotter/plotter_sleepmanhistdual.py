import logging
from typing import Dict, List, Optional, Tuple

from matplotlib.axes import Axes
from matplotlib.figure import Figure
from matplotlib.patches import Rectangle, Patch

import numpy as np
import pandas as pd
from pandas.core.frame import DataFrame, Series
from pandas.core.groupby import DataFrameGroupBy

from sqlalchemy.orm import scoped_session

from plotter.common.funcs import toMinute
from plotter.common.statistics import SleepManStatistics
from plotter.common.sleepmanutil import calcBedTimeToMinute, minuteToFormatTime
from plotter.plotter_common import (
    makeTitleWithMonthRange, pixelToInch, getHtmlImgSrcFromFigure
)
from plotter.plotparameter import PhoneImageInfo
from plotter.dao import (
    COL_INDEX, COL_WAKEUP, COL_SLEEP_SCORE, COL_SLEEPING, COL_DEEP_SLEEPING, COL_TOILET_VISITS,
    SleepManDao
)
import util.date_util as du

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

# 集計する睡眠スコアの基準値
# df['sleep_score'] >= GOOD_SLEEP_SCORE
GOOD_SLEEP_SCORE: int = 80
# df['sleep_score'] < WARN_SLEEP_SCORE
WARN_SLEEP_SCORE: int = 75
# グルービング名
GROUP_BEDTIME: str = 'bed_time'
GROUP_DEEP_SLEEPING: str = 'deep_sleeping'
GROUP_SLEEPING: str = 'sleeping'
GROUP_TOILET_VISITS: str = 'toilet_visits'

# プロット領域比
GRID_SPEC_HEIGHT_RATIO: List[int] = [20, 28, 24, 28]
# Y軸ラベル名 ※全領域共通
Y_LABEL_HIST: str = '度数 (回)'
# 1段目: 夜間トイレ回数
X_LABEL_TOILET_VISITS: str = '夜間トイレ回数'
TOILET_VISITS_MIN: int = 0
TOILET_VISITS_MAX: int = 7
STEP_TOILET_VISITS: int = 1
# 2段目: 就寝時刻: (前日) 20:00 〜 (当日) 1:00
X_LABEL_BED_TIME: str = '就寝時刻 (前日)'
BED_TIME_MIN: int = -240  # 前日 20:00
BED_TIME_MAX: int = 60  # 当日 01:00
STEP_BED_TIME: int = 30
# 3段目: 深い睡眠: 0〜120
X_LABEL_DEEP_SLEEPING: str = '深い睡眠時間 (分)'
DEEP_SLEEPING_MIN: int = 0
DEEP_SLEEPING_MAX: int = 120
STEP_DEEP_SLEEPING: int = 10
# 4段目: 睡眠時間: 4:00 〜 10:00
Y_LABEL_SLEEPING: str = '睡眠時間 (時:分)'
SLEEPING_MIN: int = 240  # 4:00
SLEEPING_MAX: int = 600  # 10:00
STEP_SLEEPING: int = 30

# 棒グラフの幅比率
BAR_WIDTH_RATIO: float = 0.8
# 棒カラー
BAR_COLOR_GOOD: str = 'steelblue'
BAR_COLOR_WARN: str = 'orangered'
# 描画領域のグリッド線スタイル: Y方向のグリッド線のみ表示
AXES_GRID_STYLE: Dict = {'axis': 'y', 'linestyle': 'dashed', 'linewidth': 0.7,
                         'alpha': 0.75}
# X軸のラベルスタイル
X_TICKS_STYLE: Dict = {'fontsize': 8, 'fontweight': 'heavy', 'rotation': 90}
# プロット領域のラベルスタイル
LABEL_STYLE: Dict = {'fontsize': 9}
# タイトルスタイル
TITLE_STYLE: Dict = {'fontsize': 10}
# 凡例スタイル
LEGEND_STYLE: Dict = {'fontsize': 9}
# 凡例ラベル
LEGEND_GOOD: str = f'睡眠スコア >= {GOOD_SLEEP_SCORE}'
LEGEND_WARN: str = f'睡眠スコア < {WARN_SLEEP_SCORE}'
# カスタム凡例
# https://matplotlib.org/stable/tutorials/intermediate/legend_guide.html
# Legend guide
WARN_LEGEND: Patch = Patch(color=BAR_COLOR_WARN, label=LEGEND_WARN)
GOOD_LEGEND: Patch = Patch(color=BAR_COLOR_GOOD, label=LEGEND_GOOD)


def makeBedtimeTicksLabel(ticks_range: range) -> List[str]:
    """
    就寝時刻用のX軸ラベルを生成する
      (1) 分が 0なら "00:00"
      (2) 分が正 (当日) ならそのまま変換関数に設定
      (3) 分が負 (前日) なら 24時間プラスした値を変換関数に設定
    :param ticks_range: 就寝時刻用のrangeオブジェクト
    :return: 就寝時刻用のX軸ラベル
    """
    result: List[str] = []
    for minutes in ticks_range:
        if minutes == 0:
            result.append("00:00")
        elif minutes > 0:
            # 当日: 0時以降
            result.append(minuteToFormatTime(minutes))
        else:
            # 前日: 24時プラス
            result.append(minuteToFormatTime(1440 + minutes))
    return result


def makeGroupingObjectsForHistogram(df_orig: DataFrame) -> Dict[str, Series]:
    """
    与えられたDataFrameからヒストグラムプロット用のグルービングオブジェクトを取得する
    :param df_orig: SQLから生成されたデータフレームを指定条件でフィルタリングされたデータフレーム
    :return: グルービングオブジェクトの辞書
    """
    # 就寝時刻の計算(分) ※起床時刻の形式("%H:%M")
    day_idx: pd.DatetimeIndex = df_orig.index
    day_array: np.ndarray = day_idx.to_pydatetime()
    bed_times: List[Optional[int]] = [
        calcBedTimeToMinute(
            day.strftime(du.FMT_ISO8601), wakeup, sleeping) for day, wakeup, sleeping in zip(
            day_array, df_orig[COL_WAKEUP], df_orig[COL_SLEEPING]
        )
    ]
    df_orig[GROUP_BEDTIME] = bed_times
    # 睡眠時間("%H:%M"): 分(整数)に変換
    df_orig[COL_SLEEPING] = df_orig[COL_SLEEPING].apply(toMinute)
    # 深い睡眠("%H:%M"): 分(整数)に変換
    df_orig[COL_DEEP_SLEEPING] = df_orig[COL_DEEP_SLEEPING].apply(toMinute)
    # グループオブジェクト
    # 就寝時刻
    ranges = range(BED_TIME_MIN, BED_TIME_MAX + 1, STEP_BED_TIME)
    grouped_bedtime: DataFrameGroupBy = df_orig.groupby(
        pd.cut(df_orig[GROUP_BEDTIME], ranges, right=False))
    # 深い睡眠
    ranges = range(DEEP_SLEEPING_MIN, DEEP_SLEEPING_MAX + 1, STEP_DEEP_SLEEPING)
    grouped_deep_sleeping: DataFrameGroupBy = df_orig.groupby(
        pd.cut(df_orig[COL_DEEP_SLEEPING], ranges, right=False))
    # 睡眠時間
    ranges = range(SLEEPING_MIN, SLEEPING_MAX + 1, STEP_SLEEPING)
    grouped_sleeping: DataFrameGroupBy = df_orig.groupby(
        pd.cut(df_orig[COL_SLEEPING], ranges, right=False))
    # 夜間トイレ回数
    ranges = range(TOILET_VISITS_MIN, TOILET_VISITS_MAX + 1, STEP_TOILET_VISITS)
    grouped_visits: DataFrameGroupBy = df_orig.groupby(
        pd.cut(df_orig[COL_TOILET_VISITS], ranges, right=False))

    # グルービングしたそれぞれの度数の辞書オブジェクト
    result: Dict[str, Series] = {
        GROUP_BEDTIME: grouped_bedtime[COL_SLEEP_SCORE].count(),
        GROUP_DEEP_SLEEPING: grouped_deep_sleeping[COL_SLEEP_SCORE].count(),
        GROUP_SLEEPING: grouped_sleeping[COL_SLEEP_SCORE].count(),
        GROUP_TOILET_VISITS: grouped_visits[COL_SLEEP_SCORE].count()
    }
    return result


def _makeBar(x_start: float, y_bottom: float, width: float, height: float,
             facecolor: str = 'blue', alpha: float = 1.,
             edgecolor: Optional[str] = None) -> Rectangle:
    """
    棒グラフの矩形を生成する
    :param x_start:  X軸左端位置
    :param y_bottom: Y軸下端位置
    :param width: 棒幅
    :param facecolor: 背景色
    :param alpha: アルファ値
    :param edgecolor: 矩形の線色
    :return: 矩形オブジェクト
    """
    rect: Rectangle = Rectangle(
        xy=(x_start, y_bottom), width=width, height=height,
        facecolor=facecolor, edgecolor=edgecolor, alpha=alpha
    )
    return rect


def _plotBedtimeBar(ax: Axes, grp_left: Series, grp_right: Series) -> None:
    """
    就寝時刻の度数をプロット (高さに対応する長方形を描画)
    :param ax: プロット領域
    :param grp_left: 左側描画用グルービングオブジェクト
    :param grp_right: 右側描画用グルービングオブジェクト
    """
    # 度数に対応する矩形を領域に追加する
    hist_max: np.int64 = np.max([grp_left.values.max(), grp_right.values.max()])
    # X軸初期位置: X軸開始位置 (2つの棒の中心) - Xステップ
    half_step: float = 1.0 * STEP_BED_TIME / 2.
    half_width: float = (BAR_WIDTH_RATIO * STEP_BED_TIME) / 2.
    x_center: float = BED_TIME_MIN - half_step
    x_start: float
    for left_hist, right_hist in zip(grp_left.values, grp_right.values):
        x_center += STEP_BED_TIME
        # 左側の棒: 中心から左側に半幅が開始点
        if left_hist > 0:
            x_start = x_center - half_width
            bar: Rectangle = _makeBar(x_start, 0, half_width, left_hist, facecolor=BAR_COLOR_WARN)
            ax.add_patch(bar)
        # 右側の棒: 中心が開始点
        if right_hist > 0:
            x_start = x_center
            bar: Rectangle = _makeBar(x_start, 0, half_width, right_hist, facecolor=BAR_COLOR_GOOD)
            ax.add_patch(bar)
    # Y軸設定
    ax.set_ylim(0, hist_max + 1)
    ax.set_ylabel(Y_LABEL_HIST, **LABEL_STYLE)
    # X軸範囲
    ax.set_xlim(BED_TIME_MIN, BED_TIME_MAX)
    # X軸ラベルは時刻文字列
    x_range: range = range(BED_TIME_MIN, BED_TIME_MAX + 1, STEP_BED_TIME)
    time_ticks: List[str] = makeBedtimeTicksLabel(x_range)
    ax.set_xticks(x_range, labels=time_ticks, **X_TICKS_STYLE)
    ax.set_xlabel(X_LABEL_BED_TIME, **LABEL_STYLE)


def _plotDeepSleepingBar(ax: Axes, grp_left: Series, grp_right: Series) -> None:
    """
    深い睡眠の度数をプロット (高さに対応する長方形を描画)
    :param ax: 深い睡眠度数プロット領域
    :param grp_left: 左側描画用グルービングオブジェクト
    :param grp_right: 右側描画用グルービングオブジェクト
    """
    hist_max: np.int64 = np.max([grp_left.values.max(), grp_right.values.max()])
    half_step: float = 1.0 * STEP_DEEP_SLEEPING / 2.
    half_width: float = (BAR_WIDTH_RATIO * STEP_DEEP_SLEEPING) / 2.
    x_center: float = DEEP_SLEEPING_MIN - half_step
    x_start: float
    for left_hist, right_hist in zip(grp_left.values, grp_right.values):
        x_center += STEP_DEEP_SLEEPING
        # 左側の棒: 中心から左側に半幅が開始点
        if left_hist > 0:
            x_start = x_center - half_width
            bar: Rectangle = _makeBar(x_start, 0, half_width, left_hist, facecolor=BAR_COLOR_WARN)
            ax.add_patch(bar)
        # 右側の棒: 中心が開始点
        if right_hist > 0:
            x_start = x_center
            bar: Rectangle = _makeBar(x_start, 0, half_width, right_hist, facecolor=BAR_COLOR_GOOD)
            ax.add_patch(bar)
    # Y軸設定
    ax.set_ylim(0, hist_max + 1)
    ax.set_ylabel(Y_LABEL_HIST, **LABEL_STYLE)
    # X軸範囲
    ax.set_xlim(DEEP_SLEEPING_MIN, DEEP_SLEEPING_MAX)
    # X軸の単位は分
    x_indexes: range = range(DEEP_SLEEPING_MIN, DEEP_SLEEPING_MAX + 1, STEP_DEEP_SLEEPING)
    ax.set_xticks(x_indexes, labels=map(str, x_indexes), **X_TICKS_STYLE)
    ax.set_xlabel(X_LABEL_DEEP_SLEEPING, **LABEL_STYLE)


def _plotSleepingnBar(ax: Axes, grp_left: Series, grp_right: Series) -> None:
    """
    睡眠時間の度数をプロット (高さに対応する長方形を描画)
    :param ax: プロット領域
    :param grp_left: 左側描画用グルービングオブジェクト
    :param grp_right: 右側描画用グルービングオブジェクト
    """
    hist_max: np.int64 = np.max([grp_left.values.max(), grp_right.values.max()])
    half_step: float = 1.0 * STEP_SLEEPING / 2.
    half_width: float = (BAR_WIDTH_RATIO * STEP_SLEEPING) / 2.
    x_center: float = SLEEPING_MIN - half_step
    x_start: float
    for left_hist, right_hist in zip(grp_left.values, grp_right.values):
        x_center += STEP_SLEEPING
        # 左側の棒: 中心から左側に半幅が開始点
        if left_hist > 0:
            x_start = x_center - half_width
            bar: Rectangle = _makeBar(x_start, 0, half_width, left_hist, facecolor=BAR_COLOR_WARN)
            ax.add_patch(bar)
        # 右側の棒: 中心が開始点
        if right_hist > 0:
            x_start = x_center
            bar: Rectangle = _makeBar(x_start, 0, half_width, right_hist, facecolor=BAR_COLOR_GOOD)
            ax.add_patch(bar)
    # Y軸設定
    ax.set_ylim(0, hist_max + 1)
    ax.set_ylabel(Y_LABEL_HIST, **LABEL_STYLE)
    # X軸範囲
    ax.set_xlim(SLEEPING_MIN, SLEEPING_MAX)
    # X軸ラベルは時刻文字列
    x_indexes: iter = range(SLEEPING_MIN, SLEEPING_MAX + 1, STEP_SLEEPING)
    x_labels: List[str] = [minuteToFormatTime(minutes, trim_hour_zero=True) for minutes in x_indexes]
    ax.set_xticks(x_indexes, labels=x_labels, **X_TICKS_STYLE)
    ax.set_xlabel(Y_LABEL_SLEEPING, **LABEL_STYLE)


def _plotToiletVisitsHist(ax: Axes, grp_left: Series, grp_right: Series) -> None:
    """
    夜間トイレ回数の度数をプロット (バーを描画)
    :param ax: プロット領域
    :param grp_left: 左側描画用グルービングオブジェクト
    :param grp_right: 右側描画用グルービングオブジェクト
    """
    hist_max: np.int64 = np.max([grp_left.values.max(), grp_right.values.max()])
    # X軸の位置: (幅) 1.0
    x = np.arange(0, TOILET_VISITS_MAX)
    # 棒幅(半分): (幅[1.] * 幅比率) / 2
    half_width: float = BAR_WIDTH_RATIO / 2.
    # 中心から右左のオフセット: 棒幅(半分) / 2
    x_offset: float = half_width / 2.
    # 左側の棒グラフ: 中心から左側にオフセット幅をマイナス
    ax.bar(x - (1. * x_offset), grp_left.values, half_width, label=LEGEND_WARN, color=BAR_COLOR_WARN)
    # 右側の棒グラフ: 中心から右側にオフセット幅をプラス
    ax.bar(x + (1. * x_offset), grp_right.values, half_width, label=LEGEND_GOOD, color=BAR_COLOR_GOOD)
    ax.legend(**LEGEND_STYLE)
    ax.set_ylim(0, hist_max + 1)
    ax.set_ylabel(Y_LABEL_HIST, **LABEL_STYLE)
    ax.set_xlabel(X_LABEL_TOILET_VISITS, **LABEL_STYLE)


def plot(sess: scoped_session,
         email_address: str, start_date: str, end_date: str,
         phone_image_info: PhoneImageInfo,
         logger: logging.Logger = None, is_debug=False) -> Tuple[SleepManStatistics, Optional[str]]:
    """
    指定された検索条件の睡眠管理データのヒストグラム画像のbase64文字列を取得する
    :param sess: SQLAlchemy scoped_session object
    :param email_address:
    :param start_date: 検索開始年月日
    :param end_date: 検索終了年月日
    :param phone_image_info: 携帯端末の画像領域サイズ情報
    :param logger:
    :param is_debug:
    :return: tuple(統計情報, プロット画像のbase64文字列)
    :raise: DatabaseError
    """
    # データベースから検索データ取得
    dao: SleepManDao = SleepManDao(
        email_address, start_date, end_date, parse_dates=[COL_INDEX],
        logger=logger, is_debug=is_debug
    )

    # 夜間頻尿要因トイレ回数も取得する
    df_data: DataFrame = dao.execute(sess, has_toilet_visits=True)
    if is_debug:
        logger.debug(f"df_data.size: {df_data.shape[0]}")
        logger.debug(f"df_data:\n {df_data.head()}")

    # データ件数
    datasize: int = df_data.shape[0]
    if datasize == 0:
        # レコード無し
        statistics: SleepManStatistics = SleepManStatistics(
            sleeping_mean=0, deep_sleeping_mean=0, record_size=0
        )
        return statistics, None

    # グラフタイトル
    plot_title: str = makeTitleWithMonthRange(start_date, end_date)
    # 測定日列をインデックスに設定
    df_data.index = df_data[COL_INDEX]

    # (1) 睡眠スコアが良いデータ
    df_score_good: DataFrame = df_data.loc[df_data[COL_SLEEP_SCORE] >= GOOD_SLEEP_SCORE].copy()
    if logger is not None and is_debug:
        logger.debug(f"df_score_good.size: {df_score_good.shape[0]}")
        logger.debug(f"df_score_good: {df_score_good}")
    # ヒストグラム用グルービングオブジェクト取得
    dict_good: Dict[str, Series] = makeGroupingObjectsForHistogram(df_score_good)
    good_bedtime: Series = dict_good[GROUP_BEDTIME]
    good_deep_sleeping: Series = dict_good[GROUP_DEEP_SLEEPING]
    good_sleeping: Series = dict_good[GROUP_SLEEPING]
    good_toilet_visits: Series = dict_good[GROUP_TOILET_VISITS]
    if logger is not None and is_debug:
        logger.debug(f"good_bedtime: {good_bedtime}")
        logger.debug(f"good_deep_sleeping: {good_deep_sleeping}")
        logger.debug(f"good_sleeping: {good_sleeping}")
        logger.debug(f"good_toilet_visits: {good_toilet_visits}")
    # (2) 睡眠スコアが悪いデータ
    df_score_warn: DataFrame = df_data.loc[df_data[COL_SLEEP_SCORE] < WARN_SLEEP_SCORE].copy()
    if logger is not None and is_debug:
        logger.debug(f"df_score_warn.size: {df_score_warn.shape[0]}")
        logger.debug(f"df_score_warn: {df_score_warn}")
    dict_warn: Dict[str, Series] = makeGroupingObjectsForHistogram(df_score_warn)
    warn_bedtime: Series = dict_warn[GROUP_BEDTIME]
    warn_deep_sleeping: Series = dict_warn[GROUP_DEEP_SLEEPING]
    warn_sleeping: Series = dict_warn[GROUP_SLEEPING]
    warn_toilet_visits: Series = dict_warn[GROUP_TOILET_VISITS]
    if logger is not None and is_debug:
        logger.debug(f"warn_bedtime: {warn_bedtime}")
        logger.debug(f"warn_deep_sleeping: {warn_deep_sleeping}")
        logger.debug(f"warn_sleeping: {warn_sleeping}")
        logger.debug(f"warn_toilet_visits: {warn_toilet_visits}")

    # 統計情報: 時刻を整数化し平均を計算
    sleeping_mean: float = df_data[COL_SLEEPING].apply(toMinute).mean()
    deep_sleeping_mean: float = df_data[COL_DEEP_SLEEPING].apply(toMinute).mean()
    # データ件数はこの時点のDataFrameの件数とする ※当日データがある場合を考慮
    statistics: SleepManStatistics = SleepManStatistics(
        round(sleeping_mean), round(deep_sleeping_mean), len(df_data)
    )

    # 携帯用の描画領域サイズ(ピクセル)をインチに変換
    fig_width_inch, fig_height_inch = pixelToInch(
        phone_image_info.px_width, phone_image_info.px_height, phone_image_info.density,
        logger=logger, is_debug=is_debug
    )

    # https://matplotlib.org/3.3.4/api/_as_gen/matplotlib.figure.Figure.html
    # https://www.scivision.dev/matplotlib-constrained-layout-tight-layout/
    fig: Figure = Figure(figsize=(fig_width_inch, fig_height_inch), constrained_layout=True)
    # 1段目: 夜間トイレ回数の度数プロット領域
    ax_toilet_visits: Axes
    # 2段目: 就寝時刻の度数プロット領域
    ax_bedtime: Axes
    # 3段目: 深い睡眠の度数プロット領域
    ax_deep_sleeping: Axes
    # 4段目: 睡眠時間の度数プロット領域
    ax_sleeping: Axes
    (ax_toilet_visits, ax_bedtime, ax_deep_sleeping, ax_sleeping) = fig.subplots(
        4, 1, gridspec_kw={'height_ratios': GRID_SPEC_HEIGHT_RATIO}
    )
    if logger is not None and is_debug:
        logger.debug(
            f"fig: {fig}, ax_toilet_visits: {ax_toilet_visits}, "
            f"ax_bedtime: {ax_bedtime},"
            f"ax_deep_sleeping: {ax_deep_sleeping},"
            f"ax_sleeping: {ax_sleeping}"
        )
    # Y方向のグリッド線のみ表示
    for axes in [ax_toilet_visits, ax_bedtime, ax_deep_sleeping, ax_sleeping]:
        axes.grid(**AXES_GRID_STYLE)

    # (1) 夜間起床回数
    # タイトル
    ax_toilet_visits.set_title(plot_title, **TITLE_STYLE)
    # Axes.barでツイン棒グラフ描画
    _plotToiletVisitsHist(ax_toilet_visits, warn_toilet_visits, good_toilet_visits)
    # カスタム(矩形)のツイン棒グラフ描画
    # (2) 就寝時刻プロット
    _plotBedtimeBar(ax_bedtime, warn_bedtime, good_bedtime)
    # (3) 深い睡眠時間プロット
    _plotDeepSleepingBar(ax_deep_sleeping, warn_deep_sleeping, good_deep_sleeping)
    # (4) 睡眠時間プロット
    _plotSleepingnBar(ax_sleeping, warn_sleeping, good_sleeping)
    # 同じ凡例をまとめて設定 (2)-(4)
    # https://matplotlib.org/stable/api/_as_gen/matplotlib.pyplot.legend.html
    for axes in [ax_bedtime, ax_deep_sleeping, ax_sleeping]:
        axes.legend(handles=[WARN_LEGEND, GOOD_LEGEND], **LEGEND_STYLE)

    # HTML用のimgSrc(base64エンコード済み)を取得
    img_src: str = getHtmlImgSrcFromFigure(fig)
    # 統計情報と画像のTupleを返却
    return statistics, img_src

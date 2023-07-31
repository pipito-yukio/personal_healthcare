import logging
from datetime import datetime
from typing import Dict, List, Optional, Tuple

from matplotlib.axes import Axes
from matplotlib.figure import Figure
from matplotlib.patches import Rectangle

import numpy as np
import pandas as pd
from pandas.core.frame import DataFrame, Series

from sqlalchemy.orm import scoped_session

from plotter.common.funcs import toMinute
from plotter.common.statistics import SleepManStatistics
from plotter.common.todaydata import TodaySleepMan
from plotter.common.sleepmanutil import calcBedTime, minuteToFormatTime
from plotter.pandas_common import rebuildIndex
from plotter.plotter_common import (
    makeTitleWithMonthRange, pixelToInch, getHtmlImgSrcFromFigure
)
from plotter.plotparameter import PhoneImageInfo
from plotter.dao import (
    COL_INDEX, COL_WAKEUP, COL_SLEEP_SCORE, COL_SLEEPING, COL_DEEP_SLEEPING, COL_TOILET_VISITS,
    SleepManDao
)
import util.dataclass_util as dcu
import util.date_util as du
from util.date_util import DateCompEnum


"""
睡眠管理データの棒グラフプロットと統計情報
[期間] 
 (1) 過去2週間前 +　当日データ(テーブル未登録): 14+1=15件数
 (2) 月間: 開始測定日, 終了測定日
"""


# 就寝時刻列定数
BED_TIME_SER: str = 'bed_time'
SLEEPING_DIFF_SER: str = 'sleeping_diff_ser'

# 棒グラフの幅倍率
BAR_WIDTH: float = 0.7
# 睡眠スコアの最大値
SCORE_MAX: float = 100
# 睡眠スコアステップ
SCORE_STEP: float = 10
# 睡眠時間(単位:分)の最小値
SLEEP_TIME_MIN: int = 0
# 睡眠時間(単位:分)の最大値: 11時間
SLEEP_TIME_MAX: int = 11 * 60
# Y軸(睡眠時間): 30分間隔で水平線を描画
SLEEP_TIME_STEP: int = 30
# X軸のマージン
X_LIM_MARGIN: float = -0.5
# 睡眠スコア(下限): 非常に良い (90〜100)
RATE_SCORE_BEST: float = 0.9
# 睡眠スコア(下限): 良い (80〜89)
RATE_SCORE_GOOD: float = 0.8
# 睡眠スコア(下限): やや低い (60〜79)
# 睡眠スコア(下限): 低い (60未満)
RATE_SCORE_BAD: float = 0.6
# 睡眠スコアの背景色
#  非常に良い (90〜100)
COLOR_SCORE_BEST: str = 'gold'
#  良い (80〜89)
COLOR_SCORE_GOOD: str = 'lime'
#  やや低い (60〜79)
COLOR_SCORE_WORN: str = 'red'
#  低い (60未満)
COLOR_SCORE_BAD: str = 'gray'
# 睡眠スコアが基準値以上の場合に描画するマーカー色
#   非常に良い(90)以上
MARKER_COLOR_SCORE_BEST: str = 'red'
#   良い(80)以上
MARKER_COLOR_SCORE_GOOD: str = 'green'
#   悪い
MARKER_COLOR_SCORE_BAD: str = 'gray'
# 折れ線の色: 睡眠スコア
SCORE_LINE_COLOR: str = 'black'
# 棒グラフの色: 睡眠時間
COLOR_BAR_SLEEPING: str = 'gold'
# 棒グラフの色: 深い睡眠時間
COLOR_BAR_DEEP_SLEEPING: str = 'violet'
# 下段領域のY軸ラベル
BOTTOM_Y_LABEL: str = '睡眠時間'
# 下段領域の凡例ラベル
LABEL_SLEEPING: str = '睡眠時間 (時:分)'
LABEL_DEEP_SLEEPING: str = '深い睡眠 (分)'
LABEL_SLEEP_SCORE: str = '睡眠スコア'
# Y軸ラベル (0〜 Max 時間) ["00:00","00:30","01:00", ..., Max時間]
SLEEPING_Y_TICKS: List = [minuteToFormatTime(x) for x in range(0, SLEEP_TIME_MAX + 1, SLEEP_TIME_STEP)]
# 上端領域のY軸ラベル
TOP_Y_LABEL: str = '夜間トイレ回数'
TOILET_VISITS_MIN: int = 0
TOILET_VISITS_MAX: int = 7

# スタイル辞書定数定義
# 睡眠スコア折れ線グラフスタイル
SCORE_LINE_STYLE: Dict = {'color': SCORE_LINE_COLOR, 'linewidth': 1.0}
# 睡眠スコア折れ線グラフスタイル
SCORE_TICKS_STYLE: Dict = {'color': SCORE_LINE_COLOR, 'fontsize': 9,
                           'fontweight': 'demibold'}
# 棒グラフの外郭線スタイル
BAR_LINE_STYLE: Dict = {'edgecolor': 'black', 'linewidth': 0.7}
# X軸のラベル(日+曜日)スタイル
X_TICKS_STYLE: Dict = {'fontsize': 9, 'fontweight': 'heavy', 'rotation': 90}
# 上段: X軸のラベル(起床時間)スタイル
TOP_X_TICKS_STYLE: Dict = {'fontsize': 9, 'fontweight': 'heavy', 'rotation': 90}
# 棒グラフの上部に出力する睡眠時間(時:分)のフォントスタイル
TIME_TICKS_STYLE: Dict = {'fontsize': 9}
# 睡眠時間用(時:分)スタイル: 黒
PLOT_TEXT_STYLE: Dict = {'fontsize': 8, 'fontweight': 'demibold',
                         'horizontalalignment': 'center', 'verticalalignment': 'bottom'}
# タイトルスタイル
TITLE_STYLE: Dict = {'fontsize': 11}
# スキャッターマーカースタイル
MARKER_SIZE_WITH_MONTH: float = 9.
# https://matplotlib.org/stable/gallery/shapes_and_collections/scatter.html
# matplotlib.pyplot.scatter
#  #sphx-glr-gallery-shapes-and-collections-scatter-py
SCATTER_SCORE_BEST_STYLE: Dict = {
    'color': MARKER_COLOR_SCORE_BEST, 's': MARKER_SIZE_WITH_MONTH}
SCATTER_SCORE_GOOD_STYLE: Dict = {
    'color': MARKER_COLOR_SCORE_GOOD, 's': MARKER_SIZE_WITH_MONTH}
SCATTER_SCORE_NORMAL_STYLE: Dict = {
    'color': SCORE_LINE_COLOR, 's': MARKER_SIZE_WITH_MONTH}
SCATTER_SCORE_BAD_STYLE: Dict = {
    'color': MARKER_COLOR_SCORE_BAD, 's': MARKER_SIZE_WITH_MONTH}
# 上段: 夜間トイレ回数マーカースタイル ※一回り小さく
SCATTER_TOILET_VISITS_STYLE: Dict = {'color': 'blue', 's': 8.}

# 描画領域のグリッド線スタイル: Y方向のグリッド線のみ表示
AXES_GRID_STYLE: Dict = {'axis': 'y', 'linestyle': 'dashed', 'linewidth': 0.7,
                         'alpha': 0.75}
# 上段プロット領域:下段プロット領域比
GRID_SPEC_HEIGHT_RATIO: List[int] = [1, 6]
# 凡例位置 (上端,右側) ※睡眠スコア値が上端にプロットされることはまれのためプロットが隠れることが無い
LEGEND_LOC: str = 'upper right'


def addTodayData(df_org: DataFrame, val_today: TodaySleepMan,
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
    # <class 'pandas._libs.tslibs.timestamps.Timestamp'>
    p_last_timestamp: pd.Timestamp = df_org[COL_INDEX][df_size - 1]
    # pythonのdateオブジェクト取得
    last_date: datetime.date = p_last_timestamp.date()
    # 最終レコードの測定日と当日データの測定日を比較する
    d_comp: DateCompEnum = du.dateCompare(str(last_date), val_today.measurement_day)
    if logger is not None and is_debug:
        logger.debug(f"compare: {d_comp}")
    # 当日データの測定日が最新なら既存に追加する
    if d_comp == DateCompEnum.GT:
        # 当日データの測定日をpandasのTimestampに変換する
        # https://pandas.pydata.org/docs/reference/api/pandas.to_datetime.html
        latest_timestamp: pd.Timestamp = pd.to_datetime(
            val_today.measurement_day + " 00:00:00", format=du.FMT_DATETIME
        )
        append_datas: List = [latest_timestamp]
        # 当日データリストの先頭(当日データの測定日)を除いたデータのリストを追加する
        values_without_day: List = [d for d in datas[1:]]
        append_datas.extend(values_without_day)
        # pandas 2.0 以降 df.append()は提供されていない
        # https://stackoverflow.com/questions/10715965/
        #   create-a-pandas-dataframe-by-appending-one-row-at-a-time
        #  Create a Pandas Dataframe by appending one row at a time
        # from pandas >= 2.0, append has been removed!
        # Not work: df_data = df_data.append(df_add)
        # https://www.geeksforgeeks.org/
        #   how-to-create-an-empty-dataframe-and-append-rows-columns-to-it-in-pandas/
        # Pandas Append Rows & Columns to Empty DataFrame
        # 当日データをDataFrameの末尾に追加する
        df_org.loc[df_size] = append_datas
        return True, df_org
    else:
        return False, None


def makeDateTextWithJpWeekday(iso_date: str) -> str:
    """
    X軸の日付ラベル文字列を生成する\n
    [形式] "日 (曜日)"
    :param iso_date: ISO8601 日付文字列
    :return: 日付ラベル文字列: '月/日 (曜日)' 月は前ゼロなし、日は前ゼロあり
    """
    val_date: datetime = datetime.strptime(iso_date, du.FMT_ISO8601)
    weekday_name = du.JP_WEEK_DAY_NAMES[val_date.weekday()]
    return f"{val_date.month}/{val_date.day:#02d} ({weekday_name})"


def drawScoreWithMarker(axes: Axes, score_ser: Series) -> None:
    """
    睡眠スコア値出力とマーカー描画
    (1)非常に良い (2)良い (3) (1),(2)以外に該当するスコア値とマーカー
    :param axes: 描画領域
    :param score_ser: 睡眠スコアSeries(欠損データ[pd.na]有り)
    """
    for x_idx, score in enumerate(score_ser):
        if pd.isna(score):  # pandasを使う場合 nan のチェックが必要
            # 欠損データはスキップ
            continue

        # マーカースタイル
        if score >= 100 * RATE_SCORE_BEST:
            scatter_style: Dict = SCATTER_SCORE_BEST_STYLE
        elif score >= 100 * RATE_SCORE_GOOD:
            scatter_style: Dict = SCATTER_SCORE_GOOD_STYLE
        elif score < 100 * RATE_SCORE_BAD:
            scatter_style: Dict = SCATTER_SCORE_BAD_STYLE
        else:
            scatter_style: Dict = SCATTER_SCORE_NORMAL_STYLE
        # マーカープロット
        axes.scatter(x_idx, score, **scatter_style)
        # 睡眠スコアは整数 ※Seriesでは浮動小数点で格納されているため整数に整形
        axes.text(x_idx, score + 1, f"{score:.0f}", **PLOT_TEXT_STYLE)


def drawRectBackground(axes: Axes,
                       y_pos_top: float, y_pos_bottom: float,
                       x_pos_start: float, x_pos_end: float,
                       facecolor: str, alpha: float = 0.2,
                       edgecolor: str = 'none') -> None:
    """
    睡眠スコアに応じた矩形領域を指定した背景色で描画
    :param axes: 描画領域
    :param y_pos_top: Y軸上端位置
    :param y_pos_bottom:  Y軸下端位置
    :param x_pos_start: X軸左端位置
    :param x_pos_end: X軸右端位置
    :param facecolor: 背景色
    :param alpha: アルファ値
    :param edgecolor: 矩形の線色
    """
    rect: Rectangle = Rectangle(
        xy=(x_pos_start, y_pos_bottom),
        width=(x_pos_end - x_pos_start), height=(y_pos_top - y_pos_bottom),
        facecolor=facecolor, edgecolor=edgecolor, alpha=alpha
    )
    axes.add_patch(rect)


def plot(sess: scoped_session,
         email_address: str, start_date: str, end_date: str,
         phone_image_info: PhoneImageInfo,
         today_data: TodaySleepMan = None,
         logger: logging.Logger = None, is_debug=False) -> Tuple[SleepManStatistics, Optional[str]]:
    """
    指定された検索条件の睡眠管理データプロット画像のbase64文字列を取得する
    :param sess: SQLAlchemy scoped_session object
    :param email_address:
    :param start_date: 検索開始年月日
    :param end_date: 検索終了年月日
    :param phone_image_info: 携帯端末の画像領域サイズ情報
    :param today_data: 当日睡眠管理データ(未登録), default None
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

    # ロードしたデータに対する計算
    # 就寝時刻の計算: (測定日[必須]+起床時間[必須]) - 睡眠時間[任意]
    day_ser: Series = df_data.index
    bed_times: List[Optional[datetime]] = [
        calcBedTime(
            day.strftime(du.FMT_ISO8601), wakeup, sleeping) for day, wakeup, sleeping in zip(
            day_ser, df_data[COL_WAKEUP], df_data[COL_SLEEPING]
        )
    ]
    # 就寝時間: X軸出力用に時刻部分のみ設定する
    df_data[BED_TIME_SER] = [bedTm.strftime("%H:%M") for bedTm in bed_times]
    # 睡眠時間と深い睡眠を整数化する
    df_data[COL_SLEEPING]: Series = df_data[COL_SLEEPING].apply(toMinute)
    df_data[COL_DEEP_SLEEPING]: Series = df_data[COL_DEEP_SLEEPING].apply(toMinute)

    # 統計情報は睡眠時間と深い睡眠の整数化が終わってから
    sleeping_mean: float = df_data[COL_SLEEPING].mean()
    deep_sleeping_mean: float = df_data[COL_DEEP_SLEEPING].mean()
    # データ件数はこの時点のDataFrameの件数とする ※当日データがある場合を考慮
    statistics: SleepManStatistics = SleepManStatistics(
        round(sleeping_mean), round(deep_sleeping_mean), len(df_data)
    )

    # 睡眠時間描画用の差分 ※積み上げ棒グラフの深い睡眠の上にスタック描画
    sleeping_diff_ser: Series = df_data[COL_SLEEPING] - df_data[COL_DEEP_SLEEPING]
    if is_debug:
        logger.debug(f"sleepingDiff:\n{sleeping_diff_ser}")
    df_data[SLEEPING_DIFF_SER] = sleeping_diff_ser

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
    #  (1)上段描画領域: 夜間トイレ回数 (Y軸), 就寝時間 (X軸)
    ax_top: Axes
    #  (2)下段描画領域: 睡眠管理データ
    ax_main: Axes
    (ax_top, ax_main) = fig.subplots(
        2, 1, gridspec_kw={'height_ratios': GRID_SPEC_HEIGHT_RATIO}
    )
    if is_debug:
        logger.debug(f"fig: {fig}, ax_top: {ax_top}, ax_main: {ax_main}")
    # Y方向のグリッド線のみ表示
    ax_main.grid(**AXES_GRID_STYLE)
    ax_top.grid(**AXES_GRID_STYLE)

    # X軸のインデックス生成
    x_indexes = np.arange(len(df_data))
    if is_debug:
        logger.debug(f"x_indexes: {x_indexes}")
    plot_size: int = len(x_indexes)

    # 下段メインプロット領域
    # X軸ラベルリスト: "年/日 (曜日) "
    x_labels: List[str] = [
        f"{makeDateTextWithJpWeekday(day.strftime(du.FMT_ISO8601))}" for day in df_data.index
    ]

    # 深い睡眠: 棒グラフ
    ax_main.bar(x_indexes, df_data[COL_DEEP_SLEEPING], BAR_WIDTH,
                color=COLOR_BAR_DEEP_SLEEPING,
                label=LABEL_DEEP_SLEEPING, **BAR_LINE_STYLE)
    # 睡眠時間 (深い睡眠との差分): 棒グラフ
    ax_main.bar(x_indexes, df_data[SLEEPING_DIFF_SER], BAR_WIDTH,
                color=COLOR_BAR_SLEEPING,
                bottom=df_data[COL_DEEP_SLEEPING],
                label=LABEL_SLEEPING, **BAR_LINE_STYLE)
    # 凡例の位置設定
    ax_main.legend(loc=LEGEND_LOC)
    ax_main.set_ylabel(BOTTOM_Y_LABEL)
    # y軸ラベル: 睡眠時間 "時:分"
    ax_main.set_yticks(np.arange(SLEEP_TIME_MIN, (SLEEP_TIME_MAX + 1), SLEEP_TIME_STEP),
                       SLEEPING_Y_TICKS,
                       **TIME_TICKS_STYLE)
    ax_main.set_ylim(SLEEP_TIME_MIN, SLEEP_TIME_MAX)
    # X軸ラベル
    ax_main.set_xticks(x_indexes, x_labels, **X_TICKS_STYLE)
    ax_main.set_xlim(X_LIM_MARGIN, plot_size + X_LIM_MARGIN)

    # 睡眠スコアを取得: 折れ線グラフ (ラベル軸は右側)
    sleep_score_ser: Series = df_data[COL_SLEEP_SCORE]
    # 右側に軸を作成
    ax_main_score = ax_main.twinx()
    ax_main_score.set_ylabel(LABEL_SLEEP_SCORE)
    ax_main_score.plot(x_indexes, sleep_score_ser, **SCORE_LINE_STYLE)
    # 右側y軸ラベル: 最大値まで表示させるため+1
    y_label_range: np.ndarray = np.arange(0, (SCORE_MAX + 1), SCORE_STEP)
    ax_main_score.set_yticks(y_label_range, y_label_range, **SCORE_TICKS_STYLE)
    # 右側Y軸値(0〜100)
    ax_main_score.set_ylim(0, SCORE_MAX)
    # 睡眠スコアが良い以上の場合はスコア値を表示
    drawScoreWithMarker(ax_main_score, sleep_score_ser)
    # 睡眠スコア範囲の矩形描画
    # 非常に良い
    drawRectBackground(ax_main, SLEEP_TIME_MAX,
                       SLEEP_TIME_MAX * RATE_SCORE_BEST,
                       X_LIM_MARGIN, plot_size + X_LIM_MARGIN,
                       facecolor=COLOR_SCORE_BEST)
    # 良い
    drawRectBackground(ax_main, SLEEP_TIME_MAX * RATE_SCORE_BEST,
                       SLEEP_TIME_MAX * RATE_SCORE_GOOD,
                       X_LIM_MARGIN, plot_size + X_LIM_MARGIN,
                       facecolor=COLOR_SCORE_GOOD)
    # やや低い
    drawRectBackground(ax_main, SLEEP_TIME_MAX * RATE_SCORE_GOOD,
                       SLEEP_TIME_MAX * RATE_SCORE_BAD,
                       X_LIM_MARGIN, plot_size + X_LIM_MARGIN,
                       facecolor=COLOR_SCORE_WORN, alpha=0.1)
    # 低い
    drawRectBackground(ax_main, SLEEP_TIME_MAX * RATE_SCORE_BAD,
                       SLEEP_TIME_MIN,
                       X_LIM_MARGIN, plot_size + X_LIM_MARGIN,
                       facecolor=COLOR_SCORE_BAD, alpha=0.1)

    # 上端プロット領域
    # タイトル設定
    ax_top.set_title(plot_title, **TITLE_STYLE)
    # 夜間トイレ回数 (散布図)
    toilet_visits_ser: Series = df_data[COL_TOILET_VISITS]
    # X軸に表示する就寝時間の欠損値は空文字を設定
    top_x_ticks: Series = df_data[BED_TIME_SER].fillna("")
    ax_top.scatter(x_indexes, toilet_visits_ser, **SCATTER_TOILET_VISITS_STYLE)
    ax_top.set_ylim(TOILET_VISITS_MIN, TOILET_VISITS_MAX)
    ax_top.set_ylabel(TOP_Y_LABEL)
    ax_top.set_yticks(range(TOILET_VISITS_MIN, TOILET_VISITS_MAX + 1))
    # 睡眠時間をX軸に表示 ※X軸数はメインプロット領域と同一
    ax_top.set_xlim(X_LIM_MARGIN, plot_size + X_LIM_MARGIN)
    ax_top.set_xticks(x_indexes, top_x_ticks, **TOP_X_TICKS_STYLE)

    # HTML用のimgSrc(base64エンコード済み)を取得
    img_src: str = getHtmlImgSrcFromFigure(fig)
    # 統計情報と画像のTupleを返却
    return statistics, img_src

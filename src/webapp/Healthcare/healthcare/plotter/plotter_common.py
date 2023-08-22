import base64
import enum
import logging
from io import BytesIO
from typing import Dict, List, Tuple

import numpy as np
from healthcare.plotter import font_conf
from matplotlib.axes import Axes
from matplotlib.figure import Figure
from matplotlib import rcParams
rcParams['font.family'] = font_conf['font.family']
font_family_font: str = 'font.' + font_conf['font.family']
rcParams[font_family_font] = font_conf['japanese.font']

"""
matplotlibの描画時に使用する関数群
"""


# 期間タイトルフォーマット
FMT_MEASUREMENT_RANGE: str = "【期間】{}〜{}"
# テキスト表示位置マージン
DRAW_POS_MARGIN: float = 0.5
# 基準値を超えた値の表示文字列スタイル
DRAW_TEXT_BASE_STYLE: Dict = {'color': 'red', 'fontsize': 8, 'fontweight': 'demibold',
                              'horizontalalignment': 'center'}
#  (1) 縦揃え: 下段 ※棒の上
DRAW_TEXT_STYLE: Dict = {**DRAW_TEXT_BASE_STYLE, 'verticalalignment': 'bottom'}
#  (2) 縦揃え: 上段 ※棒の下
DRAW_TEXT_TOP_STYLE: Dict = {**DRAW_TEXT_BASE_STYLE, 'verticalalignment': 'top'}

# HTMLのimgソースbase64エンコード文字列フォーマット
FMT_IMG_SRC_BASE64: str = "data:image/png;base64,{}"
SAVEFIG_DICT: Dict = {}


# matplotlib描画用
class DrawPosition(enum.Enum):
    """ テキスト表示位置 """
    BOTTOM = 0
    TOP = 1


def makeTitleWithMonthRange(s_start_date: str, s_end_date: str) -> str:
    """
    タイトル用月間日付範囲の生成
    :param s_start_date: 開始年月日 (ISO8601)
    :param s_end_date: 終了年月日
    :return: タイトル用月間日付範囲
    """

    def to_japanese_date(iso_date: str) -> str:
        """
        ISO8601フォーマット日付文字列を日本語の西暦("年","月","日")に置換する
        :param iso_date: ISO8601フォーマット日付文字列
        :return: 日本語の西暦
        """
        dates: List[str] = iso_date.split("-")
        return f"{dates[0]}年{dates[1]}月{dates[2]}日"

    # 表示期間 (タイトル用)
    start_jp_weekday: str = to_japanese_date(s_start_date)
    end_jp_weekday: str = to_japanese_date(s_end_date)
    return FMT_MEASUREMENT_RANGE.format(start_jp_weekday, end_jp_weekday)


def drawTextOverValue(axes: Axes, values: np.ndarray, std_value: float,
                      draw_pos: DrawPosition = DrawPosition.BOTTOM,
                      draw_pos_margin: float = DRAW_POS_MARGIN) -> None:
    """
    基準値を超えた値を対応グラフの上部に表示する
    :param axes: プロット領域
    :param values: 値のnp.ndarray
    :param std_value: 基準値
    :param draw_pos: 描画位置 (BOTTOM|TOP)
    :param draw_pos_margin:
    """
    for x_idx, val in enumerate(values):
        if not np.isnan(val) and val > std_value:
            draw_margin: float
            draw_style: Dict
            if draw_pos == DrawPosition.BOTTOM:
                draw_margin = val + draw_pos_margin
                draw_style = DRAW_TEXT_STYLE
            else:
                draw_margin = val - draw_pos_margin
                draw_style = DRAW_TEXT_TOP_STYLE
            # 数値を小数点なして出力する ※整数でも、浮動小数点数でもOK
            axes.text(x_idx, draw_margin, f"{val:.0f}", **draw_style)


def pixelToInch(width_px: int, height_px: int, density: float,
                logger: logging.Logger = None, is_debug=False) -> Tuple[float, float]:
    """
    携帯用の描画領域サイズ(ピクセル)をインチに変換する
    :param width_px: 幅(ピクセル)
    :param height_px: 高さ(ピクセル)
    :param density: 密度
    :param logger: default None
    :param is_debug: default False
    :return: 幅(インチ), 高さ(インチ)
    """
    px: float = 1 / rcParams["figure.dpi"]
    px = px / (2.0 if density > 2.0 else density)
    inch_width = width_px * px
    inch_height = height_px * px
    if logger is not None and is_debug:
        logger.debug(f"figure.dpi[px]: {px}")
        logger.debug(f"px[{density}]: {px}")
        logger.debug(f"fig_width_inch: {inch_width}, fig_height_inch: {inch_height}")
    return inch_width, inch_height


def getHtmlImgSrcFromFigure(
        figure: Figure,
        logger: logging.Logger = None, is_debug: bool = False) -> str:
    """
    プロット画像のbase64エンコード文字列を取得する
    :param figure: Figure
    :param logger:
    :param is_debug:
    :return: プロット画像のbase64エンコード文字列
    """
    # 画像をバイトストリームに溜め込みそれをbase64エンコード文字列に変換する
    buf = BytesIO()
    figure.savefig(buf, format="png", bbox_inches="tight")
    data = base64.b64encode(buf.getbuffer()).decode("ascii")
    if logger is not None and is_debug:
        logger.debug(f"data.len: {len(data)}")
    return f"data:image/png;base64,{data}"

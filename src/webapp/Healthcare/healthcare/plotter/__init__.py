import os
from typing import Dict
import healthcare.util.file_util as fu


# 日本語フォントの設定 ※IPAexフォントがインストール済みの前提
_curr_dir: str = os.path.abspath(os.path.dirname(__file__))
# プロット用のフォント設定ファイル
_FONT_CONF: str = os.path.join(_curr_dir, "conf", "plot_font.json")
# 血圧測定データプロット用設定ファイル
_BLOODPRESS_CONF: str = os.path.join(_curr_dir, "conf", "plot_bloodpress.json")

# 設定オブジェクト
font_conf: Dict = fu.read_json(_FONT_CONF)
plot_bloodpress_conf: Dict = fu.read_json(_BLOODPRESS_CONF)

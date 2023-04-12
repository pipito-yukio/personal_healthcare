# 呼び出すインストールスクリプト
2_create_healthcare_db.sh -> add_weather_table.sh
  # 既存の postgres-12 コンテナで実行するスクリプト
  data/sql/weather/
    # 気象センサーDBに天候テーブルを追加し天候状態CSVからインポートする
    # DML
    21_create_weather_condition.sql
    add_weather_table.sh

# 欠損データCSVのインポート
# メンテナンス中にはセンサーデータのモニターを停止する
# メンテナンス終了後にラズパイゼロから欠損部分のCSVをダウンロードしてから実行する
import_sensor_csv.sh
以上

#!/bin/bash

# https://stackoverflow.com/questions/34736762/script-to-automat-import-of-csv-into-postgresql
#   Script to automat import of CSV into PostgreSQL

# PK制約をドロップ
psql -Udeveloper -d sensors_pgdb -c "ALTER TABLE weather.weather_condition DROP CONSTRAINT pk_weather_condition;"


sleep 1

# データインポート
# 睡眠管理
psql -Udeveloper -d sensors_pgdb -c "\copy weather.weather_condition FROM '/home/yukio/data/sql/weather/csv/weather_condition.csv' DELIMITER ',' CSV HEADER;"

# PK制約を戻す
psql -Udeveloper -d sensors_pgdb -c "ALTER TABLE weather.weather_condition ADD CONSTRAINT pk_weather_condition PRIMARY KEY (measurement_day);"


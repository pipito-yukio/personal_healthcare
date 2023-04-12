#!/bin/bash

# postgres-12 container on sensors_pgdb
cd /home/pi/data/sql/weather
# 天候状態テーブル追加
psql -Udeveloper -d sensors_pgdb < 21_createtable_weather_condition.sql
exit1=$?
echo "21_createtable_weather_condition.sql >> status=$exit1"
if [ $exit1 -ne 0 ]; then
   exit $exit1
fi

sleep 1

# 天候データCSVのインポート
psql -Udeveloper -d sensors_pgdb -c "\copy weather.weather_condition FROM '/home/pi/data/sql/weather/csv/weather_condition.csv' DELIMITER ',' CSV HEADER;"

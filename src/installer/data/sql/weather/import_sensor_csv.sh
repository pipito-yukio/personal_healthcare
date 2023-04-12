#!/bin/bash

# サービス停止中の欠損データをインポートする ※件数が少ないので制約のDROP,ADDはしない
# t_weather.csv into t_weather table
psql -Udeveloper -d sensors_pgdb -c "\copy weather.t_weather FROM '/home/pi/data/sql/weather/csv/weather.csv' DELIMITER ',' CSV HEADER;"


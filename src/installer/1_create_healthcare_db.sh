#!/bin/bash

# 既存のsensors_pgdbに天候テーブルを追加しCSVをインポートする
docker exec -it postgres-12 sh -c "$HOME/data/sql/weather/add_weather_table.sh"
exit1=$?
echo "add_weather_table.sh >> status=$exit1"
if [ $exit1 -ne 0 ]; then
   exit $exit1
fi

# 既存のdockerコンテナからhealthcare_db作成
docker exec -it postgres-12 sh -c "$HOME/data/sql/health/create_healthcare_db.sh"
exit1=$?
echo "create_healthcare_db.sh >> status=$exit1"
if [ $exit1 -ne 0 ]; then
   exit $exit1
fi

# マイグレーション用の古いスクリプトとCSVディレクトリ削除
cd ~/data/sql
rm -rf csv sqlite3db
rm -f *.sh
cd ~

echo "Done."


#!/bin/bash

# 健康管理テーブル作成
docker exec -it postgres-12 sh -c "$HOME/data/sql/health/create_healthcare_tables.sh"
exit1=$?
echo "Create_healthcare_tables.sh >> status=$exit1"
if [ $exit1 -ne 0 ]; then
   exit $exit1
fi

sleep 2

# CSVをインポートする
docker exec -it postgres-12 sh -c "$HOME/data/sql/health/import_from_csv.sh"
exit1=$?
echo "Import_from_csv.sh >> status=$exit1"

cd ~

echo "Done."


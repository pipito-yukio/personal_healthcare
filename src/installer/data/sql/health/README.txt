# 呼び出すインストールスクリプト
(1) 2_create_healthcare_db.sh -> create_healthcare_db.sh  
  # 既存の postgres-12 コンテナで実行するスクリプト
  # healthcare_dbを作成する
  create_healthcare_db.sh

(2) 3_import_healthcare.sh -> import_from_csv.sh 
  # postgres-12h コンテナを起動してから実行するスクリプト
  import_from_csv.sh 


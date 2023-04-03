#!/bin/bash

# https://stackoverflow.com/questions/34736762/script-to-automat-import-of-csv-into-postgresql
#   Script to automat import of CSV into PostgreSQL

# FK制約をドロップ
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.sleep_management DROP CONSTRAINT fkey_sleep_management_person;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.blood_pressure DROP CONSTRAINT fkey_blood_pressure_person;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.nocturia_factors DROP CONSTRAINT fkey_nocturia_factors_person;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.walking_count DROP CONSTRAINT fkey_walking_count_person;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.body_temperature DROP CONSTRAINT fkey_body_temperature_person;"
# PK制約をドロップ
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.sleep_management DROP CONSTRAINT pkey_sleep_management;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.blood_pressure DROP CONSTRAINT pkey_blood_pressure;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.nocturia_factors DROP CONSTRAINT pkey_nocturia_factors;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.walking_count DROP CONSTRAINT pkey_walking_count;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.body_temperature DROP CONSTRAINT pkey_body_temperature;"


sleep 2

# データインポート
# 睡眠管理
psql -Udeveloper -d healthcare_db -c "\copy bodyhealth.sleep_management FROM '/home/yourname/data/sql/health/csv/sleep_management.csv' DELIMITER ',' CSV HEADER;"
# 血圧データ
psql -Udeveloper -d healthcare_db -c "\copy bodyhealth.blood_pressure FROM '/home/yourname/data/sql/health/csv/blood_pressure.csv' DELIMITER ',' CSV HEADER;"
# 夜間頻尿要因
psql -Udeveloper -d healthcare_db -c "\copy bodyhealth.nocturia_factors FROM '/home/yourname/data/sql/health/csv/nocturia_factors.csv' DELIMITER ',' CSV HEADER;"
# 歩数データ
psql -Udeveloper -d healthcare_db -c "\copy bodyhealth.walking_count FROM '/home/yourname/data/sql/health/csv/walking_count.csv' DELIMITER ',' CSV HEADER;"
# 体温測定データ
psql -Udeveloper -d healthcare_db -c "\copy bodyhealth.body_temperature FROM '/home/yourname/data/sql/health/csv/body_temperature.csv' DELIMITER ',' CSV HEADER;"

# PK制約を戻す
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.sleep_management ADD CONSTRAINT pkey_sleep_management PRIMARY KEY (pid, measurement_day);"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.blood_pressure ADD CONSTRAINT pkey_blood_pressure PRIMARY KEY (pid, measurement_day);"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.nocturia_factors ADD CONSTRAINT pkey_nocturia_factors PRIMARY KEY (pid, measurement_day);"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.walking_count ADD CONSTRAINT pkey_walking_count PRIMARY KEY (pid, measurement_day);"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.body_temperature ADD CONSTRAINT pkey_body_temperature PRIMARY KEY (pid, measurement_day);"
# FK制約を戻す
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.sleep_management ADD CONSTRAINT fkey_sleep_management_person FOREIGN KEY (pid) REFERENCES bodyhealth.person (id) ON DELETE CASCADE;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.blood_pressure ADD CONSTRAINT fkey_blood_pressure_person FOREIGN KEY (pid) REFERENCES bodyhealth.person (id) ON DELETE CASCADE;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.nocturia_factors ADD CONSTRAINT fkey_nocturia_factors_person FOREIGN KEY (pid) REFERENCES bodyhealth.person (id) ON DELETE CASCADE;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.walking_count ADD CONSTRAINT fkey_walking_count_person FOREIGN KEY (pid) REFERENCES bodyhealth.person (id) ON DELETE CASCADE;"
psql -Udeveloper -d healthcare_db -c "ALTER TABLE bodyhealth.body_temperature ADD CONSTRAINT fkey_body_temperature_person FOREIGN KEY (pid) REFERENCES bodyhealth.person (id) ON DELETE CASCADE;"


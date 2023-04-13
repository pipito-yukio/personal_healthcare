SELECT
  sm.measurement_day
  ,wakeup_time --必ず入力する
  ,sleep_score --任意
  ,sleeping_time --任意
  ,deep_sleeping_time --任意
  ,morning_measurement_time --任意
  ,morning_max --任意
  ,morning_min --任意
  ,morning_pulse_rate --任意
  ,evening_measurement_time--任意
  ,evening_max --任意
  ,evening_min --任意
  ,evening_pulse_rate --任意
  ,midnight_toilet_visits --必ず入力する
  ,has_coffee
  ,has_tea
  ,has_alcohol
  ,has_nutrition_drink
  ,has_sports_drink
  ,has_diuretic
  ,take_medicine
  ,take_bathing
  ,condition_memo --任意
  ,counts --必ず入力する
  ,measurement_time --BLEデバイス取得(任意)
  ,temperature -- BLEデバイス取得(任意)
FROM
  bodyhealth.person p
  INNER JOIN bodyhealth.sleep_management sm ON p.id = sm.pid
  INNER JOIN bodyhealth.blood_pressure bp ON p.id = bp.pid
  INNER JOIN bodyhealth.nocturia_factors nf ON p.id = nf.pid
  INNER JOIN bodyhealth.walking_count wc ON p.id = wc.pid
  INNER JOIN bodyhealth.body_temperature bt ON p.id = bt.pid
WHERE
  email='user1@examples.com'
  AND
  sm.measurement_day='2023-03-10'
  AND  
  bp.measurement_day='2023-03-10'
  AND 
  nf.measurement_day='2023-03-10'
  AND
  wc.measurement_day='2023-03-10'
  AND
  bt.measurement_day='2023-03-10'


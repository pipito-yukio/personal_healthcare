package com.examples.android.healthcare;

import com.examples.android.healthcare.data.*;
import com.examples.android.healthcare.util.FileUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * 健康管理Flaskアプリへの更新用用データの出力テスト
 * 更新データは変更の有るデータのみを出力対象とするため更新用テンプレートに更新データJSON文字列追加するよう実装
 * 出力したJSONファイルを用いてcurlコマンドで健康管理Flaskアプリに更新リクエストを実行する
 * (使用例) cd ~/Documents/java/output
 *  $ curl -X POST -H "Content-type: application/json" -d @update_data.json "dell-t7500.local:5000/healthcare/update"
 */
public class OutputUpdateJson {
    static final String JSON_NAME = "update_data.json";
    // 部分更新のためのテンプレート
    private static final String TEMPL_MAIN = "{\"emailAddress\":\"%s\",\"measurementDay\":\"%s\",\"healthcareData\":{%s}," +
            "\"weatherData\":{%s}}";
    // テストとして睡眠管理データと歩数データ、天候データのみを更新する
    private static final String TEMPL_HEALTH_SM = "\"sleepManagement\":%s";
    private static final String TEMPL_HEALTH_BP = "\"bloodPressure\":%s";
    private static final String TEMPL_HEALTH_BT = "\"bodyTemperature\":%s";
    private static final String TEMPL_HEALTH_NF = "\"nocturiaFactors\":%s";
    private static final String TEMPL_HEALTH_WC = "\"walkingCount\":%s";
    private static final String TEMPL_WEATHER = "\"weatherCondition\": %s";

    public static <List> void main(String[] args) {
        // 出力ファイル名
        // ~/Documents/java/output/update_data.json
        String saveFile = Paths.get(Constants.OUTPUT_DATA_PATH, JSON_NAME).toString();
        // GSONは部分的にJson文字列を生成するのに使用する
        Gson gson = new GsonBuilder().serializeNulls().create();

        // Androidアプリで使用する前提で実装
        // 健康管理データ
        java.util.List<String> healthList = new ArrayList<String>();
        // 1.睡眠管理データ: sleepScoreを変更
        SleepManagement sleepManagement = new SleepManagement(
                "05:30", 70, "06:40", "0:50"
        );
        String sleepMan = gson.toJson(sleepManagement);
        String jsonSleep = String.format(TEMPL_HEALTH_SM, sleepMan);
        healthList.add(jsonSleep);
        // 2.血圧測定データ
//        BloodPressure bloodPressure = new BloodPressure(
//                "06:50", 124, 76, 73,
//                "22:15", 137, 80, 57
//        );
//        String bloodPress = gson.toJson(bloodPressure);
//        String jsonBlood = String.format(TEMPL_HEALTH_BP, bloodPress);
//        healthList.add(jsonBlood);
        // 4.夜間頻尿要因
//        NocturiaFactors factors = new NocturiaFactors(
//                1,true, false, false, false, false,
//                false,false, true, "今日は疲れた"
//        );
//        String noctFactors = gson.toJson(factors);
//        String jsonFactors = String.format(TEMPL_HEALTH_NF, noctFactors);
//        healthList.add(jsonFactors);
        // 5.歩数: 変更
        WalkingCount walkingCount = new WalkingCount(8300);
        String wc = gson.toJson(walkingCount);
        String jsonWc = String.format(TEMPL_HEALTH_WC, wc);
        healthList.add(jsonWc);
        // 健康管理データ
        String healthcareData = String.join(",", healthList);
        System.out.println(healthcareData);

        // 天気情報
        WeatherCondition weatherCondition = new WeatherCondition("晴れ時々曇り");
        String weather = gson.toJson(weatherCondition);
        String jsonWeather = String.format(TEMPL_WEATHER, weather);
        System.out.println(jsonWeather);

        // メールアドレスと測定日が各テーブルの主キー
        String emailAddress = "user1@examples.com";
        String measurementDay = "2023-03-10";
        String json = String.format(TEMPL_MAIN, emailAddress, measurementDay, healthcareData, jsonWeather);
        try {
            FileUtil.saveText(saveFile, json);
        } catch (IOException e) {
            System.out.println(e);
        }
    }
}

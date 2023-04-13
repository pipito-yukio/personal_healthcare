package com.examples.android.healthcare;

import com.examples.android.healthcare.data.*;
import com.examples.android.healthcare.util.FileUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * 健康管理Flaskアプリへの登録用データの出力テスト
 * 出力したJSONファイルを用いてcurlコマンドで健康管理Flaskアプリに登録リクエストを実行する
 * (使用例) cd ~/Documents/java/output
 *  $ curl -X POST -H "Content-type: application/json" -d @register_data.json "dell-t7500.local:5000/healthcare/register"
 */
public class OutputRegisterJson {
    static final String JSON_NAME = "register_data.json";

    public static void main(String[] args) {
        // 出力ファイル名
        // ~/Documents/java/output/register_data.json
        String saveFile = Paths.get(Constants.OUTPUT_DATA_PATH, JSON_NAME).toString();
        // Gson gson = new GsonBuilder().serializeNulls().create();
        // 整形して出力
        Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

        // 睡眠管理データ
        SleepManagement sleepManagement = new SleepManagement(
           "05:30", 83, "06:40", "0:50"
        );
        // 血圧管理データ
        BloodPressure bloodPressure = new BloodPressure(
           "06:50", 124, 76, 73,
           "22:15", 137, 80, 57
        );
        // 体温測定データ
        BodyTemperature bodyTemperature = new BodyTemperature(
           null, null
        );
        // 夜間頻尿要因データ
        NocturiaFactors factors = new NocturiaFactors(
           1,true, false, false, false, false,
            false,false, true, "今日は疲れた"
        );
        // 歩数データ
        WalkingCount count = new WalkingCount(8250);
        // 健康管理データ (健康管理データベース用)
        HealthcareData healthcareData = new HealthcareData(
             sleepManagement,
             bloodPressure,
             bodyTemperature,
             factors,
             count
        );
        // 天気情報 (気象センサーデータベース用)
        WeatherCondition wc = new WeatherCondition("晴れのち曇り");
        WeatherData weatherData = new WeatherData(wc);
        RegisterData regsterData = new RegisterData(
                "user1@examples.com",
                "2023-03-10",
                healthcareData,
                weatherData
        );
        String jsonText = gson.toJson(regsterData);
        try {
            FileUtil.saveText(saveFile, jsonText);
        } catch (IOException e) {
            System.out.println(e);
        }
    };
}

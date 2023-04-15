package com.examples.android.healthcare;

import com.examples.android.healthcare.constants.JsonTemplate;
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

    public static <List> void main(String[] args) {
        // 出力ファイル名
        // ~/Documents/java/output/update_data.json
        String saveFile = Paths.get(Constants.OUTPUT_DATA_PATH, JSON_NAME).toString();
        // GSONは部分的にJson文字列を生成するのに使用する
        Gson gson = new GsonBuilder().serializeNulls().create();

        // Androidアプリで使用する前提で実装
        String jsonContent;
        String JsonWithProperty;
        // 健康管理データ
        java.util.List<String> healthList = new ArrayList<String>();
        // 1.睡眠管理データ: sleepScoreを変更
        SleepManagement sleepManagement = new SleepManagement(
                "05:30", 70, "06:40", "0:50"
        );
        jsonContent = gson.toJson(sleepManagement);
        JsonWithProperty = JsonTemplate.getJsonWithSleepManagement(jsonContent);
        healthList.add(JsonWithProperty);
        // 2.血圧測定データ: 変更なし
        // 3.体温測定データ: 変更なし
        // 4.夜間頻尿要因: 変更なし
        // 5.歩数: 変更
        WalkingCount walkingCount = new WalkingCount(8300);
        jsonContent = gson.toJson(walkingCount);
        JsonWithProperty = JsonTemplate.getJsonWithWalkingCount(jsonContent);
        healthList.add(JsonWithProperty);
        // 健康管理データ
        String healthcareDataJson = String.join(",", healthList);
        System.out.println(healthcareDataJson);

        // 天気情報
        WeatherCondition weatherCondition = new WeatherCondition("晴れ時々曇り");
        jsonContent = gson.toJson(weatherCondition);
        String weatherDataJson = JsonTemplate.getJsonWithWeatherCondition(jsonContent);
        System.out.println(weatherDataJson);

        // メールアドレスと測定日が各テーブルの主キー
        String emailAddress = "user1@examples.com";
        String measurementDay = "2023-03-10";
        // 更新用リクエスト用のJSON文字列をテンプレートから生成
        String updateJson = JsonTemplate.createUpdateJson(emailAddress, measurementDay,
                healthcareDataJson, weatherDataJson);
        try {
            FileUtil.saveText(saveFile, updateJson);
        } catch (IOException e) {
            System.out.println(e);
        }
    }
}

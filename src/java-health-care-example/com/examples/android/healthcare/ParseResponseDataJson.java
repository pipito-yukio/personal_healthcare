package com.examples.android.healthcare;

import com.examples.android.healthcare.data.GetCurrentDataResult;
import com.examples.android.healthcare.util.FileUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.List;

/**
 * 健康管理Flaskアプリからのデータ取得リクエストのレスポンス(JSON)をJava Objectに変換する
 * curlで取得したレスポンスを入力ファイルとする
 * $ curl -G -d 'emailAddress=user1@examples.com&measurementDay=2023-03-10' http://dell-t7500.local:5000/healthcare/getcurrentdata
 */
public class ParseResponseDataJson {
    static final String JSON_NAME = "response_getcurrentdata.json";

    public static void main(String[] args) {
        // 入力ファイル名
        // ~/Documents/java/input/response_getcurrentdata.json
        String readFile = Paths.get(Constants.INPUT_DATA_PATH, JSON_NAME).toString();

        try {
            List<String> responseLines = FileUtil.readLines(readFile);
            String responseJson = String.join("", responseLines);
            Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
            // Androidアプリで使用するデータ取得レスボンスデータクラス
            GetCurrentDataResult respObj = gson.fromJson(responseJson, GetCurrentDataResult.class);
            System.out.println(respObj);
        } catch (FileNotFoundException e) {
            System.out.println(e.getLocalizedMessage());
        } catch (Exception e) {
            // パースエラー
            System.out.println(e.getLocalizedMessage());
        }
    }
}

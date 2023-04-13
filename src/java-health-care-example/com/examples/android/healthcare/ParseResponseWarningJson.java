package com.examples.android.healthcare;

import com.examples.android.healthcare.data.ResponseWarningStatus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 健康管理FlaskアプリからのWarningレスポンスをJava Objectに変換する
 * curlで取得したレスポンスを入力文字列とする
 * $ curl -G -d 'emailAddress=user1@examples.com&measurementDay=2023-04-10' http://dell-t7500.local:5000/healthcare/getcurrentdata
 * {
 *   "status": {
 *   "code": 404,
 *   "message": "Data is not found."
 *   }
 * }
 */
public class ParseResponseWarningJson {
    // データ取得リクエストでレコード未登録時に返却されるレスポンス
    static final String jsonText = "{\"status\": {\"code\": 404, \"message\": \"Data is not found.\" }}";

    public static void main(String[] args) {
        Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        try {
            ResponseWarningStatus respObj = gson.fromJson(jsonText, ResponseWarningStatus.class);
            System.out.println(respObj);
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
        }
    }
}

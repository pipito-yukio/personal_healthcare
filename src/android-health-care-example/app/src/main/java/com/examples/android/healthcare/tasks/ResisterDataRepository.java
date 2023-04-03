package com.examples.android.healthcare.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.examples.android.healthcare.data.RegisterResult;
import com.examples.android.healthcare.data.ResponseStatus;
import com.examples.android.healthcare.data.ResponseWarningStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * AppTopFragment用データ登録(更新)リポジトリクラス
 */
public class ResisterDataRepository extends HealthcareRepository<RegisterResult> {
    // パス配列: 登録時, 更新時
    private static final String[] URL_PATHS = {"/register", "/update"};

    public ResisterDataRepository() {}

    @Override
    public String getRequestPath(int pathIdx) {
        return URL_PATHS[pathIdx];
    }

    @Override
    public String getResponseText(InputStream is) throws IOException {
        StringBuilder sb;
        try (BufferedReader bf = new BufferedReader
                (new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            sb = new StringBuilder();
            while ((line = bf.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * 正常時のレスポンスオブジェクトを取得する
     * <pre>HTTP OK時にサーバーが返却するレスポンス例
     {"data": {"emailAddress": "user1@example.com", "measurementDay": "2023-02-13"},
      "status": {"code": 0, "message": "OK"}}
     * </pre>
     * @param jsonText JSON文字列
     * @return レスポンスオブジェクト
     * @throws JsonParseException パース例外
     */
    @Override
    public RegisterResult parseResultJson(String jsonText) throws JsonParseException {
        Gson gson = new Gson();
        return gson.fromJson(jsonText, RegisterResult.class);
    }

    /**
     * ウォーニング時のレスポンスオブジェクトを取得する
     * <pre>ウォーニング時にサーバーが返却するレスポンス例
     {"status": {"code": 400,"message": "461,User is not found."}}
     * </pre>
     * @param jsonText ウォーニング用JSON文字列
     * @return レスポンスオブジェクト<br/>
     *   ResponseStatusオブジェクトのみがセットされDataオブジェクトはnullがセットされる
     * @throws JsonParseException パース例外
     */
    @Override
    public RegisterResult parseWarningJson(String jsonText) throws JsonParseException {
        Gson gson = new GsonBuilder().serializeNulls().create();
        // ResponseStatusに"status"タグ含めたResponseStatusのラップクラス
        // {"status": {"code": 400,"message": "461,User is not found."}}部分を受取るクラス
        ResponseWarningStatus warningStatus = gson.fromJson(jsonText, ResponseWarningStatus.class);
        // {"code": 400,"message": "461,User is not found."} 部分を受取るクラス
        ResponseStatus status = warningStatus.getStatus();
        return new RegisterResult(null, status);
    }
}

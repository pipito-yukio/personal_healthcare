package com.examples.android.healthcare.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.examples.android.healthcare.data.GetCurrentDataResult;
import com.examples.android.healthcare.data.ResponseStatus;
import com.examples.android.healthcare.data.ResponseWarningStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * AppTopFragment用登録済みデータ取得リポジトリクラス
 */
public class GetCurrentDataRepository extends HealthcareRepository<GetCurrentDataResult> {
    // GETリクエストパス
    private static final String URL_PATH = "/getcurrentdata";

    @Override
    public String getRequestPath(int pathIdx) {
        return URL_PATH;
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
     * 登録済みデータオブジェクトを取得する
     * <pre>HTTP OK時にサーバーが返却するレスポンス例 ※一部省略
     {"data": {"emailAddress": "sapporo@examples.com", "healthcareData": {  "bloodPressure": {
     "eveningMax": 124, "eveningMeasurementTime": "22:20", "eveningMin": 72, "eveningPulseRate": 59,
     "morningMax": 112, "morningMeasurementTime": "06:45", "morningMin": 67, "morningPulseRate": 65 },
     ...一部省略...
     },"walkingCount": {counts": 8576}},
     "measurementDay": "2023-02-01",
     "weatherData": {"weatherCondition": {"condition": "曇りのち雪"}}},
     "status": {"code": 0, "message": "OK"}}
     * </pre>
     * @param jsonText JSON文字列を
     * @return 登録済みデータオブジェクト
     * @throws JsonParseException パース例外
     */
    @Override
    public GetCurrentDataResult parseResultJson(String jsonText) throws JsonParseException {
        Gson gson = new Gson();
        return gson.fromJson(jsonText, GetCurrentDataResult.class);
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
    public GetCurrentDataResult parseWarningJson(String jsonText) throws JsonParseException {
        Gson gson = new GsonBuilder().serializeNulls().create();
        ResponseWarningStatus warningStatus = gson.fromJson(jsonText, ResponseWarningStatus.class);
        ResponseStatus status = warningStatus.getStatus();
        return new GetCurrentDataResult(null, status);
    }
}

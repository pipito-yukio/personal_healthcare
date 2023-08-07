package com.examples.android.healthcare.tasks;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.examples.android.healthcare.data.RegisterResult;

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

}

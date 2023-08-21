package com.examples.android.healthcare.tasks;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.examples.android.healthcare.data.GetRegisterDaysResult;

/**
 * ユーザーの登録開始日・最終登録日取得リクエスト用リポジトリ
 */
public class GetRegisterDaysRepository
        extends HealthcareRepository<GetRegisterDaysResult> {
    // GETリクエストパス
    private static final String URL_PATH = "/get_registerdays";

    @Override
    public String getRequestPath(int pathIdx) {
        return URL_PATH;
    }

    /**
     * 登録開始日・最終登録日ブジェクトを取得する
     * <pre>HTTP OK時にサーバーが返却するレスポンス例
     * {
     *   "data": {
     *     "firstDay": "2023-01-01",
     *     "lastDay": "2023-06-30"
     *   },
     *   "status": {
     *     "code": 0,
     *     "message": "OK"
     *   }
     * }
     * @param jsonText レスポンス(JSON文字列)
     * @return 登録済みデータオブジェクト
     * @throws JsonParseException パース例外
     */
    public GetRegisterDaysResult parseResultJson(String jsonText) throws JsonParseException {
        Gson gson = new Gson();
        return gson.fromJson(jsonText, GetRegisterDaysResult.class);
    }

}

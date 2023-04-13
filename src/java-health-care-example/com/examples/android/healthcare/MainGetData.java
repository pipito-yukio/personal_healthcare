package com.examples.android.healthcare;

import android.util.Log;
import com.examples.android.healthcare.constants.RequestDevice;
import com.examples.android.healthcare.data.GetCurrentDataResult;
import com.examples.android.healthcare.data.RegisterData;
import com.examples.android.healthcare.data.ResponseStatus;
import com.examples.android.healthcare.functions.AppTopUtil;
import com.examples.android.healthcare.tasks.GetCurrentDataRepository;
import com.examples.android.healthcare.tasks.HealthcareRepository;
import com.examples.android.healthcare.tasks.Result;
import java.util.Map;

/**
 * 健康管理データ登録Androidアプリ用通信部品のテスト用コード
 * データ取得リクエスト: 下記と同じ
 * $ curl -G -d 'emailAddress=user1@examples.com&measurementDay=2023-03-10' http://dell-t7500.local:5000/healthcare/getcurrentdata
 */
public class MainGetData {
    static final String TAG = "MainGetData";
    // 登録済みデータの主キー
    static final String emailAddress = "user1@examples.com";
    static final String pastDay = "2023-03-10";

    public static void main(String[] args) {
        HealthcareApplication app = new HealthcareApplication();
        // ローカルネットワーク
        Map<String, String> urlMap = app.getmRequestUrls();
        Map<String, String> headers = app.getRequestHeaders();
        String requestUrl = urlMap.get(RequestDevice.WIFI.toString());
        Log.d(TAG, "requestUrl: "+ requestUrl);
        try {
            // GETリクエスト送信: 登録済みデータの取得
            HealthcareRepository<GetCurrentDataResult> repository = new GetCurrentDataRepository();
            // リクエストパラメータ: 主キー項目(メールアドレス, 測定日付)
            String requestParams = AppTopUtil.getRequestParams(emailAddress, pastDay);
            repository.makeGetRequest(0, requestUrl, requestParams, headers,
                    app.mEexecutor, app.mHandler, (result) -> {
                if (result instanceof Result.Success) {
                    GetCurrentDataResult dataResult =
                            ((Result.Success<GetCurrentDataResult>) result).get();
                    RegisterData data = dataResult.getData();
                    Log.d(TAG, "responseResult: " + dataResult);
                } else if (result instanceof Result.Warning) {
                    ResponseStatus status =
                            ((Result.Warning<?>) result).getResponseStatus();
                    Log.w(TAG, status.toString());
                } else if (result instanceof Result.Error) {
                    // 例外メッセージ
                    Exception exception = ((Result.Error<?>) result).getException();
                    Log.w(TAG, "GET error:" + exception.toString());
                }
            });
        } finally {
            // Javaアプリでは一回きりの実行なのでシャットダウンでプロセスを終了させる
            app.mEexecutor.shutdownNow();
        }
    }
}

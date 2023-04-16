package com.examples.android.healthcare;

import android.util.Log;
import com.examples.android.healthcare.constants.RequestDevice;
import com.examples.android.healthcare.data.RegisterResult;
import com.examples.android.healthcare.data.ResponseStatus;
import com.examples.android.healthcare.tasks.HealthcareRepository;
import com.examples.android.healthcare.tasks.ResisterDataRepository;
import com.examples.android.healthcare.tasks.Result;
import com.examples.android.healthcare.util.FileUtil;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 健康管理データ登録Androidアプリ用通信部品のテスト用コード
 * データ登録リクエスト: 下記と同じ
 * ~/Documents/java/input/register_data_20230311.json
 * curl -X POST -H "Content-type: application/json" -d @register_data_20230311.json "dell-t7500.local:5000/healthcare/register"
 */
public class MainPostData {
    static final String TAG = "MainPostData";
    // テスト用登録用JSONファイル
    static final String JSON_NAME = "register_data_20230311.json";

    public static void main(String[] args) {
        String jsonFile = Paths.get(Constants.INPUT_DATA_PATH, JSON_NAME).toString();
        // 登録用データの読み込み
        String jsonText = null;
        try {
            List<String> lines = FileUtil.readLines(jsonFile);
            jsonText = String.join("", lines);
        } catch (IOException e) {
            Log.e(TAG, "Error: "+ e);
            System.exit(1);
        }

        HealthcareApplication app = new HealthcareApplication();
        // ローカルネットワーク
        Map<String, String> urlMap = app.getmRequestUrls();
        Map<String, String> headers = app.getRequestHeaders();
        String requestUrl = urlMap.get(RequestDevice.WIFI.toString());
        Log.d(TAG, "requestUrl: " + requestUrl);
        try {
            HealthcareRepository<RegisterResult> repository = new ResisterDataRepository();
            int urlNum = 0; // 登録
            repository.makeRegisterRequest(urlNum, requestUrl, jsonText, headers,
                    app.mEexecutor, app.mHandler, (result) -> {
                        if (result instanceof Result.Success) {
                            RegisterResult respResult =
                                    ((Result.Success<RegisterResult>) result).get();
                            Log.d(TAG, "responseResult: " + respResult);
                        } else if (result instanceof Result.Warning) {
                            ResponseStatus status =
                                    ((Result.Warning<?>) result).getResponseStatus();
                            Log.w(TAG, status.toString());
                        } else {
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

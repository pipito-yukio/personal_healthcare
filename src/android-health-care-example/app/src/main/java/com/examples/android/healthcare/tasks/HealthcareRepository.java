package com.examples.android.healthcare.tasks;

import static com.examples.android.healthcare.functions.MyLogging.DEBUG_OUT;

import android.os.Handler;
import android.util.Log;

import com.google.gson.JsonParseException;
import com.examples.android.healthcare.data.GetCurrentDataResult;
import com.examples.android.healthcare.data.RegisterResult;
import com.examples.android.healthcare.data.ResponseStatus;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * HTTPリクエスト実行リポジトリクラス
 * @param <T> レスポンス用Javaクラス
 */
public abstract class HealthcareRepository<T> {
    private static final String TAG = "HealthcareRepository";

    public HealthcareRepository() {}

    /**
     * GETリクエスト生成メソッド
     * @param pathIdx パス用インデックス ※サブクラスで複数のGETリクエストパス定義
     * @param baseUrl パスを含まないURL (Wifi | Mobile)
     * @param requestParameter リクエストパラメータ
     * @param headers リクエストヘッダー
     * @param executor スレッドエクゼキューター
     * @param handler Android Handlerオブジェクト
     * @param callback Activity(Fragment)が結果を受け取るコールバック
     */
    public void makeGetRequest(
            int pathIdx,
            String baseUrl,
            String requestParameter,
            Map<String, String> headers,
            ExecutorService executor,
            Handler handler,
            final RepositoryCallback<T> callback) {
        executor.execute(() -> {
            try {
                String requestUrl = baseUrl + getRequestPath(pathIdx) + requestParameter;
                Result<T> result =
                        getRequest(requestUrl, headers);
                // 200, 4xx - 50x系
                notifyResult(result, callback, handler);
            } catch (Exception e) {
                // サーバー側のレスポンスBUGか, Android側のBUG想定
                Result<T> errorResult = new Result.Error<>(e);
                notifyResult(errorResult, callback, handler);
            }
        });
    }

    /**
     * POSTリクエスト(登録・更新)生成メソッド
     * @param pathIdx パス用インデックス ※サブクラスで複数のGETリクエストパス定義
     * @param baseUrl パスを含まないURL (Wifi | Mobile)
     * @param headers リクエストヘッダー
     * @param executor スレッドエクゼキューター
     * @param handler Android Handlerオブジェクト
     * @param callback Activity(Fragment)が結果を受け取るコールバック
     */
    public void makeRegisterRequest(
            int pathIdx,
            String baseUrl,
            String jsonData,
            Map<String, String> headers,
            ExecutorService executor,
            Handler handler,
            final RepositoryCallback<T> callback) {
        executor.execute(() -> {
            try {
                String requestUrl = baseUrl + getRequestPath(pathIdx);
                Result<T> result =
                        postRegisterDataRequest(requestUrl, headers, jsonData);
                notifyResult(result, callback, handler);
            } catch (Exception e) {
                Result<T> errorResult = new Result.Error<>(e);
                notifyResult(errorResult, callback, handler);
            }
        });
    }

    /**
     * GETリクエスト実行メソッド
     * @param requestUrl リクエストURL
     * @param requestHeaders リクエストヘッダー
     * @return サーバーからのレスポンスを対応するクラスのオブジェクト
     */
    private Result<T> getRequest(
            String requestUrl, Map<String, String> requestHeaders) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(requestUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json;");
            for (String key : requestHeaders.keySet()) {
                conn.setRequestProperty(key, requestHeaders.get(key));
            }

            // Check response code: allow 200 only.
            int respCode = conn.getResponseCode();
            DEBUG_OUT.accept(TAG, "ResponseCode:" + respCode);
            if (respCode == HttpURLConnection.HTTP_OK) {
                String respText = getResponseText(conn.getInputStream());
                T result = parseResultJson(respText);
                return new Result.Success<>(result);
            } else {
                // 4xx - 50x
                // Flaskアプリからはエラーストリームが生成される
                String respText = getResponseText(conn.getErrorStream());
                DEBUG_OUT.accept(TAG, "NG: " + respText);
                // ウォーニング時のJSONはデータ部が存在しないのでウォーニング専用ハースを実行
                T result = parseWarningJson(respText);
                // ResponseStatusのみ, GET用Dataクラス == null
                ResponseStatus status = ((GetCurrentDataResult) result).getStatus();
                DEBUG_OUT.accept(TAG, "NG.ResponseStatus: " + respText);
                return new Result.Warning<>(status);
            }
        } catch (Exception ie) {
            Log.w(TAG, ie.getLocalizedMessage());
            return new Result.Error<>(ie);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * POSTリクエスト(登録・更新)実行メソッド
     * @param requestUrl リクエストURL
     * @param requestHeaders リクエストヘッダー
     * @param jsonData JSONデータ(文字列)
     * @return サーバーからのレスポンスを対応するクラスのオブジェクト
     */
    private Result<T> postRegisterDataRequest(
            String requestUrl, Map<String, String> requestHeaders, String jsonData) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(requestUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json;");
            // POSTデータ有り(JSON)
            conn.setDoOutput(true);
            // レスポンス有り
            conn.setDoInput(true);
            conn.setChunkedStreamingMode(0);
            // ヘッダー設定
            for (String key : requestHeaders.keySet()) {
                conn.setRequestProperty(key, requestHeaders.get(key));
            }
            // リクエストデータ用出力ストリーム
            OutputStream output = new BufferedOutputStream(conn.getOutputStream());
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(output, StandardCharsets.UTF_8));
            writer.write(jsonData);
            writer.flush();

            // Check response code: allow 200 only.
            int respCode = conn.getResponseCode();
            DEBUG_OUT.accept(TAG, "ResponseCode:" + respCode);
            if (respCode == HttpURLConnection.HTTP_OK) {
                String respText = getResponseText(conn.getInputStream());
                T result = parseResultJson(respText);
                return new Result.Success<>(result);
            } else {
                // 4xx - 50x: ResultStatus, Dataクラス == null
                // Flaskアプリからはエラーストリームが生成される
                String respText = getResponseText(conn.getErrorStream());
                DEBUG_OUT.accept(TAG, "NG: " + respText);
                // ウォーニング時のJSONはデータ部が存在しないのでウォーニング専用ハースを実行
                T result = parseWarningJson(respText);
                // ResponseStatusのみ, Post用Dataクラス == null
                ResponseStatus status = ((RegisterResult) result).getStatus();
                DEBUG_OUT.accept(TAG, "NG.ResponseStatus: " + respText);
                return new Result.Warning<>(status);
            }
        } catch (Exception ie) {
            // パースエラーまたはIO例外
            Log.w(TAG, ie.getLocalizedMessage());
            return new Result.Error<>(ie);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * UI側のコールバックに結果(Javaオブジェクト)をセットする
     * @param result レスポンスオブジェクト
     * @param callback UI側コールバック
     * @param handler Androidハンドラー
     */
    private void notifyResult(final Result<T> result,
                              final RepositoryCallback<T> callback,
                              final Handler handler) {
        handler.post(() -> callback.onComplete(result));
    }

    /**
     *
     * @param pathIdx パスインデックス (1 - m)
     * @return サブクラスが提供するパス
     */
    public abstract String getRequestPath(int pathIdx);

    /**
     * 入力ストリームからJSON文字列を取得
     * @param is 入力ストリーム
     * @return JSON文字列
     * @throws IOException IO例外
     */
    public abstract String getResponseText(InputStream is) throws IOException;

    /**
     * HTTP 200(OK) レスポンス時のJSON文字列をパースしてJavaオブジェクトを生成
     * @param jsonText JSON文字列を
     * @return サブグラスが定義するJavaオブジェクトを生成
     * @throws JsonParseException GSONのパース例外
     */
    public abstract T parseResultJson(String jsonText) throws JsonParseException;

    /**
     * HTTP 4xx, 50x系レスポンス時のJSON文字列をパースしてJavaオブジェクトを生成
     * @param jsonText ウォーニング用JSON文字列
     * @return サブグラスが定義するウォーニング用Javaオブジェクトを生成
     * @throws JsonParseException GSONのパース例外
     */
    public abstract T parseWarningJson(String jsonText) throws JsonParseException;
}

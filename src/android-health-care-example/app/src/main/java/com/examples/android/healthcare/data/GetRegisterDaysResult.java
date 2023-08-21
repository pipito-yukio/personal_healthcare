package com.examples.android.healthcare.data;

/**
 * ユーザーの登録開始日・最終登録日取得リクエスト用レスポンスクラス
 */
public class GetRegisterDaysResult {
    private final ResponseRegisterDays data;
    private final ResponseStatus status;

    public GetRegisterDaysResult(ResponseRegisterDays data, ResponseStatus status) {
        this.data = data;
        this.status = status;
    }

    public ResponseRegisterDays getData() { return data; }

    public ResponseStatus getStatus() { return status; }

}

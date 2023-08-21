package com.examples.android.healthcare.data;

/**
 * 画像取得リクエスト用レスポンスクラス
 */
public class GetImageDataResult {
    private final ResponseImageData data;
    private final ResponseStatus status;

    public GetImageDataResult(ResponseImageData data, ResponseStatus status) {
        this.data = data;
        this.status = status;
    }

    public ResponseImageData getData() { return this.data; }

    public ResponseStatus getStatus() {
        return status;
    }

}

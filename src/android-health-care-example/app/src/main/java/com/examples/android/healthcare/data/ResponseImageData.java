package com.examples.android.healthcare.data;

import com.google.gson.annotations.SerializedName;

import java.util.Base64;

import static com.examples.android.healthcare.functions.MyLogging.DEBUG_OUT;

/**
 * 画像取得リクエストに対するレスポンス(RAWデータ)
 * Gsonライブラリで変換
 * [形式] JSON
 */
public class ResponseImageData {
    // 画像(png)のbase64エンコード文字列
    @SerializedName("img_src")
    private final String encodedImageSrc;
    // 統計情報(カンマ区切り)
    private final String statistics;
    // 検索データ件数
    private final int rows;

    public ResponseImageData(String imgSrc, String statistics, int rows) {
        this.encodedImageSrc = imgSrc;
        this.statistics = statistics;
        this.rows = rows;
    }

    public byte[] getImageBytes() {
        if (this.encodedImageSrc != null) {
            // img_src = "data:image/png;base64, ..base64string..."
            String[] datas = this.encodedImageSrc.split(",");
            DEBUG_OUT.accept("ResponseImageData", "datas[0]" + datas[0]);
            return Base64.getDecoder().decode(datas[1]);
        } else {
            return null;
        }
    }

    public String getStatistics() { return statistics; }
    public int getRows() { return  rows; }

    @Override
    public String toString() {
        return "ResponseImageData{" +
                "imgSrc.size='" + (encodedImageSrc != null ? encodedImageSrc.length() : 0) + '\'' +
                ", statistics='" + statistics + '\'' +
                ", rows=" + rows +
                '}';
    }
}

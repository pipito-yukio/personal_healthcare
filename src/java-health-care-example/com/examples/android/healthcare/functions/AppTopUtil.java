package com.examples.android.healthcare.functions;

import com.examples.android.healthcare.data.ResponseStatus;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Map;

public class AppTopUtil {
    // GETリクエストURLフォーマット
    private static final String FMT_GET_CURRENT_DATA_PARAM = "?emailAddress=%s&measurementDay=%s";

    /**
     * リクエストパラメータをエンコードする
     * @param rawParam エンコード前のリクエストパラメータ
     * @return エンコード後のリクエストパラメータ
     */
    public static String urlEncoded(String rawParam) {
        try {
            return URLEncoder.encode(rawParam, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return rawParam;
        }
    }

    /**
     * URLエンコード済みGET取得リクエストパラメータ文字列を取得する
     * <p>?emailAddress=[encoded]&measurementDay=[encoded]</p>
     * @param emailAddress メールアドレス (必須)
     * @param measurementDay 登録日付文字列 (必須)
     * @return URLエンコード済みGET取得リクエストパラメータ文字列
     */
    public static String getRequestParams(String emailAddress, String measurementDay) {
        String encodedEmail = urlEncoded(emailAddress);
        String encodedDay = urlEncoded(measurementDay);
        return String.format(FMT_GET_CURRENT_DATA_PARAM,
                encodedEmail, encodedDay);
    }

}

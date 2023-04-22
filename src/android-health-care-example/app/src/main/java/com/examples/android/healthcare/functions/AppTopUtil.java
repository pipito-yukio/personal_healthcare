package com.examples.android.healthcare.functions;

import android.text.TextUtils;

import com.examples.android.healthcare.data.ResponseStatus;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Map;

/**
 * Androidリソースに依存しないユーティリィティメソッド
 */
public class AppTopUtil {
    // POST処理
    public enum PostRequest {
        REGISTER(0, "登録"),
        UPDATE(1, "更新");

        private final int num;
        private final String name;
        PostRequest(int num, String name) {
            this.num = num;
            this.name = name;
        }
        public int getNum() { return num; }
        public String getName() {return name; }
    }
    // JSONファイル保存タイミング
    public enum JsonFileSaveTiming {
        SAVE("一時保存"), REGISTERED("登録済み");

        private final String name;
        JsonFileSaveTiming(String name) {
            this.name = name;
        }
        public String getName() {return name; }
    }

    // 更新チェック用マップキー
    public static final String UPD_KEY_SLEEP_MAN = "SleepManagement";
    public static final String UPD_KEY_BLOOD_PRESS = "BloodPressure";
    public static final String UPD_KEY_BODY_TEMPER = "BodyTemperature";
    public static final String UPD_KEY_NOCT_FACT = "NocturiaFactors";
    // ISO-8601拡張のローカル日付フォーマット
    public static final String ISO_8601_DATE_FORMAT = "%tY-%<tm-%<td";
    // 体温表示フォーマット
    public static final String FMT_BODY_TEMPER = "%.1f";
    // GETリクエストURLフォーマット
    private static final String FMT_GET_CURRENT_DATA_PARAM = "?emailAddress=%s&measurementDay=%s";

    /**
     * 日付文字列をハイフンで分割して年月日の整数配列を取得する
     * @param dateText 日付文字列 (ISO-8601拡張ローカル形式)
     * @return 年月日の整数配列[year, month, dayOfMonth]
     */
    public static int[] splitDateValue(String dateText) {
        String[] dates = dateText.split("-");
        int year = Integer.parseInt(dates[0]);
        int month = Integer.parseInt(dates[1]);
        int day = Integer.parseInt(dates[2]);
        return new int[] {year, month, day};
    }

    /**
     * JSON保存された測定日付文字列でカレンダーオブジェクトを復元する
     * @param cal カレンダーオブジェクト
     * @param iso8601text 測定日付文字列(JSON保存)
     */
    public static void restoreCalendarObject(Calendar cal, String iso8601text) {
        int[] dates = AppTopUtil.splitDateValue(iso8601text);
        cal.set(dates[0], dates[1] - 1 , dates[2]);
    }

    /**
     * 引数のカレンダーオブジェクトからLocalDateを生成する
     * @param cal カレンダーオブジェクト
     * @return LocalDate
     */
    public static LocalDate localDateOfCalendar(Calendar cal) {
        // LocalDate.of(year, month:1 - 12, dayOfMonth)
        return LocalDate.of(cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * カレンダー選択年月日とが異なるかチェック
     * @param calendarObj カレンダーオブジェクト年月日
     * @param selectedYear 選択された年
     * @param selectedMonth 選択された月
     * @param selectedDayOfMonth 選択された日
     * @return 異なったらtrue
     */
    public static boolean changeDateByDatePicker(Calendar calendarObj,
            int selectedYear, int selectedMonth, int selectedDayOfMonth) {
        // LocalDateのmonth=[1 - 12]
        // 変更前のカレンダーオブジェクトから古いLocalDateを取得
        LocalDate oldDate = localDateOfCalendar(calendarObj);
        // 変更予定のLocalDateを取得
        LocalDate newDate = LocalDate.of(
                selectedYear, selectedMonth + 1, selectedDayOfMonth);
        return !newDate.isEqual(oldDate);
    }

    /**
     * 処理日付がプリファレンス日付より最新かどうか判定する
     * <p>True: 測定日付 > プリファレンス日付</p>
     * @param processDate 処理日付 (必須)
     * @param prefDate プリファレンス日付 (任意: 登録済み日付無し)
     * @return プリファレンス日付がnullまたはより最新ならtrue, 等しいか過去ならfalse
     */
    public static boolean morelatestInPrefDate(String processDate, String prefDate) {
        if (TextUtils.isEmpty(prefDate)) {
            // プリファレンス日付が未設定なら最新
            return true;
        }

        int[] processArray = splitDateValue(processDate);
        int[] prefArray = splitDateValue(prefDate);
        LocalDate processLocal = LocalDate.of(processArray[0], processArray[1], processArray[2]);
        LocalDate prefLocal = LocalDate.of(prefArray[0], prefArray[1], prefArray[2]);
        return processLocal.isAfter(prefLocal);
    }

    /**
     * カレンダー選択日が登録済み以下か判定する
     * @param selectedLocal カレンダー選択日 (必須)
     * @param registeredDate 登録済み日付 (任意)
     * @return 登録済み日付がnullならtrue, それ以外は下記条件を満たした場合true<br/>
     *   カレンダー選択日 <= 登録済み日付 == not (カレンダー選択日 > 登録済み日付)
     */
    public static boolean isLessRegisteredDate(LocalDate selectedLocal, String registeredDate) {
        if (registeredDate == null) {
            return true;
        }

        int[] regArray = splitDateValue(registeredDate);
        LocalDate registeredLocal = LocalDate.of(regArray[0], regArray[1], regArray[2]);
        // selectedLocal.isAfter(registeredLocal) || selectedLocal.isEqual(registeredLocal);
        return !selectedLocal.isAfter(registeredLocal);
    }

    /**
     * 2つのbooleanを比較して異なるかどうか判定する
     * @param orgValue オリジナル値
     * @param newValue 新しい値
     * @return 異なればtrue
     */
    public static boolean isDifferentFlagValue(boolean orgValue, boolean newValue) {
        return orgValue != newValue;
    }

    /**
     * 2っの整数オブジェクトが異なるかチェックする
     * <p>両方の引数ともにnull可</p>
     * @param after 修正後の文字列
     * @param beforeValue 修正前の値オブジェクト
     * @return 異なればtrue
     */
    public static boolean isDifferentIntegerValue(String after, Integer beforeValue) {
        Integer afterValue;
        if (TextUtils.isEmpty(after)) {
            afterValue = null;
        } else {
            afterValue = Integer.valueOf(after);
        }
        return isDifferentIntegerValue(afterValue, beforeValue);
    }

    /**
     * 2っの整数オブジェクトが異なるかチェックする
     * <p>両方の引数ともにnull可</p>
     * @param afterValue 修正後の値オブジェクト
     * @param beforeValue 修正前の値オブジェクト
     * @return 異なればtrue
     */
    public static boolean isDifferentIntegerValue(Integer afterValue, Integer beforeValue) {
        if (afterValue != null && beforeValue != null) {
            return afterValue.compareTo(beforeValue) != 0;
        } else {
            return afterValue != null || beforeValue != null;
        }
    }

    /**
     * 2つの浮動小数点オブジェクトが異なるかチェックする
     * <p>2つのオブジェクトがnull以外ならオブジェクトを文字列化して比較する</p>
     * @param strAfter 浮動小数点数文字列 (空文字可)
     * @param before Double (null可)
     * @return 異なればtrue
     */
    public static boolean isDefferentDoubleValue(String strAfter, Double before) {
        if (strAfter== null && before == null) {
            return false;
        }
        // 2つの引数ともにnull以外ならDoubleに変換して比較する
        if (!TextUtils.isEmpty(strAfter) && before != null) {
            Double after = Double.parseDouble(strAfter);
            return after.compareTo(before) != 0;
        }
        // 上記以外は異なる
        return true;
    }

    /**
     * ウォニング時のレスポンスステータスとメッセージ変換用マップからステータス用の文字列を取得する
     * <pre>(例) Flaskアプリ側のBadRequest時の"message"の形式: errorCode + カンマ + エラー内容
     * {"status": { "code": 400, "message": "461,User is not found."}}
     * </pre>
     * @param responseStatus ウォニング時のレスポンスステータス
     * @param responseWarningMap メッセージ変換用マップ ※日本語メッセージに変換
     * @return ステータス用の文字列
     */
    public static String getWarningFromBadRequestStatus(ResponseStatus responseStatus,
                                                        Map<Integer, String> responseWarningMap) {
        String[] items = responseStatus.getMessage().split(",");
        int warningCode = Integer.parseInt(items[0]);
        String message = responseWarningMap.get(warningCode);
        if (message == null) {
            if (items.length > 1) {
                message = items[1];
            } else {
                message = responseStatus.getMessage();
            }
        }
        return message;
    }

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

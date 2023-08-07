package com.examples.android.healthcare.tasks;
import static java.nio.charset.StandardCharsets.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;

/**
 * リクエストパラメータビルダークラス
 * A-1. 登録・更新
 * A-2. 登録済みデータ取得
 *  全て必須
 *    emailAddress, measurementDay
 *
 * B. 画像取得
 * (*)は必須パラメータ
 * B-0.各データ共通
 *  emailAddress(*)
 * B-1.血圧測定データ
 *  B-1-1.月間データ
 *   [urlPath]
 *      /getplot_bloodpress_line_ym_forphone": 月間 折れ線グラフ
 *    yearMonth(*): 検索年月, userTarget: 血圧基準値
 *  B^1-2.２週間前
 *   [urlPath]
 *      /getplot_bloodpress_line_2w_forphone": 2週間 折れ線グラフ
 *      /getplot_bloodpress_bar_2w_forphone": 2週間 棒グラフ
 *    endDay(*): 検索最終日, userTarget: 血圧基準値
 *   当日データ("測定日,AM最高血圧,AM最低血圧,AM脈拍)有り
 *    endDay(*): 検索最終日, todayData: 当日データ, userTarget: 血圧基準値
 *
 * B-2.睡眠管理データ
 *  B-2-1.月間データ
 *   [urlPath]
 *      /getplot_sleepman_bar_ym_forphone": 月間 棒グラフ
 *    yearMonth(*): 検索年月
 *  B-2-2.２週間前
 *   [urlPath]
 *      /getplot_sleepman_bar_2w_forphone": 2週間 棒グラフ
 *    endDay(*): 検索最終日
 *   当日データ("測定日,起床時刻,夜間トイレ回数,睡眠時間,深い睡眠")
 *    endDay(*): 検索最終日, todayData: 当日データ
 *  B-2-3.指定期間の睡眠管理ヒストグラム
 *   [urlPath]
 *      /getplot_sleepman_histdual_range_forphone"
 *    startDay(*): 検索開始日, endDay(*): 検索最終日
 */
public class RequestParamBuilder {
    private static final String FMT_RARAM = "%s=%s";
    private final List<String> params;

    /**
     * コンストラクタ
     * @param emailAddress 全てのリクエストで必須
     */
    public RequestParamBuilder(String emailAddress) {
        this.params = new ArrayList<>();
        String reqParam = getEncodedEmailAddress(emailAddress);
        this.params.add(reqParam);
    }

    private String getEncodedEmailAddress(String raw) {
        String encoded = urlEncoded(raw);
        return String.format(FMT_RARAM, "emailAddress", encoded);
    }

    /**
     * 測定日を追加: 登録・更新, 登録データ取得時 (必須)
     * @param measurementDay 測定日
     * @return GetImageParamBuilder
     */
    public RequestParamBuilder addMeasurementDay(String measurementDay) {
        String encoded = urlEncoded(measurementDay);
        String reqParam = String.format(FMT_RARAM, "measurementDay", encoded);
        this.params.add(reqParam);
        return this;
    }

    /**
     * 検索対象年月を追加: 月間データの場合 (必須)
     * @param yearMonth 検索対象年月文字列
     * @return GetImageParamBuilder
     */
    public RequestParamBuilder addYearMonth(String yearMonth) {
        String encoded = urlEncoded(yearMonth);
        String reqParam = String.format(FMT_RARAM, "yearMonth", encoded);
        this.params.add(reqParam);
        return this;
    }

    /**
     * 検索開始日を追加
     * @param iso8601Date 検索開始日 ※ISO8601形式
     * @return GetImageParamBuilder
     */
    public RequestParamBuilder addStartDay(String iso8601Date) {
        String encoded = urlEncoded(iso8601Date);
        String reqParam = String.format(FMT_RARAM, "startDay", encoded);
        this.params.add(reqParam);
        return this;
    }

    /**
     * 検索終了日を追加
     * @param iso8601Date 検索終了日 ※ISO8601形式
     * @return GetImageParamBuilder
     */
    public RequestParamBuilder addEndDay(String iso8601Date) {
        String encoded = urlEncoded(iso8601Date);
        String reqParam = String.format(FMT_RARAM, "endDay", encoded);
        this.params.add(reqParam);
        return this;
    }

    /**
     * 血圧測定ユーザ目標値を追加
     * @param max 最高血圧値
     * @param min 最低血圧値
     * @return GetImageParamBuilder
     */
    public RequestParamBuilder addBloodPressUserTarget(int max, int min) {
        String encoded = urlEncoded(String.format("%d,%d", max, min));
        String reqParam = String.format(FMT_RARAM, "userTarget", encoded);
        this.params.add(reqParam);
        return this;
    }

    /**
     * 当日データbase&4エンコードしてクエリーパラメータに追加する
     * @param rowData 当日データ(カンマ区切り文字列)
     * @return GetImageParamBuilder
     */
    public RequestParamBuilder addTodayData(String rowData) {
        String encoded = Base64.getEncoder().encodeToString(
                rowData.getBytes(US_ASCII)
        );
        String reqParam = String.format(FMT_RARAM, "todayData", encoded);
        this.params.add(reqParam);
        return this;
    }

    /**
     * リクエストパラメータを生成する
     * @return 先頭に"?"がついたリクエストパラメータ
     */
    public String build() {
        String joinedParam = String.join("&", this.params);
        return "?" + joinedParam;
    }

    public void clear() {
        String firstParam = null;
        if (this.params.size() > 1) {
             firstParam = this.params.get(0);
        }
        this.params.clear();
        if (firstParam != null) {
            this.params.add(firstParam);
        }
    }

    /**
     * 引数の文字列をURLエンコードする
     * @param rawParam 通常の文字列
     * @return URLエンコード済み文字列
     */
    private static String urlEncoded(String rawParam) {
        try {
            return URLEncoder.encode(rawParam, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return rawParam;
        }
    }

}

package com.examples.android.healthcare.constants;

import com.examples.android.healthcare.data.HealthcareData;

public class JsonTemplate {
    /**
     * 更新用JSONメインテンプレート<BR>
     * [必須項目] 更新時の主キー<BR>
     *   emailAddress, measurementDay<BR>
     * [任意項目]<BR>
     *   健康管理データ (healthcareData):<BR>
     *     [sleepManagement|bloodPressure| bodyTemperature | nocturiaFactors| walkingCount]<BR>
     *   天候状態 (weatherCondition)
     */
    private static final String TEMPL_MAIN = "{\"emailAddress\":\"%s\",\"measurementDay\":\"%s\"," +
            "\"healthcareData\":{%s},\"weatherData\":{%s}}";
    // 1.健康管理データ
    // 1-1.睡眠管理
    private static final String HEALTH_SM = "\"sleepManagement\":%s";
    // 1-2.血圧測定
    private static final String HEALTH_BP = "\"bloodPressure\":%s";
    // 1-3.体温測定
    private static final String HEALTH_BT = "\"bodyTemperature\":%s";
    // 1-4.夜間頻尿要因
    private static final String HEALTH_NF = "\"nocturiaFactors\":%s";
    // 1-5.歩数
    private static final String HEALTH_WC = "\"walkingCount\":%s";
    // 2.天候状態
    private static final String WEATHER = "\"weatherCondition\": %s";

    /**
     * 更新用JSON文字列を生成する。
     * <p>健康管理データまたは天候テータのいずれかは必要</p>
     * @param eailAddress メールアドレス(必須)
     * @param messurementDay 測定日付(必須)
     * @param healthcareData 健康管理データ(任意): nullなら空文字設定
     * @param weatherData 天候データ(任意): nullなら空文字設定
     * @return 更新用JSON文字列
     */
    public static String createUpdateJson(String eailAddress, String messurementDay,
                                     String healthcareData, String weatherData) {
        String healthValue = (healthcareData != null) ? healthcareData : "" ;
        String weatherValue = (weatherData != null) ? weatherData : "";
        return String.format(TEMPL_MAIN, eailAddress, messurementDay, healthValue, weatherValue);
    }

    /**
     * 更新用の睡眠管理JSON文字列を生成する。
     * <p> "sleepManagement" プロパティで括った睡眠管理JSON文字列を生成</p>
     * @param sleepManJson 睡眠管理データJSON文字列 (必須)
     * @return 更新用の睡眠管理JSON文字列
     */
    public static String getJsonWithSleepManagement(String sleepManJson) {
        return String.format(HEALTH_SM, sleepManJson);
    }

    /**
     * 更新用の血圧測定JSON文字列を生成する。
     * <p> "bloodPressure" プロパティで括った血圧測定JSON文字列を生成</p>
     * @param bloodPressJson 血圧測定データJSON文字列 (必須)
     * @return 更新用の血圧測定JSON文字列
     */
    public static String getJsonWithBloodPressure(String bloodPressJson) {
        return String.format(HEALTH_BP, bloodPressJson);
    }

    /**
     * 更新用の体温測定JSON文字列を生成する。
     * <p> "bodyTemperature" プロパティで括った体温測定JSON文字列を生成</p>
     * @param bodyTemperJson 体温測定データJSON文字列 (必須)
     * @return 更新用の体温測定JSON文字列
     */
    public static String getJsonWithBodyTemperature(String bodyTemperJson) {
        return String.format(HEALTH_BT, bodyTemperJson);
    }

    /**
     * 更新用の夜間頻尿要因JSON文字列を生成する。
     * <p> "nocturiaFactors" プロパティで括った夜間頻尿要因JSON文字列を生成</p>
     * @param noctFactorsJson 夜間頻尿要因データJSON文字列 (必須)
     * @return 更新用の夜間頻尿要因JSON文字列
     */
    public static String getJsonWithNocturiaFactors(String noctFactorsJson) {
        return String.format(HEALTH_NF, noctFactorsJson);
    }

    /**
     * 更新用の歩数JSON文字列を生成する。
     * <p> "walkingCounte" プロパティで括った歩数JSON文字列を生成</p>
     * @param walkingCntJson 歩数データJSON文字列 (必須)
     * @return 更新用の歩数JSON文字列
     */
    public static String getJsonWithWalkingCount(String walkingCntJson) {
        return String.format(HEALTH_WC, walkingCntJson);
    }

    /**
     * 更新用の天候状態JSON文字列を生成する。
     * <p> "weatherCondition" プロパティで括った天候状態JSON文字列を生成</p>
     * @param weatherCondJson 天候データJSON文字列 (必須)
     * @return 更新用の天候状態JSON文字列
     */
    public static String getJsonWithWeatherCondition(String weatherCondJson) {
        return String.format(WEATHER, weatherCondJson);
    }

}

package com.examples.android.healthcare;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/**
 * SharedPreferencesユーティリィティクラス
 * Activity, Fragmentで共通でアクセスするメソッド定義
 */
public class SharedPrefUtil {

    /** 最高血圧・最低血圧のユーザー目標未選択 */
    private static final String BP_USER_TARGET_NONE = "-1";

    /**
     * コンテキストに属するSharedPreferencesを取得する
     * @param context Application | Activity
     * @param prefKey preference key
     * @return SharedPreferences
     */
    public static SharedPreferences getSharedPrefWithKey(Context context, String prefKey) {
        return context.getSharedPreferences(prefKey, Context.MODE_PRIVATE);
    }

    /**
     * AppTopFragmentが属するアクティビィティのプリファレンスを取得する
     * @param context Activityコンテキスト
     * @return プリファレンス
     */
    public static SharedPreferences getSharedPrefInMainActivity(Context context) {
        return getSharedPrefWithKey(context,
                context.getString(R.string.pref_app_top_fragment));
    }

    /**
     * 最終保存日をプリファレンスから取得する
     * @param context Activityコンテキスト
     * @return 最終保存日
     */
    public static String getLastSavedDate(Context context) {
        SharedPreferences sharedPref = getSharedPrefInMainActivity(context);
        String key = context.getString(R.string.pref_saved_key);
        return sharedPref.getString(key, null);
    }

    /**
     * 最新登録日をプリファレンスから取得する
     * @param context Activityコンテキスト
     * @return 最新登録日
     */
    public static String getLatestRegisteredDate(Context context) {
        SharedPreferences sharedPref = getSharedPrefInMainActivity(context);
        String key = context.getString(R.string.pref_registered_key);
        return sharedPref.getString(key, null);
    }

    /**
     * 初回登録日をプリファレンスから取得する
     * @param context Activityコンテキスト
     * @return 設定済みなら初回登録日
     */
    public static String getFirstRegisterDay(Context context) {
        SharedPreferences sharedPref = getSharedPrefInMainActivity(context);
        String key = context.getString(R.string.pref_first_register_day_key);
        return sharedPref.getString(key, null);
    }

    /**
     * 初回登録日をプリファレンスに保存する
     * @param context Activityコンテキスト
     * @param value 初回登録日
     */
    public static void saveFirstRegisterDay(Context context, String value) {
        SharedPreferences sharedPref = getSharedPrefInMainActivity(context);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(context.getString(R.string.pref_first_register_day_key), value);
        editor.apply();
    }

    /**
     * メールアドレスを取得する
     * @param context Activityコンテキスト
     * @return 設定済みならメールアドレス
     */
    public static String getEmailAddressInSettings(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                context);
        return prefs.getString(context.getString(R.string.pref_key_emailaddress),
                null);
    }

    /**
     * 最高血圧・最低血圧のユーザー目標値を取得する
     * <ul>
     *     <li>未選択の場合: 返却値は "-1"</li>
     *     <li>選択された場合: 返却値は整数文字列</li>
     * </ul>
     * @param context Activityコンテキスト
     * @return 両方とも未設定ならnull, それ以外はカンマ区切り文字列("最高血圧,最低血圧")
     */
    public static String getBloodPressUserTargetInSettings(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                context);
        // 最高血圧の目標値
        String maxValue = prefs.getString(context.getString(R.string.pref_key_bp_target_max),
                BP_USER_TARGET_NONE);
        // 最低血圧の目標値
        String minValue = prefs.getString(context.getString(R.string.pref_key_bp_target_min),
                BP_USER_TARGET_NONE);
        // 両方未設定なら
        if (maxValue.equals(BP_USER_TARGET_NONE) && minValue.equals(BP_USER_TARGET_NONE)) {
            return null;
        }
        return maxValue + "," + minValue;
    }
}

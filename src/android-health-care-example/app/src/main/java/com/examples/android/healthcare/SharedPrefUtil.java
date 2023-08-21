package com.examples.android.healthcare;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/**
 * SharedPreferencesユーティリィティクラス
 * Activity, Fragmentで共通でアクセスするメソッド定義
 */
public class SharedPrefUtil {

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
                context.getString(R.string.sharedpref_app_top_fragment));
    }

    /**
     * 最終保存日をプリファレンスから取得する
     * @param context Activityコンテキスト
     * @return 最終保存日
     */
    public static String getLastSavedDate(Context context) {
        SharedPreferences sharedPref = getSharedPrefInMainActivity(context);
        String key = context.getString(R.string.sharedpref_saved_key);
        return sharedPref.getString(key, null);
    }

    /**
     * 最新登録日をプリファレンスから取得する
     * @param context Activityコンテキスト
     * @return 最新登録日
     */
    public static String getLatestRegisteredDate(Context context) {
        SharedPreferences sharedPref = getSharedPrefInMainActivity(context);
        String key = context.getString(R.string.sharedpref_registered_key);
        return sharedPref.getString(key, null);
    }

    /**
     * メールアドレスを取得する
     * @param context Activityコンテキスト
     * @return 設定済みならメールアドレス
     */
    public static String getEmailAddressInMainPrefScreen(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                context);
        return prefs.getString(context.getString(R.string.pref_emailaddress_key),
                null);
    }

}

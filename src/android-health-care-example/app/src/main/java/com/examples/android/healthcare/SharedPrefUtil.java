package com.examples.android.healthcare;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferencesユーティリィティクラス
 * Activity, Fragmentで共通でアクセスするメソッド定義
 */
public class SharedPrefUtil {

    public static SharedPreferences getSharedPrefWithKey(Context context, String prefKey) {
        return context.getSharedPreferences(prefKey, Context.MODE_PRIVATE);
    }

    /**
     * AppTopFragmentが属するアクティビィティのプリファレンスを取得する
     * @return プリファレンス
     */
    public static SharedPreferences getSharedPrefInAppTop(Context context) {
        return getSharedPrefWithKey(context,
                context.getString(R.string.sharedpref_app_top_fragment));
    }

    /**
     * 最終保存日をプリファレンスから取得する
     * @return 最終保存日
     */
    public static String getLastSavedDate(Context context) {
        SharedPreferences sharedPref = getSharedPrefInAppTop(context);
        String key = context.getString(R.string.sharedpref_saved_key);
        return sharedPref.getString(key, null);
    }

    /**
     * 最新登録日をプリファレンスから取得する
     * @return 最新登録日
     */
    public static String getLatestRegisteredDate(Context context) {
        SharedPreferences sharedPref = getSharedPrefInAppTop(context);
        String key = context.getString(R.string.sharedpref_registered_key);
        return sharedPref.getString(key, null);
    }

}

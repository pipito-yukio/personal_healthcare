package com.examples.android.healthcare.ui.main;

import android.content.Context;
import android.telephony.TelephonyManager;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.examples.android.healthcare.constants.RequestDevice;
import com.examples.android.healthcare.dialogs.CustomDialogs;

public class FragmentUtil {

    // フラグメント位置
    public static final String FRAGMENT_POS_KEY = "fragPos";

    /**
     * ActionBarタイトルにフラグメントタイトルを設定
     * @param activity フラグメントコンテナアクティビィティ
     * @param title フラグメントタイトル
     */
    public static void setActionBarTitle(AppCompatActivity activity, String title) {
        ActionBar bar = activity.getSupportActionBar();
        assert bar != null;
        bar.setTitle(title);
    }

    /**
     * ActionBarサブタイトルにリクエスト中の文字列表示
     * @param activity フラグメントコンテナアクティビィティ
     * @param message リクエスト中の文字列
     */
    public static void showActionBarGetting(AppCompatActivity activity, String message) {
        ActionBar bar = activity.getSupportActionBar();
        assert bar != null;
        bar.setSubtitle(message);
    }

    /**
     * ActionBarサブタイトルに接続中のネットワーク種別表示
     * @param activity フラグメントコンテナアクティビィティ
     * @param device RequestDevice
     */
    public static void showActionBarResult(AppCompatActivity activity, RequestDevice device) {
        ActionBar bar = activity.getSupportActionBar();
        assert bar != null;
        // AppBarサブタイトルを更新
        if (device == RequestDevice.MOBILE) {
            TelephonyManager manager =
                    (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
            String operatorName = manager.getNetworkOperatorName();
            bar.setSubtitle(device.getMessage() + " (" + operatorName +")");
        } else {
            bar.setSubtitle(device.getMessage());
        }
    }

    /**
     * ネットワーク利用不可ダイアログ表示
     * @param activity フラグメントコンテナアクティビィティ
     * @param message メッセージ
     */
    public static void showDialogNetworkUnavailable(AppCompatActivity activity,
                                                    String message) {
        DialogFragment fragment = CustomDialogs.MessageOkDialogFragment.newInstance(null, message);
        fragment.show(activity.getSupportFragmentManager(), "MessageOkDialogFragment");
    }

    /**
     * メッセージダイアログ表示 ※OKボタンのみ
     * @param activity フラグメントコンテナアクティビィティ
     * @param title タイトル(任意)
     * @param message メッセージ
     * @param tagName FragmentTag
     */
    public static void showMessageDialog(AppCompatActivity activity,
                                         String title, String message, String tagName) {
        DialogFragment fragment = CustomDialogs.MessageOkDialogFragment.newInstance(title, message);
        fragment.show(activity.getSupportFragmentManager(), tagName);
    }

}

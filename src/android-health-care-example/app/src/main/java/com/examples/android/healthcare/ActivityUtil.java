package com.examples.android.healthcare;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.examples.android.healthcare.dialogs.CustomDialogs;

public class ActivityUtil {
    /**
     * メールアドレス必須ダイアログ
     * <p>トップフラグメント画面以外で使用</p>
     * <ol>
     * <li>OKボタン押下: メールアドレス設定アクティビィティに遷移する</li>
     * <li>取消しボタン押下: 何もしない</li>
     * </ol>
     * @param activity AppCompatActivity
     * @param fragmentPosition フラグメント位置 (0開始)
     */
    public static void showConfirmDialogWithEmailAddress(AppCompatActivity activity,
                                                         int fragmentPosition) {
        CustomDialogs.ConfirmDialogFragment.ConfirmOkCancelListener listener =
                new CustomDialogs.ConfirmDialogFragment.ConfirmOkCancelListener() {
                    @Override
                    public void onOk() {
                        Intent settingsIntent = new Intent(activity, SettingsActivity.class);
                        activity.startActivity(settingsIntent);
                    }

                    @Override
                    public void onCancel() {
                        // トップフラグメント以外でメールアドレスを設定しない場合はトップ画面に戻る
                        if (fragmentPosition > 0) {
                            activity.onBackPressed();
                        }
                    }
                };
        CustomDialogs.ConfirmDialogFragment fragment =
                CustomDialogs.ConfirmDialogFragment.newInstance(
                        activity.getString(R.string.warning_required_dialog_title),
                        activity.getString(R.string.warning_need_email_address),
                        listener);
        fragment.show(activity.getSupportFragmentManager(), "ConfirmDialogFragment");
    }

}

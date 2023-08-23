package com.examples.android.healthcare.ui.main;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.examples.android.healthcare.R;
import com.examples.android.healthcare.SettingsActivity;
import com.examples.android.healthcare.constants.RequestDevice;
import com.examples.android.healthcare.data.ResponseStatus;
import com.examples.android.healthcare.dialogs.CustomDialogs;
import com.examples.android.healthcare.dialogs.CustomDialogs.ConfirmDialogFragment.ConfirmOkCancelListener;

import java.util.HashMap;
import java.util.Map;

/**
 * AppXXXXFragment共通フラグメント
 */
public abstract class AppBaseFragment extends Fragment {

    // フラグメント位置キー
    public static final String FRAGMENT_POS_KEY = "fragPos";
    // ウォーニングメッセージ用マップ
    private final Map<Integer, String> mResponseWarningMap = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initResponseWarningMap();
    }

    @Override
    public void onResume() {
        // サブクラスのフラグメントタイトル
        setActionBarTitle(getFragmentTitle());
        super.onResume();
    }

    /**
     * レスポンス時のウォーニングメッセージ用マップのロード
     */
    private void initResponseWarningMap() {
        String[] warnings = getResources().getStringArray(R.array.warning_map);
        for (String item : warnings) {
            String[] items = item.split(",");
            Integer respCode = Integer.valueOf(items[0]);
            mResponseWarningMap.put(respCode, items[1]);
        }
    }

    /**
     * ウォニング時のレスポンスステータスとメッセージ変換用マップからステータス用の文字列を取得する
     * <pre>(例) Flaskアプリ側のBadRequest時の"message"の形式: errorCode + カンマ + エラー内容
     * {"status": { "code": 400, "message": "461,User is not found."}}
     * </pre>
     * @param responseStatus ウォニング時のレスポンスステータス
     * @return ステータス用の文字列
     */
    public String getWarningFromBadRequestStatus(ResponseStatus responseStatus) {
        String[] items = responseStatus.getMessage().split(",");
        int warningCode = Integer.parseInt(items[0]);
        String message = mResponseWarningMap.get(warningCode);
        if (message == null) {
            if (items.length > 1) {
                message = items[1];
            } else {
                message = responseStatus.getMessage();
            }
        }
        return message;
    }

    //** START ActionBar methods **************************************
    /**
     * Get ActionBar
     * @return androidx.appcompat.app.ActionBar
     */
    private ActionBar getActionBar() {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        assert activity != null;
        return activity.getSupportActionBar();
    }

    /**
     * ActionBarタイトルにフラグメントタイトルを設定
     * @param title フラグメントタイトル
     */
    private void setActionBarTitle(String title) {
        ActionBar bar = getActionBar();
        assert bar != null;
        bar.setTitle(title);
    }

    /**
     * ActionBarサプタイトルにメッセージを設定
     * @param message メッセージ
     */
    private void setActionBarSubTitle(String message) {
        ActionBar bar = getActionBar();
        assert bar != null;
        bar.setSubtitle(message);
    }

    /**
     * リクエスト開始メッセージを設定する
     * <p>ActionBarサブタイトルに開始メッセージを表示</p>
     * @param message リクエスト開始メッセージ
     */
    public void setRequestStart(String message) {
        setActionBarSubTitle(message);
    }

    /**
     * リクエスト完了時にネットワーク種別を表示
     * <p>ActionBarサブタイトルにネットワーク種別を表示</p>
     * @param device RequestDevice
     */
    public void setRequestComplete(RequestDevice device) {
        String networkType;
        if (device == RequestDevice.MOBILE) {
            TelephonyManager manager =
                    (TelephonyManager) requireActivity().getSystemService(
                            Context.TELEPHONY_SERVICE);
            String operatorName = manager.getNetworkOperatorName();

            networkType = device.getMessage() + " (" + operatorName +")";
        } else {
            networkType = device.getMessage();
        }
        // AppBarサブタイトル更新
        setActionBarSubTitle(networkType);
    }
    //** END ActionBar methods **************************************

    //** START Show DialogFragment methods **************************
    /**
     * メッセージダイアログ表示 ※OKボタンのみ
     * @param title タイトル(任意)
     * @param message メッセージ
     * @param tagName FragmentTag
     */
    public void showMessageOkDialog(String title, String message, String tagName) {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        DialogFragment fragment = CustomDialogs.MessageOkDialogFragment.newInstance(title, message);
        assert activity != null;
        fragment.show(activity.getSupportFragmentManager(), tagName);
    }

    /**
     * ネットワーク利用不可ダイアログ表示
     */
    public void showDialogNetworkUnavailable() {
        // タイトルなし
        showMessageOkDialog(null, getString(R.string.warning_network_not_available),
                "MessageOkDialogFragment");
    }

    /**
     * 例外メッセージ表示ダイアログ
     * @param exp Exception
     */
    public void showDialogExceptionMessage(Exception exp) {
        String errorMessage = String.format(
                getString(R.string.exception_with_reason),
                exp.getLocalizedMessage());
        showMessageOkDialog(getString(R.string.error_response_dialog_title), errorMessage,
                "ExceptionDialogFragment");
    }

    /**
     * メールアドレス必須確認ダイアログ
     * <ol>
     * <li>OKボタン押下: メールアドレス設定アクティビィティに遷移する</li>
     * <li>取消しボタン押下: 何もしない</li>
     * </ol>
     */
    public void showConfirmRequireEmailAddress() {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        assert activity != null;
        ConfirmOkCancelListener listener = new ConfirmOkCancelListener() {
            @Override
            public void onOk() {
                Intent settingsIntent = new Intent(activity, SettingsActivity.class);
                activity.startActivity(settingsIntent);
            }

            @Override
            public void onCancel() {
                // トップフラグメント以外でメールアドレスを設定しない場合はトップ画面に戻る
                if (getFragmentPosition() > 0) {
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

    /**
     * ウォーニングをウォーニング用ステータスビューに表示する
     * @param warning ウォーニング用ステータスビュー
     */
    public void showWarningInStatusView(TextView statusView, String warning) {
        statusView.setText(warning);
        if (statusView.getVisibility() != View.VISIBLE) {
            statusView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * ウォーニング用ステータスビューを隠す
     * @param statusView ウォーニング用ステータスビュー
     */
    public void hideStatusView(TextView statusView) {
        if (statusView.getVisibility() == View.VISIBLE) {
            statusView.setText("");
            statusView.setVisibility(View.GONE);
        }
    }

    /**
     * ネットワーク利用不可メッセージをウォーニング用ステータスビューに表示する
     * <p>暗黙的なネットワークリクエスト時に利用</p>
     * @param statusView ウォーニング用ステータスビュー
     */
    public void showNetworkUnavailableInStatus(TextView statusView) {
        String warning = getString(R.string.warning_network_not_available);
        showWarningInStatusView(statusView, warning);
    }
    //** END Show DialogFragment methods ****************************

    /**
     * トースト表示
     * @param message メッセージ
     */
    public void showToast(String message, int toastLength) {
        Toast toast = Toast.makeText(this.getContext(), message, toastLength);
        toast.show();
    }

    /**
     * 長めのトースト表示
     * @param message メッセージ
     */
    public void showLongToast(String message) {
        showToast(message, Toast.LENGTH_LONG);
    }

    //** サブクラスで実装しなければならないメソッド ******
    /**
     * サブクラスのフラグメントタイトルを取得する
     * @return フラグメントタイトル
     */
    public abstract String getFragmentTitle();

    /**
     * サブラクスのViewPager2用フラグメントインデックスを取得する
     * @return フラグメントインデックスを取得する
     */
    public abstract int getFragmentPosition();

}

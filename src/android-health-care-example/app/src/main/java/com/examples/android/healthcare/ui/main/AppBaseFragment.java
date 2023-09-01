package com.examples.android.healthcare.ui.main;

import static com.examples.android.healthcare.functions.MyLogging.DEBUG_OUT;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.examples.android.healthcare.HealthcareApplication;
import com.examples.android.healthcare.R;
import com.examples.android.healthcare.SettingsActivity;
import com.examples.android.healthcare.SharedPrefUtil;
import com.examples.android.healthcare.constants.RequestDevice;
import com.examples.android.healthcare.data.GetRegisterDaysResult;
import com.examples.android.healthcare.data.ResponseRegisterDays;
import com.examples.android.healthcare.data.ResponseStatus;
import com.examples.android.healthcare.dialogs.CustomDialogs;
import com.examples.android.healthcare.dialogs.CustomDialogs.ConfirmDialogFragment.ConfirmOkCancelListener;
import com.examples.android.healthcare.functions.FileManager;
import com.examples.android.healthcare.tasks.GetRegisterDaysRepository;
import com.examples.android.healthcare.tasks.HealthcareRepository;
import com.examples.android.healthcare.tasks.NetworkUtil;
import com.examples.android.healthcare.tasks.RequestParamBuilder;
import com.examples.android.healthcare.tasks.Result;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * AppXXXXFragment共通フラグメント
 */
public abstract class AppBaseFragment extends Fragment {
    private static final String TAG = AppBaseFragment.class.getSimpleName();

    /** ISO8601形式日付文字列比較結果 */
    public enum CompareISO8601Date {
        NONE/*比較不能(比較対象にnullが含まれる場合) */, GT, EQ, LT
    }

    // フラグメント位置キー
    public static final String FRAGMENT_POS_KEY = "fragPos";
    // 初期表示用イメージ画像ファイル名
    private static final String NO_IMAGE_FILE = "NoImage_500x700.png";
    // 初期画面ビットマップ
    private Bitmap mNoImageBitmap;
    // ファイル保存、プリファレンスコミット時に利用するハンドラー
    private final Handler mHandler = new Handler();
    // 画像取得リクエスト時のヘッダー用の表示デバイス情報
    private DisplayMetrics mMetrics;

    // ウォーニングメッセージ用マップ
    private final Map<Integer, String> mResponseWarningMap = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initResponseWarningMap();
    }

    /**
     * 初回登録日の存在チェックし、存在しない場合は暗黙的にリクエストする
     * <p>非同期で実行されるためタイミングによってはサブクラスが取得できない場合がある</p>
     */
    private void requestFirstRegisterDayImplicitly(String emailAddress) {
        String firstRegisterDay = SharedPrefUtil.getFirstRegisterDay(requireContext());
        DEBUG_OUT.accept(TAG, "firstRegisterDay: " + firstRegisterDay);
        if (TextUtils.isEmpty(firstRegisterDay)) {
            // 暗黙的にサーバーにリクエストする
            RequestDevice device =  NetworkUtil.getActiveNetworkDevice(requireContext());
            if (device == RequestDevice.NONE) {
                // アクションバーのサブタイトルにメッセージを出力する
                showNetworkUnavailableInStatus(getWaringView());
                return;
            }

            HealthcareApplication app = (HealthcareApplication) requireActivity().getApplication();
            String requestUrl = app.getmRequestUrls().get(device.toString());
            Map<String, String> headers = app.getRequestHeaders();
            // ユーザの初回登録日取得
            HealthcareRepository<GetRegisterDaysResult> repos = new GetRegisterDaysRepository();
            String reqParam = new RequestParamBuilder(emailAddress).build();
            repos.makeGetRequest(0, requestUrl, reqParam, headers,
                    app.mEexecutor, app.mHandler, (result) -> {
                        if (result instanceof Result.Success) {
                            GetRegisterDaysResult daysResult =
                                    ((Result.Success<GetRegisterDaysResult>) result).get();
                            ResponseRegisterDays days = daysResult.getData();
                            DEBUG_OUT.accept(TAG, "ResponseRegisterDays: " + days);
                            String firstDay = days.getFirstDay();
                            if (firstDay != null) {
                                // プリファレンスに保存する
                                SharedPrefUtil.saveFirstRegisterDay(requireContext(), firstDay);
                            }
                        } else if (result instanceof Result.Warning) {
                            // ウォーニングメッセージをログに出力
                            ResponseStatus status =
                                    ((Result.Warning<?>) result).getResponseStatus();
                            Log.w(TAG, "WarningStatus: " + status);
                            showWarningInStatusView(getWaringView(),
                                    getWarningFromBadRequestStatus(status));
                        } else if (result instanceof Result.Error) {
                            // 例外メッセージをダイアログに表示
                            Exception exception = ((Result.Error<?>) result).getException();
                            Log.w(TAG, "Exception: " + exception);
                            showDialogExceptionMessage(exception);
                        }
                    });
        }
    }

    @Override
    public void onResume() {
        DEBUG_OUT.accept(TAG, "onResume()");

        // サブクラスのフラグメントタイトルをアクションバータイトルに設定
        setActionBarTitle(getFragmentTitle());

        // メールアドレスチェック
        String emailAddress = SharedPrefUtil.getEmailAddressInSettings(requireContext());
        if (TextUtils.isEmpty(emailAddress)) {
            // メールアドレス登録ダイアログへ
            showConfirmRequireEmailAddress();
            // サブラクスのonResume()
            super.onResume();
            return;
        }

        // 暗黙的にユーザの初回登録日を取得する
        requestFirstRegisterDayImplicitly(emailAddress);
        // 画像表示系フラグメントのみ初期イメージを取得する
        if (getFragmentPosition() > 0) {
            // 前回表示されたウォーニングビューを隠す
            hideStatusView(getWaringView());
                mMetrics = new DisplayMetrics();
                requireActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
                DEBUG_OUT.accept(TAG, "" + mMetrics);

                // 初期画面ビットマップイメージ
                if (mNoImageBitmap == null) {
                    AssetManager am = requireContext().getAssets();
                    try {
                        mNoImageBitmap = BitmapFactory.decodeStream(am.open(NO_IMAGE_FILE));
                        DEBUG_OUT.accept(TAG, "mNoImageBitmap: " + mNoImageBitmap);
                        ImageView iv = getImageView();
                        if (iv != null) {
                            iv.setImageBitmap(mNoImageBitmap);
                        }
                    }catch (IOException iex) {
                        // 通常ここには来ない
                        Log.w(TAG, iex.getLocalizedMessage());
                    }
                }

                // DEBUG 保存されている画像ファイル
                String fileNames = FileManager.checkFileNamesInContextDir(requireContext());
                DEBUG_OUT.accept(TAG, "Context.FilesDir in [ " + fileNames + "]");
                // DEBUG プリファレンスデータ
                SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                        requireContext()
                );
                Map<String, ?> prefAll = sharedPref.getAll();
                DEBUG_OUT.accept(TAG, "prefAll: " + prefAll);
        }

        // サブラクスのonResume()
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
     * ファイル保存時、プリファレンスcommit()呼び出し時のHandler取得
     * @return Handler
     */
    public Handler getHandler() {
        return mHandler;
    }

    /**
     * デバイスのDisplayMetricsを取得する
     * <ul>
     *     <l>血圧測定データグラフ表示</l>
     *     <l>睡眠管理データグラフ表示</l>
     * </ul>
     * @return デバイスのDisplayMetrics
     */
    public DisplayMetrics getDisplayMetrics() {
        assert mMetrics != null;
        return mMetrics;
    }

    /**
     * このフラグメントが所属するアクティビィティのプリファレンスオブジェクトを取得する
     * @return アクティビィティのプリファレンスオブジェクト
     */
    public SharedPreferences getSharedPreferences () {
        return SharedPrefUtil.getSharedPrefInMainActivity(requireContext());
    }

    /**
     * メールアドレスをSettingsから取得する
     * @return メールアドレス、プリファレンスに存在しなければnull
     */
    public String getEmailAddress() {
        return SharedPrefUtil.getEmailAddressInSettings(requireContext());
    }

    /**
     * 初回登録日を取得する
     * @return 初回登録日、プリファレンスに存在しなければnull
     */
    public String getFirstRegisterDay() {
        return SharedPrefUtil.getFirstRegisterDay(requireContext());
    }

    /**
     * 最後に一時保存した日付を取得する
     * @return 一時保存した日付、プリファレンスに存在しなければnull
     */
    public String getLastSavedDate() {
        return SharedPrefUtil.getLastSavedDate(requireContext());
    }

    /**
     * 最新の登録済み日付を取得する
     * @return 最新の登録済み日付、プリファレンスに存在しなければnull
     */
    public String getLatestRegisteredDate() {
        return SharedPrefUtil.getLatestRegisteredDate(requireContext());
    }

    /**
     * ２つのISO8601形式日付を比較する
     * @param iso8601Date1 日付1
     * @param iso8601Date2 日付2
     * @return CompareISO8601Date
     */
    public CompareISO8601Date compareISO8601DateStr(String iso8601Date1, String iso8601Date2) {
        if (iso8601Date1 != null && iso8601Date2 != null) {
            int compDate1 = Integer.parseInt(iso8601Date1.replace("-", ""));
            int compDate2 = Integer.parseInt(iso8601Date2.replace("-", ""));
            if (compDate1 > compDate2) {
                return CompareISO8601Date.GT;
            } else if (compDate1 == compDate2) {
                return CompareISO8601Date.EQ;
            } else {
                return CompareISO8601Date.LT;
            }
        }
        return CompareISO8601Date.NONE;
    }

    /**
     * 初期画面ビットマップを取得する
     * @return 初期画面ビットマップ
     */
    public Bitmap getNoImageBitmap() {
        return mNoImageBitmap;
    }

    /**
     * ウォニング時のレスポンスステータスとメッセージ変換用マップからステータス用の文字列を取得する
     * <pre>例 (1) Flaskアプリ側のBadRequest時の"message"の形式: errorCode + カンマ + エラー内容
     * {"status": { "code": 400, "message": "461,User is not found."}}
     * </pre>
     * <pre>例 (2) Flaskシステムがスローする例外の場合はメッセージのみとなる
     * {"status": { "code": 404, "message": "he requested URL was not found on the server. ..."}}
     * </pre>
     * @param responseStatus ウォニング時のレスポンスステータス
     * @return ステータス用の文字列
     */
    public String getWarningFromBadRequestStatus(ResponseStatus responseStatus) {
        String[] items = responseStatus.getMessage().split(",");
        String message;
        if (items.length > 1) {
            // コード付きメッセージ
            try {
                int warningCode = Integer.parseInt(items[0]);
                message = mResponseWarningMap.get(warningCode);
                if (message == null) {
                    // マップに未定義なら2つ目の項目 ※Androidアプリ側のBUGの可能性
                    message = items[1];
                }
            } catch (NumberFormatException e) {
                // 先頭が数値以外ならFlaskアプリ側BUGの可能性
                message = items[0];
            }
        } else if (items.length == 1) {
            // メッセージのみ ※Flask (404 URL Not found | 500 InternalError)
            message = items[0];
        } else {
            // 想定しないエラーの場合
            message = responseStatus.getMessage();
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
    public void showRequestComplete(RequestDevice device) {
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
     * サブラクスのViewPager2用フラグメントインデックスを取得する
     * <p>全てのサブクラスで必須</p>
     * @return フラグメントインデックス
     */
    public abstract int getFragmentPosition();

    /**
     * サブクラスのフラグメントタイトルを取得する
     * <p>全てのサブクラスで必須</p>
     * @return フラグメントタイトル
     */
    public abstract String getFragmentTitle();

    /**
     * 画像取得系サブクラスのImageViewの参照を取得する
     * <ul>
     *     <li>画像表示系フラグメントは必須</li>
     *     <li>登録系フラグメントはnull</li>
     * </ul>
     * @return 画像表示用のImageView
     */
    public abstract ImageView getImageView();

    /**
     * 画像取得系サブクラスのウォーニングビューを取得する
     * <ul>
     *     <li>画像表示系フラグメントは必須</li>
     *     <li>登録系フラグメントはnull</li>
     * </ul>
     * @return ウォーニングビュー
     */
    public abstract TextView getWaringView();

}

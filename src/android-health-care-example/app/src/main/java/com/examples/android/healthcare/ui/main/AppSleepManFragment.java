package com.examples.android.healthcare.ui.main;

import static com.examples.android.healthcare.functions.MyLogging.DEBUG_OUT;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.examples.android.healthcare.HealthcareApplication;
import com.examples.android.healthcare.R;
import com.examples.android.healthcare.SharedPrefUtil;
import com.examples.android.healthcare.constants.RequestDevice;
import com.examples.android.healthcare.data.SleepManStatistics;
import com.examples.android.healthcare.data.GetImageDataResult;
import com.examples.android.healthcare.data.NocturiaFactors;
import com.examples.android.healthcare.data.RegisterData;
import com.examples.android.healthcare.data.ResponseImageData;
import com.examples.android.healthcare.data.ResponseStatus;
import com.examples.android.healthcare.data.SleepManagement;
import com.examples.android.healthcare.functions.AppImageFragUtil;
import com.examples.android.healthcare.tasks.GetSleepManImageRepository;
import com.examples.android.healthcare.tasks.NetworkUtil;
import com.examples.android.healthcare.tasks.RequestParamBuilder;
import com.examples.android.healthcare.tasks.Result;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * 睡眠管理データプロット画像表示フラグメント
 * create an instance of this fragment.
 */
public class AppSleepManFragment extends AppBaseFragment {
    private static final String TAG = AppSleepManFragment.class.getSimpleName();
    // 当日データフォーマット: "測定日,起床時刻,夜間トイレ回数,睡眠スコア,睡眠時間,深い睡眠"
    private static final String FMT_2W_TODAY = "%s,%s,%d,%d,%s,%s";
    // 統計情報フォーマット (前空白の4桁)
    private static final String FMT_STAT_VALUE = "%4d";

    // URLパスインデックス
    enum UrlPathIndex {
        BAR_YM(0, "月間 棒グラフ"),
        BAR_2W(1, "２週間前 棒グラフ"),
        HIST_RANGE(2, "期間 ヒストグラム");

        private final int num;
        private final String name;
        UrlPathIndex(int num, String name) {
            this.num = num;
            this.name = name;
        }
        public int getNum() { return num; }
        public String getName() {return name; }
    }

    // プロット画像用ImageView
    private ImageView mImgView;
    // 検索期間(月間/２週間前) ラジオグループ
    private RadioGroup mRGrpDateRange;
    // 最新取得ボタン
    private Button mBtnGetRequest;
    // 月間ラジオボタン
    private RadioButton mRadioYM;
    // ２週間前ラジオボタン
    private RadioButton mRadio2w;
    // ヒストグラム(期間)
    private RadioButton mRadioHistRange;
    // 当日データ含む
    private CheckBox mChkIncludeToday;
    // 年月選択スピナー
    private Spinner mSpinnerYM;
    // 期間(開始)選択スピナー
    private Spinner mSpinnerRangeFrom;
    // 期間(終了)選択スピナー
    private Spinner mSpinnerRangeTo;
    // 全スピナー配列 ※まとめて設定する場合に使用
    private Spinner[] mAllSpinners;
    // 年月選択スピナーアダブター
    private ArrayAdapter<String> mSpinnerAdapter;
    // 画像保存チェック
    private CheckBox mChkSaveImg;
    // 統計情報(平均値)用ウィジット
    // レコード件数
    private TextView mTvRecCount;
    // 睡眠時間
    private TextView mTvSleepingTime;
    // 深い睡眠時間
    private TextView mTvDeepSleepingTime;
    // ウォーニング用ステータス
    private TextView mWarningStatus;
    // 年月スピナー選択値を保持するオブジェクト
    private AppImageFragUtil.SpinnerSelected mSpinnerYMSelected;
    // 期間(開始)スピナー選択値を保持するオブジェクト
    private AppImageFragUtil.SpinnerSelected mSpinnerFromSelected;
    // 期間(終了)スピナー選択値を保持するオブジェクト
    private AppImageFragUtil.SpinnerSelected mSpinnerToSelected;
    // 画像の保存ファイル名配列
    private String[] mSaveImageNames;
    // 画像のプリファレンス保存ファイル名配列
    private String[] mPrefSaveImageKeys;
    // 保存する統計情報のプリファレンスキー配列
    private String[] mPrefStatisticsKeys;

    /**
     * コンストラクタ
     * @param fragPosIdx フラグメント位置インデックス
     * @return このフラグメント
     */
    public static AppSleepManFragment newInstance(int fragPosIdx) {
        AppSleepManFragment frag = new AppSleepManFragment();
        Bundle args = new Bundle();
        args.putInt(FRAGMENT_POS_KEY, fragPosIdx);
        frag.setArguments(args);
        return frag;
    }

    //** START implements abstract methods **************************
    @Override
    public int getFragmentPosition() {
        assert getArguments() != null;
        return getArguments().getInt(FRAGMENT_POS_KEY, 2);
    }

    @Override
    public String getFragmentTitle() {
        return getString(R.string.imgfrag_sm_app_title);
    }

    public ImageView getImageView() {
        assert mImgView != null;
        return mImgView;
    }

    @Override
    public TextView getWaringView() {
        assert mWarningStatus != null;
        return mWarningStatus;
    }
    //** END implements abstract methods ****************************

    /**
     * 睡眠管理データ可視化リクエストパスインデックスを取得する
     * @return パスインデックス
     */
    private UrlPathIndex getUrlPathIndex() {
        UrlPathIndex result;
        if (mRadioYM.isChecked()) {
            result = UrlPathIndex.BAR_YM;
        } else if (mRadioHistRange.isChecked()) {
            // ヒストグラム
            result = UrlPathIndex.HIST_RANGE;
        } else {
            // ２週間
            result = UrlPathIndex.BAR_2W;
        }
        return result;
    }

    /**
     * 分を時刻文字列に変換
     * @param minutes 分
     * @return 時刻文字列("H/MI")
     */
    private String minutesToStringTime(int minutes) {
        int hour = minutes / 60;
        int min = minutes % 60;
        return String.format(Locale.US,"%d:%02d", hour, min);
    }

    /**
     * 睡眠管理データの当日データを取得する
     * @return 一時保存ファイルが存在しかつ睡眠管理データがあれば当日データ文字列, なければnull
     */
    private String getTodayData() {
        // 一時保存ファイルから復元した登録用データオブジェクトを取得
        RegisterData todayRegData = AppImageFragUtil.getRegisterDataFromJson(
                requireContext(),
                getString(R.string.last_saved_json_file));
        if (todayRegData != null) {
            String measurementDay = todayRegData.getMeasurementDay();
            // 睡眠管理データ
            SleepManagement sm = todayRegData.getHealthcareData().getSleepManagement();
            // 夜間頻尿要因データ
            NocturiaFactors factors = todayRegData.getHealthcareData().getNocturiaFactors();
            DEBUG_OUT.accept(TAG,
                    "measurementDay: " + measurementDay + ", SleepMan: " + sm
                            + ",toiletVisits: " + factors.getMidnightToiletVisits());
            // 一時保存では測定日,起床時刻,夜間トイレ回数は必須なのでチェックは不要
            // 測定日[0],起床時間[1],夜間トイレ回数[2],睡眠スコア[3],睡眠時間[4],深い睡眠[5]
            //  睡眠スコアがnull ->> -1
            int sleepScore = (sm.getSleepScore() != null) ? sm.getSleepScore() : -1;
            //  深い睡眠がnull ->> 初期値 "00:00"を設定する
            //  ※Pandas-Matplotlib: 睡眠時間のみの棒を描画するには、睡眠時間と深い睡眠の差分が必要
            String deepSleeping = (sm.getDeepSleepingTime() != null)
                    ? sm.getDeepSleepingTime() : getString(R.string.init_tag_time_value);
            String result = String.format(Locale.US, FMT_2W_TODAY, measurementDay,
                    sm.getWakeupTime(), factors.getMidnightToiletVisits(),
                    sleepScore, sm.getSleepingTime(), deepSleeping);
            DEBUG_OUT.accept(TAG, "todayData: " + result);
            return result;
        }

        return null;
    }

    //** START request with repository *****************************
    /**
     * 画像取得用のリクエストパラメータ生成
     * @param emailAddress メールアドレス
     * @return リクエストパラメータ文字列
     */
    private String makeRequestParameter(String emailAddress) {
        RequestParamBuilder builder = new RequestParamBuilder(emailAddress);
        if (mRadioYM.isChecked()) {
            // 月間データ: 年月スピナーで選択されたオブジェクトから値を取得
            String value = mSpinnerYMSelected.getValue();
            // リクエスト用に区切りをハイフンに置き換える
            String reqYearMonth = value.replace("/", "-");
            builder.addYearMonth(reqYearMonth);
        } else if (mRadioHistRange.isChecked()) {
            // ヒストグラム: 開始スピナーから開始日="年月/01",終了スピナーから終了日="年月/末日"
            String fromYM = mSpinnerFromSelected.getValue();
            String startDay = fromYM.replace("/", "-") + "-01";
            String toYM = mSpinnerToSelected.getValue();
            String reqToYm = toYM.replace("/", "-");
            String endDay = AppImageFragUtil.getLastDayInYearMonth(reqToYm);
            builder.addStartDay(startDay).addEndDay(endDay);
        } else {
            // ２週間は棒グラフのみ: 検索終了日に昨日を設定
            String yesterday = AppImageFragUtil.getYesterday();
            builder.addEndDay(yesterday);
            // 当日データがあれば追加する
            if (mChkIncludeToday.isChecked()) {
                String todayData = getTodayData();
                if (todayData != null) {
                    builder.addTodayData(todayData);
                }
            }
        }
        return builder.build();
    }

    /**
     * ウォーニングメッセージを取得
     * @param status レスポンスステータス
     * @return ウォーニングメッセージ
     */
    private String getResponseWarning(ResponseStatus status) {
        String message;
        switch (status.getCode()) {
            case 400: // BadRequest: リクエストパラメータなどの不備
                message = getWarningFromBadRequestStatus(status);
                break;
            case 404: // NotFound: データ未登録
                message = getString(R.string.warning_data_not_found);
                break;
            default: // 50x系エラー
                message = status.getMessage();
        }
        return message;
    }

    /**
     * 期間(終)は期間(始)以上であること
     * @return エラーがある場合はエラーメッセージ、ない場合はnull
     */
    private String checkHistRangeSelections() {
        String selectedFrom = mSpinnerFromSelected.getValue();
        String selectedTo = mSpinnerToSelected.getValue();
        if (!TextUtils.isEmpty(selectedFrom) && !TextUtils.isEmpty(selectedTo)) {
            String sFrom = selectedFrom.replace("/", "");
            String sTo = selectedTo.replace("/", "");
            int fromVal = Integer.parseInt(sFrom);
            int toVal = Integer.parseInt(sTo);
            if (fromVal > toVal) {
                return getString(R.string.warning_imgfrag_sm_hist_range);
            }
        } else {
            return getString(R.string.warning_imgfrag_sm_hist_not_selected);
        }

        return null;
    }

    /**
     * 画像取得リクエスト
     */
    private void getImageRequest() {
        // 期間ラジオボタンによって必要な値が設定されているかチェックする
        if (mRadioHistRange.isChecked()) {
            String chkMsg = checkHistRangeSelections();
            if (chkMsg != null) {
                showMessageOkDialog(getString(R.string.title_imgfrag_sm_hist), chkMsg,
                        "WaringDialog");
                return;
            }
        }

        // ネットワークデバイスが無効なら送信しない
        RequestDevice device =  NetworkUtil.getActiveNetworkDevice(requireContext());
        if (device == RequestDevice.NONE) {
            showDialogNetworkUnavailable();
            return;
        }

        mBtnGetRequest.setEnabled(false);
        // リクエスト開始メッセージを設定する
        setRequestStart(getString(R.string.msg_gettting_graph));

        // アプリケーション取得
        HealthcareApplication app = (HealthcareApplication) requireActivity().getApplication();
        String requestUrl = app.getmRequestUrls().get(device.toString());
        // 画像取得リクエスト用リポジトリ
        GetSleepManImageRepository repos = new GetSleepManImageRepository();
        // ImageViewサイズとDisplayMetrics.densityをリクエストヘッダに追加する
        Map<String, String> headers = app.getRequestHeaders();
        AppImageFragUtil.appendImageSizeToHeaders(headers,
                mImgView.getWidth(), mImgView.getHeight(), getDisplayMetrics().density);
        // メールアドレス取得 ※画像取得フラグメントはメールアドレス必須のため、存在しない場合ここに来ない
        String emailAddress = SharedPrefUtil.getEmailAddressInSettings(requireContext());
        // プロット期間からURLパスインデックスを取得
        UrlPathIndex urlPathIdx = getUrlPathIndex();
        // 選択されたラジオボタンからリクエストパラメータを生成する
        String reqParam = makeRequestParameter(emailAddress);
        DEBUG_OUT.accept(TAG, "reqParam: " + reqParam);

        repos.makeGetRequest(urlPathIdx.getNum(), requestUrl, reqParam, headers,
                app.mEexecutor, app.mHandler, (result) -> {
                    // ボタン復帰
                    mBtnGetRequest.setEnabled(true);
                    // リクエスト完了時にネットワーク種別を表示
                    showRequestComplete(device);

                    if (result instanceof Result.Success) {
                        GetImageDataResult imageResult =
                                ((Result.Success<GetImageDataResult>) result).get();
                        ResponseImageData data = imageResult.getData();
                        DEBUG_OUT.accept(TAG, "data: " + data);
                        // 統計情報を取得する ※取得件数が0件なら No Image
                        SleepManStatistics stat = repos.getStatistics(data);
                        if (stat != null && stat.getRecCount() > 0) {
                            showStatistics(stat);
                            showSuccess(urlPathIdx, data);
                            // 画像保存可否
                            if (mChkSaveImg.isChecked()) {
                                // 選択日付をプリファレンスに保存
                                if (mRadioYM.isChecked()) {
                                    // 月間
                                    saveSelectedSpinnerValueInSharedPref(
                                            getString(R.string.pref_key_sm_spinner_ym_selected),
                                            mSpinnerYMSelected);
                                } else if (mRadioHistRange.isChecked()) {
                                    // 期間
                                    saveSelectedSpinnerValueInSharedPref(
                                            getString(R.string.pref_key_sm_spinner_from_selected),
                                            mSpinnerFromSelected);
                                    saveSelectedSpinnerValueInSharedPref(
                                            getString(R.string.pref_key_sm_spinner_to_selected),
                                            mSpinnerToSelected);
                                }
                                // 統計情報を保存
                                saveStatisticsInSharedPref(urlPathIdx, stat);
                            }
                        } else {
                            // レコード無しなら統計情報をリセットしNoImage画像を表示
                            resetWidgetsWithRemovePref(urlPathIdx);
                        }
                    } else if (result instanceof Result.Warning) {
                        ResponseStatus status =
                                ((Result.Warning<?>) result).getResponseStatus();
                        DEBUG_OUT.accept(TAG, "WarningStatus: " + status);
                        showWarningInStatusView(mWarningStatus, getResponseWarning(status));
                        resetWidgetsWithRemovePref(urlPathIdx);
                    } else if (result instanceof Result.Error) {
                        // 例外メッセージをダイアログに表示
                        Exception exception = ((Result.Error<?>) result).getException();
                        Log.w(TAG, "Error:" + exception);
                        showDialogExceptionMessage(exception);
                        resetWidgetsWithRemovePref(urlPathIdx);
                    }
                });
    }
    //** END request with repository *****************************

    /**
     * 当日データ含むチェックボックスの状態更新
     * <p>一時保存ファイルが存在すればラジオボタンの状態に関わらずチェックする</p>
     */
    private void updateCheckIncludeToday() {
        String savedDate = SharedPrefUtil.getLastSavedDate(requireContext());
        DEBUG_OUT.accept(TAG, "savedDate: " + savedDate);
        boolean fileNone = TextUtils.isEmpty(savedDate);
        mChkIncludeToday.setEnabled(!fileNone);
        mChkIncludeToday.setChecked(!fileNone);
    }

    //** START Radio Buttons control *******************************************
    /** 2週間ラジオボタン選択時の他のラジオボタン制御 */
    private void radio2wSelected() {
        // 全スピナー無効
        setAllSpinnersDisabled();
        // 本日含むは可
        if (!mChkIncludeToday.isEnabled()) {
            mChkIncludeToday.setEnabled(true);
        }
    }

    /** 月間ラジオボタン選択時の他のラジオボタン制御 */
    private void radioYMSelected() {
        // 月間スピナー有効
        mSpinnerYM.setEnabled(true);
        // 期間範囲スピナー類無効
        setRangeSpinnersEnable(false);
        // 本日含むは不可
        if (mChkIncludeToday.isEnabled()) {
            mChkIncludeToday.setEnabled(false);
        }
    }

    /** ヒストグラム(期間)ラジオボタン選択時の他のラジオボタン制御 */
    private void radioHistRangeSelected() {
        // 月間スピナー無効
        mSpinnerYM.setEnabled(false);
        // 期間範囲スピナー類有効
        setRangeSpinnersEnable(true);
        // 本日含むは不可
        if (mChkIncludeToday.isEnabled()) {
            mChkIncludeToday.setEnabled(false);
        }
    }
    //** END Radio Buttons control *********************************************

    //** START define Listeners *********************************************
    /**
     * 最新画像取得リスナー
     */
    private final View.OnClickListener mButtonRequestListener = v -> getImageRequest();

    /**
     * 検索期間(２週間/月間/期間) ラジオグループの切り替えリスナー
     * <ul>
     *     <li>他のグループ内ラジオボタン等の可・不可制御</li>
     *     <li>画像・統計情報の復元</li>
     * </ul>
     */
    private final RadioGroup.OnCheckedChangeListener mRGrpRangeChangeListener =(grp, checkedId) -> {
        DEBUG_OUT.accept(TAG, "radioGroup: " + grp.getId() + ",checkedId: " + checkedId);
        // 期間ラジオグループ
        if (checkedId == mRadioYM.getId()) {
            // 月間選択
            radioYMSelected();
        } else if (checkedId == mRadioHistRange.getId()) {
            // 範囲選択 (ヒストグラム)
            radioHistRangeSelected();
        } else {
            // ２週間選択
            radio2wSelected();
        }
        // 切り替えごとに対応する画像ファイルと統計情報を復元
        restoreImageWithStatistics();
    };

    /** 年月スピナー選択リスナー */
    private final AdapterView.OnItemSelectedListener mSpinnerYMListener =
        new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = (String) parent.getItemAtPosition(position);
                mSpinnerYMSelected.setPosition(position);
                mSpinnerYMSelected.setValue(value);
                DEBUG_OUT.accept(TAG, "SpinnerYM.onItemSelected[" + mSpinnerYMSelected + "]");
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mSpinnerYMSelected.setPosition(AppImageFragUtil.SpinnerSelected.UNSELECTED);
                mSpinnerYMSelected.setValue(null);
            }
    };

    /** 期間(開始)スピナー選択リスナー */
    private final AdapterView.OnItemSelectedListener mSpinnerRangeFromListener =
        new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = (String) parent.getItemAtPosition(position);
                mSpinnerFromSelected.setPosition(position);
                mSpinnerFromSelected.setValue(value);
                DEBUG_OUT.accept(TAG,
                        "SpinnerRangeFrom.onItemSelected[" + mSpinnerFromSelected + "]");
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mSpinnerFromSelected.setPosition(-1);
                mSpinnerFromSelected.setValue(null);
            }
    };

    /** 期間(終了)スピナー選択リスナー */
    private final AdapterView.OnItemSelectedListener mSpinnerRangeToListener =
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String value = (String) parent.getItemAtPosition(position);
                    mSpinnerToSelected.setPosition(position);
                    mSpinnerToSelected.setValue(value);
                    DEBUG_OUT.accept(TAG,
                            "SpinnerRangeTo.onItemSelected[" + mSpinnerToSelected + "]");
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    mSpinnerToSelected.setPosition(-1);
                    mSpinnerToSelected.setValue(null);
                }
            };
    //** End define Listeners *********************************************

    //** START init widgets ***********************************************
    private void initStatisticsWidgets(View mainView) {
        mTvRecCount = mainView.findViewById(R.id.tvFragSmRecCount);
        mTvSleepingTime = mainView.findViewById(R.id.tvSleepingTime);
        mTvDeepSleepingTime = mainView.findViewById(R.id.tvDeepSleepingTime);
    }

    /**
     * 期間選択スピナー(開始/終了)の可・不可設定
     * @param enable 可・不可
     */
    private void setRangeSpinnersEnable(boolean enable) {
        mSpinnerRangeFrom.setEnabled(enable);
        mSpinnerRangeTo.setEnabled(enable);
    }

    /** 全ての選択スピナーを不可に設定 */
    private void setAllSpinnersDisabled() {
        for (Spinner item : mAllSpinners) {
            item.setEnabled(false);
        }
    }

    private void initSpinner(View mainView) {
        // アダブターは全てのスピナー共通
        mSpinnerAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item);
        mSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // 月間スピナー
        mSpinnerYM = mainView.findViewById(R.id.spinnerSmYearMonth);
        // 期間(開始)スピナー
        mSpinnerRangeFrom = mainView.findViewById(R.id.spinnerSmRangeFrom);
        // 期間(終了)スピナー
        mSpinnerRangeTo = mainView.findViewById(R.id.spinnerSmRangeTo);
        mAllSpinners = new Spinner[]{mSpinnerYM, mSpinnerRangeFrom, mSpinnerRangeTo};
        for (Spinner item : mAllSpinners) {
            item.setAdapter(mSpinnerAdapter);
            item.setEnabled(false);
        }
    }

    private void initWidget(View mainView) {
        // 検索期間(月間/２週間前/ヒストグラム[期間範囲]) ラジオグループ
        mRGrpDateRange = mainView.findViewById(R.id.radioGroupSmDateRange);
        // 期間ラジオボタン
        mRadioYM = mainView.findViewById(R.id.radioSmYM);
        mRadio2w = mainView.findViewById(R.id.radioSm2w);
        mRadioHistRange = mainView.findViewById(R.id.radioSmHistRange);
        // チェックボックス
        mChkIncludeToday = mainView.findViewById(R.id.chkIncludeSmToday);
        // プロット画像表示
        mImgView = mainView.findViewById(R.id.imgSleepMan);
        // ウォーニングステータス
        mWarningStatus = mainView.findViewById(R.id.tvFragSmStatus);
        // 実行ボタン
        mBtnGetRequest = mainView.findViewById(R.id.btnSmGetRequest);
        mBtnGetRequest.setOnClickListener(mButtonRequestListener);
        // 初期画面生成時は不可
        mBtnGetRequest.setEnabled(false);
        // 選択スピナー
        initSpinner(mainView);
        // 画像保存チェック
        mChkSaveImg = mainView.findViewById(R.id.chkSmSaveImg);
        // 統計情報用ウィジット
        initStatisticsWidgets(mainView);
    }
    //** End init widgets ***********************************************


    //** START life cycle events *****************************
    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        DEBUG_OUT.accept(TAG, "onCreateView()");

        View mainView = inflater.inflate(
                R.layout.fragment_image_sleep_man, container,false);
        initWidget(mainView);
        // ラジオボタン類を２週間で初期化
        radio2wSelected();
        // 画像の保存ファイル名配列の生成
        mSaveImageNames = new String[] {
                getString(R.string.file_name_sm_bar_ym)/* 月間 棒グラフ */,
                getString(R.string.file_name_sm_bar_2w)/* ２週間 棒グラフ */,
                getString(R.string.file_name_sm_hist_range)/* 期間 ヒストグラム */
        };
        // プリファレンス画像保存キー名配列の生成
        mPrefSaveImageKeys = new String[] {
                getString(R.string.pref_key_sm_bar_ym),
                getString(R.string.pref_key_sm_bar_2w),
                getString(R.string.pref_key_sm_hist_range)
        };
        // プリファレンス統計情報保存キー名配列の生成
        mPrefStatisticsKeys = new String[] {
                getString(R.string.pref_stat_sm_bar_ym),
                getString(R.string.pref_stat_sm_bar_2w),
                getString(R.string.pref_stat_sm_hist_range)
        };
        // 空のスピナーすビナー選択オブジェクト生成
        mSpinnerYMSelected = new AppImageFragUtil.SpinnerSelected();
        mSpinnerFromSelected = new AppImageFragUtil.SpinnerSelected();
        mSpinnerToSelected = new AppImageFragUtil.SpinnerSelected();
        return mainView;
    }

    @Override
    public void onResume() {
        super.onResume();
        DEBUG_OUT.accept(TAG, "onResume()");

        // 初回登録日をプリファレンスから取得する
        String firstRegisterDay = SharedPrefUtil.getFirstRegisterDay(requireContext());
        DEBUG_OUT.accept(TAG, "firstRegisterDay: " + firstRegisterDay);
        if (firstRegisterDay != null) {
            // スピナーに年月リストを設定する
            AppImageFragUtil.setYearMonthListToSpinnerAdapter(
                    (ArrayAdapter<String>) mSpinnerYM.getAdapter(),
                    firstRegisterDay);
        }
        mBtnGetRequest.setEnabled(firstRegisterDay != null);

        // プリファレンスからラジオボタンの状態を復元する ※onPauseで実行する
        if (restoreRadioButtonsState()) {
            // 画像ファイルと統計情報の復元
            restoreImageWithStatistics();
        }
        // ラジオボタンの復元が終わってから当日データ含むチェックボックスの更新
        // 登録画面で一時保存した場合は無条件でチェックされる
        updateCheckIncludeToday();

        // ラジオグループリスナー登録
        mRGrpDateRange.setOnCheckedChangeListener(mRGrpRangeChangeListener);
        // スピナーリスナー登録
        mSpinnerYM.setOnItemSelectedListener(mSpinnerYMListener);
        mSpinnerRangeFrom.setOnItemSelectedListener(mSpinnerRangeFromListener);
        mSpinnerRangeTo.setOnItemSelectedListener(mSpinnerRangeToListener);
    }

    @Override
    public void onStart() {
        super.onStart();
        DEBUG_OUT.accept(TAG, "onStart()");
    }

    public void onPause() {
        super.onPause();
        DEBUG_OUT.accept(TAG, "onPause()");

        // ラジオグループリスナー解除 ※null可
        mRGrpDateRange.setOnCheckedChangeListener(null);
        // スピナーリスナー解除 ※null可
        mSpinnerYM.setOnItemSelectedListener(null);
        mSpinnerRangeFrom.setOnItemSelectedListener(null);
        mSpinnerRangeTo.setOnItemSelectedListener(null);
    }

    @Override
    public void onStop() {
        super.onStop();
        DEBUG_OUT.accept(TAG, "onStop()");

        // ラジオボタンの状態をプリファレンスに保存する
        saveRadioButtonsState();
    }
    //** END life cycle events *****************************

    /**
     * ImageViewに取得した画像を表示するとともにファイルに保存
     * @param urlPathIdx URLパスインデックス
     * @param data ResponseImageData
     */
    private void showSuccess(UrlPathIndex urlPathIdx, ResponseImageData data) {
        hideStatusView(mWarningStatus);
        byte[] decoded = data.getImageBytes();
        if (decoded != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(
                    decoded, 0, decoded.length);
            mImgView.setImageBitmap(bitmap);
            // Bitmapのファイル保存
            saveBitmapToFile(urlPathIdx, bitmap);
        }
    }

    /**
     * 統計情報を表示する
     * <ul>
     *     <li>睡眠時間: 分を時刻(時:分)に変換して表示</li>
     *     <li>深い睡眠: 分をそのまま表示</li>
     * </ul>
     * @param stat SleepManStatistics
     */
    private void showStatistics(SleepManStatistics stat) {
        if (stat.getRecCount() > 0) {
            mTvRecCount.setText(String.format(Locale.US, FMT_STAT_VALUE, stat.getRecCount()));
            // 睡眠時間: 分->時刻表示
            mTvSleepingTime.setText(minutesToStringTime(stat.getSleepingTimeMean()));
            // 深い睡眠: 分
            mTvDeepSleepingTime.setText(String.format(Locale.US, FMT_STAT_VALUE,
                    stat.getDeepSleepingTimeMean()));
        } else {
            resetStatisticsViews();
        }
    }

    /**
     * 統計情報を初期値にリセットする
     */
    private void resetStatisticsViews() {
        mTvRecCount.setText(getString(R.string.value_frag_stat_rec_count_blank));
        mTvSleepingTime.setText(getString(R.string.value_frag_sm_stat_sleeping_blank));
        mTvDeepSleepingTime.setText(getString(R.string.value_frag_sm_stat_deep_blank));
    }

    /**
     * 統計情報をリセットしNoImage画像を表示するとともにプリファレンスからも削除
     * <ul>
     *     <li>データ件数が0件時</li>
     *     <li>ウォーニング時</li>
     *     <li>例外発生時</li>
     * </ul>
     */
    private void resetWidgetsWithRemovePref(UrlPathIndex pathIdx) {
        // NoImage画像を表示
        mImgView.setImageBitmap(getNoImageBitmap());
        // 統計情報表示ビューリセット
        resetStatisticsViews();
        // 対象画像ファイルとプリファレンスキー削除
        deleteSavedImage(pathIdx);
        // 統計情報をプリファレンスから削除
        removeStatisticsKey(pathIdx);
    }

    //** START shared preferences save/restore *****************************
    /**
     * スピナーで選択された位置の年月をプリファレンスに保存する
     * <ul>
     *     <li>[保存タイミング] リクエストが月間/期間でかつ正常終了時</li>
     *     <li>位置を保持すると当日が次の月に変わると位置も1つずれてしまうため文字列を保持する</li>
     * </ul>
     * @param prefKey プリファレンスキー
     * @param spinnerSelected スピナー選択オブジェクト
     */
    private void saveSelectedSpinnerValueInSharedPref(
            String prefKey, AppImageFragUtil.SpinnerSelected spinnerSelected) {
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(prefKey, spinnerSelected.getValue());
        editor.apply();
    }

    /**
     * 全ての期間ラジオボタンの状態をプリファレンスに保存
     * <ul>
     *     <li>２週間</li>
     *     <li>月間</li>
     *     <li>期間(ヒストグラム)</li>
     * </ul>
     * <p>実行タイミング: onStop()</p>
     */
    private void saveRadioButtonsState() {
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        SharedPreferences.Editor editor = sharedPref.edit();
        // 全てのラジオボタンの状態を保存
        editor.putBoolean(getString(R.string.pref_key_sm_radio_2w), mRadio2w.isChecked());
        editor.putBoolean(getString(R.string.pref_key_sm_radio_ym), mRadioYM.isChecked());
        editor.putBoolean(getString(R.string.pref_key_sm_radio_range), mRadioHistRange.isChecked());
        // 保存の事実自体の保存 ※復元時にキーを削除
        editor.putString(getString(R.string.pref_key_sm_stop_saved),
                getString(R.string.pref_value_stop_saved));
        editor.apply();
    }

    /**
     * プリファレンスから復元した選択値がnull以外なら該当するスピナーに適用
     * @param restoreVal 復元した選択値
     * @param spinner スピナー
     */
    private void restoreSpinnerSelection(String restoreVal, Spinner spinner) {
        int pos = mSpinnerAdapter.getPosition(restoreVal);
        DEBUG_OUT.accept(TAG, "Spinner.id: " + spinner.getId() + ",pos: " + pos);
        if (pos > 0) {
            spinner.setSelection(pos);
        }
    }

    /**
     * 前回の値が保持されていればラジオボタンの状態をプリファレンスから復元
     * <ul>
     *    <li>onResume()に実行 ※onStopの対onStart()だとスピナーの年月リストが未設定</li>
     *    <li>リスナー登録前 ※リスナー登録はonResume()の最後</li>
     * </ul>
     */
    private boolean restoreRadioButtonsState() {
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        // onStop時の保存キーの存在チェック
        String saved = sharedPref.getString(getString(R.string.pref_key_sm_stop_saved), null);
        DEBUG_OUT.accept(TAG, "restoreRadioButtonsState.saved: " + saved);
        if (saved != null) {
            // ２週間チェック
            boolean is2w = sharedPref.getBoolean(getString(R.string.pref_key_sm_radio_2w),
                    false);
            mRadio2w.setChecked(is2w);
            // 月間チェック
            boolean isYm = sharedPref.getBoolean(getString(R.string.pref_key_sm_radio_ym),
                    false);
            mRadioYM.setChecked(isYm);
            // 期間チェック
            boolean isRange = sharedPref.getBoolean(getString(R.string.pref_key_sm_radio_range),
                    false);
            mRadioHistRange.setChecked(isRange);
            DEBUG_OUT.accept(TAG,"is2w: " + is2w + ",isYM: " + isYm + ",isRange: " + isRange);
            // 関連するラジオボタンを一括更新
            if (mRadioYM.isChecked()) {
                radioYMSelected();
            } else if (mRadioHistRange.isChecked()) {
                radioHistRangeSelected();
            } else {
                radio2wSelected();
            }
            // 年月スピナーの選択位置をプリファレンスから取得した年月文字列から復元
            String ymVal = sharedPref.getString
                    (getString(R.string.pref_key_sm_spinner_ym_selected),null);
            String fromVal = sharedPref.getString(
                    getString(R.string.pref_key_sm_spinner_from_selected),null);
            String toVal = sharedPref.getString(
                    getString(R.string.pref_key_sm_spinner_to_selected),null);
            DEBUG_OUT.accept(TAG,"ym: " + ymVal + ",from: " + isYm + ",to: " + isRange);
            // 年月スピナーが生成されていないケースを考慮 ※メールアドレス未設定
            // スピナーサイズは全スピナーで共通
            int spinnerSize = mSpinnerYM.getAdapter().getCount();
            // 先頭位置以外なら更新 ※先頭(0)ならそのまま
            if (spinnerSize > 1) {
                if (ymVal != null) {
                    restoreSpinnerSelection(ymVal, mSpinnerYM);
                }
                if (fromVal != null) {
                    restoreSpinnerSelection(fromVal, mSpinnerRangeFrom);
                }
                if (toVal != null) {
                    restoreSpinnerSelection(toVal, mSpinnerRangeTo);
                }
            }
            // 復元完了でキーを削除する
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.remove(getString(R.string.pref_key_sm_stop_saved));
            editor.apply();
        }

        return saved != null;
    }

    /**
     * 統計情報をプリファレンスに保存する
     * @param urlPathIdx UrlPathIndex
     * @param stat 統計情報オブジェクト
     */
    private void saveStatisticsInSharedPref(UrlPathIndex urlPathIdx, SleepManStatistics stat) {
        Gson gson = new GsonBuilder().serializeNulls().create();
        String json = gson.toJson(stat);
        String key = mPrefStatisticsKeys[urlPathIdx.getNum()];
        DEBUG_OUT.accept(TAG, "saveStat[" + key + "]: " + json);
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(key, json);
        editor.apply();
    }

    /**
     * プリファレンスの統計情報から統計情報オブジェクトを復元する
     * @param urlPathIdx UrlPathIndex
     */
    private SleepManStatistics restoreStatisticsObject(UrlPathIndex urlPathIdx) {
        String key = mPrefStatisticsKeys[urlPathIdx.getNum()];
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        String json = sharedPref.getString(key, null);
        if (!TextUtils.isEmpty(json)) {
            Gson gson = new Gson();
            SleepManStatistics stat = gson.fromJson(json, SleepManStatistics.class);
            DEBUG_OUT.accept(TAG, "Stat[" + key + "]: " + stat);
            return stat;
        }

        DEBUG_OUT.accept(TAG, "Stat[" + key + "]: null");
        return null;
    }

    /**
     * 統計情報のキーと値を削除する
     * @param urlPathIdx UrlPathIndex
     */
    private void removeStatisticsKey(UrlPathIndex urlPathIdx) {
        String key = mPrefStatisticsKeys[urlPathIdx.getNum()];
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(key);
        editor.apply();
    }
    //** END shared preferences save/restore *****************************

    //** START imageView: to save file/ restore from file *****************************
    /**
     * 取得した画像のBitmapをファイル保存
     * @param urlPathIdx UrlPathIndex
     * @param bitmap 画像のBitmap
     */
    private void saveBitmapToFile(UrlPathIndex urlPathIdx, Bitmap bitmap) {
        String saveName = mSaveImageNames[urlPathIdx.getNum()];
        getHandler().post(() -> {
            try {
                String absSavePath = AppImageFragUtil.saveBitmapToPng(requireContext(),
                        bitmap, saveName);
                DEBUG_OUT.accept(TAG, "absSavePath: " + absSavePath);
                if (absSavePath != null) {
                    // ファイル名をプリファレンスに保存する
                    SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                            requireContext()
                    );
                    // 画像ファイル名保存のプリファレンスキー
                    String prefKey = mPrefSaveImageKeys[urlPathIdx.getNum()];
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString(prefKey, absSavePath);
                    editor.apply();
                }
            } catch (IOException e) {
                Log.w(TAG, "saveBitmapToFile: " + e.getLocalizedMessage());
            }
        });
    }

    /**
     * 前回保存した画像とプリファレンスキー削除する
     * <p>[実行タイミング] 画像取得エラー時(レコードなしも同様)</p>
     * @param urlPathIdx UrlPathIndex
     */
    private void deleteSavedImage(UrlPathIndex urlPathIdx) {
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        String prefKey = mPrefSaveImageKeys[urlPathIdx.getNum()];
        String savedPath = sharedPref.getString(prefKey, null);
        DEBUG_OUT.accept(TAG, "delete.prefKey: " + prefKey + ",path: " + savedPath);
        // 対象画像とプリファレンスキーを削除
        if (savedPath != null) {
            getHandler().post(() -> {
                File file = new File(savedPath);
                if (file.exists()) {
                    if (file.delete()) {
                        DEBUG_OUT.accept(TAG, "Deleted.File: " + file.getAbsolutePath());
                    }
                }
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.remove(prefKey);
                editor.apply();
            });
        }
    }

    /**
     * 現在のラジオボタンの状態から保存した画像ファイルが存在すればBitmapオブジェクトを取得する
     * @param urlPathIdx UrlPathIndex
     * @return 保存した画像ファイルが存在すればBitmapオブジェクト
     */
    private Bitmap restoreBitmapFromFile(UrlPathIndex urlPathIdx) {
        // 画像ファイル名保存のプリファレンスキー
        String prefKey = mPrefSaveImageKeys[urlPathIdx.getNum()];
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        String savedFileName = sharedPref.getString(prefKey, null);
        DEBUG_OUT.accept(TAG, "savedFileName: " + savedFileName);
        if (savedFileName != null) {
            try {
                return AppImageFragUtil.readBitmapFromAbsolutePath(savedFileName);
            } catch (IOException e) {
                Log.w(TAG, "restoreBitmapFromFile: " + e.getLocalizedMessage());
            }
        }
        return null;
    }

    /**
     * 現在選択されているラジオボタンから保存済みの画像と統計情報を対応するウィジットに復元する
     */
    private void restoreImageWithStatistics() {
        // URLパスインデックス取得
        UrlPathIndex pathIdx = getUrlPathIndex();
        // 画像復元
        Bitmap savedBitmap = restoreBitmapFromFile(pathIdx);
        DEBUG_OUT.accept(TAG, "savedBitmap: " + savedBitmap);
        if (savedBitmap != null) {
            mImgView.setImageBitmap(savedBitmap);
        } else {
            // 保存された画像がなければNoImage画像
            mImgView.setImageBitmap(getNoImageBitmap());
        }
        // 統計情報復元
        SleepManStatistics stat = restoreStatisticsObject(pathIdx);
        if (stat != null) {
            showStatistics(stat);
        } else {
            resetStatisticsViews();
        }
    }
    //** END imageView: to save file/ restore from file *****************************

}

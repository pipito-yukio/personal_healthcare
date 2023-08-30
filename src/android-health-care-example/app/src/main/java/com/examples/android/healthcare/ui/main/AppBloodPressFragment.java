package com.examples.android.healthcare.ui.main;

import static com.examples.android.healthcare.functions.MyLogging.DEBUG_OUT;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.examples.android.healthcare.HealthcareApplication;
import com.examples.android.healthcare.R;
import com.examples.android.healthcare.SharedPrefUtil;
import com.examples.android.healthcare.constants.RequestDevice;
import com.examples.android.healthcare.data.BloodPressStatistics;
import com.examples.android.healthcare.data.BloodPressure;
import com.examples.android.healthcare.data.GetImageDataResult;
import com.examples.android.healthcare.data.RegisterData;
import com.examples.android.healthcare.data.ResponseImageData;
import com.examples.android.healthcare.data.ResponseStatus;
import com.examples.android.healthcare.functions.AppImageFragUtil;
import com.examples.android.healthcare.tasks.GetBloodPressImageRepository;
import com.examples.android.healthcare.tasks.NetworkUtil;
import com.examples.android.healthcare.tasks.RequestParamBuilder;
import com.examples.android.healthcare.tasks.Result;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * 血圧測定データプロット画像表示フラグメント
 * create an instance of this fragment.
 */
public class AppBloodPressFragment extends AppBaseFragment {
    private static final String TAG = AppBloodPressFragment.class.getSimpleName();
    // 当日データフォーマット: "測定日,AM最高血圧,AM最低血圧,AM脈拍"
    private static final String FMT_2W_TODAY = "%s,%d,%d,%d";
    // 統計情報の血圧平均値フォーマット (前空白の4桁)
    private static final String FMT_STAT_VALUE = "%4d";

    // URLパスインデックス
    enum UrlPathIndex {
        LINE_YM(0, "月間 折れ線グラフ"),
        LINE_2W(1, "２週間前 折れ線グラフ"),
        BAR_2W(2, "２週間前 棒グラフ");

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
    // グラフタイプ選択 ラジオグループ
    private RadioGroup mRGrpGraphType;
    // 最新取得ボタン
    private Button mBtnGetRequest;
    // 月間ラジオボタン
    private RadioButton mRadioYM;
    // ２週間前ラジオボタン
    private RadioButton mRadio2w;
    // 折れ線グラフ
    private RadioButton mRadioGraphLine;
    // 棒グラフ
    private RadioButton mRadioGraphBar;
    // 当日データ含む
    private CheckBox mChkIncludeToday;
    // 年月選択スピナー
    private Spinner mSpinnerYM;
    // 年月選択スピナーアダブター
    private ArrayAdapter<String> mSpinnerAdapter;
    // 画像保存チェック
    private CheckBox mChkSaveImg;
    // 統計情報(平均値)用ウィジット
    // レコード件数
    private TextView mTvRecCount;
    // AM測定最高血圧
    private TextView mTvAmMeanMax;
    // AM測定最低血圧
    private TextView mTvAmMeanMin;
    // PM測定最高血圧
    private TextView mTvPmMeanMax;
    // PM測定最低血圧
    private TextView mTvPmMeanMin;
    // ウォーニング用ステータス
    private TextView mWarningStatus;
    // 血圧値初期値
    private String mInitMeanValue;
    // 年月スピナー選択値を保持するオブジェクト
    private AppImageFragUtil.SpinnerSelected mSpinnerSelected;
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
    public static AppBloodPressFragment newInstance(int fragPosIdx) {
        AppBloodPressFragment frag = new AppBloodPressFragment();
        Bundle args = new Bundle();
        args.putInt(FRAGMENT_POS_KEY, fragPosIdx);
        frag.setArguments(args);
        return frag;
    }

    //** START implements abstract methods **************************
    @Override
    public int getFragmentPosition() {
        assert getArguments() != null;
        return getArguments().getInt(FRAGMENT_POS_KEY, 1);
    }

    @Override
    public String getFragmentTitle() { return getString(R.string.imgfrag_bp_app_title); }

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
     * 血圧測定データ可視化リクエストパスインデックスを取得する
     * @return パスインデックス
     */
    private UrlPathIndex getUrlPathIndex() {
        UrlPathIndex result;
        if (mRadioYM.isChecked()) {
            // 月間
            result = UrlPathIndex.LINE_YM;
        } else {
            // ２週間
            if (mRadioGraphLine.isChecked()) {
                // 折れ線グラフ
                result = UrlPathIndex.LINE_2W;
            } else {
                // 棒グラフ
                result = UrlPathIndex.BAR_2W;
            }
        }
        return result;
    }

    /**
     * 血圧測定データの当日データ(AM測定値)を取得する
     * @return 一時保存ファイルが存在しかつAM血圧測定データがあれば当日データ文字列, なければnull
     */
    private String getTodayData() {
        // 一時保存ファイルから復元した登録用データオブジェクトを取得
        RegisterData todayRegData = AppImageFragUtil.getRegisterDataFromJson(
                requireContext(),
                getString(R.string.last_saved_json_file));
        if (todayRegData != null) {
            String measurementDay = todayRegData.getMeasurementDay();
            BloodPressure bp = todayRegData.getHealthcareData().getBloodPressure();
            DEBUG_OUT.accept(TAG,
                    "measurementDay: " + measurementDay + ", BloodPress: " + bp);
            // 一時保存では午前中の血圧測定データが未設定の可能性があるため値が設定されているか確認する
            if (bp.getMorningMax() != null && bp.getMorningMin() != null &&
                    bp.getMorningPulseRate() != null) {
                String result = String.format(Locale.US, FMT_2W_TODAY, measurementDay,
                        bp.getMorningMax(), bp.getMorningMin(), bp.getMorningPulseRate());
                DEBUG_OUT.accept(TAG, "todayData: " + result);
                return result;
            } else {
                return null;
            }
        }

        return null;
    }

    /**
     * ユーザー目標値文字列から最高血圧値(整数値)と最低血圧値(整数値)を取得する
     * @param strUserTarget ユーザー目標値文字列 ※not null
     * @return ユーサー目標の配列
     */
    private static int[] splitBloodPressUserTarget(String strUserTarget) {
        String[] targets = strUserTarget.split(",");
        int targetMax = Integer.parseInt(targets[0]);
        int targetMin = Integer.parseInt(targets[1]);
        return new int[] {targetMax, targetMin};
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
            String value = mSpinnerSelected.getValue();
            // 区切りをハイフンに置き換える
            builder.addYearMonth(value.replace("/", "-"));
        } else {
            // ２週間: 検索終了日に昨日を設定
            String yesterday = AppImageFragUtil.getYesterday();
            builder.addEndDay(yesterday);
            // 当日AMデータがあれば追加する
            if (mChkIncludeToday.isChecked()) {
                String todayData = getTodayData();
                if (todayData != null) {
                    builder.addTodayData(todayData);
                }
            }
        }
        // 最高血圧・最低血圧のユーザー目標値 ※任意項目
        String userTarget = SharedPrefUtil.getBloodPressUserTargetInSettings(requireContext());
        if (userTarget != null) {
            int[] targets = splitBloodPressUserTarget(userTarget);
            builder.addBloodPressUserTarget(targets[0], targets[1]);
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
     * 画像取得リクエスト
     */
    private void getImageRequest() {
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
        GetBloodPressImageRepository repos = new GetBloodPressImageRepository();
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
                        BloodPressStatistics stat = repos.getStatistics(data);
                        if (stat != null && stat.getRecCount() > 0) {
                            showStatistics(stat);
                            showSuccess(urlPathIdx, data);
                            // 画像保存可否
                            if (mChkSaveImg.isChecked()) {
                                // 月間なら選択日付をプリファレンスに保存
                                if (mRadioYM.isChecked()) {
                                    saveSelectedSpinnerValueInSharedPref();
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
    /**
     * 2週間ラジオボタン選択時の他のラジオボタン制御
     * @param updateGraphTypeChecked チェック状態を更新するかどうか。復元時にはチェック状態を更新しない
     */
    private void radio2wSelected(boolean updateGraphTypeChecked) {
        // スピナー無効
        if (mSpinnerYM.isEnabled()) {
            mSpinnerYM.setEnabled(false);
        }
        // 棒グラフ有効
        if (!mRadioGraphBar.isEnabled()) {
            mRadioGraphBar.setEnabled(true);
        }
        // 本日含むは可
        if (!mChkIncludeToday.isEnabled()) {
            mChkIncludeToday.setEnabled(true);
        }
        if (updateGraphTypeChecked) {
            // 折線グラフをチェックにする
            mRadioGraphLine.setChecked(true);
        }
    }

    /**
     * 月間ラジオボタン選択時の他のラジオボタン制御
     * @param updateGraphTypeChecked チェック状態を更新するかどうか。復元時にはチェック状態を更新しない
     */
    private void radioYMSelected(boolean updateGraphTypeChecked) {
        // スピナー有効
        if (!mSpinnerYM.isEnabled()) {
            mSpinnerYM.setEnabled(true);
        }
        // 棒グラフは無効
        mRadioGraphBar.setEnabled(false);
        // 本日含むは不可
        if (mChkIncludeToday.isEnabled()) {
            mChkIncludeToday.setEnabled(false);
        }
        if (updateGraphTypeChecked) {
            // 棒グラフのチェックを外す
            mRadioGraphBar.setChecked(false);
            // 折線グラフをチェックにする
            mRadioGraphLine.setChecked(true);
        }
    }
    //** END Radio Buttons control *********************************************

    //** START define Listeners *********************************************
    /** 最新画像取得リスナー */
    private final View.OnClickListener mButtonRequestListener = v -> getImageRequest();

    /**
     * 検索期間(月間/２週間前) ラジオグループの切り替えリスナー
     * <ul>
     *     <li>他のグループ内ラジオボタン等の可・不可制御</li>
     *     <li>画像・統計情報の復元</li>
     * </ul>
     */
    private final RadioGroup.OnCheckedChangeListener mRGrpRangeChangeListener =(grp, checkedId) -> {
        DEBUG_OUT.accept(TAG, "radioGroup: " + grp.getId() + ",checkedId: " + checkedId);
        if (grp.getId() == mRGrpDateRange.getId()) {
            // 期間ラジオグループ
            if (checkedId == mRadio2w.getId()) {
                // ２週間選択
                radio2wSelected(true/*グラフ型のチェック状態を更新*/);
            } else {
                // 月間選択
                radioYMSelected(true);
            }
        }
        // 切り替えごとに対応する画像ファイルと統計情報を復元
        restoreImageWithStatistics();
    };

    /**
     * グラフ型ラジオグループの切り替えリスナー
     * <ul>
     *     <li>選択されたグラフ型ラジオに対応する画像・統計情報の復元</li>
     * </ul>
     */
    private final RadioGroup.OnCheckedChangeListener mRGrpGraphChangeListener =(grp, checkedId) -> {
        DEBUG_OUT.accept(TAG, "radioGroup: " + grp.getId() + ",checkedId: " + checkedId);
        // 選択されたグラフ型ラジオに対応する画像・統計情報の復元
        restoreImageWithStatistics();
    };

    /**
     * 年月スピナー選択リスナー
     */
    private final AdapterView.OnItemSelectedListener mYmSpinnerListener =
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String value = (String) parent.getItemAtPosition(position);
                    mSpinnerSelected.setPosition(position);
                    mSpinnerSelected.setValue(value);
                    DEBUG_OUT.accept(TAG, "onItemSelected[" + mSpinnerSelected + "]");
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    mSpinnerSelected.setPosition(-1);
                    mSpinnerSelected.setValue(null);
                }
    };
    //** End define Listeners *********************************************

    //** START init widgets ***********************************************
    private void initStatisticsWidgets(View mainView) {
        mTvRecCount = mainView.findViewById(R.id.tvFragBpRecCount);
        mTvAmMeanMax = mainView.findViewById(R.id.tvAmMeanMax);
        mTvAmMeanMin = mainView.findViewById(R.id.tvAmMeanMin);
        mTvPmMeanMax = mainView.findViewById(R.id.tvPmMeanMax);
        mTvPmMeanMin = mainView.findViewById(R.id.tvPmMeanMin);
        mInitMeanValue = getString(R.string.value_frag_stat_rec_count_blank);
    }

    private void initSpinner(View mainView) {
        // https://developer.android.com/guide/topics/ui/controls/spinner?hl=ja
        //  スピナー
        mSpinnerYM = mainView.findViewById(R.id.spinnerBpYearMonth);
        // 空リスト(文字列)のアダブターを設定する
        // public ArrayAdapter(@NonNull Context context, @LayoutRes int resource)
        //  this(context, resource, 0, new ArrayList<>());
        // 選択した選択肢がスピナー コントロールでどのように表示されるかを定義するレイアウトリソース
        mSpinnerAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item);
        // アダプターがスピナー選択リストに表示するために使用するレイアウト
        mSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerYM.setAdapter(mSpinnerAdapter);
        // 初期画面ではスピナー不可
        mSpinnerYM.setEnabled(false);
    }

    private void initWidget(View mainView) {
        // 検索期間(月間/２週間前) ラジオグループ
        mRGrpDateRange = mainView.findViewById(R.id.radioGroupBpDateRange);
        // グラフ型選択 ラジオグループ
        mRGrpGraphType = mainView.findViewById(R.id.radioGroupBpGraphType);
        // 期間ラジオボタン
        mRadioYM = mainView.findViewById(R.id.radioBpYM);
        mRadio2w = mainView.findViewById(R.id.radioBP2w);
        // グラフ型(折れ線/棒)
        mRadioGraphLine = mainView.findViewById(R.id.radioBpGraphLine);
        mRadioGraphBar = mainView.findViewById(R.id.radioBpGraphBar);
        // チェックボックス
        mChkIncludeToday = mainView.findViewById(R.id.chkIncludeBpToday);
        // プロット画像表示
        mImgView = mainView.findViewById(R.id.imgBloodBress);
        // ウォーニングステータス
        mWarningStatus = mainView.findViewById(R.id.tvFragBpStatus);
        // 実行ボタン
        mBtnGetRequest = mainView.findViewById(R.id.btnBpGetRequest);
        mBtnGetRequest.setOnClickListener(mButtonRequestListener);
        // 初期画面生成時は不可
        mBtnGetRequest.setEnabled(false);
        // 年月選択スピナー
        initSpinner(mainView);
        // 画像保存チェック
        mChkSaveImg = mainView.findViewById(R.id.chkBpSaveImg);
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
                R.layout.fragment_image_blood_press, container,false);
        initWidget(mainView);
        // ラジオボタン類を２週間で初期化
        radio2wSelected(false);
        // 画像の保存ファイル名配列の生成
        mSaveImageNames = new String[] {
            getString(R.string.file_name_bp_line_ym)/* 月間 折れ線グラフ */,
            getString(R.string.file_name_bp_line_2w)/* ２週間 折れ線グラフ */,
            getString(R.string.file_name_bp_bar_2w)/* ２週間 棒グラフ */
        };
        // プリファレンス画像保存キー名配列の生成
        mPrefSaveImageKeys = new String[] {
            getString(R.string.pref_key_bp_line_ym),
            getString(R.string.pref_key_bp_line_2w),
            getString(R.string.pref_key_bp_bar_2w)
        };
        // プリファレンス統計情報保存キー名配列の生成
        mPrefStatisticsKeys = new String[] {
                getString(R.string.pref_stat_bp_line_ym),
                getString(R.string.pref_stat_bp_line_2w),
                getString(R.string.pref_stat_bp_bar_2w)
        };
        // 空のオブジェクト生成
        mSpinnerSelected = new AppImageFragUtil.SpinnerSelected();
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
        mRGrpGraphType.setOnCheckedChangeListener(mRGrpGraphChangeListener);
        // スピナーリスナー登録
        mSpinnerYM.setOnItemSelectedListener(mYmSpinnerListener);
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
        mRGrpGraphType.setOnCheckedChangeListener(null);
        // スピナーリスナー解除 ※null可
        mSpinnerYM.setOnItemSelectedListener(null);
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
     * @param stat BloodPressStatistics
     */
    private void showStatistics(BloodPressStatistics stat) {
        if (stat.getRecCount() > 0) {
            mTvRecCount.setText(String.format(Locale.US, FMT_STAT_VALUE, stat.getRecCount()));
            mTvAmMeanMax.setText(String.format(Locale.US, FMT_STAT_VALUE, stat.getAmMaxMean()));
            mTvAmMeanMin.setText(String.format(Locale.US, FMT_STAT_VALUE, stat.getAmMinMean()));
            mTvPmMeanMax.setText(String.format(Locale.US, FMT_STAT_VALUE, stat.getPmMaxMean()));
            mTvPmMeanMin.setText(String.format(Locale.US, FMT_STAT_VALUE, stat.getPmMinMean()));
        } else {
            resetStatisticsViews();
        }
    }

    /**
     * 統計情報を初期値にリセットする
     */
    private void resetStatisticsViews() {
        mTvRecCount.setText(mInitMeanValue);
        mTvAmMeanMax.setText(mInitMeanValue);
        mTvAmMeanMin.setText(mInitMeanValue);
        mTvPmMeanMax.setText(mInitMeanValue);
        mTvPmMeanMin.setText(mInitMeanValue);
    }

    /**
     * 統計情報をリセットしNoImage画像を表示するとともにプリファレンスからも削除
     * <ul>
     *     <li>データ件数が0件時</li>
     *     <li>ウォーニング時</li>
     *     <li>例外発生時</li>
     * </ul>
     * @param pathIdx UrlPathIndex
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
     * 年月スピナーで選択された位置の年月をプリファレンスに保存する
     * <ul>
     *     <li>[保存タイミング] リクエストが月間でかつ正常終了時</li>
     *     <li>位置を保持すると当日が次の月に変わると位置も1つずれてしまうため文字列を保持する</li>
     * </ul>
     */
    private void saveSelectedSpinnerValueInSharedPref() {
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        SharedPreferences.Editor editor = sharedPref.edit();
        String ym = mSpinnerSelected.getValue();
        editor.putString(getString(R.string.pref_key_bp_spinner_selected), ym);
        editor.apply();
    }

    /**
     * ラジオボタンの状態をプリファレンスに保存
     * <ul>
     *     <li>上段ラジオグループ: 月間ラジオのチェック状態</li>
     *     <li>下段ラジオグループ: 折れ線グラフラジオのチェック状態</li>
     * </ul>
     * <p>実行タイミング: onStop()</p>
     */
    private void saveRadioButtonsState() {
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        SharedPreferences.Editor editor = sharedPref.edit();
        // ラジオボタン
        editor.putBoolean(getString(R.string.pref_key_bp_radio_ym), mRadioYM.isChecked());
        editor.putBoolean(getString(R.string.pref_key_bp_radio_line), mRadioGraphLine.isChecked());
        // ラジオボタン
        // 保存の事実自体の保存 ※復元時にキーを削除
        editor.putString(getString(R.string.pref_key_bp_stop_saved),
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
        String saved = sharedPref.getString(getString(R.string.pref_key_bp_stop_saved), null);
        DEBUG_OUT.accept(TAG, "restoreRadioButtonsState.saved: " + saved);
        if (saved != null) {
            // 先にグラフ型のチェック復元
            boolean isLine = sharedPref.getBoolean(getString(R.string.pref_key_bp_radio_line),
                    false);
            if (isLine) {
                mRadioGraphLine.setChecked(true);
            } else {
                mRadioGraphBar.setChecked(true);
            }
            // 月間チェック
            boolean isYmChecked = sharedPref.getBoolean(getString(R.string.pref_key_bp_radio_ym),
                    false);
            if (isYmChecked) {
                mRadioYM.setChecked(true);
            } else {
                mRadio2w.setChecked(true);
            }
            // 関連するラジオボタンを一括更新
            if (mRadioYM.isChecked()) {
                radioYMSelected(false/*グラフ型のチェック状態を更新しない*/);
            } else {
                radio2wSelected(false);
            }
            // 年月スピナーの選択位置をプリファレンスから取得した年月文字列から復元
            String ymVal = sharedPref.getString(getString(R.string.pref_key_bp_spinner_selected),
                    null);
            // 年月スピナーが生成されていないケースを考慮 ※メールアドレス未設定
            int spinnerSize = mSpinnerYM.getAdapter().getCount();
            DEBUG_OUT.accept(TAG,
                    "isYM: " + isYmChecked + ",isLine: " + isLine
                            + ",ym: " + ymVal + ",spinnerSize: " + spinnerSize);
            // 先頭位置以外なら更新 ※先頭(0)ならそのまま
            if (spinnerSize > 1) {
                if (ymVal != null) {
                    // スピナーの位置を復元
                    restoreSpinnerSelection(ymVal, mSpinnerYM);
                }
            }
            // 復元完了でキーを削除する
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.remove(getString(R.string.pref_key_bp_stop_saved));
            editor.apply();
        }

        return saved != null;
    }

    /**
     * 統計情報をプリファレンスに保存する
     * @param urlPathIdx URLパスインデックス
     * @param stat 統計情報オブジェクト
     */
    private void saveStatisticsInSharedPref(UrlPathIndex urlPathIdx, BloodPressStatistics stat) {
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
    private BloodPressStatistics restoreStatisticsObject(UrlPathIndex urlPathIdx) {
        String key = mPrefStatisticsKeys[urlPathIdx.getNum()];
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        String json = sharedPref.getString(key, null);
        if (!TextUtils.isEmpty(json)) {
            Gson gson = new Gson();
            BloodPressStatistics stat = gson.fromJson(json, BloodPressStatistics.class);
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
        BloodPressStatistics stat = restoreStatisticsObject(pathIdx);
        if (stat != null) {
            showStatistics(stat);
        } else {
            resetStatisticsViews();
        }
    }
    //** END imageView: to save file/ restore from file *****************************

}

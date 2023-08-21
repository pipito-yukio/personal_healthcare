package com.examples.android.healthcare.ui.main;

import static com.examples.android.healthcare.functions.MyLogging.DEBUG_OUT;

import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
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
import com.examples.android.healthcare.ActivityUtil;
import com.examples.android.healthcare.HealthcareApplication;
import com.examples.android.healthcare.R;
import com.examples.android.healthcare.SharedPrefUtil;
import com.examples.android.healthcare.constants.RequestDevice;
import com.examples.android.healthcare.data.BloodPressStatistics;
import com.examples.android.healthcare.data.BloodPressure;
import com.examples.android.healthcare.data.GetImageDataResult;
import com.examples.android.healthcare.data.GetRegisterDaysResult;
import com.examples.android.healthcare.data.RegisterData;
import com.examples.android.healthcare.data.ResponseImageData;
import com.examples.android.healthcare.data.ResponseRegisterDays;
import com.examples.android.healthcare.data.ResponseStatus;
import com.examples.android.healthcare.functions.AppImageFragUtil;
import com.examples.android.healthcare.functions.FileManager;
import com.examples.android.healthcare.tasks.GetBloodPressImageRepository;
import com.examples.android.healthcare.tasks.GetRegisterDaysRepository;
import com.examples.android.healthcare.tasks.HealthcareRepository;
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
public class AppBloodPressFragment extends Fragment {
    private static final String TAG = "AppBloodPressFragment";
    // 初期表示用イメージ
    private static final String NO_IMAGE_FILE = "NoImage_500x700.png";
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

    // 画像ファイル保存、プリファレンスコミット時に利用するハンドラー
    private final Handler mHandler = new Handler();
    private Bitmap mNoImageBitmap;
    // プロット画像用ImageView
    private ImageView mImgView;
    // ラジオグループ
    private RadioGroup mRGrpDateRange;
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
    // 画像保存チェック
    private CheckBox mChkSaveImg;
    // ステータスピュー
    private TextView mTvStatus;
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
    // 血圧値初期値
    private String mInitMeanValue;

    private DisplayMetrics mMetrics;
    private int mImageWd;
    private int mImageHt;
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
        args.putInt(FragmentUtil.FRAGMENT_POS_KEY, fragPosIdx);
        frag.setArguments(args);
        return frag;
    }

    public int getFragmentPosition() {
        assert getArguments() != null;
        return getArguments().getInt(FragmentUtil.FRAGMENT_POS_KEY, 1);
    }

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

    /**
     * 血圧測定データプロット画像取得用のリクエストパラメータ生成
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

    //** START request with repository *****************************
    /**
     * 画像取得リクエスト
     */
    private void getImageRequest() {
        // ネットワークデバイスが無効なら送信しない
        RequestDevice device =  NetworkUtil.getActiveNetworkDevice(requireContext());
        if (device == RequestDevice.NONE) {
            FragmentUtil.showDialogNetworkUnavailable((AppCompatActivity) requireActivity(),
                    getString(R.string.warning_network_not_available));
            return;
        }

        mBtnGetRequest.setEnabled(false);
        // アクションバーのサブタイトルに取得中メッセージ表示
        FragmentUtil.showActionBarGetting((AppCompatActivity) requireActivity(),
                getString(R.string.msg_gettting_graph));

        // アプリケーション取得
        HealthcareApplication app = (HealthcareApplication) requireActivity().getApplication();
        String requestUrl = app.getmRequestUrls().get(device.toString());
        GetBloodPressImageRepository repos = new GetBloodPressImageRepository();
        // ImageViewサイズとDisplayMetrics.densityをリクエストヘッダに追加する
        Map<String, String> headers = app.getRequestHeaders();
        AppImageFragUtil.appendImageSizeToHeaders(headers, mImageWd, mImageHt, mMetrics.density);
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
                    // アクションバーのサブタイトル更新
                    FragmentUtil.showActionBarResult((AppCompatActivity) requireActivity(), device);

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
                            resetSuccessWidgets(urlPathIdx);
                            // 統計情報をプリファレンスから削除
                            removeStatisticsKey(urlPathIdx);
                        }
                    } else if (result instanceof Result.Warning) {
                        ResponseStatus status =
                                ((Result.Warning<?>) result).getResponseStatus();
                        Log.w(TAG, "Warning:" + status);
                        String warning = String.format(
                                getString(R.string.warning_getdata_with_reason),
                                status.getMessage());
                        FragmentUtil.showMessageDialog(
                                (AppCompatActivity) requireActivity(),
                                getString(R.string.error_response_dialog_title),
                                warning,"WarningDialogFragment");
                        resetSuccessWidgets(urlPathIdx);
                        removeStatisticsKey(urlPathIdx);
                    } else if (result instanceof Result.Error) {
                        // 例外メッセージをダイアログに表示
                        Exception exception = ((Result.Error<?>) result).getException();
                        Log.w(TAG, "Error:" + exception);
                        String message = String.format(
                                getString(R.string.exception_with_reason), exception.getLocalizedMessage());
                        FragmentUtil.showMessageDialog(
                                (AppCompatActivity) requireActivity(),
                                getString(R.string.error_response_dialog_title),message,
                                "ExceptionDialogFragment");
                        resetSuccessWidgets(urlPathIdx);
                        removeStatisticsKey(urlPathIdx);
                    }
                });
    }

    /**
     * 初回登録日の存在チェックし、存在しない場合は暗黙的にリクエストする
     */
    private void requestFirstRegisterDayWithImplicitly() {
        String firstRegisterDay = SharedPrefUtil.getFirstRegisterDay(requireContext());
        DEBUG_OUT.accept(TAG, "firstRegisterDay: " + firstRegisterDay);
        if (TextUtils.isEmpty(firstRegisterDay)) {
            // 暗黙的にサーバーにリクエストする
            RequestDevice device =  NetworkUtil.getActiveNetworkDevice(requireContext());
            if (device == RequestDevice.NONE) {
                showStatusNetworkUnavailable();
                return;
            }

            HealthcareApplication app = (HealthcareApplication) requireActivity().getApplication();
            String requestUrl = app.getmRequestUrls().get(device.toString());
            Map<String, String> headers = app.getRequestHeaders();
            // ユーザの初回登録日取得
            HealthcareRepository<GetRegisterDaysResult> repos = new GetRegisterDaysRepository();
            // メールアドレス ※メールアドレスが未設定ならこの画面には遷移しない
            String emailAddress = SharedPrefUtil.getEmailAddressInSettings(requireContext());
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
                                SharedPrefUtil.saveFirstRegisterDay(requireContext(),
                                        firstDay);
                                // スピナーに年月リストを設定する
                                AppImageFragUtil.setYearMonthListToSpinnerAdapter(
                                        (ArrayAdapter<String>) mSpinnerYM.getAdapter(),
                                        firstDay);
                                mBtnGetRequest.setEnabled(true);
                            }
                        } else if (result instanceof Result.Warning) {
                            // ウォーニングメッセージをログに出力
                            ResponseStatus status =
                                    ((Result.Warning<?>) result).getResponseStatus();
                            Log.w(TAG, "WarningStatus: " + status);
                        } else if (result instanceof Result.Error) {
                            // 例外メッセージをダイアログに表示
                            Exception exception = ((Result.Error<?>) result).getException();
                            Log.w(TAG, "Exception: " + exception);
                        }
                    });
        } else {
            // プリファレンスの初回登録日から生成する
            AppImageFragUtil.setYearMonthListToSpinnerAdapter(
                    (ArrayAdapter<String>) mSpinnerYM.getAdapter(),
                    firstRegisterDay);
            mBtnGetRequest.setEnabled(true);
        }
    }
    //** END request with repository *****************************

    /**
     * 当日データ含むチェックボックスウィジットの状態更新
     */
    private void updateCheckIncludeToday() {
        if (mRadioYM.isChecked()) {
            // 月間がチェックされていたら当日のチェックを外す
            mChkIncludeToday.setEnabled(false);
            if (mChkIncludeToday.isChecked()) {
                mChkIncludeToday.setChecked(false);
            }
        } else {
            // ２週間なら一時保存ファイルチェック
            String savedDate = SharedPrefUtil.getLastSavedDate(requireContext());
            // 一時保存ファイルがなければ当日データチェック不可
            boolean fileNone = TextUtils.isEmpty(savedDate);
            mChkIncludeToday.setEnabled(!fileNone);
            mChkIncludeToday.setChecked(!fileNone);
        }
    }

    //** START Radio Buttons control *******************************************
    /** 月間ラジオボタン選択時の他のラジオボタン制御  */
    private void radioYMSelected() {
        // スピナー有効
        if (!mSpinnerYM.isEnabled()) {
            mSpinnerYM.setEnabled(true);
        }
        // 棒グラフは無効
        mRadioGraphBar.setEnabled(false);
        // 棒グラフのチェックを外す
        if (mRadioGraphBar.isChecked()) {
            mRadioGraphBar.setChecked(false);
        }
        // 折線グラフをチェックにする
        if (!mRadioGraphLine.isChecked()) {
            mRadioGraphLine.setChecked(true);
        }
        // 本日含むは不可
        if (mChkIncludeToday.isEnabled()) {
            mChkIncludeToday.setEnabled(false);
        }
    }

    /** 2週間ラジオボタン選択時の他のラジオボタン制御 */
    private void radio2wSelected() {
        // スピナー無効
        if (mSpinnerYM.isEnabled()) {
            mSpinnerYM.setEnabled(false);
        }
        // 棒グラフ有効
        if (!mRadioGraphBar.isEnabled()) {
            mRadioGraphBar.setEnabled(true);
        }
        // 折線グラフをチェックにする
        if (!mRadioGraphLine.isChecked()) {
            mRadioGraphLine.setChecked(true);
        }
        // 本日含むは可
        if (!mChkIncludeToday.isEnabled()) {
            mChkIncludeToday.setEnabled(true);
        }
    }
    //** END Radio Buttons control *********************************************

    //** START define Listeners *********************************************
    /** 最新画像取得リスナー */
    private final View.OnClickListener mButtonRequestListener = v -> getImageRequest();

    /**
     * ラジオグループ切り替えリスナー
     * <p>グループ内ウィジットの可・不可制御</p>
     */
    private final RadioGroup.OnCheckedChangeListener mRGrpChangeListener =(group, checkedId) -> {
        DEBUG_OUT.accept(TAG, "radioGroup: " + group.getId() + ",checkedId: " + checkedId);
        if (group.getId() == mRGrpDateRange.getId()) {
            // 期間ラジオグループ
            if (checkedId == mRadio2w.getId()) {
                // ２週間選択
                radio2wSelected();
            } else {
                // 月間選択
                radioYMSelected();
            }
        }
        // 切り替えごとに対応する画像ファイルが存在すれば復元する
        restoreImageView();
        // 対応する統計情報を該当するビューに復元
        restoreStatisticsViews();
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
        ArrayAdapter<String> adapter= new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item);
        // アダプターがスピナー選択リストに表示するために使用するレイアウト
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerYM.setAdapter(adapter);
        // 初期画面ではスピナー不可
        mSpinnerYM.setEnabled(false);
    }

    private void initWidget(View mainView) {
        // 検索期間(月間/２週間前) ラジオグループ
        mRGrpDateRange = mainView.findViewById(R.id.radioGroupBpDateRange);
        mRadioYM = mainView.findViewById(R.id.radioBpYM);
        mRadio2w = mainView.findViewById(R.id.radioBP2w);
        // グラフ型(折れ線/棒) ラジオグループはモニターしない
        mRadioGraphLine = mainView.findViewById(R.id.radioBpGraphLine);
        mRadioGraphBar = mainView.findViewById(R.id.radioBpGraphBar);
        // チェックボックス
        mChkIncludeToday = mainView.findViewById(R.id.chkIncludeBpToday);
        // プロット画像表示
        mImgView = mainView.findViewById(R.id.imgBloodBress);
        // ステータスビュー
        mTvStatus = mainView.findViewById(R.id.tvFragBpStatus);
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
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        DEBUG_OUT.accept(TAG, "onCreateView()");
        mMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        DEBUG_OUT.accept(TAG, "" + mMetrics);

        View mainView = inflater.inflate(
                R.layout.fragment_image_blood_press, container,false);
        initWidget(mainView);
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

        // アプリバーにタイトル表示
        FragmentUtil.setActionBarTitle((AppCompatActivity) requireActivity(),
                getString(R.string.imgfrag_bp_app_title));
        // メールアドレス必須
        String emailAddress = SharedPrefUtil.getEmailAddressInSettings(requireContext());
        DEBUG_OUT.accept(TAG, "check EmailAddress: " + emailAddress);
        if (TextUtils.isEmpty(emailAddress)) {
            // メールアドレス入力確認ダイアログ
            ActivityUtil.showConfirmDialogWithEmailAddress(
                    (AppCompatActivity) requireActivity(), getFragmentPosition());
            // ダイアログOKでメール入力した後、バックキーでこの画面に戻ってくる
            // ダイアログOKでメール入力した後、アプリバーの←でトップ画面に戻る
            // ダイアログCANCELならバックキー実行でトップ画面に遷移する
            // 戻り必須 ※初回登録日チェックまで実行されNullPoで落ちる
            return;
        }

        // 初期イメージ設定
        if (mNoImageBitmap == null) {
            AssetManager am = requireContext().getAssets();
            try {
                mNoImageBitmap = BitmapFactory.decodeStream(am.open(NO_IMAGE_FILE));
                Log.w(TAG, "mNoImageBitmap: " + mNoImageBitmap);
                if (mNoImageBitmap != null) {
                    mImgView.setImageBitmap(mNoImageBitmap);
                    mImageWd = mImgView.getWidth();
                    mImageHt = mImgView.getHeight();
                    DEBUG_OUT.accept(TAG, "ImageView.width: " + mImageWd + ",height: " + mImageHt);
                }
            }catch (IOException iex) {
                // 通常ここには来ない
                Log.w(TAG, iex.getLocalizedMessage());
            }
        }

        // 初回登録日チェックリクエストを暗黙的に実行
        requestFirstRegisterDayWithImplicitly();
        // DEBUG
        String fileNames = FileManager.checkFileNamesInContextDir(requireContext());
        DEBUG_OUT.accept(TAG, "Context.FilesDir in [ " + fileNames + "]");
        // DEBUG
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        Map<String, ?> prefAll = sharedPref.getAll();
        DEBUG_OUT.accept(TAG, "prefAll: " + prefAll);
        // プリファレンスからラジオボタンの状態を復元する ※onPauseで実行する
        if (restoreRadioButtonsState()) {
            // 当日データチェックウィジットの更新
            updateCheckIncludeToday();
            // 統計情報の復元
            restoreStatisticsViews();
            // 保存された画像ファイルがあれば復元する
            restoreImageView();
        }

        // ラジオグループリスナー登録
        mRGrpDateRange.setOnCheckedChangeListener(mRGrpChangeListener);
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

    private void showStatusNetworkUnavailable() {
        mTvStatus.setText(getString(R.string.warning_network_not_available));
        mTvStatus.setVisibility(View.VISIBLE);
    }

    /**
     * ImageViewに取得した画像を表示するとともにファイルに保存
     * @param urlPathIdx URLパスインデックス
     * @param data ResponseImageData
     */
    private void showSuccess(UrlPathIndex urlPathIdx, ResponseImageData data) {
        if (mTvStatus.getVisibility() == View.VISIBLE) {
            mTvStatus.setText("");
            mTvStatus.setVisibility(View.GONE);
        }
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
     */
    private void resetSuccessWidgets(UrlPathIndex pathIdx) {
        resetStatisticsViews();
        mImgView.setImageBitmap(mNoImageBitmap);
        // 対象画像ファイルとプリファレンスキー削除
        deleteSavedImage(pathIdx);
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
        editor.putBoolean(getString(R.string.pref_key_bp_radio_ym),
                mRadioYM.isChecked());
        editor.putBoolean(getString(R.string.pref_key_bp_radio_line),
                mRadioGraphLine.isChecked());
        // ラジオボタン
        // 保存の事実自体の保存 ※復元時にキーを削除
        editor.putString(getString(R.string.pref_key_bp_stop_saved),
                "saveRadioButtonsState");
        editor.apply();
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
            if (isLine && !mRadioGraphLine.isChecked()) {
                mRadioGraphLine.setChecked(true);
            } else {
                mRadioGraphBar.setChecked(true);
            }
            // 月間チェック
            boolean isYmChecked = sharedPref.getBoolean(getString(R.string.pref_key_bp_radio_ym),
                    false);
            if (isYmChecked && !mRadioYM.isChecked()) {
                mRadioYM.setChecked(true);
            }
            // 関連するラジオボタンを一括更新
            if (mRadioYM.isChecked()) {
                radioYMSelected();
            } else {
                radio2wSelected();
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
                // 復元した文字列に対応する位置
                ArrayAdapter<String> adapter = (ArrayAdapter<String>) mSpinnerYM.getAdapter();
                int pos = adapter.getPosition(ymVal);
                if (pos > 1) {
                    mSpinnerYM.setSelection(pos);
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
     */
    private BloodPressStatistics restoreStatisticsObject() {
        UrlPathIndex urlPathIdx = getUrlPathIndex();
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
     * @param urlPathIdx URLパスインデックス
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
     * @param pathIdx リクエストパスインデックス
     * @param bitmap 画像のBitmap
     */
    private void saveBitmapToFile(UrlPathIndex pathIdx, Bitmap bitmap) {
        String saveName = mSaveImageNames[pathIdx.getNum()];
        mHandler.post(() -> {
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
                    String prefKey = mPrefSaveImageKeys[pathIdx.getNum()];
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
     * @param pathIdx リクエストパスインデックス
     */
    private void deleteSavedImage(UrlPathIndex pathIdx) {
        SharedPreferences sharedPref = SharedPrefUtil.getSharedPrefInMainActivity(
                requireContext()
        );
        String prefKey = mPrefSaveImageKeys[pathIdx.getNum()];
        String savedPath = sharedPref.getString(prefKey, null);
        DEBUG_OUT.accept(TAG, "delete.prefKey: " + prefKey + ",path: " + savedPath);
        // 対象画像とプリファレンスキーを削除
        if (savedPath != null) {
            mHandler.post(() -> {
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
     * @return 保存した画像ファイルが存在すればBitmapオブジェクト
     */
    private Bitmap restoreBitmapFromFile() {
        // 現在選択されているラジオボタンからURLパスインデックス取得
        UrlPathIndex pathIdx = getUrlPathIndex();
        // 画像ファイル名保存のプリファレンスキー
        String prefKey = mPrefSaveImageKeys[pathIdx.getNum()];
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
     * 画像保存ファイルから血圧データ取得画像表示ビューに画像を復元する
     */
    private void restoreImageView() {
        Bitmap savedBitmap = restoreBitmapFromFile();
        DEBUG_OUT.accept(TAG, "savedBitmap: " + savedBitmap);
        if (savedBitmap != null) {
            mImgView.setImageBitmap(savedBitmap);
        }
    }

    /**
     * プリファレンスから統計情報オブジェクトを復元し対応するビューを更新
     */
    private void restoreStatisticsViews() {
        BloodPressStatistics stat = restoreStatisticsObject();
        if (stat != null) {
            showStatistics(stat);
        } else {
            resetStatisticsViews();
        }
    }
    //** END imageView: to save file/ restore from file *****************************

}

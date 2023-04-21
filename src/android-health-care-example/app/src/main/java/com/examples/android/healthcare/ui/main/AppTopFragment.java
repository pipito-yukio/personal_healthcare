package com.examples.android.healthcare.ui.main;

import static com.examples.android.healthcare.functions.AppTopUtil.UPD_KEY_BODY_TEMPER;
import static com.examples.android.healthcare.functions.MyLogging.DEBUG_OUT;
import static com.examples.android.healthcare.functions.AppTopUtil.UPD_KEY_SLEEP_MAN;
import static com.examples.android.healthcare.functions.AppTopUtil.UPD_KEY_BLOOD_PRESS;
import static com.examples.android.healthcare.functions.AppTopUtil.UPD_KEY_NOCT_FACT;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.viewbinding.BuildConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.examples.android.healthcare.HealthcareApplication;
import com.examples.android.healthcare.R;

import com.examples.android.healthcare.SettingsActivity;
import com.examples.android.healthcare.constants.JsonTemplate;
import com.examples.android.healthcare.constants.RequestDevice;
import com.examples.android.healthcare.data.BloodPressure;
import com.examples.android.healthcare.data.BodyTemperature;
import com.examples.android.healthcare.data.GetCurrentDataResult;
import com.examples.android.healthcare.data.HealthcareData;
import com.examples.android.healthcare.data.NocturiaFactors;
import com.examples.android.healthcare.data.RegisterData;
import com.examples.android.healthcare.data.RegisterResult;
import com.examples.android.healthcare.data.ResponseStatus;
import com.examples.android.healthcare.data.SleepManagement;
import com.examples.android.healthcare.data.WalkingCount;
import com.examples.android.healthcare.data.WeatherCondition;
import com.examples.android.healthcare.data.WeatherData;
import com.examples.android.healthcare.dialogs.CustomDialogs.EditDialogFragement;
import com.examples.android.healthcare.dialogs.CustomDialogs.ConfirmDialogFragment;
import com.examples.android.healthcare.dialogs.CustomDialogs.ConfirmDialogFragment.ConfirmOkCancelListener;
import com.examples.android.healthcare.dialogs.CustomDialogs.MessageOkDialogFragment;
import com.examples.android.healthcare.dialogs.PickerDialogs.DatePickerFragment;
import com.examples.android.healthcare.dialogs.PickerDialogs.TimePickerFragment;
import com.examples.android.healthcare.dialogs.PickerDialogs.NumberPickerDialog;
import com.examples.android.healthcare.functions.AppTopUtil;
import com.examples.android.healthcare.functions.AppTopUtil.JsonFileSaveTiming;
import com.examples.android.healthcare.functions.AppTopUtil.PostRequest;
import com.examples.android.healthcare.functions.FileManager;
import com.examples.android.healthcare.tasks.GetCurrentDataRepository;
import com.examples.android.healthcare.tasks.HealthcareRepository;
import com.examples.android.healthcare.tasks.NetworkUtil;
import com.examples.android.healthcare.tasks.ResisterDataRepository;
import com.examples.android.healthcare.tasks.Result;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AppTopFragment extends Fragment {
    private static final String TAG = AppTopFragment.class.getSimpleName();

    // NumberPickerDialog判別用タグキー: ID
    private static final Integer TAG_ID_MIDNIGHT_TOILET_COUNT = 0;
    private static final Integer TAG_ID_SLEEP_SCORE = 1;
    private static final Integer TAG_ID_BLOOD_PRESSURE_MAX = 2;
    private static final Integer TAG_ID_BLOOD_PRESSURE_MIN = 3;
    private static final Integer TAG_ID_PULSE_RATE = 4;

    public static AppTopFragment newInstance() {
        return new AppTopFragment();
    }

    // 保存ボタン
    private Button mBtnSave;
    // 送信ボタン (登録, 更新)
    private Button mBtnSend;
    // BLEデバイス取込みボタン
//    private Button mBtnBleImport;

    // 測定日付表示ウィジット: DatePickerDialogを通じて日付を設定
    private TextView mInpMeasurementDate;
    // 時刻系表示ウィジット: TimePickerDialog起動を通じて時刻を設定
    // 起床時刻: 必須
    private TextView mInpWakeupTime;
    // 睡眠時間: 必須
    private TextView mInpSleepingTime;
    // 深い睡眠: 任意
    private TextView mInpDeepSleepingTime;
    // 血圧測定時刻: 任意
    private TextView mInpMeasurementTime;
    // 数値系表示ウィジット: NumberPickerDialog起動を通じて整数値を設定
    // 夜間トイレ回数: 必須
    private TextView mInpMidnightToiletVisits;
    // 睡眠スコア: 任意
    private TextView mInpSleepScore;
    // 最高血圧 (午前/午後 共用): 任意
    private TextView mInpBloodPressureMax;
    // 最低血圧 (午前/午後 共用): 任意
    private TextView mInpBloodPressureMin;
    // 脈拍 (午前/午後 共用): 任意
    private TextView mInpPulseRate;
    // 体温: 任意 ※BLEデバイスから取り込む想定
    private TextView mInpBodyTemper;
    // 体温測定時刻: 任意 ※BLEデバイスから取り込む想定
    private TextView mInpBodyTemperTime;
    // 歩数: 必須
    private TextView mInpWalkingCount;
    // 天候: 必須
    private TextView mInpWeatherCond;
    // タイトルラベル: 夜間トイレ回数, 睡眠スコア, 最高血圧, 最低血圧, 脈拍
    private TextView mLblMidnightToiletVisits;
    private TextView mLblSleepScore;
    private TextView mLblBloodPressureMax;
    private TextView mLblBloodPressureMin;
    private TextView mLblPulseRate;
    // 単位ラベル: 夜間トイレ回数, 睡眠スコア, 最高血圧, 最低血圧, 脈拍
    private TextView mUnitMidnightToiletVisits;
    private TextView mUnitBloodPressureMax;
    private TextView mUnitBloodPressureMin;
    private TextView mUnitPulseRate;
    // 血圧測定: 午前・午後ラジオボタン
    private RadioButton mRadioMorning;
    private RadioButton mRadioEvening;
    //  選択されているラジオボタンID
    private int mSelectedRadioId;
    // 測定日付の比較用基準オブジェクト(本日)
    private final LocalDate mNowLocalDate = LocalDate.now();
    // DatePickerDialogに連動するカレンダーオブジェクト
    private final Calendar mMeasurementDayCal = Calendar.getInstance();
    // 夜間頻尿要因チェックボックス
    private CheckBox mChkCoffee;
    private CheckBox mChkTea;
    private CheckBox mChkAlcohol;
    private CheckBox mChkNutritionDrink; // 栄養ドリンク
    private CheckBox mChkSportsDrink; // スポーツドリンク(入浴後)
    private CheckBox mChkDiuretic;  // その他(利尿作用有り) 
    private CheckBox mChkTakeMedicine; // 服薬有無
    private CheckBox mChkTakeBathing; // 入浴
    // 直接入力ウィジット
    //  健康状態メモ: 任意
    private EditText mEditConditionMemo;
    // ステータステキスト(通常表示)
    private TextView mTextStatus;
    // ウォーニングステータス(ハイライト表示)
    private TextView mWarningStatus;
    // 全てのチェックボックス配列: [用途] 一括リセット
    private CheckBox[] mAllCheckBoxes;
    // 整数値系入力ウィジットのデフォルト値の格納キー ※数値の任意項目に付随する
    // (1) 睡眠スコア
    private int mKeyDefSleepScore;
    // (2) 最大血圧
    private int mKeyDefBloodPressureMax;
    // (3) 最低血圧
    private int mKeyDefBloodPressureMin;
    // (4) 脈拍
    private int mKeyDefPulseRate;

    // NumberPickerDialogのダイアログタイトル配列
    private String[] mAlertDialogTitles;
    // NumberPickerDialogの見出し配列
    private String[] mNumberPickerLabels;
    // NumberPickerDialogの単位配列
    private String[] mNumberPickerUnits;
    // NumberPickerDialogを起動し設定値を表示するウィジットの配列
    private TextView[] mNumberPickerViews;
    // NumberPickerDialogの範囲配列
    private String[] mNumberPickerRanges;
    // 時刻フォーマット文字列
    private String mFmtShowTime; // 時刻
    private String mFmtShowRangeTime; // 時間
    // 更新用データ
    // サーバーからデータを取得したときに設定され、新規登録時・JSONからの復元時nullを設定
    private RegisterData mRegisterDataForUpdate;
    // 更新チェック用マップ
    private final Map<String, Map<Integer, Boolean>> mUpdateCheckMap = new HashMap<>();
    // リクエストウォーニングマップ
    private final Map<Integer, String> mResponseWarningMap = new HashMap<>();
    // カレントのPostRequestオブジェクト: 登録か更新
    private PostRequest mCurrentPostRequest;

    // JSONファイル保存、プリファレンスコミット時に利用するハンドラー
    private final Handler mHandler = new Handler();

    //--- START: TextViewの更新メソッド ----
    /**
     * ステータスに日付関連のステータス文字列を表示する
     * @param dateText 日付文字列
     * @param showFmt 表示フォーマット
     */
    private void showStatusWithPreocessDate(String dateText, String showFmt) {
        if (mWarningStatus.getVisibility() == View.VISIBLE) {
            mWarningStatus.setVisibility(View.GONE);
            mTextStatus.setVisibility(View.VISIBLE);
        }
        int[] dates = AppTopUtil.splitDateValue(dateText);
        String status = String.format(showFmt, dates[0], dates[1], dates[2]);
        mTextStatus.setText(status);
    }

    /**
     * メッセージをウォーニングビューに表示する
     * <p>ステータスビューが表示されていたら非表示にしてからウォーニングビューを表示</p>
     * @param message メッセージ
     */
    private void showWarning(String message) {
        mWarningStatus.setText(message);
        if (mWarningStatus.getVisibility() != View.VISIBLE) {
            mWarningStatus.setVisibility(View.VISIBLE);
            mTextStatus.setVisibility(View.GONE);
        }
    }

    /**
     * メッセージをステータスビューに表示
     * <p>ウォーニングビューが表示されていたら非表示にする</p>
     * @param message メッセージ
     */
    private void showStatus(String message) {
        if (!TextUtils.isEmpty(message)) {
            mTextStatus.setText(message);
        } else {
            mTextStatus.setText("");
        }
        if (mWarningStatus.getVisibility() == View.VISIBLE) {
            mWarningStatus.setVisibility(View.GONE);
            mTextStatus.setVisibility(View.VISIBLE);
        }
    }

    /**
     * ステータスビューのクリア
     * <p>ウォーニングビューが表示されていたら非表示にしてからステータスビューを表示</p>
     */
    private void clearStatus() {
        mTextStatus.setText("");
        if (mWarningStatus.getVisibility() == View.VISIBLE) {
            mWarningStatus.setVisibility(View.GONE);
            mTextStatus.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 日付入力ウィジットの表示とキー付きTAG値(自分のIDがキー)を更新する
     *  カレンダーオブジェクトも更新する
     * @param tv 日付入力ウィジット
     * @param tagValue TAG値
     */
    private void updateDateView(TextView tv, String tagValue) {
        // 自分のIDをキーとしてISO拡張フォーマットの日付を保持する
        tv.setTag(tv.getId(), tagValue);
        // 日本語表示用日付を表示する
        int[] dates = AppTopUtil.splitDateValue(tagValue);
        int year = dates[0];
        int month = dates[1];
        int dayOfMonth = dates[2];
        String showDate = String.format(getString(R.string.format_show_date),
                year, month, dayOfMonth);
        tv.setText(showDate);
    }

    /**
     * 時刻系入力ウィジットのキー付きTAG値を設定し表示を更新する
     * @param tv 時刻系入力ウィジット
     * @param tagId キー付きTAGのID
     * @param tagValue キー付きTAG値
     * @param showTimeFmt 表示用時刻フォーマット
     */
    private void updateTimeViewByTag(TextView tv, int tagId, String tagValue, String showTimeFmt) {
        if (TextUtils.isEmpty(tagValue)) {
            tv.setTag(tagId, getString(R.string.init_tag_time_value));
        } else {
            tv.setTag(tagId, tagValue);
        }
        DEBUG_OUT.accept(TAG, "View.Id: " + tv.getId() + ",TagId: " + tagId + ",tagValue: "
                + tagValue);
        String showTime = tagToShowTime(tagValue, showTimeFmt);
        tv.setText(showTime);
    }

    /**
     * 時刻系入力ウィジットのキー付きタグにタグ値をテキストにフォーマット済み時刻を設定する
     * @param tv 時刻系入力ウィジット
     * @param tagValue タグ値(nullも含む)
     * @param showTimeFmt 時刻フォーマット
     */
    private void updateTimeView(TextView tv, String tagValue, String showTimeFmt) {
        updateTimeViewByTag(tv, tv.getId(), tagValue, showTimeFmt);
    }

    /**
     * 整数値系入力ウィジットのキー付きTAGにタグ値とテキストに設定する
     * @param tv 整数値系入力ウィジット
     * @param tagId ダグID
     * @param tagValue タグ値 (null可)
     */
    private void updateNumberViewByTag(TextView tv, int tagId, Integer tagValue) {
        tv.setTag(tagId, tagValue);
        if (tagValue != null) {
            tv.setText(String.valueOf(tagValue));
        } else {
            tv.setText("");
        }
    }

    /**
     * 整数値系入力ウィジットに数値オブジェクトをキー付きタグとテキストに設定する
     * @param tv 整数値系入力ウィジット
     * @param value テキスト値
     */
    private void updateNumberView(TextView tv, Integer value) {
        updateNumberViewByTag(tv, tv.getId(), value);
    }
    //--- END: TextViewの更新メソッド ----

    /**
     * 整数値系入力ウィジットのタグに保持している値をウィジットに表示する
     *  <p>但しタグIDがnullならブランクを設定</p>
     *  [表示] TAG値 [キー付きTAG値]
     * @param tv 整数値系入力ウィジット
     * @param tagId タグID
     */
    private void showNumberViewByTag(TextView tv, int tagId) {
        Object tagValue = tv.getTag(tagId);
        DEBUG_OUT.accept(TAG, "View.Id: " + tv.getId() + ",tagValue: " + tagValue);
        if (tagValue == null) {
            tv.setText("");
        } else {
            tv.setText(String.valueOf(tagValue));
        }
    }

    //--- START: TextViewの変換系メソッド ----
    /**
     * 時刻系入力ウィジットとキー付きTAGのIDから時刻文字列を取得する
     * @param tv 時刻系入力ウィジット
     * @param tagId キー付きTAGのID
     * @return 時刻文字列
     */
    private String toStringOfTimeViewByTag(TextView tv, int tagId) {
        String timeValue = (String) tv.getTag(tagId);
        if (getString(R.string.init_tag_time_value).equals(timeValue)) {
            return null;
        } else {
            return timeValue;
        }
    }

    /**
     * 時刻系入力ウィジットから時刻文字列を取得する
     * @param tv 時刻系入力ウィジット
     * @return 時刻文字列
     */
    private String toStringOfTimeView(TextView tv) {
        return toStringOfTimeViewByTag(tv, tv.getId());
    }

    /**
     * TextViewから入力された文字列を取得する
     * @param tv TextView
     * @return テキストが空ならnull, 空以外なりテキスト
     */
    private String toStringOfTextView(TextView tv) {
        String text = tv.getText().toString();
        if (TextUtils.isEmpty(text)) {
            return null;
        } else {
            return text;
        }
    }

    /**
     * 自分自身のIDをキーにTextViwのTagから値を取得する
     * @param tv TextViwe
     * @return TextViweに格納しているTag値
     */
    private String toStringOfTextViewBySelfTag(TextView tv) {
        return (String) tv.getTag(tv.getId());
    }

    /**
     * 整数値系入力ウィジットのテキストからIntegerオブジェクトを生成する
     * @param tv 整数値系入力ウィジット
     * @return Integerオブジェクト, テキストがブランクならnull
     */
    private Integer toIntegerOfNumberView(TextView tv) {
        String numberValue = tv.getText().toString();
        if (TextUtils.isEmpty(numberValue)) {
            return null;
        } else {
            return Integer.valueOf(numberValue);
        }
    }

    /**
     * 整数値系入力ウィジットとTAG値からIntegerオブジェクトを生成する
     * @param tv 整数値系入力ウィジット
     * @param tagId キー付きTAGのID
     * @return タグ値がnullならnull, それ以外はIntegerオブジェクト
     */
    private Integer toIntegerOfNumberViewByTag(TextView tv, int tagId) {
        Object value = tv.getTag(tagId);
        if (value == null) {
            return null;
        }
        return (Integer) value;
    }
    //--- END: TextViewの変換系メソッド ----

    //-- START TextViewのリストア系メソッド
    /**
     * 時刻系入力ウィジット(TextViwe)を引数の時刻文字列で復元(表示)する
     * @param restoreTime 時刻文字列 (null可)
     * @param tv 設定するTextView
     * @param showTimeFormat 表示時刻フォーマット
     */
    private void restoreTimeViewByValue(String restoreTime, TextView tv, String showTimeFormat) {
        if (!TextUtils.isEmpty(restoreTime)) {
            updateTimeView(tv, restoreTime, showTimeFormat);
        } else {
            // nullなら時刻の初期値を設定
            updateTimeView(tv, getString(R.string.init_tag_time_value), showTimeFormat);
        }
    }

    /**
     * 整数系入力ウィジット(TextViwe)に引数の整数オブジェクト復元(表示)する
     * @param restoreNumber 整数オブジェクト (null可)
     * @param tv 設定するTextView
     */
    private void restoreNumberViewByValue(Integer restoreNumber, TextView tv) {
        if (restoreNumber != null) {
            updateNumberView(tv, restoreNumber);
        } else {
            updateNumberView(mInpSleepScore, null);
        }
    }
    //--- END: TextViewのリストア系メソッド

    // ボタンクリックリスナー
    private final View.OnClickListener mBtnOnClickListener = v -> {
        if (v.getId() == R.id.btnSave) {
            saveJsonTextFromInputWidgets();
        } else if (v.getId() == R.id.btnSend) {
            sendRegisterData();
        }
    };

    // RadioGroup: 午前/午後ラジオボタンの切替えイベント
    private final RadioGroup.OnCheckedChangeListener mRadioChangedListener =
            new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            mSelectedRadioId = checkedId;
            DEBUG_OUT.accept(TAG, "SelectedRadio.Id:" + mSelectedRadioId);

            // 午前/午後 ごと入力値に切り替える: それぞれのTAGに設定した測定時刻を表示用に変換する
            mInpMeasurementTime.setText(
                    tagToShowTime(
                        (String) mInpMeasurementTime.getTag(mSelectedRadioId), mFmtShowTime
                    )
            );
            // 測定時刻以外はタグの値(数値オブジェクト)を設定する
            showNumberViewByTag(mInpBloodPressureMax, mSelectedRadioId);
            showNumberViewByTag(mInpBloodPressureMin, mSelectedRadioId);
            showNumberViewByTag(mInpPulseRate, mSelectedRadioId);
        }
    };
    
    // 日付ピッカーダイアログ起動イベントリスナー
    private final View.OnClickListener mDatePickerViewClickListener = this::showDatePicker;
    
    // 時刻ピッカーダイアログ起動イベントリスナー
    private final View.OnClickListener mTimePickerViewClickListener = v -> {
        if (v.getId() == R.id.inpMeasurementTime) {
            // 血圧測定時刻([時間帯] 午前/午後): 各時間帯の入力値はウィジットの時間帯毎のキー付きTAGに設定
            showBloodPressureTimePicker(v);
        } else {
            // 起床時刻, 睡眠時間, 深い睡眠, 体温測定時刻
            showTimePicker(v);
        }
    };

    // NumberPickerDialog起動イベントリスナー
    private final View.OnClickListener mNumberPickerClickListener = v -> {
        // デフォルトのTAG値から入力対象のキーを取得する
        int viewTag = (Integer) v.getTag();
        String title = mAlertDialogTitles[viewTag];
        String label = mNumberPickerLabels[viewTag];
        String unit = mNumberPickerUnits[viewTag];
        TextView inpView = mNumberPickerViews[viewTag];
        // 初期値は数値系入力ウィジットから取得
        Integer initValue = getInitNumberValueOfTextView(inpView);
        // NumberPickerDialogに引き渡す最小値, 最大値
        String strRange = mNumberPickerRanges[viewTag];
        String[] strRanges = strRange.split(",");
        int minValue = Integer.parseInt(strRanges[0]);
        int maxValue = Integer.parseInt(strRanges[1]);
        showNumberPickerDialog(title, inpView, label, unit, initValue, minValue, maxValue);
    };

    // 数値系EditText入力ダイアログ起動イベントリスナー[体温入力(実数), 歩数入力(整数)]
    private final View.OnClickListener mNumberInputClickListener = v -> {
        EditDialogFragement.EditOkCancelListener listener =
                new EditDialogFragement.EditOkCancelListener() {
                    @Override
                    public void onOk(String editValue) {
                        if (v.getId() == mInpBodyTemper.getId()) {
                            if (!TextUtils.isEmpty(editValue)) {
                                // 実数値は小数点第1位で表示
                                String showValue = String.format(Locale.JAPANESE,
                                        AppTopUtil.FMT_BODY_TEMPER, Double.valueOf(editValue));
                                mInpBodyTemper.setText(showValue);
                            } else {
                                mInpBodyTemper.setText("");
                            }
                            // 入力ビューと入力値をonChangeリスナーに設定
                            mOnNumberChangedOnEditDialog.onChanged((TextView) v, editValue);
                        } else {
                            mInpWalkingCount.setText(editValue);
                            mOnNumberChangedOnEditDialog.onChanged((TextView) v, editValue);
                        }
                    }
                    @Override
                    public void onCancel() {
                    }
                };
        // EditTextの引数
        EditDialogFragement.EditInputType editInputType;
        String title;
        String value;
        if (v.getId() == mInpBodyTemper.getId()) {
            editInputType = EditDialogFragement.EditInputType.BODY_TEMPER;
            title = String.format(getString(R.string.format_input_title),
                    getString(R.string.lbl_body_temper));
            value = mInpBodyTemper.getText().toString();
        } else {
            editInputType = EditDialogFragement.EditInputType.WALKING_COUNT;
            title = String.format(getString(R.string.format_input_title),
                    getString(R.string.lbl_walking_count));
            value = mInpWalkingCount.getText().toString();
        }
        DialogFragment fragment = EditDialogFragement.newInstance(title, value,
                editInputType, listener);
        fragment.show(requireActivity().getSupportFragmentManager(), "EditDialogFragment");
    };

    // テキスト入力ダイアログ起動イベントリスナー [天候]
    private final View.OnClickListener mTextInputClickListener = v -> {
        EditDialogFragement.EditOkCancelListener listener =
                new EditDialogFragement.EditOkCancelListener() {
            @Override
            public void onOk(String editValue) {
                mInpWeatherCond.setText(editValue);
                mOnTextChangedOnEditDialog.onChanged((TextView) v, editValue);
            }
            @Override
            public void onCancel() {
            }
        };
        String title = String.format(getString(R.string.format_input_title),
                getString(R.string.lbl_weather));
        String value = mInpWeatherCond.getText().toString();
        DialogFragment fragment = EditDialogFragement.newInstance(title, value,
                EditDialogFragement.EditInputType.NONE, listener);
        fragment.show(requireActivity().getSupportFragmentManager(), "EditDialogFragment");
    };

    // チェックボックス更新イベントリスナー
    private final OnCheckedChangeListener mOnCheckedChangeListener = (compButton, isChecked) -> {
        if (mRegisterDataForUpdate != null) {
            // 取得データとの変更チェック
            NocturiaFactors factors = mRegisterDataForUpdate.getHealthcareData()
                    .getNocturiaFactors();
            // ラベルはcompoundButtonから取得
            String tvLabel = compButton.getText().toString();
            DEBUG_OUT.accept(TAG, "OnChange(" + tvLabel + "): " + isChecked);
            // 変更があればステータス表示
            if (isUpdateNocturiaFactors(factors, compButton, isChecked)) {
                putValueInUpdateCheckMap(UPD_KEY_NOCT_FACT, compButton.getId(), true);
                String status = String.format(getString(R.string.status_onchange_with_item),
                        tvLabel);
                showStatus(status);
            } else {
                putValueInUpdateCheckMap(UPD_KEY_NOCT_FACT, compButton.getId(), false);
            }
        }
    };

    // EditTextウィジットフォーカスチェンジリスナー ※テキストの変更チェック用
    private final View.OnFocusChangeListener mEditFocusChangeListener = (v, hasFocus) -> {
        if (mRegisterDataForUpdate != null) {
            if (!hasFocus) {
                // フォーカスがなくなったら変更チェック
                if (v.getId() == mEditConditionMemo.getId()) {
                    // 健康状態メモ
                    NocturiaFactors factors = mRegisterDataForUpdate.getHealthcareData()
                            .getNocturiaFactors();
                    String beforeValue = factors.getConditionMemo();
                    if (!TextUtils.equals(mEditConditionMemo.getText().toString(), beforeValue)) {
                        putValueInUpdateCheckMap(UPD_KEY_NOCT_FACT, v.getId(), true);
                        String status = String.format(getString(R.string.status_onchange_with_item),
                                getString(R.string.edit_hint_body_condition));
                        showStatus(status);
                    } else {
                        putValueInUpdateCheckMap(UPD_KEY_NOCT_FACT, v.getId(), false);
                    }
                }
            }
        }
    };

    // 数値ピッカー系入力テキストビュー変更通知 [睡眠スコア (睡眠管理), 夜間トイレ回数 (夜間頻尿要因)]
    private final AppTopCustom.OnTextViewChanged mOnNumberViewChanged = (tv, setValue) -> {
        if (mRegisterDataForUpdate != null) {
            int viewId = tv.getId();
            if (viewId == mInpSleepScore.getId()) {
                // 睡眠スコア (任意)
                SleepManagement sleepMan = mRegisterDataForUpdate.getHealthcareData()
                        .getSleepManagement();
                Integer beforeValue = sleepMan.getSleepScore();
                DEBUG_OUT.accept(TAG, "SleepScore.after: " + setValue
                        + ",before: " + beforeValue);
                if (AppTopUtil.isDifferentIntegerValue(setValue, beforeValue)) {
                    putValueInUpdateCheckMap(UPD_KEY_SLEEP_MAN, viewId, true);
                    String status = String.format(getString(R.string.status_onchange_with_item),
                            getString(R.string.lbl_sleep_score));
                    showStatus(status);
                } else {
                    putValueInUpdateCheckMap(UPD_KEY_SLEEP_MAN, viewId, false);
                }
            } else {
                // 夜間トイレ回数(必須)
                NocturiaFactors factors = mRegisterDataForUpdate.getHealthcareData()
                        .getNocturiaFactors();
                Integer beforeValue = factors.getMidnightToiletVisits();
                DEBUG_OUT.accept(TAG, "MidNightVisits.after: " + setValue
                        + ",before: " + beforeValue);
                if (AppTopUtil.isDifferentIntegerValue(setValue, beforeValue)) {
                    putValueInUpdateCheckMap(UPD_KEY_NOCT_FACT, viewId, true);
                    String status = String.format(getString(R.string.status_onchange_with_item),
                            getString(R.string.lbl_midnight_toilet_visits));
                    showStatus(status);
                } else {
                    putValueInUpdateCheckMap(UPD_KEY_NOCT_FACT, viewId, false);
                }
            }
        }
    };

    // 時刻ピッカー系の入力変更通知 (1)睡眠管理: 起床時刻, 睡眠時間, 深い睡眠 (2)体温
    private final AppTopCustom.OnTextViewChanged mOnTimeViewChanged = (tv, setValue) -> {
        if (mRegisterDataForUpdate != null) {
            String beforeValue;
            String tvLabel;
            int viewId = tv.getId();
            if (viewId == mInpBodyTemperTime.getId()) {
                // 体温測定時刻
                BodyTemperature bodyTemper = mRegisterDataForUpdate.getHealthcareData()
                        .getBodyTemperature();
                beforeValue = bodyTemper.getMeasurementTime();
                DEBUG_OUT.accept(TAG, "BodyTemper.after: " + setValue
                        + ",before: " + beforeValue);
                if (!TextUtils.equals(setValue, beforeValue)) {
                    putValueInUpdateCheckMap(UPD_KEY_BODY_TEMPER, viewId, true);
                    String status = String.format(getString(R.string.status_onchange_with_item),
                            getString(R.string.lbl_body_temper_time));
                    showStatus(status);
                } else {
                    putValueInUpdateCheckMap(UPD_KEY_BODY_TEMPER, viewId, false);
                }
            } else {
                // 睡眠管理
                SleepManagement sleepMan = mRegisterDataForUpdate.getHealthcareData()
                        .getSleepManagement();
                if (viewId == mInpWakeupTime.getId()) {
                    beforeValue = sleepMan.getWakeupTime();
                    tvLabel = getString(R.string.lbl_wakeup_time);
                } else if (viewId == mInpSleepingTime.getId()) {
                    beforeValue = sleepMan.getSleepingTime();
                    tvLabel =  getString(R.string.lbl_sleeping_time);
                } else {
                    beforeValue = sleepMan.getDeepSleepingTime();
                    tvLabel =  getString(R.string.lbl_deep_sleeping_time);
                }
                DEBUG_OUT.accept(TAG, "SleepManagment.viewId(" + viewId
                        + "): after: " + setValue + ",before: " + beforeValue);
                if (!TextUtils.equals(setValue, beforeValue)) {
                    String status = String.format(getString(R.string.status_onchange_with_item),
                            tvLabel);
                    putValueInUpdateCheckMap(UPD_KEY_SLEEP_MAN, viewId, true);
                    showStatus(status);
                } else {
                    putValueInUpdateCheckMap(UPD_KEY_SLEEP_MAN, viewId, false);
                }
            }
        }
    };

    // 血圧測定時刻変更通知
    private final AppTopCustom.OnTextViewTagChanged mOnBloodPressTimeChanged =
            (tv, radioId, setValue) -> {
        if (mRegisterDataForUpdate != null) {
            BloodPressure bp = mRegisterDataForUpdate.getHealthcareData().getBloodPressure();
            String beforeValue;
            String tvLabel;
            if (radioId == mRadioMorning.getId()) {
                beforeValue = bp.getMorningMeasurementTime();
                tvLabel = getString(R.string.lbl_morning) + getString(R.string.lbl_measurement_time);
            } else {
                beforeValue = bp.getEveningMeasurementTime();
                tvLabel = getString(R.string.lbl_evening) + getString(R.string.lbl_measurement_time);
            }
            // 血圧測定キー: 入力ウィジットID + 選択されているラジオボタンID
            int viewByTagId = tv.getId() + radioId;
            DEBUG_OUT.accept(TAG, "BloodPressure.viewById(" + viewByTagId
                    + "): after: " + setValue + ",before: " + beforeValue);
            // ステータス表示
            if (!TextUtils.equals(setValue, beforeValue)) {
                putValueInUpdateCheckMap(UPD_KEY_BLOOD_PRESS, viewByTagId, true);
                String status = String.format(getString(R.string.status_onchange_with_item),
                        tvLabel);
                showStatus(status);
            } else {
                putValueInUpdateCheckMap(UPD_KEY_BLOOD_PRESS, viewByTagId, false);
            }
        }
    };

    // 血圧測定値入力変更通知
    private final AppTopCustom.OnTextViewTagChanged mOnBloodPressNumberChanged =
            (tv, radioId, setValue) -> {
        if (mRegisterDataForUpdate != null) {
            BloodPressure bp = mRegisterDataForUpdate.getHealthcareData().getBloodPressure();
            Integer beforeValue;
            int viewId = tv.getId();
            String tvLabel;
            if (viewId == mInpBloodPressureMax.getId()) {
                if (radioId == mRadioMorning.getId()) {
                    beforeValue = bp.getMorningMax();
                } else {
                    beforeValue = bp.getEveningMax();
                }
                tvLabel = getString(R.string.lbl_blood_pressure_max);
            } else if(viewId == mInpBloodPressureMin.getId()) {
                if (radioId == mRadioMorning.getId()) {
                    beforeValue = bp.getMorningMin();
                } else {
                    beforeValue = bp.getEveningMin();
                }
                tvLabel = getString(R.string.lbl_blood_pressure_min);
            } else {
                if (radioId == mRadioMorning.getId()) {
                    beforeValue = bp.getMorningPulseRate();
                } else {
                    beforeValue = bp.getEveningPulseRate();
                }
                tvLabel = getString(R.string.lbl_pulse_rate);
            }
            // 血圧測定キー: 入力ウィジットID + 選択されているラジオボタンID
            int viewByTagId = tv.getId() + radioId;
            DEBUG_OUT.accept(TAG, "BloodPressure.viewById(" + viewByTagId
                    + "): after: " + setValue + ",before: " + beforeValue);
            // ステータス表示
            if (AppTopUtil.isDifferentIntegerValue(setValue, beforeValue)) {
                putValueInUpdateCheckMap(UPD_KEY_BLOOD_PRESS, viewByTagId, true);
                String status = String.format(getString(R.string.status_onchange_with_item),
                        tvLabel);
                showStatus(status);
            } else {
                putValueInUpdateCheckMap(UPD_KEY_BLOOD_PRESS, viewByTagId, false);
            }
        }
    };

    // 数値系ExitText代替入力変更通知 (1)体温 (2)歩数
    private final AppTopCustom.OnTextViewChanged mOnNumberChangedOnEditDialog = (tv, setValue) -> {
        if (mRegisterDataForUpdate != null) {
            int viewId = tv.getId();
            if (viewId == mInpBodyTemper.getId()) {
                BodyTemperature bodyTemper = mRegisterDataForUpdate.getHealthcareData()
                        .getBodyTemperature();
                Double beforeValue = bodyTemper.getTemperature();
                DEBUG_OUT.accept(TAG, "BodyTemper.after: " + setValue
                        + ",before: " + beforeValue);
                // 体温は更新チェックマップを更新
                if (AppTopUtil.isDefferentDoubleValue(setValue, beforeValue)) {
                    putValueInUpdateCheckMap(UPD_KEY_BODY_TEMPER, viewId, true);
                    String status = String.format(getString(R.string.status_onchange_with_item),
                            getString(R.string.lbl_body_temper));
                    showStatus(status);
                } else {
                    putValueInUpdateCheckMap(UPD_KEY_BODY_TEMPER, viewId, false);
                }
            } else if (viewId == mInpWalkingCount.getId()) {
                WalkingCount wc = mRegisterDataForUpdate.getHealthcareData()
                        .getWalkingCount();
                Integer beforeValue = wc.getCounts();
                DEBUG_OUT.accept(TAG, "WalkingCount.after: " + setValue
                        + ",before: " + beforeValue);
                if (AppTopUtil.isDifferentIntegerValue(setValue, beforeValue)) {
                    // 歩数は変更をステータスに表示するのみ
                    String status = String.format(getString(R.string.status_onchange_with_item),
                            getString(R.string.lbl_walking_count));
                    showStatus(status);
                }
            }
        }
    };

    // 天候入力変更通知
    private final AppTopCustom.OnTextViewChanged mOnTextChangedOnEditDialog = (tv, setValue) -> {
        if (mRegisterDataForUpdate != null) {
            WeatherCondition wc = mRegisterDataForUpdate.getWeatherData()
                    .getWeatherCondition();
            String beforeValue = wc.getCondition();
            DEBUG_OUT.accept(TAG, "after: " + setValue + ",before: " + beforeValue);
            if (!TextUtils.equals(setValue, beforeValue)) {
                // 変更をステータスに表示するのみ
                String status = String.format(getString(R.string.status_onchange_with_item),
                        getString(R.string.lbl_weather));
                showStatus(status);
            }
        }
    };

    /**
     * 整数値系入力ウィジットから初期値(または設定値)を取得する
     *  <p>(1)睡眠スコア, (2)最高血圧, (3)最低血圧, (4)脈拍</p>
     * @param tv 整数値系入力ウィジット
     * @return テキストがブランク以外なら数値テキストの整数オブジェクト,それ以外はデフォルト値
     */
    private Integer getInitNumberValueOfTextView(TextView tv) {
        Integer result;
        if (TextUtils.isEmpty(tv.getText().toString())) {
            // 任意項目のテキストがブランクの場合はそれぞれの入力項目のデフォルト値をTAGから取得する
            if (tv.getId() == R.id.inpBloodPressureMax/*最高血圧*/) {
                result = (Integer) mInpBloodPressureMax.getTag(mKeyDefBloodPressureMax);
            } else if (tv.getId() == R.id.inpBloodPressureMin/*最低血圧*/) {
                result = (Integer) mInpBloodPressureMin.getTag(mKeyDefBloodPressureMin);
            } else if (tv.getId() == R.id.inpPulseRate/*脈拍*/) {
                result = (Integer) mInpPulseRate.getTag(mKeyDefPulseRate);
            } else {/*睡眠スコア*/
                result = (Integer) mInpSleepScore.getTag(mKeyDefSleepScore);
            }
        } else {
            // 必須項目のテキストから整数値を取得 ※数値系のTextViewなので必ず整数が設定される
            result = Integer.parseInt(tv.getText().toString());
        }
        return result;
    }

    /**
     * 本日日付を測定日付TextViewに設定する
     * @param v 測定日付ウィジット
     */
    private void setTodayValue(TextView v) {
        Date now = new Date();
        // TAG値用の日付生成
        String tagValue = String.format(AppTopUtil.ISO_8601_DATE_FORMAT, now);
        updateDateView(v, tagValue);
    }

    /**
     * 時刻系入力ウィジットのTAG値から表示用の時刻文字列を生成する
     * @param tagTime TAGに設定されている時刻("hh:mm")
     * @param showTimeFmt 表示フォーマット
     * @return 表示用の時刻文字列
     */
    private String tagToShowTime(String tagTime, String showTimeFmt) {
        String[] times = tagTime.split(":");
        int hour = Integer.parseInt(times[0]);
        int minute = Integer.parseInt(times[1]);
        return String.format(showTimeFmt, hour, minute);
    }

    /**
     * NumberPickerDialog用の配列初期化
     */
    private void initArraysForNumberPicker() {
        // ダイアログタイトル配列
        mAlertDialogTitles = new String[] {
                getString(R.string.alertdialogtitle_toiletvisits),
                getString(R.string.alertdialogtitle_sleepscore),
                getString(R.string.alertdialogtitle_bloodpressure),
                getString(R.string.alertdialogtitle_bloodpressure),
                getString(R.string.alertdialogtitle_bloodpressure),
        };
        // 入力項目見出し配列
        mNumberPickerLabels = new String[] {
                mLblMidnightToiletVisits.getText().toString(),
                mLblSleepScore.getText().toString(),
                mLblBloodPressureMax.getText().toString(),
                mLblBloodPressureMin.getText().toString(),
                mLblPulseRate.getText().toString(),
        };
        // 入力項目単位配列
        mNumberPickerUnits = new String[] {
                mUnitMidnightToiletVisits.getText().toString(),
                ""/* 睡眠スコアは単位なし */,
                mUnitBloodPressureMax.getText().toString(),
                mUnitBloodPressureMin.getText().toString(),
                mUnitPulseRate.getText().toString(),
        };
        // 入力項目ウィジット配列
        mNumberPickerViews = new TextView[] {
                mInpMidnightToiletVisits, mInpSleepScore, mInpBloodPressureMax,
                mInpBloodPressureMin, mInpPulseRate,
        };
        // 数値の範囲文字列("最大値,最小値")配列
        mNumberPickerRanges = new String[] {
                getString(R.string.range_midnight_toiletvisits),
                getString(R.string.range_sleep_score),
                getString(R.string.range_bloodpressure_max),
                getString(R.string.range_bloodpressure_min),
                getString(R.string.range_pluse_rate),
        };
    }

    /**
     * サーバから取得したデータの更新をチェックするマップの初期化
     * <p>入力項目が2つ以上あるの入力グループのID配列のマップ初期化する</p>
     * <ul>【グループ】:【キー】
     *     <li>睡眠管理: UPD_KEY_SLEEP_MAN</li>
     *     <li>血圧測定: UPD_KEY_BLOOD_PRESS</li>
     *     <li>体温測定: UPD_KEY_BODY_TEMPER</li>
     *     <li>夜間頻尿要因: UPD_KEY_NOCT_FACT</li>
     * </ul>
     */
    private void initUpdateCheckMap() {
        // 睡眠管理
        Integer[] sleepManKeys = {R.id.inpWakeupTime, R.id.inpSleepScore, R.id.inpSleepingTime,
                R.id.inpDeepSleepingTime};
        Map<Integer, Boolean> sleepManMap = new HashMap<>();
        for (Integer id : sleepManKeys) {
            sleepManMap.put(id, false);
        }
        mUpdateCheckMap.put(UPD_KEY_SLEEP_MAN, sleepManMap);
        // 血圧測定
        int morningId = R.id.radioMorning;
        int eveningId = R.id.radioEvening;
        int morningTimeId = R.id.inpMeasurementTime + morningId;
        int eveningTimeId = R.id.inpMeasurementTime + eveningId;
        int morningBloodMax = R.id.inpBloodPressureMax + morningId;
        int eveningBloodMax = R.id.inpBloodPressureMax + eveningId;
        int morningBloodMin = R.id.inpBloodPressureMin + morningId;
        int eveningBloodMin = R.id.inpBloodPressureMin + eveningId;
        int morningPulseRate = R.id.inpPulseRate + morningId;
        int eveningPulseRate = R.id.inpPulseRate + eveningId;
        Integer[] bloodPressureKeysArray = new Integer[]{
                morningTimeId, morningBloodMax, morningBloodMin, morningPulseRate,
                eveningTimeId, eveningBloodMax,eveningBloodMin, eveningPulseRate};
        Map<Integer, Boolean> bloodPressMap = new HashMap<>();
        for (Integer id : bloodPressureKeysArray) {
            bloodPressMap.put(id, false);
        }
        mUpdateCheckMap.put(UPD_KEY_BLOOD_PRESS, bloodPressMap);
        // 体温測定
        Integer[] bodyTemperArray = {R.id.inpBodyTemper, R.id.inpBodyTemperTime};
        Map<Integer, Boolean> bodyTemperMap = new HashMap<>();
        for (Integer id : bodyTemperArray) {
            bodyTemperMap.put(id, false);
        }
        mUpdateCheckMap.put(UPD_KEY_BODY_TEMPER, bodyTemperMap);
        // 夜間頻尿要因
        Integer[] noctFactKeys = {
                R.id.inpMidnightToiletVisits, R.id.chkCoffee, R.id.chkTea, R.id.chkAlcohol,
                R.id.chkNutritionDrink, R.id.chkSportsDrink, R.id.chkDiuretic,
                R.id.chkTakeMedicine, R.id.chkTakeBathing, R.id.editConditionMemo
        };
        Map<Integer, Boolean> noctFactMap = new HashMap<>();
        for (Integer id : noctFactKeys) {
            noctFactMap.put(id, false);
        }
        mUpdateCheckMap.put(UPD_KEY_NOCT_FACT, noctFactMap);
    }

    /**
     * レスポンス時のフォーニングメッセージ用マップの初期化
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
     * 読込みデータの更新確認用マップの更新フラグリセット
     * <p>タイミング: 更新チェック用モニター開始時</p>
     */
    private void resetUpdateCheckMap() {
        // https://www.geeksforgeeks.org/hashmap-replaceallbifunction-method-in-java-with-examples/
        // HashMap replaceAll(BiFunction) method in Java with Examples
        Map<Integer, Boolean> sleepManMap = mUpdateCheckMap.get(UPD_KEY_SLEEP_MAN);
        assert sleepManMap != null;
        sleepManMap.replaceAll((key, value) -> false);
        Map<Integer, Boolean> bloodPressMap = mUpdateCheckMap.get(UPD_KEY_BLOOD_PRESS);
        assert bloodPressMap != null;
        bloodPressMap.replaceAll((key, value) -> false);
        Map<Integer, Boolean> bodyTemperMap = mUpdateCheckMap.get(UPD_KEY_BODY_TEMPER);
        assert bodyTemperMap != null;
        bodyTemperMap.replaceAll((key, value) -> false);
        Map<Integer, Boolean> noctFactMap = mUpdateCheckMap.get(UPD_KEY_NOCT_FACT);
        assert noctFactMap != null;
        noctFactMap.replaceAll((key, value) -> false);
    }

    /**
     * 入力グループ名に対応する更新マップの更新有無
     * @param inputGroup UPD_KEY_SLEEP_MAN, UPD_KEY_BLOOD_PRESS, UPD_KEY_BODY_TEMPER,
     *                   UPD_KEY_NOCT_FACT
     * @return ひとつでもtrueの項目が有るなら(更新有り)true
     */
    private boolean hasTrueInUpdateCheckMap(String inputGroup) {
        Map<Integer, Boolean> propMap = mUpdateCheckMap.get(inputGroup);
        assert propMap != null;
        DEBUG_OUT.accept(TAG, inputGroup + ": " + propMap);
        return propMap.containsValue(true);
    }

    /**
     * 入力グループ名と入力項目に対応するマップの変更有無を設定する
     * @param groupKey 入力グループキー
     * @param key 入力項目キー
     * @param value 変更有無(true|false)
     */
    private void putValueInUpdateCheckMap(String groupKey, Integer key, Boolean value) {
        Map<Integer, Boolean> propMap = mUpdateCheckMap.get(groupKey);
        assert propMap != null;
        propMap.put(key, value);
    }

    /**
     * EditTextウィジットに共通の設定を行う
     * <ul>
     * <li>フォーカス時にテキストを全選択状態にする</li>
     * <li><フォーカスチェンジリスナーを設定する/li>
     * </ul>
     * @param editText 直接入力系ウィジット
     * @param isEnable 編集可否
     */
    private void setEditTextSetting(EditText editText, boolean isEnable) {
        // フォーカス時にテキストを全選択状態にする
        // https://stackoverflow.com/questions/4669464/select-all-text-inside-edittext-when-it-gets-focus
        editText.setSelectAllOnFocus(isEnable);
        editText.setFocusableInTouchMode(isEnable);
        editText.setEnabled(isEnable);
        // フォーカスON/OFF時
        if (isEnable) {
            editText.setOnFocusChangeListener(mEditFocusChangeListener);
        } else {
            editText.setOnFocusChangeListener(null);
        }
    }

    /**
     * 睡眠管理ウィジットの初期設定
     * <ol>
     *   <li>起床時間<br/>
     *    [画面表示] 00 時 00 分, [キー付きTAG値] 00:00 ※レイアウトファイル設定済み<br/>
     *    [リスナー] 時刻ピッカーダイアログ起動リスナー
     *   </li>
     *   <li>夜間トイレ回数<br/>
     *    [画面表示] デフォルト値: 1<br/>
     *    [リスナー] 数値ピッカーダイアログ起動リスナー<br/>
     *   </li>
     *   <li>睡眠スコア<br/>
     *    [画面表示] ブランク, [キー付きTAG値] null<br/>
     *    [値キー(mKeyDefSleepScore)] デフォルト値 (R.string.init_sleep_score)<br/>
     *    [リスナー] 数値ピッカーダイアログ起動リスナー<br/>
     *   </li>
     *   <li>睡眠時間<br/>
     *    [画面表示] 00 時間 00 分, [キー付きTAG値] 00:00 ※レイアウトファイル設定済み<br/>
     *    [リスナー] 時刻ピッカーダイアログ起動リスナー
     *   </li>
     *   <li>深い睡眠<br/>
     *    [画面表示] 00 時間 00 分, [キー付きTAG値] 00:00 ※レイアウトファイル設定済み<br/>
     *    [リスナー] 時刻ピッカーダイアログ起動リスナー
     *   </li>
     * </ol>
     * @param mainView フラグメントビュー
     */
    private void initWidetsOfSleepManagement(View mainView) {
        // 1.起床時間
        mInpWakeupTime = mainView.findViewById(R.id.inpWakeupTime);
        // 2.夜間トイレ回数
        mLblMidnightToiletVisits = mainView.findViewById(R.id.lblMidnightToiletVisits);
        mUnitMidnightToiletVisits = mainView.findViewById(R.id.unitMidnightToiletVisits);
        mInpMidnightToiletVisits = mainView.findViewById(R.id.inpMidnightToiletVisits);
        // (1)数値ピッカーダイアログ用の配列番号をタグに設定
        mInpMidnightToiletVisits.setTag(TAG_ID_MIDNIGHT_TOILET_COUNT);
        // 3.睡眠スコア ※表示はブランク
        mLblSleepScore = mainView.findViewById(R.id.lblSleepScore);
        mInpSleepScore = mainView.findViewById(R.id.inpSleepScore);
        // (1)数値ピッカーダイアログ用の配列番号をタグに設定
        mInpSleepScore.setTag(TAG_ID_SLEEP_SCORE);
        // (2)デフォルト値キー付きTAG値にデフォルト値を設定する
        mKeyDefSleepScore = R.id.defSleepScore;
        mInpSleepScore.setTag(mKeyDefSleepScore,
                Integer.valueOf(getString(R.string.init_sleep_score)));
        // 4.睡眠時間
        mInpSleepingTime = mainView.findViewById(R.id.inpSleepingTime);
        mInpDeepSleepingTime = mainView.findViewById(R.id.inpDeepSleepingTime);

        // 時刻ピッカーダイアログ起動リスナー設定
        mInpWakeupTime.setOnClickListener(mTimePickerViewClickListener);
        mInpSleepingTime.setOnClickListener(mTimePickerViewClickListener);
        mInpDeepSleepingTime.setOnClickListener(mTimePickerViewClickListener);
        // 数値ピッカータイアログ起動リスナー設定
        mInpMidnightToiletVisits.setOnClickListener(mNumberPickerClickListener);
        mInpSleepScore.setOnClickListener(mNumberPickerClickListener);
    }

    /**
     * 血圧測定ウィジットの初期設定
     *  <ol>
     *   <li>午前/午後ラジオボタン<br/>[デフォルト] 午前</li>
     *   <li>測定時刻<br/>
     *    [画面表示] 00 時 00 分, [キー付きTAG値] "00:00" ※レイアウトファイル設定済み<br/>
     *    [リスナー] 時刻ピッカーダイアログ起動リスナー
     *   </li>
     *   <li>最高血圧<br/>
     *    [画面表示] ブランク, [キー付きTAG] null<br/>
     *    [キー(mKeyDefBloodPressureMax)] デフォルト値 (R.string.init_bloodpressure_max)<br/>
     *    [リスナー] 数値ピッカーダイアログ起動リスナー
     *   </li>
     *   <li>最低血圧<br/>
     *    [画面表示] ブランク, [キー付きTAG] null<br/>
     *    [値キー(mKeyDefBloodPressureMin)] デフォルト値 (R.string.init_bloodpressure_min)<br/>
     *    [リスナー] 数値ピッカーダイアログ起動リスナー
     *   </li>
     *   <li>脈拍<br/>
     *    [画面表示] ブランク, [キー付きTAG] null<br/>
     *    [値キー(mKeyDefPulseRate)] デフォルト値 (R.string.init_pulse_rate)<br/>
     *    [リスナー] 数値ピッカーダイアログ起動リスナー
     *   </li>
     * </ol>
     * @param mainView フラグメントビュー
     */
    private void initWidgetsOfBloodPressure(View mainView) {
        // ラジオボタン: 午前 / 午後
        RadioGroup radioGroup = mainView.findViewById(R.id.radioGroupBloodPressure);
        radioGroup.setOnCheckedChangeListener(mRadioChangedListener);
        mRadioMorning = mainView.findViewById(R.id.radioMorning);
        mRadioEvening = mainView.findViewById(R.id.radioEvening);
        mSelectedRadioId = mRadioMorning.getId();
        // 測定時刻: [入力項目]
        mInpMeasurementTime = mainView.findViewById(R.id.inpMeasurementTime);
        // 1.最高血圧 [ラベル, 単位ラベル, 入力項目]
        mLblBloodPressureMax = mainView.findViewById(R.id.lblBloodPressureMax);
        mUnitBloodPressureMax = mainView.findViewById(R.id.unitBloodPressureMax);
        mInpBloodPressureMax = mainView.findViewById(R.id.inpBloodPressureMax);
        //  数値ピッカーダイアログ用の配列番号をタグに設定
        mInpBloodPressureMax.setTag(TAG_ID_BLOOD_PRESSURE_MAX);
        //  最高血圧のデフォルト値をデフォルトキーのTAGに設定
        mKeyDefBloodPressureMax = R.id.defBloodPressureMax;
        mInpBloodPressureMax.setTag(mKeyDefBloodPressureMax,
                Integer.valueOf(getString(R.string.init_bloodpressure_max)));
        // 2.最低血圧 [ラベル, 単位ラベル, 入力項目]
        mLblBloodPressureMin = mainView.findViewById(R.id.lblBloodPressureMin);
        mUnitBloodPressureMin = mainView.findViewById(R.id.unitBloodPressureMin);
        mInpBloodPressureMin = mainView.findViewById(R.id.inpBloodPressureMin);
        //  数値ピッカーダイアログ用の配列番号をタグに設定
        mInpBloodPressureMin.setTag(TAG_ID_BLOOD_PRESSURE_MIN);
        //  最低血圧のデフォルト値をデフォルトキーのTAGに設定
        mKeyDefBloodPressureMin = R.id.defBloodPressureMin;
        mInpBloodPressureMin.setTag(mKeyDefBloodPressureMin,
                Integer.valueOf(getString(R.string.init_bloodpressure_min)));

        // 3.脈拍 [ラベル, 単位ラベル, 入力項目]
        mLblPulseRate = mainView.findViewById(R.id.lblPulseRate);
        mUnitPulseRate = mainView.findViewById(R.id.unitPulseRate);
        mInpPulseRate = mainView.findViewById(R.id.inpPulseRate);
        //  数値ピッカーダイアログ用の配列番号をタグに設定
        mInpPulseRate.setTag(TAG_ID_PULSE_RATE);
        //  脈拍のデフォルト値をデフォルトキーのTAGに設定
        mKeyDefPulseRate = R.id.defPulseRate;
        mInpPulseRate.setTag(mKeyDefPulseRate,
                Integer.valueOf(getString(R.string.init_pulse_rate)));

        // 血圧測定値のタグに数値オブジェクトの初期値を設定
        resetBloodPressureWidgetsValue(getString(R.string.init_tag_time_value), mFmtShowTime);

        // 時刻ピッカーダイアログ起動リスナー設定
        mInpMeasurementTime.setOnClickListener(mTimePickerViewClickListener);
        // 数値ピッカーダイアログ起動リスナー設定
        mInpBloodPressureMax.setOnClickListener(mNumberPickerClickListener);
        mInpBloodPressureMin.setOnClickListener(mNumberPickerClickListener);
        mInpPulseRate.setOnClickListener(mNumberPickerClickListener);
    }

    /**
     * 夜間頻尿の要因ウィジットの初期設定
     * @param mainView フラグメントビュー
     */
    private void initWidgetsOfNocturiaFactors(View mainView) {
        // 飲料系チェックボックス
        mChkCoffee = mainView.findViewById(R.id.chkCoffee);
        mChkTea = mainView.findViewById(R.id.chkTea);
        mChkAlcohol = mainView.findViewById(R.id.chkAlcohol);
        mChkNutritionDrink = mainView.findViewById(R.id.chkNutritionDrink);
        mChkSportsDrink = mainView.findViewById(R.id.chkSportsDrink);
        mChkDiuretic = mainView.findViewById(R.id.chkDiuretic);
        // 習慣系チェックボックス
        mChkTakeMedicine = mainView.findViewById(R.id.chkTakeMedicine);
        mChkTakeBathing = mainView.findViewById(R.id.chkTakeBathing);
        // 健康状態メモ
        mEditConditionMemo = mainView.findViewById(R.id.editConditionMemo);
        // 生成時は編集不可に設定
        mEditConditionMemo.setEnabled(false);
        // 健康状態メモの編集可チェックボックス
        CheckBox chkEditable = mainView.findViewById(R.id.chkConditionMemoEditable);
        // 編集可否チェックボックスリスナー
        chkEditable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setEditTextSetting(mEditConditionMemo, isChecked);
        });
        // チェックボックスのリセット用配列
        mAllCheckBoxes = new CheckBox[] {
                mChkCoffee, mChkTea, mChkAlcohol, mChkNutritionDrink, mChkSportsDrink, mChkDiuretic,
                mChkTakeMedicine, mChkTakeBathing,
        };
    }

    /**
     * 更新チェック用モニター開始
     * <ol>
     *     <li>全てのチェックボックスのチェック変更リスナーを設定</li>
     *     <li>読込みデータの更新確認用マップのフラグリセット</li>
     * </ol>
     */
    private void startUpdateMonitor() {
        setAllCheckboxChangedListener();
        resetUpdateCheckMap();
    }

    /**
     * 更新チェック用モニター終了
     * <p>全てのチェックボックスのチェック変更リスナーを削除</p>
     */
    private void terminateUpdateMonitor() {
        removeAllCheckboxChangedListener();
    }

    /**
     * 全てのチェックボックスにチェック変更リスナーを設定
     * <p>[設定タイミング] データの取得時</p>
     */
    private void setAllCheckboxChangedListener() {
        for(CheckBox chkBox : mAllCheckBoxes) {
            chkBox.setOnCheckedChangeListener(mOnCheckedChangeListener);
        }
    }

    /**
     * 全てのチェックボックスから変更リスナーを削除削除
     * <p>[設定タイミング] 新規登録時</p>
     */
    private void removeAllCheckboxChangedListener() {
        for(CheckBox chkBox : mAllCheckBoxes) {
            chkBox.setOnCheckedChangeListener(null);
        }
    }

    /**
     * 体温測定ウィジットの初期設定
     * @param mainView フラグメントビュー
     */
    private void initWidgetsOfBodyTemperature(View mainView) {
        // 体温
        mInpBodyTemper = mainView.findViewById(R.id.inpBodyTemper);
        // 数値EditText入力ダイアログ起動リスナー
        mInpBodyTemper.setOnClickListener(mNumberInputClickListener);
        // 体温測定時刻
        mInpBodyTemperTime = mainView.findViewById(R.id.inpBodyTemperTime);
        mInpBodyTemperTime.setOnClickListener(mTimePickerViewClickListener);
        // BLEインポート
//        mBtnBleImport = mainView.findViewById(R.id.btnBleImport);
        //TODO 当面運用していないためリスナーを未定義とする
        // mBtnBleImport.setOnClickListener();
    }

    /**
     * カレントのPOSTリクエストオブジェクトと送信ボタン名を変更する
     * @param setRequest POSTリクエストオブジェクト(登録|更新)
     */
    private void changePostRequestWithButton(PostRequest setRequest) {
        mCurrentPostRequest = setRequest;
        mBtnSend.setText(setRequest.getName());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View mainView = inflater.inflate(
                R.layout.fragment_top_main, container, false);

        // 時刻フォーマット文字列取得
        mFmtShowTime = getString(R.string.format_show_time);
        mFmtShowRangeTime = getString(R.string.format_show_range_time);
        // 測定日付
        mInpMeasurementDate = mainView.findViewById(R.id.inpMeasurementDate);
        mInpMeasurementDate.setOnClickListener(mDatePickerViewClickListener);
        // 当日設定
        setTodayValue(mInpMeasurementDate);
        // 睡眠管理ウィジット初期化
        initWidetsOfSleepManagement(mainView);
        // 血圧管理ウィジット初期化
        initWidgetsOfBloodPressure(mainView);
        // 体温インポート
        initWidgetsOfBodyTemperature(mainView);
        // 夜間起床回数要因
        initWidgetsOfNocturiaFactors(mainView);
        // 歩数
        mInpWalkingCount = mainView.findViewById(R.id.inpWalkingCount);
        // 数値EditText入力ダイアログ起動リスナー
        mInpWalkingCount.setOnClickListener(mNumberInputClickListener);
        // 天候
        mInpWeatherCond = mainView.findViewById(R.id.inpWeatherCond);
        // EditText入力ダイアログ起動リスナー
        mInpWeatherCond.setOnClickListener(mTextInputClickListener);
        // 保存ボタン
        mBtnSave = mainView.findViewById(R.id.btnSave);
        mBtnSave.setOnClickListener(mBtnOnClickListener);
        // 送信ボタン("登録" | "更新")
        mBtnSend = mainView.findViewById(R.id.btnSend);
        mBtnSend.setOnClickListener(mBtnOnClickListener);
        // 送信ボタン文字列設定
        changePostRequestWithButton(PostRequest.REGISTER);
        // ステータ
        mTextStatus = mainView.findViewById(R.id.textStatus);
        mWarningStatus = mainView.findViewById(R.id.warningStatus);
        // 数値ピッカーダイアログ用配列初期化
        initArraysForNumberPicker();
        // 更新有無チェック用マップ初期化
        initUpdateCheckMap();
        // レスボンス時ウォーニングメッセージマップ初期化
        initResponseWarningMap();
        return mainView;
    }

    @Override
    public void onPause() {
        super.onPause();
        DEBUG_OUT.accept(TAG, "onPause()");

        // 更新チェック用リスナー削除
        if (mRegisterDataForUpdate != null) {
            terminateUpdateMonitor();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        DEBUG_OUT.accept(TAG, "onResume()");

        // 当日のJsonファルがあればリストアする
        restoreWidgetsFromJson();
        // 更新チェック用リスナー設定
        if (mRegisterDataForUpdate != null) {
            startUpdateMonitor();
        }
    }

    /**
     * 新規登録モードにリセットする
     * <ul>【呼び出し契機】
     *     <li>カレンダー選択で過去以外の日付に変わるか、過去日でも新規登録になる場合</li>
     *     <li>GETリクエストで処理日のデータが未登録の場合</li></ul>
     * <ol>【処理順】
     *     <li>更新用オブジェクトを破棄</li>
     *     <li>更新チェック用モニター終了(チェックボックスチェンジリスナー削除)</li>
     *     <li>入力ウィジットをリセット</li>
     *     <li>ステータスを初期化</li>
     *     <li>POSTリクエストオブジェクトを登録に戻す</li>
     * </ol>
     */
    private void resetToNewRegistrationMode() {
        mRegisterDataForUpdate = null;
        terminateUpdateMonitor();
        resetInputWidgetsValue();
        clearStatus();
        changePostRequestWithButton(PostRequest.REGISTER);
    }

    /**
     * 日付ピッカーダイアログを表示する
     * @param v 起動したウィジット
     */
    private void showDatePicker(View v) {
        DialogFragment newFragment = new DatePickerFragment(
                requireActivity(), mMeasurementDayCal, (view, year, month, dayOfMonth) -> {
            // 測定日付ウィジットを更新するためのタグ値を生成
            String tagValue = String.format(getString(R.string.format_tag_date),
                    year, month + 1/*カレンダー月 +1*/, dayOfMonth);
            DEBUG_OUT.accept(TAG, "showDatePicker:v.id=" + v.getId() + ",tag:" + tagValue);
            updateDateView((TextView) v, tagValue);
            // カレンダーオブジェクトを更新
            mMeasurementDayCal.set(year, month, dayOfMonth);
            // カレンダーで選択した日によって条件を満たさなければサーバーに問い合わせる
            LocalDate selectedLocal = AppTopUtil.localDateOfCalendar(mMeasurementDayCal);
            DEBUG_OUT.accept(TAG, "selected: " + selectedLocal + " ,now: " + mNowLocalDate);
            if (selectedLocal.isBefore(mNowLocalDate)) {
                // 過去日の場合はプリファレンスから登録済み日付を取得する (未登録ならnull)
                String regDate = getLatestRegisteredDateInPref();
                DEBUG_OUT.accept(TAG, String.format("Compare: %s =< %s", selectedLocal, regDate));
                // カレンダー選択日が最新の登録済み日付以下ならリクエストする
                // 過去日で登録済み日付を超える選択日は未登録日付なのでリクエストしない
                if (AppTopUtil.isLessRegisteredDate(selectedLocal, regDate)) {
                    String emailAddress = getUserEmailWithThisSettings();
                    if (!TextUtils.isEmpty(emailAddress)) {
                        // 過去日は保存不可
                        mBtnSave.setEnabled(false);
                        // メールアドレスと選択した日付でサーバーから登録済みデータを取得
                        sendGetCurrentDataRequest(emailAddress, tagValue);
                    } else {
                        // メールアドレスの設定が必要
                        showConfirmDialogWithEmailAddress();
                    }
                } else {
                    // 過去日でも新規登録なので新規登録モードにリセット
                    mBtnSave.setEnabled(true);
                    resetToNewRegistrationMode();
                }
            } else {
                // 過去以外の日付に変わったら新規登録モードにリセット
                resetToNewRegistrationMode();
                if(mNowLocalDate.isAfter(selectedLocal)) {
                    // 選択日が本日よりも未来日なら保存・登録ができない
                    showWarning(getString(R.string.warning_not_allow_future));
                    mBtnSave.setEnabled(false);
                    mBtnSend.setEnabled(false);
                } else { // 本日ならボタンを戻す
                    mBtnSave.setEnabled(true);
                    mBtnSend.setEnabled(true);
                }
            }
        });
        newFragment.show(requireActivity().getSupportFragmentManager(), "DatePickerFragment");
    }

    /**
     * 時刻ピッカーダイアログ用の時刻データを生成する
     * @param tagTime 時刻表示ウィジットのキー付きTAGに設定されている時刻文字列
     * @return 時刻データ
     */
    private TimePickerFragment.TimeHolder createTimeHolder(String tagTime) {
        String[] times = tagTime.split(":");
        int hour = Integer.parseInt(times[0]);
        int minute = Integer.parseInt(times[1]);
        return new TimePickerFragment.TimeHolder(hour, minute);
    }

    /**
     * 時刻ピッカーダイアログに必要な引数を保持するデータを生成する
     * @param v 対象となる時刻表示ウィジット
     * @param tagId TAGキーID
     * @return 引数データ
     */
    private TimePickerFragment.TimeHolder getTimeHoler(View v, int tagId) {
        String tagValue = (String) v.getTag(tagId);
        TimePickerFragment.TimeHolder holder;
        if (!tagValue.equals(getString(R.string.init_tag_time_value))) {
            holder = createTimeHolder(tagValue);
        } else {
            // 午前 / 午後
            String timeClassValue;
            if (mSelectedRadioId == mRadioMorning.getId()) {
                timeClassValue = getString(R.string.time_class_am);
            } else {
                timeClassValue = getString(R.string.time_class_pm);
            }
            holder = createTimeHolder(timeClassValue);
        }
        return holder;
    }

    /**
     * 時刻設定ダイアログを表示する
     *  (1)起床時刻: 時刻フォーマット
     *  (2)睡眠時間: 時間フォーマット
     *  (3)深い睡眠: 時間フォーマット
     * @param v 対象のビュー
     */
    private void showTimePicker(View v) {
        TimePickerFragment.TimeHolder holder = getTimeHoler(v, v.getId());
        DialogFragment newFragment = new TimePickerFragment(
                requireActivity(), holder, (view, hourOfDay, minute) -> {
            // 時刻フォーマット文字列
            String timeFormat;
            if (v.getId() == R.id.inpWakeupTime || v.getId() == R.id.inpBodyTemperTime) {
                // 起床時刻, 体温測定時刻
                timeFormat = mFmtShowTime;
            } else {
                // 睡眠時間, 深い睡眠
                timeFormat = mFmtShowRangeTime;
            }
            // Tag用フォーマットでタグに設定
            String tagValue = String.format(getString(R.string.format_tag_time), hourOfDay, minute);
            TextView tv = (TextView) v;
            updateTimeView(tv, tagValue, timeFormat);
            // 時刻入力ウィジットの変更通知
            mOnTimeViewChanged.onChanged(tv, tagValue);
        });
        newFragment.show(requireActivity().getSupportFragmentManager(), "TimePickerFragment");
    }

    /**
     * 血圧の測定時刻用設定ダイアログを表示する
     *  血圧測定時刻: 午前(AM) /午後(PM) 12時間 表示
     * @param v  血圧の測定時刻入力ウィジット
     */
    private void showBloodPressureTimePicker(View v) {
        TimePickerFragment.TimeHolder holder = getTimeHoler(v, mSelectedRadioId);
        DialogFragment newFragment = new TimePickerFragment(
                requireActivity(), holder, (view, hourOfDay, minute) -> {
            // Tag値用のフォーマット
            String tagValue = String.format(getString(R.string.format_tag_time), hourOfDay, minute);
            // AM/PM毎の測定時刻をTagに設定
            TextView tv = (TextView) v;
            updateTimeViewByTag(tv, mSelectedRadioId, tagValue, mFmtShowTime);
            // 時刻変更通知
            mOnBloodPressTimeChanged.onChanged(tv, mSelectedRadioId, tagValue);
        }, false /* AM/PM 12時 表示 */);
        newFragment.show(requireActivity().getSupportFragmentManager(),
                "showBloodPressureTimePicker");
    }

    /**
     * 数値ピッカーダイアログを表示する
     * @param dialogTitle ダイアログタイトル
     * @param inpView 入力対象のウィジット
     * @param lbl 入力対象のタイトルラベル
     * @param unit 入力対象の単位ラベル
     * @param initValue 入力対象の初期値
     * @param minValue 入力対象の最小値
     * @param maxValue 入力対象の最大値
     */
    private void showNumberPickerDialog(
            String dialogTitle,
            TextView inpView, String lbl, String unit,
            int initValue, int minValue, int maxValue) {
        NumberPickerDialog.DialogItem item = new NumberPickerDialog.DialogItem(
                dialogTitle, lbl, unit, initValue, minValue, maxValue
        );
        NumberPickerDialog.ValueListener listener = new NumberPickerDialog.ValueListener() {
            @Override
            public void onDecideValue(int number) {
                String sNumber = String.valueOf(number);
                inpView.setText(sNumber);
                // 血圧入力項目は午前/午後それぞれのタグに値を保持する
                switch (inpView.getId()) {
                    case R.id.inpBloodPressureMax:
                    case R.id.inpBloodPressureMin:
                    case R.id.inpPulseRate:
                        // タグには数値オブジェクトを入れる
                        inpView.setTag(mSelectedRadioId, number);
                        // 血圧測定数値ピッカー入力ウィジットの変更通知
                        mOnBloodPressNumberChanged.onChanged(inpView, mSelectedRadioId, sNumber);
                        break;
                    default:
                        // 上記以外の数値ピッカー入力ウィジットの変更通知
                        mOnNumberViewChanged.onChanged(inpView, sNumber);
                }
            }
            @Override
            public void onCancel() {/* No ope*/}
        };
        NumberPickerDialog picker = new NumberPickerDialog(requireActivity(), item, listener);
        picker.createNumberPickerDialog().show();
    }

    /**
     * JSONファイルを保存する
     * <p>それぞれのプリファレンスを更新するとともに処理日をステータスに表示する</p>
     * <ul>【呼出し契機】
     *     <li>一時保存の場合は常に呼び出される</li>
     *     <li>登録済み保存の場合はより最新の日付であった場合のみ呼び出される</li>
     * </ul>
     * @param jsonFileType JSONファイル保存型 (一時保存 | 登録済み)
     */
    private void saveJsonToFile(JsonFileSaveTiming jsonFileType) {
        // 測定日付を保存日キーとする
        String dateValue = toStringOfTextViewBySelfTag(mInpMeasurementDate);
        String prefKey;
        String fileName;
        String message;
        if (jsonFileType.equals(JsonFileSaveTiming.REGISTERED)) {
            // 登録済みの場合: 最も最新の日付ならJSONファイルとプリファレンスを上書きする
            prefKey = getString(R.string.sharedpref_registered_key);
            fileName = getString(R.string.latest_registered_json_file);
            message = getString(R.string.message_register_success);
        } else {
            // 一時保存
            prefKey = getString(R.string.sharedpref_saved_key);
            fileName = getString(R.string.last_saved_json_file);
            message = getString(R.string.message_save_success);
        }
        mHandler.post(()-> {
            String json = generateJsonTextForRegist();
            DEBUG_OUT.accept(TAG, "saved(" + fileName + "):\n"  + json);
            try {
                // Jsonデータをファイル保存
                FileManager.saveText(Objects.requireNonNull(getContext()), fileName, json);
                // 日付をプリファレンスに保存する
                SharedPreferences sharedPref = getThisSharedPref();
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(prefKey, dateValue);
                editor.commit();
                // ステータスに表示
                showStatus(message);
            } catch (IOException e) {
                String errMsg = String.format(getString(R.string.warning_save_with_2reason),
                        jsonFileType.getName(), e.getLocalizedMessage());
                // ウォーニングステータスに表示
                showWarning(errMsg);
            }
        });
    }

    /**
     * 入力ウィジットからJSON文字列を生成しファイルに保存する
     * <p>保存前に入力必須項目である起床時刻がデフォルト時刻以外に設定されているかチェックする</p>
     */
    private void saveJsonTextFromInputWidgets() {
        // 保存時でも起床時刻の設定は必要
        List<String> warnings = checkRequiredInputs(true);
        if (!warnings.isEmpty()) {
            // メッセージダイアログ表示
            String warning = String.join("\n", warnings);
            DialogFragment fragment = MessageOkDialogFragment.newInstance(
                    getString(R.string.warning_required_dialog_title), warning);
            fragment.show(requireActivity().getSupportFragmentManager(), "RequiredDialogFragment");
            return;
        }

        // 一時保存JSONファイル保存
        saveJsonToFile(JsonFileSaveTiming.SAVE);
    }

    /**
     * 全ての入力ウィジットから新規登録用のJSON文字列を生成する
     * @return JSON文字列
     */
    private String generateJsonTextForRegist() {
        Gson gson;
        if (BuildConfig.DEBUG) {
            // 整形
            gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        } else {
            // 整形なし、nullの場合 nullを出力
            gson = new GsonBuilder().serializeNulls().create();
        }
        RegisterData registerData = generateRegisterData();
        String result = gson.toJson(registerData);
        DEBUG_OUT.accept(TAG, "GenJsonForRegist: \n" + result);
        return result;
    }

    /**
     * 変更のあった入力ウィジットのグループのデータを含む更新リクエスト用JSON文字列を生成する
     * @return 更新リクエスト用JSON文字列, 健康管理データと天候データのいずれも変更なしの場合はnull
     */
    private String generateJsonTextForUpdate() {
        // メールアドレス(必須)
        String emailAddress = getUserEmailWithThisSettings();
        // 測定日付(必須)
        String iso8601DateValue = toStringOfTextViewBySelfTag(mInpMeasurementDate);
        // JSON整形なし
        Gson gson = new GsonBuilder().serializeNulls().create();

        // 変更グループリスト
        List<String> updateList = new ArrayList<>();
        // 健康管理
        HealthcareData beforeData = mRegisterDataForUpdate.getHealthcareData();
        // 睡眠管理
        SleepManagement beforeSleepMan = beforeData.getSleepManagement();
        if (hasTrueInUpdateCheckMap(UPD_KEY_SLEEP_MAN)
                || hasUpdateOfSleepManagement(beforeSleepMan)) {
            SleepManagement sleepManagement = newSleepManagement();
            String jsonContent = gson.toJson(sleepManagement);
            String JsonWithProperty = JsonTemplate.getJsonWithSleepManagement(jsonContent);
            updateList.add(JsonWithProperty);
        }
        // 血圧測定
        BloodPressure beforeBloodPress = beforeData.getBloodPressure();
        if (hasTrueInUpdateCheckMap(UPD_KEY_BLOOD_PRESS)
                || hasUpdateOfBloodPressure(beforeBloodPress)) {
            BloodPressure bloodPressure = newBloodPressure();
            String jsonContent = gson.toJson(bloodPressure);
            String JsonWithProperty = JsonTemplate.getJsonWithBloodPressure(jsonContent);
            updateList.add(JsonWithProperty);
        }
        // 体温
        if (hasTrueInUpdateCheckMap(UPD_KEY_BODY_TEMPER)) {
            BodyTemperature bodyTemperature = newBodyTemperature();
            String jsonContent = gson.toJson(bodyTemperature);
            String JsonWithProperty = JsonTemplate.getJsonWithBodyTemperature(jsonContent);
            updateList.add(JsonWithProperty);
        }
        // 夜間頻尿要因
        NocturiaFactors beforeFactors = beforeData.getNocturiaFactors();
        if (hasUpdateConditionMemo(beforeFactors.getConditionMemo()) /* 健康状態メモのみ単独判定 */
                || hasTrueInUpdateCheckMap(UPD_KEY_NOCT_FACT)) {
            NocturiaFactors nocturiaFactors = newNocturiaFactors();
            String jsonContent = gson.toJson(nocturiaFactors);
            String JsonWithProperty = JsonTemplate.getJsonWithNocturiaFactors(jsonContent);
            updateList.add(JsonWithProperty);
        }
        // 歩数
        WalkingCount beforeWalkingCount = beforeData.getWalkingCount();
        if (hasUpdateWalkingCount(beforeWalkingCount)) {
            WalkingCount walking = new WalkingCount(toIntegerOfNumberView(mInpWalkingCount));
            String jsonContent = gson.toJson(walking);
            String JsonWithProperty = JsonTemplate.getJsonWithWalkingCount(jsonContent);
            updateList.add(JsonWithProperty);
        }
        // 健康管理データ部分JSON
        String healthcareDataJson;
        if (!updateList.isEmpty()) {
            healthcareDataJson= String.join(",", updateList);
        } else {
            healthcareDataJson = null;
        }

        // 天候データ部分JSON
        String weatherDataJson;
        WeatherCondition wc = mRegisterDataForUpdate.getWeatherData().getWeatherCondition();
        if (hasUpdateWeather(wc)) {
            WeatherCondition weather = new WeatherCondition(toStringOfTextView(mInpWeatherCond));
            String jsonContent = gson.toJson(weather);
            weatherDataJson = JsonTemplate.getJsonWithWeatherCondition(jsonContent);
        } else {
            // 変更がない場合は空文字
            weatherDataJson = null;
        }
        // 健康管理データと天候データのいずれも変更がなければ更新なしとしてnullを返却
        if (healthcareDataJson == null && weatherDataJson == null) {
            DEBUG_OUT.accept(TAG, "Not Updated!");
            return null;
        }

        // 更新用リクエスト用のJSON文字列をテンプレートから生成
        String result = JsonTemplate.createUpdateJson(emailAddress, iso8601DateValue,
                healthcareDataJson, weatherDataJson);
        DEBUG_OUT.accept(TAG, result);
        return result;
    }

    /**
     * 必須入力項目に入力漏れがないかチェックする<br/>
     *  <ol>【必須項目】
     *    <li>起床時刻<br/>
     *      必ず設定する ※"00 時 00 分"以外
     *    </li>
     *    <li>睡眠時間, 歩数, 天候<br/>
     *      チェックするタイミングが一時保存なら未設定も可
     *    </li>
     *  </ol>
     *  [タイミング]
     *    保存ボタンと登録ボタン押下時
     * @param  timingSave チェックするタイミングが保存ボタン押下時ならtrueを指定
     * @return 入力漏れがある項目のメッセージのリスト,ない場合は空のリスト
     */
    private List<String > checkRequiredInputs(boolean timingSave) {
        List<String> result = new ArrayList<>();

        // 1.起床時刻: 未設定かどうかチェック (キー付きTag値が初期値のまま)
        String wakeupTime = (String) mInpWakeupTime.getTag(mInpWakeupTime.getId());
        if (getString(R.string.init_tag_time_value).equals(wakeupTime)) {
            result.add(String.format(getString(R.string.warning_required_time),
                    getString(R.string.lbl_wakeup_time)));
        }
        if (timingSave) {
            return result;
        }

        // 2.睡眠時間:  Tag値チェック
        String sleepTime = (String) mInpSleepingTime.getTag(mInpSleepingTime.getId());
        if (getString(R.string.init_tag_time_value).equals(sleepTime)) {
            result.add(String.format(getString(R.string.warning_required_time),
                    getString(R.string.lbl_sleeping_time)));
        }
        // 3.歩数
        if (TextUtils.isEmpty(mInpWalkingCount.getText().toString())) {
            result.add(String.format(getString(R.string.warning_required_edit),
                    getString(R.string.lbl_walking_count)));
        }
        // 4.天候
        if (TextUtils.isEmpty(mInpWeatherCond.getText().toString())) {
            result.add(String.format(getString(R.string.warning_required_edit),
                    getString(R.string.lbl_weather)));
        }
        return result;
    }

    /**
     * 送信確認ダイアログ表示 (登録|更新)
     */
    private void showConfimOkCancelDialog(ConfirmOkCancelListener listener) {
        String message = String.format(
                getString(R.string.confirm_message_with_send), mCurrentPostRequest.getName());
        ConfirmDialogFragment confirm = ConfirmDialogFragment.newInstance(
                getString(R.string.confirm_dialog_title), message, listener);
        confirm.show(Objects.requireNonNull(requireActivity()).getSupportFragmentManager(),
                "ConfirmDialogFragment");
    }

    /**
     * メッセージダイアログ表示 ※OKボタンのみ
     * @param title タイトル(任意)
     * @param message メッセージ
     * @param tagName FragmentTag
     */
    private void showMessageDialog(String title, String message, String tagName) {
        DialogFragment fragment = MessageOkDialogFragment.newInstance(title, message);
        fragment.show(requireActivity().getSupportFragmentManager(), tagName);
    }

    /**
     * ネットワーク利用不可ダイアログ
     */
    private void showDialogNetworkUnavailable() {
        String warning = getString(R.string.warning_network_not_available);
        DialogFragment fragment = MessageOkDialogFragment.newInstance(null, warning);
        fragment.show(requireActivity().getSupportFragmentManager(), "MessageOkDialogFragment");
    }

    /**
     * メールアドレス必須ダイアログ
     * <ol>
     * <li>OKボタン押下: メールアドレス設定アクティビィティに遷移する</li>
     * <li>取消しボタン押下: 何もしない</li>
     * </ol>
     */
    private void showConfirmDialogWithEmailAddress() {
        ConfirmOkCancelListener listener = new ConfirmOkCancelListener() {
            @Override
            public void onOk() {
                Intent settingsIntent = new Intent(requireActivity(), SettingsActivity.class);
                startActivity(settingsIntent);
            }

            @Override
            public void onCancel() {
                // No operation.
            }
        };
        ConfirmDialogFragment fragment = ConfirmDialogFragment.newInstance(
                getString(R.string.warning_required_dialog_title),
                getString(R.string.warning_need_email_address),
                listener);
        fragment.show(requireActivity().getSupportFragmentManager(), "ConfirmDialogFragment");
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
                message = AppTopUtil.getWarningFromBadRequestStatus(status, mResponseWarningMap);
                break;
            case 404: // NotFound: データ未登録
                message = getString(R.string.warning_data_not_found);
                break;
            case 409: // Conflict: 登録済み
                message = getString(R.string.warning_data_already_resistered);
                break;
            default: // 50x系エラー
                message = status.getMessage();
        }
        return message;
    }

    /**
     * 入力ウィジットからJSON文字列を生成し、サーバーに送信する
     * POSTリクエスト用JSON文字列生成
     * <ul>
     *     <li>登録: 全グループ項目のJSON文字列を生成</li>
     *     <li>更新: 各グループのうち変更のあった部分を含むJSON文字列を生成</li>
     * </ul>
     */
    private void sendRegisterData() {
        // ネットワークデバイスが無効なら送信しない
        RequestDevice device =  NetworkUtil.getActiveNetworkDevice(
                Objects.requireNonNull(getContext()));
        if (device == RequestDevice.NONE) {
            showDialogNetworkUnavailable();
            return;
        }

        // メールアドレスチェック
        if (TextUtils.isEmpty(getUserEmailWithThisSettings())) {
            showConfirmDialogWithEmailAddress();
            return;
        }

        // 必須入力項目チェック
        List<String> warnings = checkRequiredInputs(false);
        if (!warnings.isEmpty()) {
            // メッセージダイアログ表示
            String warning = String.join("\n", warnings);
            showMessageDialog(getString(R.string.warning_required_dialog_title), warning,
                    "RequiredDialogFragment");
            return;
        }

        // 確認ダイアログのボタン(OK|CANCEL)押下イベント受信リスナー
        ConfirmOkCancelListener listener = new ConfirmOkCancelListener() {
            @Override
            public void onOk() {
                // 送信ボタン不可
                mBtnSend.setEnabled(false);
                // アクションバーにネットワークデバイス状況を設定
                showActionBarGetting(device);
                // アプリケーション取得
                HealthcareApplication app = (HealthcareApplication) requireActivity()
                        .getApplication();
                // アプリケーションに保持しているリクエスト情報からネットワーク種別に応じたリクエストURLを取得
                String requestUrl = app.getmRequestUrls().get(device.toString());
                Map<String, String> headers = app.getRequestHeaders();
                // 入力ウィジットからJsonデータ取得
                String jsonText;
                if (PostRequest.REGISTER.getNum() == mCurrentPostRequest.getNum()) {
                    jsonText = generateJsonTextForRegist(); // 全ての入力ウィジットからJSON文字列
                } else {
                    jsonText = generateJsonTextForUpdate(); // 更新された部分のみのJSON文字列
                }
                // POST 送信: 登録(0), 更新(1)
                int urlNum = mCurrentPostRequest.getNum();
                HealthcareRepository<RegisterResult> repository = new ResisterDataRepository();
                String requestUrlWithPath = requestUrl + repository.getRequestPath(urlNum);
                DEBUG_OUT.accept(TAG, "requestUrlWithPath: " + requestUrlWithPath);
                repository.makeRegisterRequest(urlNum, requestUrl, jsonText, headers,
                        app.mEexecutor, app.mHandler, (result) -> {
                            // ボタン状態を戻す
                            mBtnSend.setEnabled(true);
                            // リクエストURLをAppBarに表示
                            showActionBarResult(requestUrlWithPath);
                            if (result instanceof Result.Success) {
                                String recentJson;
                                // 登録処理の場合
                                if (mCurrentPostRequest.equals(PostRequest.REGISTER)) {
                                    // 更新前のプリファレンスから最新登録日付を取得する
                                    String before = getLatestRegisteredDateInPref();
                                    // 測定日付から登録日付を取得
                                    String after = toStringOfTextViewBySelfTag(mInpMeasurementDate);
                                    // 登録日付がプリファレンスの最新登録日付より最新の場合のみ上書き保存
                                    if (AppTopUtil.morelatestInPrefDate(after, before)) {
                                        // 最新登録日の復元用に登録済みJSONをファイル保存
                                        saveJsonToFile(JsonFileSaveTiming.REGISTERED);
                                    }
                                    // 送信成功なら一時保存JSONファイルを削除する
                                    deleteSavedFile();
                                    // 登録: リクエストのJSON文字列をそのまま利用
                                    recentJson = jsonText;
                                } else {
                                    // 更新: 全入力ウィジットからJSON文字列を生成する
                                    recentJson = generateJsonTextForRegist();
                                }
                                // 古いオブジェクトを破棄してから最新の更新用オブジェクトを生成する
                                if (mRegisterDataForUpdate != null) {
                                    mRegisterDataForUpdate = null;
                                }
                                Gson gson = new Gson();
                                mRegisterDataForUpdate =
                                        gson.fromJson(recentJson, RegisterData.class);
                                // ステータス更新
                                String status = String.format(
                                        getString(R.string.msg_post_ok_with_type),
                                        mCurrentPostRequest.getName());
                                showStatus(status);
                                // 保存ボタン不可
                                mBtnSave.setEnabled(false);
                                // ボタンを更新に変更
                                if (mCurrentPostRequest.equals(PostRequest.REGISTER)) {
                                    // 送信ボタンを更新に変更してそのまま変更可能にする
                                    changePostRequestWithButton(PostRequest.UPDATE);
                                }
                            } else if (result instanceof Result.Warning) {
                                // ウォーニングメッセージをダイアログに表示
                                ResponseStatus status =
                                        ((Result.Warning<?>) result).getResponseStatus();
                                DEBUG_OUT.accept(TAG, "WarningStatus: " + status);
                                // 引数 (1)POSTリクエスト種別: [登録 | 更新] (2)エラー内容
                                String warning = String.format(
                                        getString(R.string.warning_register_with_2_reason),
                                        mCurrentPostRequest.getName(), status.getMessage());
                                showMessageDialog(getString(R.string.error_response_dialog_title),
                                        warning,"WarningDialogFragment");
                            } else {
                                // 例外メッセージをダイアログに表示
                                Exception exception = ((Result.Error<?>) result).getException();
                                String message = String.format(
                                        getString(R.string.exception_with_reason), exception.getLocalizedMessage());
                                showMessageDialog(getString(R.string.error_response_dialog_title),message,
                                        "ExceptionDialogFragment");
                            }
                        });
            }
            @Override
            public void onCancel() {
                // キャンセルボタン押下ならステータスに表示
                String cancelMessage = String.format(getString(
                        R.string.msg_request_canceled_with_type), mCurrentPostRequest.getName());
                showStatus(cancelMessage);
            }
        };
        // 送信確認ダイアログ表示
        showConfimOkCancelDialog(listener);
    }

    /**
     * GETリクエストで該当するデータを取得する
     * @param emailAddress メールアドレス
     * @param pastDay 過去の測定日付
     */
    private void sendGetCurrentDataRequest(String emailAddress, String pastDay) {
        RequestDevice device =  NetworkUtil.getActiveNetworkDevice(getContext());
        if (device == RequestDevice.NONE) {
            showDialogNetworkUnavailable();
            return;
        }

        showActionBarGetting(device);
        HealthcareApplication app = (HealthcareApplication) requireActivity().getApplication();
        String requestUrl = app.getmRequestUrls().get(device.toString());
        Map<String, String> headers = app.getRequestHeaders();
        // GETリクエスト送信: 登録済みデータの取得
        HealthcareRepository<GetCurrentDataResult> repository = new GetCurrentDataRepository();
        String requestUrlWithPath = requestUrl + repository.getRequestPath(0);
        // リクエストパラメータ: 主キー項目(メールアドレス, 測定日付)
        String requestParams = AppTopUtil.getRequestParams(emailAddress, pastDay);
        repository.makeGetRequest(0, requestUrl, requestParams, headers,
                app.mEexecutor, app.mHandler, (result) -> {
                    // リクエストURLをAppBarに表示
                    showActionBarResult(requestUrlWithPath);

                    // 送信ボタンを戻す
                    mBtnSend.setEnabled(true);
                    if (result instanceof Result.Success) {
                        GetCurrentDataResult dataResult =
                                ((Result.Success<GetCurrentDataResult>) result).get();
                        RegisterData data = dataResult.getData();
                        // 古いオブジェクトを破棄する
                        if (mRegisterDataForUpdate != null) {
                            mRegisterDataForUpdate = null;
                        }
                        // https://www.baeldung.com/java-deep-copy
                        //  6.3. JSON Serialization With Jackson
                        Gson gson = new Gson();
                        // 各入力フィールドに変更があったかどうかを確認するためのオブジェクト
                        mRegisterDataForUpdate =
                                gson.fromJson(gson.toJson(data), RegisterData.class);
                        // ウィジット更新
                        updateInputWidgetsFromRegisterData(data);
                        // ステータス更新
                        showStatus(getString(R.string.message_get_current_ok));
                        // 更新用モニター開始
                        startUpdateMonitor();
                        // 送信ボタンのラベルを"更新"に変更
                        changePostRequestWithButton(PostRequest.UPDATE);
                    } else if (result instanceof Result.Warning) {
                        ResponseStatus status =
                           ((Result.Warning<?>) result).getResponseStatus();
                        DEBUG_OUT.accept(TAG, "WarningStatus: " + status);
                        if (status.getCode() == 404) {
                            // 未登録(404)なら新規登録モードにリセット
                            mBtnSave.setEnabled(true);
                            resetToNewRegistrationMode();
                        }
                        showWarning(getResponseWarning(status));
                    } else {
                        // 例外メッセージをダイアログに表示
                        Exception exception = ((Result.Error<?>) result).getException();
                        Log.w(TAG, "GET error:" + exception.toString());
                        String errorMessage = String.format(
                                getString(R.string.exception_with_reason),
                                exception.getLocalizedMessage());
                        showMessageDialog(getString(R.string.error_response_dialog_title),
                                errorMessage,"ExceptionFragment");
                    }
                });
    }

    /**
     * このフラグメントが属するアクティビィティのプリファレンスを取得する
     * @return プリファレンス
     */
    private SharedPreferences getThisSharedPref() {
        return Objects.requireNonNull(getContext()).getSharedPreferences(
                getString(R.string.sharedpref_app_top_fragment), Context.MODE_PRIVATE);
    }

    /**
     * 最終保存日をプリファレンスから取得する
     * @return 最終保存日
     */
    private String getLastSavedDateInPref() {
        SharedPreferences sharedPref = getThisSharedPref();
        DEBUG_OUT.accept(TAG, sharedPref.getAll().toString());
        return sharedPref.getString(getString(R.string.sharedpref_saved_key), null);
    }

    /**
     * 最新登録日をプリファレンスから取得する
     * @return 最新登録日
     */
    private String getLatestRegisteredDateInPref() {
        SharedPreferences sharedPref = getThisSharedPref();
        DEBUG_OUT.accept(TAG, sharedPref.getAll().toString());
        return sharedPref.getString(getString(R.string.sharedpref_registered_key), null);
    }

    /**
     * JSONファイルから入力ウィジットを復元する
     * @param jsonFileType JSONファイル保存契機("登録"|"一時保存)
     * @param jsonFileName JSONファイル名
     */
    private void loadJsonFromFile(JsonFileSaveTiming jsonFileType, String jsonFileName) {
        mHandler.post(() -> {
            try {
                String json = FileManager.readText(Objects.requireNonNull(getContext()),
                        jsonFileName);
                DEBUG_OUT.accept(TAG, "restore.json: " + json);
                Gson gson = new Gson();
                RegisterData data = gson.fromJson(json, RegisterData.class);
                // 入力ウィジット復元
                updateInputWidgetsFromRegisterData(data);
                if (jsonFileType == JsonFileSaveTiming.REGISTERED) {
                    // 登録済みJSONなら更新用オブジェクトに設定する
                    mRegisterDataForUpdate =
                            gson.fromJson(gson.toJson(data), RegisterData.class);
                    // 更新用モニター開始
                    startUpdateMonitor();
                    // 送信ボタンのラベルを"更新"に変更
                    changePostRequestWithButton(PostRequest.UPDATE);
                }
            } catch (IOException e) {
                Log.w(TAG, e.getLocalizedMessage());
            }
        });
    }

    /**
     * JSONファイルからデータオブジェクトを生成し入力ウィジットを値を復元する
     * <p>【タイミング】onResume()</p>
     * <ol>
     *     <li>一時保存JSONファイルがある場合は一時保存JSONファイルから復元</li>
     *     <li>上記以外で登録済みJSONファイルがある場合は当該JSONファイルから復元</li>
     *     <li>いずれのJSONファイルもない場合は何もしない</li>
     * </ol>
     */
    private void restoreWidgetsFromJson() {
        // 最終保存日を取得
        String lastDate = getLastSavedDateInPref();
        DEBUG_OUT.accept(TAG, "restore.lastSavedDate: " + lastDate);
        if (!TextUtils.isEmpty(lastDate)) {
            // JSONファイルから復元
            loadJsonFromFile(JsonFileSaveTiming.SAVE, getString(R.string.last_saved_json_file));
            // ステータスに処理日付を表示
            showStatusWithPreocessDate(lastDate, getString(R.string.status_saved_datefmt));
            return;
        }

        // 最新登録日を取得
        String latestDate = getLatestRegisteredDateInPref();
        if (!TextUtils.isEmpty(latestDate)) {
            DEBUG_OUT.accept(TAG, "restore.latestRegisteredDate: " + latestDate);
            loadJsonFromFile(JsonFileSaveTiming.REGISTERED,
                    getString(R.string.latest_registered_json_file));
            showStatusWithPreocessDate(latestDate,
                    getString(R.string.status_last_registered_datefmt));
        }
    }

    /**
     * 全ての入力ウィジットから登録用データオブジェクトを生成する
     * <p>メールアドレスと測定日付は登録時の主キーとなる</p>
     * @return 登録用データオブジェクト
     */
    private RegisterData generateRegisterData(){
        // メールアドレス(必須) ※健康管理アプリ設定のメールアドレス
        String emailAddress = getUserEmailWithThisSettings();
        // 測定日付(必須)
        String iso8601DateValue = toStringOfTextViewBySelfTag(mInpMeasurementDate);
        // 睡眠管理
        SleepManagement sleepManagement = newSleepManagement();
        // 血圧
        BloodPressure bloodPressure = newBloodPressure();
        // 体温
        BodyTemperature bodyTemperature = newBodyTemperature();
        // 夜間トイレ回数の要因
        NocturiaFactors nocturiaFactors = newNocturiaFactors();
        // 歩数データ
        WalkingCount walking = new WalkingCount(toIntegerOfNumberView(mInpWalkingCount));
        // 健康管理データコンテナ
        HealthcareData healthcareData = new HealthcareData(
                sleepManagement,
                bloodPressure,
                bodyTemperature,
                nocturiaFactors,
                walking
        );
        // 天候状態
        WeatherCondition weather = new WeatherCondition(toStringOfTextView(mInpWeatherCond));
        // 天候データコンテナ
        WeatherData weatherData = new WeatherData(weather);
        // 登録用データオブジェクト生成
        return new RegisterData(emailAddress, iso8601DateValue, healthcareData, weatherData);
    }

    /**
     * 睡眠管理入力ウィジットから睡眠管理オブジェクトを生成する
     * <ol>
     *  <li>起床時間 (*)必須<br/>
     *    キー付きTAG値から取得
     *  </li>
     *  <li>睡眠スコア: スマートバンドの実測値<br/>
     *    表示がブランクならnull(※1)、それ以外は数値を設定
     *  </li>
     *  <li>睡眠時間 (*)必須: スマートバンドの実測値(または直接設定値)<br/>
     *    キー付きTAG値
     *  </li>
     *  <li>深い睡眠: スマートバンドの実測値<br/>
     *    初期値と同じならはnull(※)、それ以外はキー付きTAG値
     *  </li>
     * </ol>
     *  (※)スマートバンドの電池切れなど測定不能のケースを考慮
     * @return 睡眠管理オブジェクト
     */
    private SleepManagement newSleepManagement() {
        SleepManagement result = new SleepManagement(
           toStringOfTimeView(mInpWakeupTime) /* 起床時刻 */,
           toIntegerOfNumberView(mInpSleepScore) /* 睡眠スコア */,
           toStringOfTimeView(mInpSleepingTime) /*睡眠時間 ※変更必須*/,
           toStringOfTimeView(mInpDeepSleepingTime) /* 深い睡眠 */
        );
        DEBUG_OUT.accept(TAG, "newSleepManagement: " + result);
        return result;
    }

    /**
     * 血圧測定入力ウィジットから血圧測定オブジェクトを生成する
     *  <ol>
     *    <li>午前の血圧測定値<br/>
     *   午前ラジオボタンIDをキーとしてそれぞれの入力測定項目のテキストから取得する
     *   </li>
     *   <li>午後の血圧測定値<br/>
     *   午後ラジオボタンIDをキーとしてそれぞれの入力測定項目のテキストから取得する
     *   </li>
     *  </ol>
     *  <p>測定忘れを考慮し以下のルールでオブジェクトに変換する</p>
     *  <ul>
     *    <li>(A) 測定時刻(キー付きTAG値)が初期値以外<br/>
     *    最高血圧(キー付きTAG値), 最低血圧(キー付きTAG値), 脈拍(キー付きTAG値)<br/>
     *    ※但し値が未入力(表示はブランク)ならキー付きTAG値はnull
     *    </li>
     *    <li>(B) 測定時刻(キー付きTAG値)が初期値に等しい場合<br/>
     *    全ての測定値をnullとする
     *    </li>
     *  </ul>
     * @return 血圧測定オブジェクト
     */
    private BloodPressure newBloodPressure() {
        // 血圧データは午前・午後ラジオボタンIDをキーにして測定値をTAG値から取得する
        int morningId = mRadioMorning.getId();
        int eveningId = mRadioEvening.getId();
        // 午前.測定時刻: キー付きTAG値
        String morningMeasurementTime = toStringOfTimeViewByTag(mInpMeasurementTime, morningId);
        Integer morningBloodPressureMax;
        Integer morningBloodPressureMin;
        Integer morningPulseRate;
        if (!TextUtils.isEmpty(morningMeasurementTime)) {
            // 午前.最高血圧: キー付きTAG値
            morningBloodPressureMax = toIntegerOfNumberViewByTag(mInpBloodPressureMax, morningId);
            // 午前.最低血圧: キー付きTAG値
            morningBloodPressureMin = toIntegerOfNumberViewByTag(mInpBloodPressureMin, morningId);
            // 午前.脈拍: キー付きTAG値
            morningPulseRate = toIntegerOfNumberViewByTag(mInpPulseRate, morningId);
        } else {
            morningBloodPressureMax = null;
            morningBloodPressureMin = null;
            morningPulseRate = null;
        }
        // 午後.測定時刻: キー付きTAG値
        String eveningMeasurementTime = toStringOfTimeViewByTag(mInpMeasurementTime, eveningId);
        Integer eveningBloodPressureMax;
        Integer eveningBloodPressureMin;
        Integer eveningPulseRate;
        if (!TextUtils.isEmpty(eveningMeasurementTime)) {
            // 午後.最高血圧: キー付きTAG値
            eveningBloodPressureMax = toIntegerOfNumberViewByTag(mInpBloodPressureMax, eveningId);
            // 午後.最低血圧: キー付きTAG値
            eveningBloodPressureMin = toIntegerOfNumberViewByTag(mInpBloodPressureMin, eveningId);
            // 午後.脈拍: キー付きTAG値
            eveningPulseRate = toIntegerOfNumberViewByTag(mInpPulseRate, eveningId);
        } else {
            eveningBloodPressureMax = null;
            eveningBloodPressureMin = null;
            eveningPulseRate = null;
        }
        BloodPressure result = new BloodPressure(
           morningMeasurementTime,
           morningBloodPressureMax, morningBloodPressureMin, morningPulseRate,
           eveningMeasurementTime,
           eveningBloodPressureMax, eveningBloodPressureMin, eveningPulseRate
        );
        DEBUG_OUT.accept(TAG, "newBloodPressure: " + result);
        return result;
    }

    /**
     * 体温測定入力ウィジットから体温測定オブジェクトを生成する
     *  <p>この項目は BLEボタンでの自動入力を想定する ※リリース時点の運用は無い</p>
     * @return 体温測定オブジェクト
     */
    private BodyTemperature newBodyTemperature() {
        // 体温 (任意)
        String sTemper = toStringOfTextView(mInpBodyTemper);
        Double temper = (!TextUtils.isEmpty(sTemper)) ? Double.parseDouble(sTemper) : null;
        // 体温測定時刻 (任意)
        String bodyMeasurementTime = toStringOfTimeView(mInpBodyTemperTime);
        return new BodyTemperature(bodyMeasurementTime, temper);
    }

    /**
     * 夜間頻尿要因ウィジットから頻尿要因オブジェクトを生成する
     * <ol>
     *   <li>夜間トイレ回数 (*)必須</li>
     *   <li>チェックボックスはそのまま設定</li>
     *   <li>健康状態: 未入力ならnullを設定</li>
     * </ol>
     * @return 頻尿要因オブジェクト
     */
    private NocturiaFactors newNocturiaFactors() {
        // 夜間トイレ回数: (*)必須
        int midnightToiletVisits = Integer.parseInt(mInpMidnightToiletVisits.getText().toString());
        // 健康状態: 任意
        String conditionMemo = toStringOfTextView(mEditConditionMemo);
        return new NocturiaFactors(midnightToiletVisits,
                mChkCoffee.isChecked(), mChkTea.isChecked(), mChkAlcohol.isChecked(),
                mChkNutritionDrink.isChecked()/* 栄養ドリンク */,mChkSportsDrink.isChecked(),
                mChkDiuretic.isChecked()/* その他(利尿作用有り) */,
                mChkTakeMedicine.isChecked(), mChkTakeBathing.isChecked(),
                (!TextUtils.isEmpty(conditionMemo)) ? conditionMemo : null);
    }

    /**
     * 登録用オブジェクトを入力ウィジットに適用する
     * <p>復元した測定日付に合わせてカレンダーオブジェクトも更新する</p>
     * <ol>
     *   <li>一時保存したJSONファイルからの復元<br/>
     *   ※起床時間以外の必須項目はnull(初期値)の可能性がある
     *   </li>
     *   <li>登録済み取得リクエストから取得したデータ<br/>
     *   ※必須項目には必ず値が設定されている
     *   </li>
     * </ol>
     * @param data 登録用データオブジェクト
     */
    private void updateInputWidgetsFromRegisterData(RegisterData data) {
        // 測定日付を復元
        String measurementDay = data.getMeasurementDay();
        updateDateView(mInpMeasurementDate, measurementDay);
        // 測定日付に連動してカレンダーオブジェクトを復元
        AppTopUtil.restoreCalendarObject(mMeasurementDayCal, measurementDay);
        // 健康管理データコンテナ
        HealthcareData healthcare = data.getHealthcareData();
        // 睡眠管理
        SleepManagement sleepManagement = healthcare.getSleepManagement();
        sleepManagementToWidgets(sleepManagement);
        // 血圧測定
        BloodPressure bloodPressure = healthcare.getBloodPressure();
        bloodPressureToWidgets(bloodPressure);
        // 体温測定データ
        BodyTemperature bodyTemper = healthcare.getBodyTemperature();
        bodyTemperatureToWidgets(bodyTemper);
        // 頻尿要因
        NocturiaFactors factors = healthcare.getNocturiaFactors();
        nocturiaFactorsToWidgets(factors);
        // 歩数
        WalkingCount walkingCount = healthcare.getWalkingCount();
        Integer counts = walkingCount.getCounts();
        if (counts != null) {
            mInpWalkingCount.setText(String.valueOf(counts));
        } else {
            mInpWalkingCount.setText("");
        }
        // 天候データコンテナ
        WeatherData weatherData = data.getWeatherData();
        // 天候状態: 健康管理DBと別トランザクションで登録されるため登録に失敗することを想定しnullチェック
        WeatherCondition weatherCondition = weatherData.getWeatherCondition();
        mInpWeatherCond.setText("");
        if (weatherCondition != null) {
            String condition = weatherCondition.getCondition();
            if (!TextUtils.isEmpty(condition)) {
                mInpWeatherCond.setText(condition);
            }
        }
    }

    /**
     * 睡眠管理データオブジェクトを対応する入力ウィジットに適用する
     *  <p>起床時刻はどのタイミングでも必ず設定されている</p>
     * @param sleepManagement 睡眠管理データオブジェクト
     */
    private void sleepManagementToWidgets(SleepManagement sleepManagement) {
        DEBUG_OUT.accept(TAG, "SleepManagement: " + sleepManagement);
        // 起床時刻: 必須
        String wakeupTime = sleepManagement.getWakeupTime();
        if (!TextUtils.isEmpty(wakeupTime)) {
            updateTimeView(mInpWakeupTime, wakeupTime, mFmtShowTime);
        }
        // 睡眠スコア: 任意
        Integer sleepScore = sleepManagement.getSleepScore();
        restoreNumberViewByValue(sleepScore, mInpSleepScore);
        // 睡眠時間: 任意(保存時), 必須(送信時)
        String sleepingTime = sleepManagement.getSleepingTime();
        restoreTimeViewByValue(sleepingTime, mInpSleepingTime, mFmtShowRangeTime);
        // 深い睡眠: 任意
        String deepSleepingTime = sleepManagement.getDeepSleepingTime();
        restoreTimeViewByValue(deepSleepingTime, mInpDeepSleepingTime, mFmtShowRangeTime);
    }

    /**
     * 血圧測定データオブジェクトを対応する入力ウィジットに適用する
     * @param pressure 血圧測定データオブジェクト
     */
    private void bloodPressureToWidgets(BloodPressure pressure) {
        DEBUG_OUT.accept(TAG, "BloodPressure: " + pressure);
        int morningId = mRadioMorning.getId();
        int eveningId = mRadioEvening.getId();
        // 午前の測定データをタグに設定する
        String morningTime = pressure.getMorningMeasurementTime();
        if (!TextUtils.isEmpty(morningTime)) {
            mInpMeasurementTime.setTag(morningId, morningTime);
            mInpBloodPressureMax.setTag(morningId, pressure.getMorningMax());
            mInpBloodPressureMin.setTag(morningId, pressure.getMorningMin());
            mInpPulseRate.setTag(morningId, pressure.getMorningPulseRate());
        } else {
            morningTime = getString(R.string.init_tag_time_value);
            mInpMeasurementTime.setTag(morningId, morningTime);
            mInpBloodPressureMax.setTag(morningId, null);
            mInpBloodPressureMin.setTag(morningId, null);
            mInpPulseRate.setTag(morningId, null);
        }
        // 午後の測定データをタグに設定する
        String eveningTime = pressure.getEveningMeasurementTime();
        if (!TextUtils.isEmpty(eveningTime)) {
            mInpMeasurementTime.setTag(eveningId, eveningTime);
            mInpBloodPressureMax.setTag(eveningId, pressure.getEveningMax());
            mInpBloodPressureMin.setTag(eveningId, pressure.getEveningMin());
            mInpPulseRate.setTag(eveningId, pressure.getEveningPulseRate());
        } else {
            eveningTime = getString(R.string.init_tag_time_value);
            mInpMeasurementTime.setTag(eveningId, eveningTime);
            mInpBloodPressureMax.setTag(eveningId, null);
            mInpBloodPressureMin.setTag(eveningId, null);
            mInpPulseRate.setTag(eveningId, null);
        }
        // 画面表示は選択されたラジオボタン
        if (mSelectedRadioId == morningId) {
            updateTimeViewByTag(mInpMeasurementTime, morningId, morningTime, mFmtShowTime);
            showNumberViewByTag(mInpBloodPressureMax, morningId);
            showNumberViewByTag(mInpBloodPressureMin, morningId);
            showNumberViewByTag(mInpPulseRate, morningId);
        } else {
            updateTimeViewByTag(mInpMeasurementTime, eveningId, eveningTime, mFmtShowTime);
            showNumberViewByTag(mInpBloodPressureMax, eveningId);
            showNumberViewByTag(mInpBloodPressureMin, eveningId);
            showNumberViewByTag(mInpPulseRate, eveningId);
        }
    }

    /**
     * 体温測定データオブジェクトを対応する入力ウィジットに適用する
     *  <ul>
     *  <li>1.体温: 任意項目
     *  <li>2.測定時刻: 任意項目
     *  </ul>
     * @param temperature 体温測定データオブジェクト
     */
    private void bodyTemperatureToWidgets(BodyTemperature temperature) {
        DEBUG_OUT.accept(TAG, "BodyTemperature: " + temperature);
        if (temperature != null) {
            // 体温はnullが有りうる
            Double bodyTemper = temperature.getTemperature();
            if (bodyTemper != null) {
                mInpBodyTemper.setText(String.valueOf(bodyTemper));
            }
            // 測定時刻もnullがあり得る
            restoreTimeViewByValue(temperature.getMeasurementTime(),
                    mInpBodyTemperTime, mFmtShowTime);
        } else {
            mInpBodyTemper.setText("");
        }
    }

    /**
     * 夜間頻尿要因オブジェクトを対応する入力ウィジットに適用する
     * <ol>
     *     <li>夜間トイレ回数: 必須</li>
     *     <li>チェックボックス項目: 必須</li>
     *     <li>健康状態メモ: 任意</li>
     * </ol>
     * @param factors 夜間頻尿要因オブジェクト
     */
    private void nocturiaFactorsToWidgets(NocturiaFactors factors) {
        DEBUG_OUT.accept(TAG, "NocturiaFactors: " + factors);
        // 夜間トイレ回数: 必須
        int midnightToiletVisits = factors.getMidnightToiletVisits();
        mInpMidnightToiletVisits.setText(String.valueOf(midnightToiletVisits));
        // 飲み物要因
        mChkCoffee.setChecked(factors.hasCoffee());
        mChkTea.setChecked(factors.hasTea());
        mChkAlcohol.setChecked(factors.hasAlcohol());
        mChkNutritionDrink.setChecked(factors.hasNutritionDrink());
        mChkSportsDrink.setChecked(factors.hasSportsDrink());
        mChkDiuretic.setChecked(factors.hasDiuretic());
        // 習慣要因
        mChkTakeMedicine.setChecked(factors.isTakeMedicine());
        mChkTakeBathing.setChecked(factors.isTakeBathing());
        // 健康状態メモ
        mEditConditionMemo.setText("");
        String conditionMemo = factors.getConditionMemo();
        if (conditionMemo != null) {
            mEditConditionMemo.setText(conditionMemo);
        }
    }

    /**
     * 一時JSONファイルを削除する
     */
    private void deleteSavedFile() {
        String lastDate = getLastSavedDateInPref();
        String fileName =getString(R.string.last_saved_json_file);
        DEBUG_OUT.accept(TAG, "delete.lastDate: " + lastDate);
        if (TextUtils.isEmpty(lastDate)) {
            return;
        }

        // 保存先ディレクトリのファイルリスト
        File savedDir = requireContext().getFilesDir();
        File[] fileArray = savedDir.listFiles();
        if (fileArray == null || fileArray.length == 0) {
             return;
        }

        // DEBUG
        String fileNames = Arrays.stream(fileArray)
                .map(File::toString)
                .collect(Collectors.joining(",")
        );
        DEBUG_OUT.accept(TAG, "fileNames: " + fileNames);
        // ファイルがあれば削除する
        mHandler.post(() -> {
            File jsonFile= new File(savedDir, fileName);
            if (jsonFile.delete()) {
                DEBUG_OUT.accept(TAG, "Deleted fileName: " + jsonFile);
            } else {
                DEBUG_OUT.accept(TAG, "Not delete: " + jsonFile);
            }
            // プリファレンス取得
            SharedPreferences sharedPref = getThisSharedPref();
            SharedPreferences.Editor editor = sharedPref.edit();
            // キーを削除
            editor.remove(getString(R.string.sharedpref_saved_key));
            editor.commit();
        });
    }

    /**
     * メール設定からメールアドレスを取得する
     * @return メールアドレス
     */
    private String getUserEmailWithThisSettings() {
        // https://developer.android.com/guide/topics/ui/settings/use-saved-values?hl=ja
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                requireActivity());
        return prefs.getString(getString(R.string.pref_emailaddress_key), null);
    }

    /**
     * 全てのチェックボックスをリセット
     */
    private void resetCheckBoxes() {
        for(CheckBox cb : mAllCheckBoxes) {
            cb.setChecked(false);
        }
    }

    /**
     * 即て日付以外の全てのウィジットに初期値を再設定する: 測定日付が切り替わったときを想定
     */
    private void resetInputWidgetsValue() {
        String initTagTimeValue = getString(R.string.init_tag_time_value);
        // 起床時刻
        updateTimeView(mInpWakeupTime, initTagTimeValue, mFmtShowTime);
        mInpMidnightToiletVisits.setText(R.string.init_midnight_toilet_visits);
        // 睡眠スコア表示はブランク
        mInpSleepScore.setText("");
        // 睡眠時間, 深い睡眠を初期値に戻す
        updateTimeView(mInpSleepingTime, initTagTimeValue, mFmtShowRangeTime);
        updateTimeView(mInpDeepSleepingTime, initTagTimeValue, mFmtShowRangeTime);
        // 血圧管理
        resetBloodPressureWidgetsValue(initTagTimeValue, mFmtShowTime);
        // 体温
        mInpBodyTemper.setText("");
        updateTimeView(mInpBodyTemperTime, initTagTimeValue, mFmtShowTime);
        // 要因と生活習慣のチェックボックスをリセット
        resetCheckBoxes();
        // 健康状態メモ
        mEditConditionMemo.setText("");
        // 歩数
        mInpWalkingCount.setText("");
        // 天候
        mInpWeatherCond.setText("");
    }

    /**
     * 血圧測定ウィジットの表示とキー付きTAG値をリセットする
     * <ol>
     *   <li>午前ラジオボタンをチェック状態に設定</li>
     *   <li>測定時刻<br/>
     *   [キー付きTAG値] 午前/午後 毎にキー付きTAG値に初期値を設定
     *   </li>
     *   <li>最大血圧値, 最小血圧値, 脈拍<br/>
     *   [表示] ブランク<br/>
     *   [キー付きTAG値] 午前/午後 毎にそれぞれの項目のデフォルト値を設定
     *   </li>
     * </ol>
     * @param initTagTimeValue キー付きTAG用の初期時刻
     * @param showTimeFmt 表示用時刻フォーマット
     */
    private void resetBloodPressureWidgetsValue(String initTagTimeValue, String showTimeFmt) {
        // 午前 / 午後 ラジオボタン
        mRadioMorning.setChecked(true);
        mSelectedRadioId = mRadioMorning.getId();
        int morningId = mRadioMorning.getId();
        int eveningId = mRadioEvening.getId();
        // 測定時刻: キー付きTAG値に初期値を設定
        mInpMeasurementTime.setTag(morningId, initTagTimeValue);
        mInpMeasurementTime.setTag(eveningId, initTagTimeValue);
        // 血圧測定値入力ウィジットの午前/午後毎のキー付きTAGにnullを設定
        resetTagValuesToBloodWidget(mInpBloodPressureMax, morningId, eveningId);
        resetTagValuesToBloodWidget(mInpBloodPressureMin, morningId, eveningId);
        resetTagValuesToBloodWidget(mInpPulseRate, morningId, eveningId);
        // 午前の初期表示
        updateTimeViewByTag(mInpMeasurementTime, mSelectedRadioId, initTagTimeValue, showTimeFmt);
        // それぞれの入力項目をブランクに設定する
        mInpBloodPressureMax.setText("");
        mInpBloodPressureMin.setText("");
        mInpPulseRate.setText("");
    }

    /**
     * 血圧系(数値)ウィジットのタグにAM/PM毎の値を設定する
     * @param tv 血圧系(数値)ウィジット
     * @param tagIdAM AM時間帯ラジオID
     * @param tagIdPM PM時間帯ラジオID
     */
    private void resetTagValuesToBloodWidget(TextView tv, int tagIdAM, int tagIdPM) {
        tv.setTag(tagIdAM, null);
        tv.setTag(tagIdPM, null);
    }

    /**
     * アクションバータイトルにネットワークデバイスを表示する
     * @param device ネットワークデバイス (モバイル|WiFi)
     */
    private void showActionBarGetting(RequestDevice device) {
        ActionBar bar = ((AppCompatActivity)requireActivity()).getSupportActionBar();
        // AppBarタイトル: ネットワーク接続種別
        if (device == RequestDevice.MOBILE) {
            TelephonyManager manager =
                    (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
            // モバイル接続の場合はキャリア名にかっこ付きで表示
            String operatorName = manager.getNetworkOperatorName();
            bar.setTitle(device.getMessage() + " (" + operatorName +")");
        } else {
            bar.setTitle(device.getMessage());
        }
        // AppBarサブタイトル: 取得中
        bar.setSubtitle(getResources().getString(R.string.msg_gettting_data));
    }

    /**
     * アクションパーサブタイトルにリクエストURLを表示する
     * @param reqUrlWithPath リクエストURL
     */
    private void showActionBarResult(String reqUrlWithPath) {
        ActionBar bar = ((AppCompatActivity)requireActivity()).getSupportActionBar();
        bar.setSubtitle(reqUrlWithPath);
    }

    /**
     * 夜間頻尿要因チェックボックスの変更チェック
     * @param orgFactors 夜間頻尿要因オブジェクト
     * @param cb 変更されたチェックボックス
     * @param isChecked チェック済み状態
     * @return 変更されていればtrue
     */
    private boolean isUpdateNocturiaFactors(NocturiaFactors orgFactors,
                                            CompoundButton cb, boolean isChecked) {
        boolean result;
        switch (cb.getId()) {
            case R.id.chkCoffee:
                result = AppTopUtil.isDifferentFlagValue(orgFactors.hasCoffee(), isChecked);
                break;
            case R.id.chkTea:
                result = AppTopUtil.isDifferentFlagValue(orgFactors.hasTea(), isChecked);
                break;
            case R.id.chkAlcohol:
                result = AppTopUtil.isDifferentFlagValue(orgFactors.hasAlcohol(), isChecked);
                break;
            case R.id.chkNutritionDrink:
                result = AppTopUtil.isDifferentFlagValue(orgFactors.hasNutritionDrink(), isChecked);
                break;
            case R.id.chkSportsDrink:
                result = AppTopUtil.isDifferentFlagValue(orgFactors.hasSportsDrink(), isChecked);
                break;
            case R.id.chkDiuretic:
                result = AppTopUtil.isDifferentFlagValue(orgFactors.hasDiuretic(), isChecked);
                break;
            case R.id.chkTakeMedicine:
                result = AppTopUtil.isDifferentFlagValue(orgFactors.isTakeMedicine(), isChecked);
                break;
            case R.id.chkTakeBathing:
                result = AppTopUtil.isDifferentFlagValue(orgFactors.isTakeBathing(), isChecked);
                break;
            default:
                result = false;
        }
        return result;
    }

    /**
     * 睡眠管理ウィジットの変更チェック
     * @param before 編集前の睡眠管理オブジェクト
     * @return 変更があればtrue
     */
    private boolean hasUpdateOfSleepManagement(SleepManagement before) {
        // 起床時刻: 必須
        String wakeupTime = toStringOfTextViewBySelfTag(mInpWakeupTime);
        if (!TextUtils.equals(wakeupTime, before.getWakeupTime())) {
            return true;
        }
        // 睡眠スコア: 任意
        Integer sleepScore = toIntegerOfNumberView(mInpSleepScore);
        if (AppTopUtil.isDifferentIntegerValue(sleepScore, before.getSleepScore())) {
            return true;
        }
        // 睡眠時間
        String sleepingTime = toStringOfTextViewBySelfTag(mInpSleepingTime);
        if (!TextUtils.equals(sleepingTime, before.getSleepingTime())) {
            return true;
        }
        // 深い睡眠
        String deepSleepingTime = toStringOfTextViewBySelfTag(mInpDeepSleepingTime);
        return !TextUtils.equals(deepSleepingTime, before.getDeepSleepingTime());
    }

    /**
     * 血圧測定ウィジットの変更チェック
     * @param before 編集前の血圧測定オブジェクト
     * @return 変更があればtrue
     */
    private boolean hasUpdateOfBloodPressure(BloodPressure before) {
        // 測定時刻 (午前/午後)
        String morningTime = toStringOfTimeViewByTag(mInpMeasurementTime, mRadioMorning.getId());
        if (!TextUtils.equals(morningTime, before.getMorningMeasurementTime())) {
            return true;
        }
        String eveningTime = toStringOfTimeViewByTag(mInpMeasurementTime, mRadioEvening.getId());
        if (!TextUtils.equals(eveningTime, before.getEveningMeasurementTime())) {
            return true;
        }
        // 最高血圧 (午前/午後)
        Integer mornMax = toIntegerOfNumberViewByTag(mInpBloodPressureMax, mRadioMorning.getId());
        if (AppTopUtil.isDifferentIntegerValue(mornMax, before.getMorningMax())) {
            return true;
        }
        Integer evenMax = toIntegerOfNumberViewByTag(mInpBloodPressureMax, mRadioEvening.getId());
        if (AppTopUtil.isDifferentIntegerValue(evenMax, before.getEveningMax())) {
            return true;
        }
        // 最低血圧 (午前/午後)
        Integer mornMin = toIntegerOfNumberViewByTag(mInpBloodPressureMin, mRadioMorning.getId());
        if (AppTopUtil.isDifferentIntegerValue(mornMin, before.getMorningMin())) {
            return true;
        }
        Integer evenMin = toIntegerOfNumberViewByTag(mInpBloodPressureMin, mRadioEvening.getId());
        if (AppTopUtil.isDifferentIntegerValue(evenMin, before.getEveningMin())) {
            return true;
        }
        // 脈拍 (午前/午後)
        Integer mornPulse = toIntegerOfNumberViewByTag(mInpPulseRate, mRadioMorning.getId());
        if (AppTopUtil.isDifferentIntegerValue(mornPulse, before.getMorningPulseRate())) {
            return true;
        }
        Integer evenPulse = toIntegerOfNumberViewByTag(mInpPulseRate, mRadioEvening.getId());
        return AppTopUtil.isDifferentIntegerValue(evenPulse, before.getEveningPulseRate());
    }

    /**
     * 健康状態メモの変更チェック
     * @param before 変更前テキスト
     * @return 変更有りならtrue
     */
    private boolean hasUpdateConditionMemo(String before) {
        String memo = mEditConditionMemo.getText().toString();
        return !TextUtils.equals(memo, before);
    }

    /**
     * 歩数の変更チェック
     * @param before 歩数管理オブジェクト
     * @return 変更有りならtrue
     */
    private boolean hasUpdateWalkingCount(WalkingCount before) {
        Integer counts = toIntegerOfNumberView(mInpWalkingCount);
        return AppTopUtil.isDifferentIntegerValue(counts, before.getCounts());
    }

    /**
     * 天候の変更チェック
     * @param before 天候状態オブジェクト
     * @return 変更有りならtrue
     */
    private boolean hasUpdateWeather(WeatherCondition before) {
        String cond = mInpWeatherCond.getText().toString();
        return !TextUtils.equals(cond, before.getCondition());
    }

}

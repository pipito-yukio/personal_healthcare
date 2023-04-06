package com.examples.android.healthcare.dialogs;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.examples.android.healthcare.R;

import java.util.Calendar;

/*
カスタム選択系フラグメントダイアログクラスの定義
(1)日付選択ダイアログ (2)時刻選択ダイアログ (3)数値選択ダイアログ

[Offical Reference]
https://developer.android.com/guide/topics/ui/controls/pickers?hl=ja
  選択ツール
https://developer.android.com/guide/topics/ui/dialogs?hl=ja
  ダイアログ
*/
public class PickerDialogs {

    public static class TimePickerFragment extends DialogFragment {
        public static class TimeHolder {
            private final int mHour;
            private final int mMinute;

            public TimeHolder(int hour, int minute) {
                mHour = hour;
                mMinute = minute;
            }

            public int getHour() {
                return mHour;
            }

            public int getMinute() {
                return mMinute;
            }
        }

        private final Context mContext;
        private final TimeHolder mTimeHolder;
        private final TimePickerDialog.OnTimeSetListener mListener;
        private final Boolean mIs24HourView;

        public TimePickerFragment(@NonNull Context context,
                                  TimeHolder timeHolder,
                                  @NonNull TimePickerDialog.OnTimeSetListener listener,
                                  @NonNull boolean is24Hour) {
            mContext = context;
            mTimeHolder = timeHolder;
            mListener = listener;
            mIs24HourView = is24Hour;
        }

        public TimePickerFragment(@NonNull Context context,
                                  TimeHolder timeHolder, @NonNull TimePickerDialog.OnTimeSetListener listener) {
            this(context, timeHolder, listener, DateFormat.is24HourFormat(context));
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int hour;
            int minute;
            if (mTimeHolder == null) {
                final Calendar cal = Calendar.getInstance();
                hour = cal.get(Calendar.HOUR_OF_DAY);
                minute = cal.get(Calendar.MINUTE);
            } else {
                hour = mTimeHolder.getHour();
                minute = mTimeHolder.getMinute();
            }
            return new TimePickerDialog(mContext, mListener, hour, minute, mIs24HourView);
        }
    }

    public static class DatePickerFragment extends DialogFragment {
        private final Context mContext;
        private final DatePickerDialog.OnDateSetListener mListener;
        private Calendar mCalendar;

        public DatePickerFragment(@NonNull Context context,
                                  Calendar cal,
                                  @NonNull DatePickerDialog.OnDateSetListener listener) {
            mContext = context;
            mCalendar = cal;
            mListener = listener;
        }

        public DatePickerFragment(@NonNull Context context,
                                  @NonNull DatePickerDialog.OnDateSetListener listener) {
            this(context, null, listener);
        }

        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (mCalendar == null) {
                mCalendar = Calendar.getInstance();
            }
            int year = mCalendar.get(Calendar.YEAR);
            int month = mCalendar.get(Calendar.MONTH); // 0-11
            int day = mCalendar.get(Calendar.DAY_OF_MONTH);
            return new DatePickerDialog(mContext, mListener, year, month, day);
        }
    }


    public static class NumberPickerDialog {
        public interface ValueListener {
            void onDecideValue(int number);

            void onCancel();
        }

        public static class DialogItem {
            private final String mDialogTitle;
            private final String mTitleLabel;
            private final String mUnitLabel;
            private final int mInitValue;
            private final int mMinValue;
            private final int mMaxValue;

            public DialogItem(String dialogTitle,
                              String titleLabel, String unitLabel, int initValue, int minValue, int maxValue) {
                mDialogTitle = dialogTitle;
                mTitleLabel = titleLabel;
                mUnitLabel = unitLabel;
                mInitValue = initValue;
                mMinValue = minValue;
                mMaxValue = maxValue;
            }

            public String getDialogTitle() {
                return mDialogTitle;
            }

            public String getTitleLabel() {
                return mTitleLabel;
            }

            public String getUnitLabel() {
                return mUnitLabel;
            }

            public int getMinValue() {
                return mMinValue;
            }

            public int getInitValue() {
                return mInitValue;
            }

            public int getMaxValue() {
                return mMaxValue;
            }
        }

        private final Context mContext;
        private final DialogItem mDialogItem;
        private final ValueListener mListener;

        public NumberPickerDialog(Context context, DialogItem item, ValueListener listener) {
            mContext = context;
            mDialogItem = item;
            mListener = listener;
        }

        public AlertDialog createNumberPickerDialog() {
            LayoutInflater factory = LayoutInflater.from(mContext);
            final View entryView = factory.inflate(R.layout.dialog_number_picker, null);
            final TextView labelView = entryView.findViewById(R.id.label);
            final TextView unitView = entryView.findViewById(R.id.unitView);
            labelView.setText(mDialogItem.getTitleLabel());
            unitView.setText(mDialogItem.getUnitLabel());
            final NumberPicker picker = entryView.findViewById(R.id.numPicker);
            picker.setMinValue(mDialogItem.getMinValue());
            picker.setMaxValue(mDialogItem.getMaxValue());
            picker.setValue(mDialogItem.getInitValue());
            return new AlertDialog.Builder(mContext)
                    .setTitle(mDialogItem.getDialogTitle())
                    .setView(entryView)
                    .setPositiveButton(R.string.alert_dialog_ok,
                            (dialog, whichButton) -> mListener.onDecideValue(picker.getValue())
                    )
                    .setNegativeButton(R.string.alert_dialog_cancel, (dialog, whichButton) -> mListener.onCancel()
                    )
                    .create();
        }
    }

}

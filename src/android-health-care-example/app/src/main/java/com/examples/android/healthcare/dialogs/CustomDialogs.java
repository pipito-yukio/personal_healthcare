package com.examples.android.healthcare.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.examples.android.healthcare.R;

/**
 * メッセージダイアログクラス
 */

public class CustomDialogs {
    /**
     * OKボタンイベント処理の不要なメッセージ表示のみのフラグメントダイアログクラス
     */
    public static class MessageOkDialogFragment extends DialogFragment {
        public static MessageOkDialogFragment newInstance(String title, String message) {
            MessageOkDialogFragment frag = new MessageOkDialogFragment();
            Bundle args = new Bundle();
            // タイトルは任意
            if (!TextUtils.isEmpty(title)) {
                args.putString("title", title);
            }
            // メッセージは文字列指定
            args.putString("message", message);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            assert getArguments() != null;
            String title = getArguments().getString("title");
            String message = getArguments().getString("message");
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            if (!TextUtils.isEmpty(title)) {
                builder.setTitle(title);
            }
            return builder
                    .setMessage(message)
                    .setPositiveButton(R.string.alert_dialog_ok,
                            (dialog, whichButton) -> {
                            })
                    .create();
        }
    }

    /**
     * OK/CANCEL ボタンイベント処理が必要なフラグメントダイアログクラス
     */
    public static class ConfirmDialogFragment extends DialogFragment {
        public interface ConfirmOkCancelListener {
            void onOk();
            void onCancel();
        }

        private final ConfirmOkCancelListener mListener;
        public ConfirmDialogFragment(ConfirmOkCancelListener listener) {
            mListener = listener;
        }

        public static ConfirmDialogFragment newInstance(String title, String message,
                                                          ConfirmOkCancelListener listener) {
            ConfirmDialogFragment frag = new ConfirmDialogFragment(listener);
            Bundle args = new Bundle();
            // タイトルは任意
            if (!TextUtils.isEmpty(title)) {
                args.putString("title", title);
            }
            // メッセージは文字列指定
            args.putString("message", message);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            assert getArguments() != null;
            String  title = getArguments().getString("title");
            String message = getArguments().getString("message");
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            if (!TextUtils.isEmpty(title)) {
                builder.setTitle(title);
            }
            return builder
                    .setMessage(message)
                    .setPositiveButton(R.string.alert_dialog_ok,
                            (dialog, whichButton) -> mListener.onOk())
                    .setNegativeButton(R.string.alert_dialog_cancel,
                            (dialog, whichButton) -> mListener.onCancel())
                    .create();
        }
    }

    /**
     * 1つのEditTextを有する入力ダイアログクラス
     */
    public static class EditDialogFragement extends DialogFragment {
        // 入力型
        public enum EditInputType {
            BODY_TEMPER/*体温(numberDecimal)*/, WALKING_COUNT/*歩数(nuberSigned)*/, NONE
        }
        // OK時に編集値を受け取るリスナー
        public interface EditOkCancelListener {
            void onOk(String editValue);
            void onCancel();
        }

        private final EditInputType mEditInputType;
        private final EditOkCancelListener mListener;
        public EditDialogFragement(EditInputType type,
                                   EditOkCancelListener listener) {
            mEditInputType = type;
            mListener = listener;
        }

        public static EditDialogFragement newInstance(String title, String editValue,
                                 EditInputType type, EditOkCancelListener listener) {
            EditDialogFragement frag = new EditDialogFragement(type, listener);
            Bundle args = new Bundle();
            // タイトルは文字列
            args.putString("title", title);
            // メッセージは文字列指定
            args.putString("editValue", editValue);
            frag.setArguments(args);
            return frag;
        }

        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            assert getArguments() != null;
            String title = getArguments().getString("title");
            String editValue = getArguments().getString("editValue");
            LayoutInflater factory = getLayoutInflater();
            View entryView;
            EditText mEditText;
            if (mEditInputType == EditInputType.BODY_TEMPER) {
                entryView = factory.inflate(R.layout.edit_body_temper, null);
                mEditText = entryView.findViewById(R.id.editBodyTemper);
            } else if (mEditInputType == EditInputType.WALKING_COUNT){
                entryView = factory.inflate(R.layout.edit_walking_count, null);
                mEditText = entryView.findViewById(R.id.editWalkingCount);
            } else {
                entryView = factory.inflate(R.layout.edit_weather_cond, null);
                mEditText = entryView.findViewById(R.id.editWeatherCond);
            }
            if (!TextUtils.isEmpty(editValue)) {
                mEditText.setSelectAllOnFocus(true);
                mEditText.setText(editValue);
            } else {
                mEditText.setText("");
            }
            return new AlertDialog.Builder(getActivity())
                    .setTitle(title)
                    .setView(entryView)
                    .setPositiveButton(R.string.alert_dialog_ok,
                            (dialog, whichButton) -> mListener.onOk(mEditText.getText().toString()))
                    .setNegativeButton(R.string.alert_dialog_cancel,
                            (dialog, whichButton) -> mListener.onCancel())
                    .create();
        }
    }
}

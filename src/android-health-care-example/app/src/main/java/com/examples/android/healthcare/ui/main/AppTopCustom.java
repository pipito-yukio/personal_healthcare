package com.examples.android.healthcare.ui.main;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;

/**
 * カスタムリスナー及びコールバック定義
 */
public class AppTopCustom {

    /**
     * カスタムテキスト変更ウォッチャークラス
     * <p>デフォルトをNoOpeで実装</p>
     */
    public static abstract class ChangedTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // No ope
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // No ope
        }

        @Override
        public void afterTextChanged(Editable s) {
            String afterText = s.toString();
            onChanged(afterText);
        }

        /**
         * テキスト変更通知
         * @param s 変更後のテキスト
         */
        public abstract void onChanged(String s);
    }

    /**
     * 入力系TextView変更通知コールバック
     */
    public interface OnTextViewChanged {
        void onChanged(TextView tv, String setValue);
    }

    /**
     * TAGキー付き入力系TextView変更通知コールバック
     */
    public interface  OnTextViewTagChanged {
        void onChanged(TextView tv, int tagKey, String setValue);
    }
}

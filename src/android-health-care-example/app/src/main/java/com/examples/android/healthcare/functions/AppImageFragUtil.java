package com.examples.android.healthcare.functions;

import static com.examples.android.healthcare.functions.MyLogging.DEBUG_OUT;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.examples.android.healthcare.HealthcareApplication;
import com.examples.android.healthcare.data.RegisterData;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 健康管理データ可視化フラグメント用共通メソッド
 */
public class AppImageFragUtil {
    private static final String TAG = "AppImageFragUtil";

    // 年月スピナー用フォーマット
    private static final String FMT_YM = "%d/%02d";

    /**
     * 年月スピナーから選択された値と選択位置を保持するクラス
     */
    public static class SpinnerSelected {
        public static final int UNSELECTED = -1;
        private int position;
        private String value;

        public SpinnerSelected() {}
        public int getPosition() { return position; }
        public String getValue() { return value; }
        public void setPosition(int position) { this.position = position; }
        public void setValue(String value) { this.value = value; }

        @NonNull
        @Override
        public String toString() {
            return "SpinnerSelected{position=" + position + ", value='" + value + "'}";
        }
    }


    private static int[] splitYearMonth(String strDate) {
        String[] dates = strDate.split("-");
        // [年(整数), 月(整数]
        return new int[] {Integer.parseInt(dates[0]), Integer.parseInt(dates[1])};
    }

    /**
     * 年月リストを生成する
     * @param startRegisterDay 登録開始日
     * @return 年月リスト(降順)
     */
    public static List<String> makeYearMonthList(String startRegisterDay) {
        List<String> result = new ArrayList<>();
        // 本日
        LocalDate today = LocalDate.now();
        String strToday = today.format(DateTimeFormatter.ISO_DATE);
        int[] latestYM = splitYearMonth(strToday);
        int currYear = latestYM[0];
        int currMonth = latestYM[1];
        // 本日は先頭
        result.add(String.format(Locale.getDefault(), FMT_YM, currYear, currMonth));
        int[] firstYM = splitYearMonth(startRegisterDay);
        int currYM = currYear * 100 + currMonth;
        int stopYM = firstYM[0] * 100 + firstYM[1];
        // 年月を登録年月より大きい場合は減算を続ける
        while (currYM > stopYM) {
            // 年と月を減算
            currMonth -= 1;
            if (currMonth == 0) {
                currMonth = 12;
                currYear -= 1;
            }
            result.add(String.format(Locale.getDefault(), FMT_YM, currYear, currMonth));
            currYM = currYear * 100 + currMonth;
        }
        return result;
    }

    /**
     * 年月リストを生成し年月選択スピナーアダブターに設定する
     * <p>アダブターのリストが空なら年月リストを生成しアダブターに追加する ※何回も呼び出しされる可能性がある</p>
     * @param adapter ArrayAdapter<String>
     * @param firstRegDate 初回登録年月日
     */
    public static void setYearMonthListToSpinnerAdapter(
            ArrayAdapter<String> adapter, String firstRegDate) {
        if (adapter.getCount() == 0) {
            // 登録年月から本日までの年月リスト生成
            List<String> yearMonthList = makeYearMonthList(firstRegDate);
            DEBUG_OUT.accept(TAG, "yearMonthList: " + yearMonthList);
            // addAll() is called notifyDataSetChanged();
            adapter.addAll(yearMonthList);
        }
    }

    /**
     * 前日(ISO8601形式)文字列取得
     * @return 前日文字列(ISO8601形式)
     */
    public static String getYesterday() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        return yesterday.format(DateTimeFormatter.ISO_DATE);
    }

    /**
     * 指定年月の最終日文字列(ISO8601)を取得する
     * @param yearMonth 指定年月
     * @return 最終日文字列(ISO8601)
     */
    public static String getLastDayInYearMonth(String yearMonth) {
        int[] dayParts = AppTopUtil.splitDateValue(yearMonth + "-01");
        LocalDate first = LocalDate.of(dayParts[0], dayParts[1], dayParts[2]);
        LocalDate endDate = first.with(TemporalAdjusters.lastDayOfMonth());
        return endDate.format(DateTimeFormatter.ISO_DATE);
    }

    /**
     * リクエストヘッダー("X-Request-Image-Size")に端末サイズ情報文字列を設定する
     *  (例) X-Request-Image-Size: 1064x1593x1.50
     * @param headers リクエストヘッダー用のマップオブジェクト
     * @param imageWd ImageViewの幅
     * @param imageHt ImageViewの高さ
     * @param density デバイス密度
     */
    public static void appendImageSizeToHeaders(Map<String, String> headers,
                                               int imageWd, int imageHt, float density) {
        // サイズは半角数値なのでロケールは "US"とする
        String imgSize = String.format(Locale.US, "%dx%dx%f", imageWd, imageHt, density);
        if (headers.containsKey(HealthcareApplication.REQUEST_IMAGE_SIZE_KEY)) {
            // キーが存在すれば上書き
            headers.replace(HealthcareApplication.REQUEST_IMAGE_SIZE_KEY, imgSize);
        } else {
            // なければ追加
            headers.put(HealthcareApplication.REQUEST_IMAGE_SIZE_KEY, imgSize);
        }
    }

    /**
     * トップ画面で保存されたJSONファイルからRegisterDataを取得
     * <p>復元前にJSONファイルの存在をチェックする</p>
     * @param context アクティビィティのコンテキスト
     * @param jsonFileName "last_saved.json"(一時保存) | "latest_registered.json"(最新登録保存)
     * @return 対象のJSONファイルが存在すれば RegisterData, 存在しなければnull
     */
    public static RegisterData getRegisterDataFromJson(Context context, String jsonFileName) {
        if (!FileManager.isFileExist(context, jsonFileName)) {
            return null;
        }
        try {
            String json = FileManager.readText(context, jsonFileName);
            if (!TextUtils.isEmpty(json)) {
                Gson gson = new Gson();
                return gson.fromJson(json, RegisterData.class);
            } else {
                return null;
            }
        } catch (IOException exp) {
            Log.w(TAG, exp);
            return null;
        }
    }

    /**
     * ImageViewのビットマップをファイル保存
     * @param context Activity
     * @param bitmap ImageViewのビットマップ
     * @param saveName 保存名
     * @return 保存完了なら保存ファイル(絶対パス)
     */
    public static String saveBitmapToPng(Context context,
                                          Bitmap bitmap, String saveName) throws IOException {
        // http://www.java2s.com/example/android/graphics/save-bitmap-to-a-file-path.html
        //  save Bitmap to a File Path - Android Graphics
        if (bitmap == null) {
            return null;
        }

        File rootPath = context.getFilesDir();
        if (rootPath.exists()) {
            Path savePath = Paths.get(rootPath.getAbsolutePath(), saveName);
            File file = savePath.toFile();
            if (file.exists()) {
                file.delete();
            }
            try(BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(file))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.flush();
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    public static Bitmap readBitmapFromAbsolutePath(String absolutePath) throws IOException {
        File file = new File(absolutePath);
        if (file.exists()) {
            Bitmap result;
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
                result = BitmapFactory.decodeStream(in);
            }
            return result;
        } else {
            return null;
        }
    }

}

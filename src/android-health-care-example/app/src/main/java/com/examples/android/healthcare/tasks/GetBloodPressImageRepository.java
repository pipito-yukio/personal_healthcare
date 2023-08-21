package com.examples.android.healthcare.tasks;

import com.examples.android.healthcare.data.BloodPressStatistics;
import com.examples.android.healthcare.data.ResponseImageData;
import com.examples.android.healthcare.functions.ImageDataStatistics;

/**
 * 血圧管理データプロット画像取得リポジトリクラス
 */
public class GetBloodPressImageRepository extends GetImageRepository
        implements ImageDataStatistics<BloodPressStatistics, ResponseImageData> {
    private static final String[] URLS = {
            "/getplot_bloodpress_line_ym_forphone" /*月間 折れ線グラフ*/,
            "/getplot_bloodpress_line_2w_forphone" /*2週間 折れ線グラフ*/,
            "/getplot_bloodpress_bar_2w_forphone" /*2週間 棒グラフ*/
    };

    @Override
    public String getRequestPath(int urlIdx) {
        return URLS[urlIdx];
    }

    /**
     * 血圧測定データの統計情報オブジェクトを取得する
     * @param data 画像データ取得結果オブジェクト
     * @return 血圧測定データの統計情報オブジェクト
     */
    public BloodPressStatistics getStatistics(ResponseImageData data) {
        String statisticsText = data.getStatistics();
        String[] items = statisticsText.split(",");
        BloodPressStatistics statistics;
        if (items.length == 4) {
            statistics = new BloodPressStatistics(
                    Integer.parseInt(items[0]), Integer.parseInt(items[1]),
                    Integer.parseInt(items[2]), Integer.parseInt(items[3]),
                    data.getRows()
            );
        } else {
            statistics = null;
        }
        return statistics;
    }

}

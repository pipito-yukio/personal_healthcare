package com.examples.android.healthcare.tasks;

import com.examples.android.healthcare.data.*;
import com.examples.android.healthcare.functions.ImageDataStatistics;

/**
 * 睡眠管理データプロット画像取得リポジトリクラス
 */
public class GetSleepManImageRepository extends GetImageRepository
        implements ImageDataStatistics<SleepManStatistics, ResponseImageData> {
    private static final String[] URLS = {
            "/getplot_sleepman_bar_ym_forphone" /*月間 棒グラフ*/,
            "/getplot_sleepman_bar_2w_forphone" /*2週間 棒グラフ*/,
            "/getplot_sleepman_histdual_range_forphone" /*指定範囲 ヒストグラム*/
    };

    @Override
    public String getRequestPath(int urlIdx) {
        return URLS[urlIdx];
    }

    /**
     * 睡眠管理データの統計情報オブジェクトを取得する
     * @param data 画像データ取得結果オブジェクト
     * @return 睡眠管理データの統計情報オブジェクト
     */
    public SleepManStatistics getStatistics(ResponseImageData data) {
        String statisticsText = data.getStatistics();
        String[] items = statisticsText.split(",");
        SleepManStatistics statistics;
        if (items.length == 2) {
            statistics = new SleepManStatistics(
                    Integer.parseInt(items[0]), Integer.parseInt(items[1]),
                    data.getRows()
            );
        } else {
            statistics = null;
        }
        return statistics;
    }

}

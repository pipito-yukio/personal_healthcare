package com.examples.android.healthcare.data;

public class BloodPressStatistics {
    // 午前の平均最高血圧
    private int amMaxMean;
    // 午前の平均最低血圧
    private int amMinMean;
    // 午後の平均最高血圧
    private int pmMaxMean;
    // 午後の平均最低血圧
    private int pmMinMean;
    // レコード件数
    private int recCount;

    public BloodPressStatistics(int amMaxMean, int amMinMean, int pmMaxMean, int pmMinMean,
                                int recCount) {
        this.amMaxMean = amMaxMean;
        this.amMinMean = amMinMean;
        this.pmMaxMean = pmMaxMean;
        this.pmMinMean = pmMinMean;
        this.recCount = recCount;
    }

    public int getAmMaxMean() {
        return amMaxMean;
    }

    public int getAmMinMean() {
        return amMinMean;
    }

    public int getPmMaxMean() {
        return pmMaxMean;
    }

    public int getPmMinMean() {
        return pmMinMean;
    }

    public int getRecCount() {
        return recCount;
    }

    @Override
    public String toString() {
        return "BloodPressStatistics{" +
                "amMaxMean=" + amMaxMean +
                ", amMinMean=" + amMinMean +
                ", pmMaxMean=" + pmMaxMean +
                ", pmMinMean=" + pmMinMean +
                ", recCount=" + recCount +
                '}';
    }
}

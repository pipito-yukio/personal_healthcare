package com.examples.android.healthcare.data;

import androidx.annotation.NonNull;

public class SleepManStatistics {
    // 平均睡眠時間 (分)
    private final int sleepingTimeMean;
    // 平均深い睡眠時間 (分)
    private final int deepSleepingTimeMean;
    // レコード件数
    private final int recCount;

    public SleepManStatistics(int sleepingTimeMean, int deepSleepingTimeMean, int recCount) {
        this.sleepingTimeMean = sleepingTimeMean;
        this.deepSleepingTimeMean = deepSleepingTimeMean;
        this.recCount = recCount;
    }

    public int getSleepingTimeMean() {
        return sleepingTimeMean;
    }

    public int getDeepSleepingTimeMean() {
        return deepSleepingTimeMean;
    }

    public int getRecCount() {
        return recCount;
    }

    @NonNull
    @Override
    public String toString() {
        return "SleepManStatistics{" +
                "sleepingTimeMean=" + sleepingTimeMean +
                ", deepSleepingTimeMean=" + deepSleepingTimeMean +
                ", recCount=" + recCount +
                '}';
    }
}

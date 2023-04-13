package com.examples.android.healthcare.data;

public class SleepManagement {
    private final String wakeupTime;
    private final Integer sleepScore;
    private final String sleepingTime;
    private final String deepSleepingTime;

    public SleepManagement(String wakeupTime, Integer sleepScore,
                           String sleepingTime, String deepSleepingTime) {
        this.wakeupTime = wakeupTime;
        this.sleepScore = sleepScore;
        this.sleepingTime = sleepingTime;
        this.deepSleepingTime = deepSleepingTime;
    }

    public String getWakeupTime() { return wakeupTime; }
    public Integer getSleepScore() { return sleepScore; }
    public String getSleepingTime() { return sleepingTime; }
    public String getDeepSleepingTime() { return deepSleepingTime; }

    @Override
    public String toString() {
        return "SleepManagement{" +
                "wakeupTime='" + wakeupTime + '\'' +
                ", sleepScore=" + sleepScore +
                ", sleepingTime='" + sleepingTime + '\'' +
                ", deepSleepingTime='" + deepSleepingTime + '\'' +
                '}';
    }
}

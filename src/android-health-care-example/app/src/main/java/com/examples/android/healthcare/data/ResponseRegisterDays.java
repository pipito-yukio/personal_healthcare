package com.examples.android.healthcare.data;

public class ResponseRegisterDays {
    // 登録開始日
    private final String firstDay;
    // 登録最終費
    private final String lastDay;

    public ResponseRegisterDays(String firstDay, String lastDay) {
        this.firstDay = firstDay;
        this.lastDay = lastDay;
    }

    public String getFirstDay() {
        return firstDay;
    }

    public String getLastDay() {
        return lastDay;
    }

    @Override
    public String toString() {
        return "RegisterDays{" +
                "firstDay='" + firstDay + '\'' +
                ", lastDay='" + lastDay + '\'' +
                '}';
    }
}

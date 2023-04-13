package com.examples.android.healthcare.constants;

public enum RequestDevice {
    WIFI("wifi", "Wi-Fi接続"),
    MOBILE("mobile", "モバイル接続"),
    NONE("none", "接続なし");

    private final String name;
    private final String message;

    RequestDevice(String name, String message) {
        this.name = name;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return name;
    }
}


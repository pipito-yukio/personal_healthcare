package com.examples.android.healthcare.data;

import androidx.annotation.NonNull;

public class ResponseWarningStatus {
    private final ResponseStatus status;

    public ResponseWarningStatus(ResponseStatus status) {
        this.status = status;
    }
    public ResponseStatus getStatus() {
        return status;
    }

    @NonNull
    @Override
    public String toString() {
        return "ResponseWarningStatus{status=" + status + '}';
    }
}

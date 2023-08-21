package com.examples.android.healthcare.data;

public class ResponseWarningStatus {
    private final ResponseStatus status;

    public ResponseWarningStatus(ResponseStatus status) {
        this.status = status;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "ResponseWarningStatus{status=" + status + '}';
    }
}

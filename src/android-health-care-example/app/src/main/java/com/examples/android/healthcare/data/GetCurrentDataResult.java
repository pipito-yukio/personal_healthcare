package com.examples.android.healthcare.data;

import com.google.gson.annotations.SerializedName;

public class GetCurrentDataResult {
    @SerializedName("data")
    private final RegisterData data;
    private final ResponseStatus status;

    public GetCurrentDataResult(RegisterData data, ResponseStatus status) {
        this.data = data;
        this.status = status;
    }

    public RegisterData getData() {
        return data;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "GetCurrentDataResult{" +
                "data=" + data +
                ", status=" + status +
                '}';
    }
}

package com.examples.android.healthcare.data;

public class RegisterResult {
    private final RegisterResponseData data;
    private final ResponseStatus status;

    public RegisterResult(RegisterResponseData data, ResponseStatus status) {
        this.data = data;
        this.status = status;
    }

    public RegisterResponseData getData() {
        return data;
    }

    public ResponseStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "HealthcareRegisterResult{" +
                "data=" + data +
                ", status=" + status +
                '}';
    }
}

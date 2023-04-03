package com.examples.android.healthcare.data;

public class RegisterResponseData {
    private final String emailAddress;
    private final String measurementDay;

    public  RegisterResponseData(String emailAddress, String measurementDay) {
        this.emailAddress = emailAddress;
        this.measurementDay = measurementDay;
    }

    public String getEmailAddress() { return emailAddress; }
    public String getMeasurementDay() { return measurementDay; }

    @Override
    public String toString() {
        return "RegisterResponseData{" +
                "emailAddress='" + emailAddress + '\'' +
                ", measurementDay='" + measurementDay + '\'' +
                '}';
    }
}

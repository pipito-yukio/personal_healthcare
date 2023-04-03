package com.examples.android.healthcare.data;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class BodyTemperature {
    private final String measurementTime;
    private final Double temperature;

    public BodyTemperature(@Nullable String measurementTime, @Nullable Double temperature) {
        this.measurementTime = measurementTime;
        this.temperature = temperature;
    }

    public String getMeasurementTime() { return measurementTime; }
    public Double getTemperature() { return temperature; }

    @Override
    public String toString() {
        return "BodyTemperature{" +
                "measurementTime='" + measurementTime + '\'' +
                ", temperature=" + temperature +
                '}';
    }
}

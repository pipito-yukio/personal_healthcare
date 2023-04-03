package com.examples.android.healthcare.data;

import androidx.annotation.Nullable;

/**
 * 血圧測定値は測定忘れなどに対応するため全ての項目をnull可とする
 */
public class BloodPressure {
    private final String morningMeasurementTime;
    private final Integer morningMax;
    private final Integer morningMin;
    private final Integer morningPulseRate;
    private final String eveningMeasurementTime;
    private final Integer eveningMax;
    private final Integer eveningMin;
    private final Integer eveningPulseRate;

    public BloodPressure(@Nullable String morningMeasurementTime,
                         @Nullable Integer morningMax,
                         @Nullable Integer morningMin,
                         @Nullable Integer morningPulseRate,
                         @Nullable String eveningMeasurementTime,
                         @Nullable Integer eveningMax,
                         @Nullable Integer eveningMin,
                         @Nullable Integer eveningPulseRate) {
        this.morningMeasurementTime = morningMeasurementTime;
        this.morningMax = morningMax;
        this.morningMin = morningMin;
        this.morningPulseRate = morningPulseRate;
        this.eveningMeasurementTime = eveningMeasurementTime;
        this.eveningMax = eveningMax;
        this.eveningMin = eveningMin;
        this.eveningPulseRate = eveningPulseRate;
    }

    public String getMorningMeasurementTime() { return morningMeasurementTime; }
    public Integer getMorningMax() { return morningMax; }
    public Integer getMorningMin() { return morningMin; }
    public Integer getMorningPulseRate() { return morningPulseRate; }
    public String getEveningMeasurementTime() { return eveningMeasurementTime; }
    public Integer getEveningMax() { return eveningMax; }
    public Integer getEveningMin() { return eveningMin; }
    public Integer getEveningPulseRate() { return eveningPulseRate; }

    @Override
    public String toString() {
        return "BloodPressure{" +
                "morningMeasurementTime=" + morningMeasurementTime +
                ", morningMax=" + morningMax +
                ", morningMin=" + morningMin +
                ", morningPulseRate=" + morningPulseRate +
                ", eveningMeasurementTime=" + eveningMeasurementTime +
                ", eveningMax=" + eveningMax +
                ", eveningMin=" + eveningMin +
                ", eveningPulseRate=" + eveningPulseRate +
                '}';
    }
}

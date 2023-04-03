package com.examples.android.healthcare.data;

public class HealthcareData {
    private final SleepManagement sleepManagement;
    private final BloodPressure bloodPressure;
    private final BodyTemperature bodyTemperature;
    private final NocturiaFactors nocturiaFactors;
    private final WalkingCount walkingCount;

    public HealthcareData(SleepManagement sleepManagement,
                          BloodPressure bloodPressure,
                          BodyTemperature bodyTemperature,
                          NocturiaFactors nocturiaFactors,
                          WalkingCount walkingCount) {
        this.sleepManagement = sleepManagement;
        this.bloodPressure = bloodPressure;
        this.bodyTemperature = bodyTemperature;
        this.nocturiaFactors = nocturiaFactors;
        this.walkingCount = walkingCount;
    }

    public SleepManagement getSleepManagement() { return sleepManagement; }
    public BloodPressure getBloodPressure() { return bloodPressure; }
    public BodyTemperature getBodyTemperature() { return bodyTemperature; }
    public NocturiaFactors getNocturiaFactors() { return nocturiaFactors; }
    public WalkingCount getWalkingCount() { return walkingCount; }

    @Override
    public String toString() {
        return "HealthcareData{" +
                " sleepManagement=" + sleepManagement +
                ", bloodPressure=" + bloodPressure +
                ", bodyTemperature=" + bodyTemperature +
                ", nocturiaFactors=" + nocturiaFactors +
                ", walkingCount=" + walkingCount +
                '}';
    }
}

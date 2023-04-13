package com.examples.android.healthcare.data;

public class WeatherCondition {
    private final String condition;

    public WeatherCondition(String condition) {
        this.condition = condition;
    }

    public String getCondition() { return condition; }

    @Override
    public String toString() {
        return "weatherCondition{" +
                "condition='" + condition + '\'' +
                '}';
    }
}

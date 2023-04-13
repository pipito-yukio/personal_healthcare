package com.examples.android.healthcare.data;

public class WeatherData {
    private final WeatherCondition weatherCondition;

    public WeatherData(WeatherCondition wc) {
        this.weatherCondition = wc;
    }

    public WeatherCondition getWeatherCondition() {return weatherCondition; }

    @Override
    public String toString() {
        return "WeatherData{" +
                "weatherCondition=" + weatherCondition +
                '}';
    }
}

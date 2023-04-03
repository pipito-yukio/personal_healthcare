package com.examples.android.healthcare.data;

public class RegisterData {
    // 登録用主キー: emailAddress -> persion.id
    private final String emailAddress;
    // 登録用主キー: 測定日付
    private final String measurementDay;
    private final HealthcareData healthcareData;
    private final WeatherData weatherData;

    public RegisterData(String emailAddress, String measurementDay,
                        HealthcareData healthcareData, WeatherData weatherData) {
        this.emailAddress = emailAddress;
        this.measurementDay = measurementDay;
        this.healthcareData = healthcareData;
        this.weatherData = weatherData;
    }

    public String getEmailAddress() { return emailAddress; }
    public String getMeasurementDay() { return measurementDay; }
    public HealthcareData getHealthcareData() { return healthcareData; }
    public WeatherData getWeatherData() { return weatherData; }

    @Override
    public String toString() {
        return "RegisterData{" +
                "emailAddress='" + emailAddress + '\'' +
                ", measurementDay='" + measurementDay + '\'' +
                ", healthcareData=" + healthcareData +
                ", weatherData=" + weatherData +
                '}';
    }
}

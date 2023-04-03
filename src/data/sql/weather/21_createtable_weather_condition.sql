CREATE TABLE IF NOT EXISTS weather.weather_condition(
   measurement_day date NOT NULL,
   condition VARCHAR(60) NOT NULL,
   CONSTRAINT pk_weather_condition PRIMARY KEY (measurement_day)
);
ALTER TABLE weather.weather_condition OWNER TO developer;

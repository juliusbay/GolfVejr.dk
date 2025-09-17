package com.example.golfvejr.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Details {

    @JsonProperty("air_pressure_at_sea_level")
    private double airPressureAtSeaLevel;

    @JsonProperty("air_temperature")
    private double airTemperature;

    @JsonProperty("cloud_area_fraction")
    private double cloudAreaFraction;

    @JsonProperty("relative_humidity")
    private double relativeHumidity;

    @JsonProperty("wind_from_direction")
    private double windFromDirection;

    @JsonProperty("wind_speed")
    private double windSpeed;

    @JsonProperty("precipitation_amount")
    private double precipitationAmount;

    private String status;

}

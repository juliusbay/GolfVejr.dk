package com.example.golfvejr.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
public class TimeSeries {

    @JsonProperty("data")
    private ForecastData data;

    @JsonProperty("time")
    private ZonedDateTime time;
}

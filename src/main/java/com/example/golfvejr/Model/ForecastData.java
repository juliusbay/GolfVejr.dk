package com.example.golfvejr.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForecastData {

    @JsonProperty("instant")
    private Instant instant;

    @JsonProperty("next_12_hours")
    private ForecastPeriod next12Hours;

    @JsonProperty("next_6_hours")
    private ForecastPeriod next6Hours;

    @JsonProperty("next_1_hours")
    private ForecastPeriod next1Hours;
}

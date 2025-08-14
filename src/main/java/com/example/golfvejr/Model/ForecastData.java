package com.example.golfvejr.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForecastData {

    @JsonProperty("instant")
    Instant instant;

    @JsonProperty("next_12_hours")
    ForecastPeriod next12Hours;

    @JsonProperty("next_6_hours")
    ForecastPeriod next6Hours;

    @JsonProperty("next_1_hours")
    ForecastPeriod next1Hours;
}

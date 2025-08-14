package com.example.golfvejr.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForecastPeriod {

    @JsonProperty("summary")
    Summary summary;

    @JsonProperty("details")
    Details details;
}

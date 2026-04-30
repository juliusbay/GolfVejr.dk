package com.example.golfvejr.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OverpassResponse {

    @JsonProperty("elements")
    private List<OverpassElement> elements;
}

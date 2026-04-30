package com.example.golfvejr.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class OverpassElement {

    @JsonProperty("type")
    private String type;

    @JsonProperty("id")
    private Long id;

    // Present on nodes; null for ways/relations (use center instead)
    @JsonProperty("lat")
    private Double lat;

    @JsonProperty("lon")
    private Double lon;

    // Present on ways and relations when queried with "out center;"
    @JsonProperty("center")
    private OverpassCenter center;

    @JsonProperty("tags")
    private Map<String, String> tags;
}

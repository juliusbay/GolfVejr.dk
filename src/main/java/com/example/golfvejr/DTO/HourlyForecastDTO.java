package com.example.golfvejr.DTO;

import java.util.List;

public record HourlyForecastDTO(
        String time,
        double temperature,
        double windSpeed,
        double windGust,
        double precipitation,
        String status,
        int score,
        String summary,
        List<String> goodFactors,
        List<String> badFactors
) {}

package com.example.golfvejr.DTO;

import java.util.List;

public record HourlyForecastDTO(
        String time,
        double temperature,
        double windSpeed,
        double windGust,
        double windDirection,    // degrees, wind_from_direction (0° = wind blowing FROM north)
        double precipitation,    // mm/hour — normalized; -1.0 when no forecast data at all
        boolean isSixHour,       // true when sourced from next_6_hours (used for "ca." labels)
        String status,
        int score,
        String summary,
        List<String> goodFactors,
        List<String> badFactors,
        String symbolCode        // MET Norway symbol_code, e.g. "clearsky_day", "rain"
) {}

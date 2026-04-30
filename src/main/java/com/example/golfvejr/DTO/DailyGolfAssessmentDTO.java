package com.example.golfvejr.DTO;

import java.util.List;

public record DailyGolfAssessmentDTO(
        String date,
        String dayOfWeek,
        String overallStatus,
        int score,
        String summary,
        List<String> goodFactors,
        List<String> badFactors,
        String bestWindow,
        List<HourlyForecastDTO> hourlyForecasts
) {}

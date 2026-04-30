package com.example.golfvejr.Controller;

import com.example.golfvejr.DTO.DailyGolfAssessmentDTO;
import com.example.golfvejr.Service.ForecastService;
import com.example.golfvejr.Service.GolfClubService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastApiController {

    private final ForecastService forecastService;
    private final GolfClubService golfClubService;

    @GetMapping("/{clubId}")
    public List<DailyGolfAssessmentDTO> getForecast(@PathVariable Long clubId) {
        var club = golfClubService.getClubById(clubId);
        return forecastService.getForecastForClub(club.getLatitude(), club.getLongitude());
    }

    @GetMapping("/{clubId}/best-days")
    public List<DailyGolfAssessmentDTO> getBestDays(@PathVariable Long clubId) {
        var club = golfClubService.getClubById(clubId);
        return forecastService.getForecastForClub(club.getLatitude(), club.getLongitude())
                .stream()
                .sorted(Comparator.comparingInt(DailyGolfAssessmentDTO::score).reversed())
                .limit(7)
                .collect(Collectors.toList());
    }
}

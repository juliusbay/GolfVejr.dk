package com.example.golfvejr.Service;

import com.example.golfvejr.DTO.DailyGolfAssessmentDTO;
import com.example.golfvejr.DTO.HourlyForecastDTO;
import com.example.golfvejr.Model.TimeSeries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ForecastService {

    private static final ZoneId COPENHAGEN_TZ = ZoneId.of("Europe/Copenhagen");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // Minimum best-window score to bother suggesting a tee time
    private static final int WINDOW_MIN_SCORE   = 45;
    // Hard cutoff: no golf window can be recommended after this hour
    private static final int LATEST_WINDOW_HOUR = 21;

    private final YrApiService yrApiService;
    private final GolfAssessmentService golfAssessmentService;

    public List<DailyGolfAssessmentDTO> getForecastForClub(double lat, double lon) {
        List<TimeSeries> allEntries = yrApiService.getForecast(lat, lon).getProperties().getTimeSeries();

        Map<LocalDate, List<TimeSeries>> byDay = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            byDay.put(LocalDate.now(COPENHAGEN_TZ).plusDays(i), new ArrayList<>());
        }
        for (TimeSeries ts : allEntries) {
            LocalDate date = ts.getTime().withZoneSameInstant(COPENHAGEN_TZ).toLocalDate();
            if (byDay.containsKey(date)) byDay.get(date).add(ts);
        }

        return byDay.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> buildDailyAssessment(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private DailyGolfAssessmentDTO buildDailyAssessment(LocalDate date, List<TimeSeries> entries) {
        String dateStr = date.format(DATE_FORMATTER);
        String raw = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.of("da"));
        String dayOfWeek = raw.substring(0, 1).toUpperCase() + raw.substring(1);

        List<HourlyForecastDTO> allHours    = new ArrayList<>();
        List<Integer> allHourNums           = new ArrayList<>();
        List<HourlyForecastDTO> daytimeDTOs = new ArrayList<>();
        List<Integer> daytimeHourNums       = new ArrayList<>();
        List<HourlyForecastDTO> windowDTOs  = new ArrayList<>();
        List<Integer> windowHourNums        = new ArrayList<>();

        for (TimeSeries ts : entries) {
            HourlyForecastDTO dto = golfAssessmentService.assess(ts);
            int hour = ts.getTime().withZoneSameInstant(COPENHAGEN_TZ).getHour();
            allHours.add(dto);
            allHourNums.add(hour);
            if (hour >= 7 && hour <= 22) {
                daytimeDTOs.add(dto);
                daytimeHourNums.add(hour);
            }
            if (hour >= 7 && hour <= LATEST_WINDOW_HOUR) {
                windowDTOs.add(dto);
                windowHourNums.add(hour);
            }
        }

        List<HourlyForecastDTO> assessment = daytimeDTOs.isEmpty() ? allHours : daytimeDTOs;
        List<Integer> hours = daytimeDTOs.isEmpty() ? allHourNums : daytimeHourNums;

        // Time-weighted score: midday hours (10–16) count 1.5×, all others 1×.
        double total = 0, weightSum = 0;
        for (int i = 0; i < assessment.size(); i++) {
            int h = hours.get(i);
            double w = (h >= 10 && h <= 16) ? 1.5 : 1.0;
            total     += assessment.get(i).score() * w;
            weightSum += w;
        }
        int avgScore = weightSum > 0 ? (int) Math.round(total / weightSum) : 0;

        List<String> goodFactors = new ArrayList<>();
        List<String> badFactors  = new ArrayList<>();

        // 80th-percentile wind avoids a single spike dominating the day's label.
        List<Double> sortedWinds = assessment.stream()
                .map(HourlyForecastDTO::windSpeed)
                .sorted()
                .toList();
        double wind    = sortedWinds.get((int) (sortedWinds.size() * 0.8));
        double maxGust = assessment.stream().mapToDouble(HourlyForecastDTO::windGust).max().orElse(0);
        double maxPrecip = -1;
        boolean maxPrecipIsEstimate = false;
        for (HourlyForecastDTO h : assessment) {
            if (h.precipitation() >= 0 && h.precipitation() > maxPrecip) {
                maxPrecip           = h.precipitation();
                maxPrecipIsEstimate = h.isSixHour();
            }
        }

        if (wind <= 4) {
            goodFactors.add(fmt("Svag vind (%.1f m/s)", wind));
        } else if (wind <= 7) {
            goodFactors.add(fmt("Let brise (%.1f m/s)", wind));
        } else if (wind <= 10) {
            badFactors.add(fmt("Moderat vind (%.1f m/s)", wind));
        } else {
            badFactors.add(fmt("Kraftig vind (%.1f m/s)", wind));
        }

        // Show gust label only when gusts are meaningfully above the typical wind.
        if (maxGust != wind && maxGust > wind + 2.0) {
            badFactors.add(fmt("Vindstød op til %.1f m/s", maxGust));
        }

        String approx = maxPrecipIsEstimate ? "ca. " : "";
        if (maxPrecip < 0) {
            badFactors.add("Mulighed for regn");
        } else if (maxPrecip <= 0.05) {
            // dry — no label
        } else if (maxPrecip <= 0.15) {
            badFactors.add(fmt("Let regn (%s%.1f mm/t)", approx, maxPrecip));
        } else if (maxPrecip <= 0.30) {
            badFactors.add(fmt("Regn (%s%.1f mm/t)", approx, maxPrecip));
        } else {
            badFactors.add(fmt("Regn forventet (%s%.1f mm/t)", approx, maxPrecip));
        }

        // Single temperature label based on the warmest daytime hour (07:00–21:00).
        double maxTemp = windowDTOs.isEmpty()
                ? assessment.stream().mapToDouble(HourlyForecastDTO::temperature).max().orElse(0)
                : windowDTOs.stream().mapToDouble(HourlyForecastDTO::temperature).max().orElse(0);

        String tempLabel = buildTemperatureLabel(maxTemp, badFactors);
        if (maxTemp >= 14) {
            goodFactors.add(0, tempLabel);
        } else {
            badFactors.add(0, tempLabel);
        }

        String overallStatus = GolfAssessmentService.deriveStatus(avgScore);
        String summary = GolfAssessmentService.deriveSummary(avgScore, badFactors);
        String bestWindow = findBestWindow(windowHourNums, windowDTOs);

        return new DailyGolfAssessmentDTO(
                dateStr, dayOfWeek, overallStatus, avgScore,
                summary, goodFactors, badFactors, bestWindow, allHours);
    }

    // Sliding-window search over daytime entries (window = 4 hours or all entries if fewer).
    // Returns "HH:00–HH:00" for the block with the highest average score, or null if
    // the best block is below the minimum threshold (conditions not worth recommending).
    private String findBestWindow(List<Integer> hours, List<HourlyForecastDTO> dtos) {
        int n = hours.size();
        if (n < 8) return null;

        int span = hours.get(n - 1) - hours.get(0);
        if (span < 6) return null;

        int windowSize = Math.min(4, n);
        int bestStart  = 0;
        double bestAvg = -1;

        for (int i = 0; i <= n - windowSize; i++) {
            double avg = 0;
            for (int j = i; j < i + windowSize; j++) avg += dtos.get(j).score();
            avg /= windowSize;
            if (avg > bestAvg) { bestAvg = avg; bestStart = i; }
        }

        if (bestAvg < WINDOW_MIN_SCORE) return null;

        int startHour = hours.get(bestStart);
        int endHour   = hours.get(Math.min(bestStart + windowSize - 1, n - 1)) + 1;
        return String.format("%02d:00–%02d:00", startHour, endHour);
    }

    private static String buildTemperatureLabel(double temp, List<String> badFactors) {
        if (temp >= 18 && badFactors.isEmpty()) return fmt("Perfekt temperatur (%.0f°C)", temp);
        if (temp >= 18) return fmt("God temperatur (%.0f°C)", temp);
        if (temp >= 14) return fmt("Fin temperatur (%.0f°C)", temp);
        if (temp >= 10) return fmt("Køligt vejr (%.0f°C)", temp);
        if (temp >= 6)  return fmt("Koldt vejr (%.0f°C)", temp);
        return fmt("For koldt til golf (%.0f°C)", temp);
    }

    private static String fmt(String pattern, Object... args) {
        return String.format(java.util.Locale.ROOT, pattern, args);
    }

}

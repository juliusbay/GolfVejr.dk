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
        List<HourlyForecastDTO> daytimeDTOs = new ArrayList<>();
        List<Integer> daytimeHourNums       = new ArrayList<>();
        List<HourlyForecastDTO> windowDTOs  = new ArrayList<>();
        List<Integer> windowHourNums        = new ArrayList<>();

        for (TimeSeries ts : entries) {
            HourlyForecastDTO dto = golfAssessmentService.assess(ts);
            allHours.add(dto);
            int hour = ts.getTime().withZoneSameInstant(COPENHAGEN_TZ).getHour();
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

        int avgScore = (int) assessment.stream()
                .mapToInt(HourlyForecastDTO::score)
                .average().orElse(0);

        // Strip per-hour temperature labels; replace with one representative label below.
        List<String> goodFactors = new ArrayList<>(topN(assessment.stream()
                .flatMap(h -> h.goodFactors().stream())
                .filter(f -> !isTemperatureFactor(f))
                .collect(Collectors.toList()), 2));

        List<String> badFactors = new ArrayList<>(topN(assessment.stream()
                .flatMap(h -> h.badFactors().stream())
                .filter(f -> !isTemperatureFactor(f))
                .collect(Collectors.toList()), 3));

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
        if (n < 2) return null;

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

    private static boolean isTemperatureFactor(String factor) {
        return factor.contains("°C");
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

    // Returns the top N most frequently occurring factor strings, deduplicating by base
    // label (strips parenthetical values like "(8°C)") to avoid near-identical entries
    // like "Kølige temperaturer (8°C)" and "Kølige temperaturer (5°C)" both appearing.
    private List<String> topN(List<String> items, int n) {
        // Strip parenthetical suffix to get the base label used as grouping key
        Map<String, String> baseToFirst = new LinkedHashMap<>();
        Map<String, Long> baseCounts = new LinkedHashMap<>();
        for (String item : items) {
            String base = item.replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
            baseToFirst.putIfAbsent(base, item);
            baseCounts.merge(base, 1L, Long::sum);
        }
        return baseCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .map(e -> baseToFirst.get(e.getKey()))
                .collect(Collectors.toList());
    }
}

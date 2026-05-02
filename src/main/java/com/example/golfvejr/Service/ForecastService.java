package com.example.golfvejr.Service;

import com.example.golfvejr.DTO.DailyGolfAssessmentDTO;
import com.example.golfvejr.DTO.HourlyForecastDTO;
import com.example.golfvejr.Model.TimeSeries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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

    public List<DailyGolfAssessmentDTO> getForecastForClub(double lat, double lon, String timePref) {
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
                .map(e -> buildDailyAssessment(e.getKey(), e.getValue(), lat, lon, timePref))
                .collect(Collectors.toList());
    }

    private DailyGolfAssessmentDTO buildDailyAssessment(LocalDate date, List<TimeSeries> entries, double lat, double lon, String timePref) {
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
            if (hour >= 6 && hour <= 22) {
                daytimeDTOs.add(dto);
                daytimeHourNums.add(hour);
            }
            if (hour >= 6 && hour <= LATEST_WINDOW_HOUR) {
                windowDTOs.add(dto);
                windowHourNums.add(hour);
            }
        }

        List<HourlyForecastDTO> assessment = daytimeDTOs.isEmpty() ? allHours : daytimeDTOs;
        List<Integer> hours = daytimeDTOs.isEmpty() ? allHourNums : daytimeHourNums;

        String sunsetTime = calculateSunset(lat, lon, date);
        int sunsetHour = -1;
        if (sunsetTime != null) {
            try { sunsetHour = Integer.parseInt(sunsetTime.split(":")[0]); }
            catch (NumberFormatException ignored) {}
        }

        // Preferred time window for scoring.
        // "all": midday (10–16) counts 1.5×, others 1× (original behavior).
        // Specific pref: in-window hours 3×, out-of-window 0.25×, post-sunset excluded.
        int[] prefRange = preferredRange(timePref, sunsetHour);
        double total = 0, weightSum = 0;
        for (int i = 0; i < assessment.size(); i++) {
            int h = hours.get(i);
            if (sunsetHour >= 0 && h > sunsetHour) continue;
            double w;
            if (isAllDay(timePref)) {
                w = (h >= 10 && h <= 16) ? 1.5 : 1.0;
            } else {
                w = (h >= prefRange[0] && h < prefRange[1]) ? 3.0 : 0.25;
            }
            total     += assessment.get(i).score() * w;
            weightSum += w;
        }
        int avgScore = weightSum > 0 ? (int) Math.round(total / weightSum) : 0;

        // No daylight hours scored → the day is effectively over.
        // Mark as "Nat" so the UI can show a neutral night state instead of a scary red zero.
        boolean isNightOnly = (weightSum == 0 && sunsetHour >= 0);

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

        // Single temperature label based on the warmest daytime hour (06:00–21:00).
        double maxTemp = windowDTOs.isEmpty()
                ? assessment.stream().mapToDouble(HourlyForecastDTO::temperature).max().orElse(0)
                : windowDTOs.stream().mapToDouble(HourlyForecastDTO::temperature).max().orElse(0);

        String tempLabel = buildTemperatureLabel(maxTemp, badFactors);
        if (maxTemp >= 14) {
            goodFactors.add(0, tempLabel);
        } else {
            badFactors.add(0, tempLabel);
        }

        String overallStatus = isNightOnly ? "Nat" : GolfAssessmentService.deriveStatus(avgScore);
        String summary       = isNightOnly ? "Solen er gået ned" : GolfAssessmentService.deriveSummary(avgScore, badFactors);
        // Restrict best-window search to the preferred hours (and always exclude post-sunset).
        List<HourlyForecastDTO> prefWindowDTOs     = new ArrayList<>();
        List<Integer>           prefWindowHourNums = new ArrayList<>();
        for (int i = 0; i < windowDTOs.size(); i++) {
            int h = windowHourNums.get(i);
            if (sunsetHour >= 0 && h > sunsetHour) continue;
            if (isAllDay(timePref) || (h >= prefRange[0] && h < prefRange[1])) {
                prefWindowDTOs.add(windowDTOs.get(i));
                prefWindowHourNums.add(h);
            }
        }
        String bestWindow = findBestWindow(prefWindowHourNums, prefWindowDTOs);

        // Set score=0 for post-sunset hours in the displayed hourly table.
        // The daily average was already calculated above without these hours.
        if (sunsetHour >= 0) {
            List<HourlyForecastDTO> penalized = new ArrayList<>(allHours.size());
            for (int i = 0; i < allHours.size(); i++) {
                HourlyForecastDTO dto = allHours.get(i);
                if (allHourNums.get(i) > sunsetHour) {
                    dto = new HourlyForecastDTO(
                            dto.time(), dto.temperature(), dto.windSpeed(), dto.windGust(),
                            dto.precipitation(), dto.isSixHour(),
                            "Solnedgang", 0, "Solen er gået ned",
                            List.of(), List.of(), dto.symbolCode());
                }
                penalized.add(dto);
            }
            allHours = penalized;
        }

        return new DailyGolfAssessmentDTO(
                dateStr, dayOfWeek, overallStatus, avgScore,
                summary, goodFactors, badFactors, bestWindow, allHours, sunsetTime);
    }

    // Sliding-window search over daytime entries (window = 4 hours or all entries if fewer).
    // Returns "HH:00–HH:00" for the block with the highest average score, or null if
    // the best block is below the minimum threshold (conditions not worth recommending).
    private String findBestWindow(List<Integer> hours, List<HourlyForecastDTO> dtos) {
        int n = hours.size();
        if (n < 3) return null;

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

    private static boolean isAllDay(String timePref) {
        return timePref == null || timePref.isBlank() || timePref.equalsIgnoreCase("all");
    }

    // Returns [startHour inclusive, endHour exclusive] for the preferred scoring window.
    private static int[] preferredRange(String timePref, int sunsetHour) {
        int effectiveSunset = sunsetHour >= 0 ? sunsetHour : 22;
        if (timePref == null) return new int[]{6, effectiveSunset};
        return switch (timePref.toLowerCase()) {
            case "morgen"      -> new int[]{6, 12};
            case "eftermiddag" -> new int[]{12, 18};
            case "aften"       -> new int[]{18, effectiveSunset};
            default            -> new int[]{6, effectiveSunset};
        };
    }

    private static String buildTemperatureLabel(double temp, List<String> badFactors) {
        if (temp >= 18 && badFactors.isEmpty()) return fmt("Perfekt temperatur (%.0f°C)", temp);
        if (temp >= 18) return fmt("God temperatur (%.0f°C)", temp);
        if (temp >= 14) return fmt("Fin temperatur (%.0f°C)", temp);
        if (temp >= 10) return fmt("Køligt vejr (%.0f°C)", temp);
        if (temp >= 6)  return fmt("Koldt vejr (%.0f°C)", temp);
        return fmt("For koldt til golf (%.0f°C)", temp);
    }

    // NOAA solar equations — accurate to within ~1 minute for Danish latitudes.
    // Returns "HH:mm" in Copenhagen time, or null on polar day/night.
    private String calculateSunset(double lat, double lon, LocalDate date) {
        // Julian Day for noon UT on the given date
        double jd = date.toEpochDay() + 2440588.0;

        // Julian centuries since J2000.0
        double t = (jd - 2451545.0) / 36525.0;

        // Geometric mean longitude of the sun (degrees)
        double l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360;

        // Geometric mean anomaly (degrees)
        double m    = 357.52911 + t * (35999.05029 - 0.0001537 * t);
        double mRad = Math.toRadians(m);

        // Equation of center
        double c = (1.914602 - t * (0.004817 + 0.000014 * t)) * Math.sin(mRad)
                 + (0.019993 - 0.000101 * t) * Math.sin(2 * mRad)
                 + 0.000289 * Math.sin(3 * mRad);

        // Apparent longitude (corrected for nutation and aberration)
        double omega  = 125.04 - 1934.136 * t;
        double lambda = (l0 + c) - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega));

        // Obliquity of the ecliptic (corrected)
        double epsilon = 23.0
                + (26.0 + (21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
                + 0.00256 * Math.cos(Math.toRadians(omega));

        // Solar declination
        double decl    = Math.toDegrees(Math.asin(
                Math.sin(Math.toRadians(epsilon)) * Math.sin(Math.toRadians(lambda))));

        // Equation of time (minutes)
        double y      = Math.pow(Math.tan(Math.toRadians(epsilon / 2.0)), 2);
        double eqTime = 4.0 * Math.toDegrees(
                  y * Math.sin(2 * Math.toRadians(l0))
                - 2 * 0.016708634 * Math.sin(mRad)
                + 4 * 0.016708634 * y * Math.sin(mRad) * Math.cos(2 * Math.toRadians(l0))
                - 0.5 * y * y * Math.sin(4 * Math.toRadians(l0))
                - 1.25 * 0.016708634 * 0.016708634 * Math.sin(2 * mRad));

        // Sunset hour angle — 90.833° accounts for atmospheric refraction + solar disc radius
        double latRad  = Math.toRadians(lat);
        double declRad = Math.toRadians(decl);
        double cosHa   = (Math.cos(Math.toRadians(90.833)) - Math.sin(latRad) * Math.sin(declRad))
                       / (Math.cos(latRad) * Math.cos(declRad));

        if (Math.abs(cosHa) > 1.0) return null; // polar day or polar night

        double ha = Math.toDegrees(Math.acos(cosHa));

        // Sunset in UTC minutes from midnight: solar noon + hour-angle offset
        double sunsetUtcMinutes = 720.0 - 4.0 * lon - eqTime + ha * 4.0;

        ZonedDateTime sunsetUtc   = date.atStartOfDay(ZoneOffset.UTC)
                                        .plusSeconds(Math.round(sunsetUtcMinutes * 60));
        ZonedDateTime sunsetLocal = sunsetUtc.withZoneSameInstant(COPENHAGEN_TZ);

        return String.format("%02d:%02d", sunsetLocal.getHour(), sunsetLocal.getMinute());
    }

    private static String fmt(String pattern, Object... args) {
        return String.format(java.util.Locale.ROOT, pattern, args);
    }

}

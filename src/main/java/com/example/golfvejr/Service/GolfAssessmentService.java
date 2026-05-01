package com.example.golfvejr.Service;

import com.example.golfvejr.DTO.HourlyForecastDTO;
import com.example.golfvejr.Model.TimeSeries;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class GolfAssessmentService {

    private static final ZoneId COPENHAGEN_TZ = ZoneId.of("Europe/Copenhagen");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // --- Score weights — base range is 0–70 (normalized to 0–100 after rain penalty) ---
    private static final int WEIGHT_TEMP = 35;
    private static final int WEIGHT_WIND = 35;

    // --- Temperature thresholds (°C) ---
    private static final double TEMP_OPTIMAL_LOW  = 18.0;
    private static final double TEMP_OPTIMAL_HIGH = 24.0;
    private static final double TEMP_GOOD_LOW     = 14.0;
    private static final double TEMP_PLAYABLE_LOW = 10.0;
    private static final double TEMP_POOR_LOW     =  6.0;

    // --- Wind speed thresholds (m/s) ---
    private static final double WIND_OPTIMAL  =  4.0;
    private static final double WIND_FINE     =  7.0;
    private static final double WIND_MODERATE = 10.0;
    private static final double WIND_STRONG   = 13.0;

    // --- Gust thresholds (m/s) ---
    private static final double GUST_MODERATE = 10.0;
    private static final double GUST_STRONG   = 14.0;

    // --- Precipitation thresholds (mm/h) ---
    private static final double PRECIP_DRY      = 0.05;
    private static final double PRECIP_LIGHT    = 0.15;
    private static final double PRECIP_MODERATE = 0.30;
    private static final double PRECIP_CAP      = 0.30; // hard cap threshold (known rain only)

    // --- Status cutoffs ---
    private static final int STATUS_GREEN  = 70;
    private static final int STATUS_YELLOW = 40;

    // --- Evening soft penalty (applied at hour >= 20) ---
    private static final int HOUR_EVENING    = 20;
    private static final int EVENING_PENALTY =  5;

    public HourlyForecastDTO assess(TimeSeries ts) {
        var zdt     = ts.getTime().withZoneSameInstant(COPENHAGEN_TZ);
        var instant = ts.getData().getInstant().getDetails();

        double temp  = instant.getAirTemperature();
        double wind  = instant.getWindSpeed();
        Double gustVal = instant.getWindSpeedOfGust();
        double gust  = (gustVal != null) ? gustVal : wind;

        double precipitation;      // mm/hour — used for scoring and stored in DTO
        boolean isSixHour = false;
        var data = ts.getData();

        if (data.getNext1Hours() != null && data.getNext1Hours().getDetails() != null) {
            precipitation = data.getNext1Hours().getDetails().getPrecipitationAmount();
        } else if (data.getNext6Hours() != null && data.getNext6Hours().getDetails() != null) {
            // Divide by 3 (not 6) for a worst-case hourly estimate from the 6h window.
            precipitation = data.getNext6Hours().getDetails().getPrecipitationAmount() / 3.0;
            isSixHour     = true;
        } else {
            precipitation = -1.0;          // no data — treat as uncertain rain
        }

        List<String> good = new ArrayList<>();
        List<String> bad  = new ArrayList<>();

        int tempScore   = scoreTemperature(temp, good, bad);
        int windScore   = scoreWind(wind, good, bad);
        windScore       = applyGustPenalty(windScore, gust, bad);
        windScore       = applyTemperatureWindModifier(windScore, temp);

        int baseScore   = tempScore + windScore;               // max = WEIGHT_TEMP + WEIGHT_WIND = 70
        int rainPenalty = calculateRainPenalty(precipitation, bad);
        int score       = Math.max(0, baseScore - rainPenalty);

        // Hard cap: only applies to known heavy rain — not when precipitation is unknown (-1).
        if (precipitation >= 0 && precipitation > PRECIP_CAP) {
            score = Math.min(score, 25);
        }

        // Soft penalty after 20:00 — still playable but slightly depressed score.
        if (zdt.getHour() >= HOUR_EVENING) {
            score = Math.max(0, score - EVENING_PENALTY);
        }

        // Normalize from 0–70 base range to 0–100.
        int normalizedScore = (int) Math.round((score / 70.0) * 100);

        String status  = deriveStatus(normalizedScore);
        String summary = deriveSummary(normalizedScore, bad);

        // 6h entries show the covered window, e.g. "14:00–20:00".
        String time;
        if (isSixHour) {
            int startH = zdt.getHour();
            int endH   = (startH + 6) % 24;
            time = String.format("%02d:00–%02d:00", startH, endH);
        } else {
            time = zdt.format(TIME_FORMATTER);
        }

        return new HourlyForecastDTO(time, temp, wind, gust, precipitation, isSixHour, status, normalizedScore, summary, good, bad);
    }

    // -- Scoring methods --

    private int scoreTemperature(double temp, List<String> good, List<String> bad) {
        if (temp >= TEMP_OPTIMAL_LOW && temp <= TEMP_OPTIMAL_HIGH) {
            good.add(fmt("Behagelig temperatur (%.0f°C)", temp));
            return WEIGHT_TEMP;                              // 100 %
        }
        if (temp > TEMP_OPTIMAL_HIGH) {
            good.add(fmt("Varm men spilbar (%.0f°C)", temp));
            return scale(WEIGHT_TEMP, 0.80);                // 80 %
        }
        if (temp >= TEMP_GOOD_LOW) {
            good.add(fmt("Acceptabel temperatur (%.0f°C)", temp));
            return scale(WEIGHT_TEMP, 0.75);                // 75 %
        }
        if (temp >= TEMP_PLAYABLE_LOW) {
            bad.add(fmt("Kølige temperaturer (%.0f°C)", temp));
            return scale(WEIGHT_TEMP, 0.45);                // 45 % — noticeable drop
        }
        if (temp >= TEMP_POOR_LOW) {
            bad.add(fmt("Koldt vejr (%.0f°C)", temp));
            return scale(WEIGHT_TEMP, 0.20);                // 20 % — cold
        }
        bad.add(fmt("For koldt til golf (%.0f°C)", temp));
        return 0;
    }

    private int scoreWind(double wind, List<String> good, List<String> bad) {
        if (wind <= WIND_OPTIMAL) {
            good.add(fmt("Svag vind (%.1f m/s)", wind));
            return WEIGHT_WIND;                              // 100 %
        }
        if (wind <= WIND_FINE) {
            good.add(fmt("Let brise (%.1f m/s)", wind));
            return scale(WEIGHT_WIND, 0.80);                // 80 %
        }
        if (wind <= WIND_MODERATE) {
            bad.add(fmt("Moderat vind (%.1f m/s)", wind));
            return scale(WEIGHT_WIND, 0.55);                // 55 %
        }
        if (wind <= WIND_STRONG) {
            bad.add(fmt("Kraftig vind (%.1f m/s)", wind));
            return scale(WEIGHT_WIND, 0.25);                // 25 %
        }
        bad.add(fmt("For kraftig vind til golf (%.1f m/s)", wind));
        return 0;
    }

    private int applyGustPenalty(int windScore, double gust, List<String> bad) {
        if (gust <= GUST_MODERATE) return windScore;
        if (gust <= GUST_STRONG) {
            bad.add(fmt("Vindstød op til %.1f m/s", gust));
            return scale(windScore, 0.75);                   // ~25 % reduction
        }
        bad.add(fmt("Kraftige vindstød (%.1f m/s)", gust));
        return scale(windScore, 0.50);                       // 50 % reduction
    }

    // On warm days the wind feels more tolerable: close 30 % of the gap to the
    // maximum wind score, so a breeze doesn't drag down an otherwise great summer day.
    private int applyTemperatureWindModifier(int windScore, double temp) {
        if (temp >= TEMP_OPTIMAL_LOW) {
            int deficit = WEIGHT_WIND - windScore;
            windScore += (int) Math.round(deficit * 0.30);
        }
        return windScore;
    }

    private int calculateRainPenalty(double precip, List<String> bad) {
        if (precip < 0) {
            bad.add("Mulighed for regn");
            return 15;
        }
        if (precip <= PRECIP_DRY) return 0;
        if (precip <= PRECIP_LIGHT) {
            bad.add(fmt("Let regn (%.1f mm/t)", precip));
            return 20;
        }
        if (precip <= PRECIP_MODERATE) {
            bad.add(fmt("Regn (%.1f mm/t)", precip));
            return 35;
        }
        bad.add(fmt("Regn forventet (%.1f mm/t)", precip));
        return 50;
    }

    // -- Shared helpers --

    public static String deriveStatus(int score) {
        if (score >= STATUS_GREEN)  return "GREEN";
        if (score >= STATUS_YELLOW) return "YELLOW";
        return "RED";
    }

    public static String deriveSummary(int score, List<String> badFactors) {
        if (score >= 85) return "Optimale golfforhold";
        if (score >= 70) return "Gode golfforhold";
        if (score >= 55) {
            return badFactors.isEmpty() ? "Spilbart golfvejr"
                    : "Spilbart, men " + shortLabel(badFactors.get(0));
        }
        if (score >= 40) {
            return badFactors.isEmpty() ? "Marginal golfvejr"
                    : "Marginal — " + shortLabel(badFactors.get(0));
        }
        if (badFactors.stream().anyMatch(f -> f.toLowerCase().startsWith("regn"))) {
            return "Ikke anbefalet – regn forventet";
        }
        if (score >= 20) return "Vanskelige golfforhold";
        return "Ikke egnet til golf";
    }

    private static String shortLabel(String factor) {
        int paren = factor.indexOf('(');
        String label = paren > 0 ? factor.substring(0, paren).trim() : factor;
        return label.toLowerCase();
    }

    private static int scale(int weight, double fraction) {
        return (int) Math.round(weight * fraction);
    }

    private static String fmt(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }
}

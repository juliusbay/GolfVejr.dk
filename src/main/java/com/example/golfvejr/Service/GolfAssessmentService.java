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

    // --- Score weights (must sum to 100) ---
    private static final int WEIGHT_TEMP   = 20;
    private static final int WEIGHT_WIND   = 40;
    private static final int WEIGHT_PRECIP = 40;

    // --- Temperature thresholds (°C) ---
    private static final double TEMP_IDEAL_LOW  = 15.0;
    private static final double TEMP_IDEAL_HIGH = 24.0;
    private static final double TEMP_OK_LOW     = 10.0;
    private static final double TEMP_OK_HIGH    = 28.0;
    private static final double TEMP_COLD_LOW   =  5.0;

    // --- Wind speed thresholds (m/s) ---
    private static final double WIND_IDEAL    =  5.0;
    private static final double WIND_MODERATE =  8.0;
    private static final double WIND_STRONG   = 12.0;

    // --- Gust thresholds (m/s) — aligned with wind thresholds ---
    private static final double GUST_MODERATE = 8.0;
    private static final double GUST_STRONG   = 12.0;

    // --- Precipitation thresholds (mm/h) ---
    private static final double PRECIP_TRACE      = 0.1;  // effectively dry
    private static final double PRECIP_ACCEPTABLE = 0.5;  // drizzle limit — anything above forces RED

    // --- Status cutoffs ---
    private static final int STATUS_GREEN  = 70;
    private static final int STATUS_YELLOW = 40;

    public HourlyForecastDTO assess(TimeSeries ts) {
        var instant = ts.getData().getInstant().getDetails();
        double temp = instant.getAirTemperature();
        double wind = instant.getWindSpeed();
        Double gustVal = instant.getWindSpeedOfGust();
        double gust = (gustVal != null) ? gustVal : wind;

        double precip = 0.0;
        if (ts.getData().getNext1Hours() != null
                && ts.getData().getNext1Hours().getDetails() != null) {
            precip = ts.getData().getNext1Hours().getDetails().getPrecipitationAmount();
        }

        List<String> good = new ArrayList<>();
        List<String> bad  = new ArrayList<>();

        int windScore = scoreWind(wind, good, bad);
        windScore     = applyGustPenalty(windScore, gust, bad);

        int score = scoreTemperature(temp, good, bad)
                  + windScore
                  + scorePrecipitation(precip, good, bad);

        // Hard rule: rain above drizzle level is always RED regardless of other conditions.
        if (precip > PRECIP_ACCEPTABLE) {
            score = Math.min(score, 35);
        }

        String status  = deriveStatus(score);
        String summary = deriveSummary(score, bad);
        String time    = ts.getTime().withZoneSameInstant(COPENHAGEN_TZ).format(TIME_FORMATTER);

        return new HourlyForecastDTO(time, temp, wind, gust, precip, status, score, summary, good, bad);
    }

    // -- Scoring methods --

    private int scoreTemperature(double temp, List<String> good, List<String> bad) {
        if (temp >= TEMP_IDEAL_LOW && temp <= TEMP_IDEAL_HIGH) {
            good.add(fmt("Behagelig temperatur (%.0f°C)", temp));
            return WEIGHT_TEMP;
        }
        if (temp >= TEMP_OK_LOW && temp < TEMP_IDEAL_LOW) {
            good.add(fmt("Acceptabel temperatur (%.0f°C)", temp));
            return scale(WEIGHT_TEMP, 0.70);
        }
        if (temp > TEMP_IDEAL_HIGH && temp <= TEMP_OK_HIGH) {
            good.add(fmt("Varm men spilbar (%.0f°C)", temp));
            return scale(WEIGHT_TEMP, 0.70);
        }
        if (temp >= TEMP_COLD_LOW) {
            bad.add(fmt("Kølige temperaturer (%.0f°C)", temp));
            return scale(WEIGHT_TEMP, 0.40);
        }
        bad.add(fmt("For koldt til golf (%.0f°C)", temp));
        return 0;
    }

    private int scoreWind(double wind, List<String> good, List<String> bad) {
        if (wind < WIND_IDEAL) {
            good.add(fmt("Svag vind (%.1f m/s)", wind));
            return WEIGHT_WIND;
        }
        if (wind < WIND_MODERATE) {
            bad.add(fmt("Moderat vind (%.1f m/s)", wind));
            return scale(WEIGHT_WIND, 0.75);
        }
        if (wind < WIND_STRONG) {
            bad.add(fmt("Kraftig vind (%.1f m/s)", wind));
            return scale(WEIGHT_WIND, 0.40);
        }
        bad.add(fmt("For kraftig vind til golf (%.1f m/s)", wind));
        return 0;
    }

    private int applyGustPenalty(int windScore, double gust, List<String> bad) {
        if (gust < GUST_MODERATE) return windScore;
        if (gust < GUST_STRONG) {
            bad.add(fmt("Vindstød (%.1f m/s)", gust));
            return scale(windScore, 0.75);
        }
        bad.add(fmt("Kraftige vindstød (%.1f m/s)", gust));
        return scale(windScore, 0.40);
    }

    private int scorePrecipitation(double precip, List<String> good, List<String> bad) {
        if (precip <= PRECIP_TRACE) {
            good.add("Tørre forhold");
            return WEIGHT_PRECIP;
        }
        if (precip <= PRECIP_ACCEPTABLE) {
            good.add(fmt("Let dryp (%.1f mm/t)", precip));
            return scale(WEIGHT_PRECIP, 0.70);
        }
        // Above PRECIP_ACCEPTABLE — status will be forced to RED in assess()
        bad.add(fmt("Regn forventet (%.1f mm/t)", precip));
        return 0;
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
        // RED zone — check if rain is the primary driver
        if (badFactors.stream().anyMatch(f -> f.toLowerCase().startsWith("regn"))) {
            return "Ikke anbefalet – regn forventet";
        }
        if (score >= 20) return "Vanskelige golfforhold";
        return "Ikke egnet til golf";
    }

    // Strips the parenthetical value so "Kraftig vind (9.5 m/s)" → "kraftig vind"
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

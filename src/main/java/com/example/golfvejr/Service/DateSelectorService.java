package com.example.golfvejr.Service;

import com.example.golfvejr.Model.TimeSeries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class DateSelectorService {
    private final YrApiService yrApiService;

    // Mangler variable til at modtage koordinater
    public List<TimeSeries> getForecastForDate(LocalDateTime date){
        List<TimeSeries> completeForecast = yrApiService.getForecast(55.67594, 12.56553).getProperties().getTimeSeries();
        List<TimeSeries> timeSeriesForDate = new ArrayList<>();

        for (TimeSeries t : completeForecast) {
            if (t.getTime().toLocalDate().equals(date.toLocalDate())) {
                timeSeriesForDate.add(t);
            }
        }

        return timeSeriesForDate;
    }

    // Mangler variable til at modtage koordinater
    public List<TimeSeries> getForecastForDateAndLocation(LocalDateTime date, double lat, double lon){
        List<TimeSeries> completeForecast = yrApiService.getForecast(lat, lon).getProperties().getTimeSeries();
        List<TimeSeries> timeSeriesForDate = new ArrayList<>();

        for (TimeSeries t : completeForecast) {
            if (t.getTime().toLocalDate().equals(date.toLocalDate())) {
                timeSeriesForDate.add(t);
            }
        }

        return timeSeriesForDate;
    }

    public TimeSeries getForecastForHour(LocalDateTime dateAndTime){
        List<TimeSeries> timeSeriesForDate = getForecastForDate(dateAndTime);
        TimeSeries timeEntry = new TimeSeries();

        for (TimeSeries t : timeSeriesForDate) {
            if (t.getTime().getHour() == dateAndTime.getHour()) {
                timeEntry = t;
            }
        }

        return timeEntry;
    }

}

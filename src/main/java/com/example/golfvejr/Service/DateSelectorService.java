package com.example.golfvejr.Service;

import com.example.golfvejr.Model.Details;
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

    public String golfingConditions(Details hour){

        if (hour.getPrecipitationAmount() >= 1.5){
            return "Står ned i stænger";
        }

        if (hour.getAirTemperature() < 5){
            return "Super koldt";
        }

        if (hour.getWindSpeed() >= 12){
            return "Blæser en halv pelikan";
        }

        if (hour.getPrecipitationAmount() < 1 && hour.getPrecipitationAmount() > 0 ||
                hour.getWindSpeed() < 12 && hour.getWindSpeed() > 6 ||
                hour.getAirTemperature() >= 5 &&  hour.getAirTemperature() < 15){
            return "Gul";
        }

        return "Grøn";
    }

}

package com.example.golfvejr.Controller;

import com.example.golfvejr.Model.CompleteForecast;
import com.example.golfvejr.Model.TimeSeries;
import com.example.golfvejr.Service.DateSelectorService;
import com.example.golfvejr.Service.YrApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Controller
public class ForecastController {

    private final YrApiService yrApiService;
    private final DateSelectorService dateSelectorService;


    // Koordinater skal opdateres til en @requestParam og dropdown menu for hver golfklub. Pt. er koordinater København
    @GetMapping("/")
    public String getForecast(Model model) {
        List<List<TimeSeries>> forecastNext10Days = new ArrayList<>();
        LocalDateTime dateAndTime = LocalDateTime.now();

        for (int i = 0; i < 9; i++){
            List<TimeSeries> oneDayForecast = dateSelectorService.getForecastForDate(dateAndTime.plusDays(i));
            forecastNext10Days.add(oneDayForecast);
        }

        model.addAttribute("forecastNext10Days", forecastNext10Days);

        return "index";
    }
}

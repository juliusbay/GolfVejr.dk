package com.example.golfvejr.Controller;

import com.example.golfvejr.Model.CompleteForecast;
import com.example.golfvejr.Model.TimeSeries;
import com.example.golfvejr.Service.YrApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@RequiredArgsConstructor
@Controller
public class ForecastController {

    private final YrApiService yrApiService;


    // Koordinater skal opdateres til en @requestParam og dropdown menu for hver golfklub. Pt. er koordinater København
    @GetMapping("/golfvejr")
    public String getForecast(Model model) {
        CompleteForecast forecast = yrApiService.getForecast(60.10, 9.58);
        List<TimeSeries> timeEntries = forecast.getProperties().getTimeSeries();

        model.addAttribute("forecast", forecast);
        model.addAttribute("timeEntries", timeEntries);

        return "index";
    }
}

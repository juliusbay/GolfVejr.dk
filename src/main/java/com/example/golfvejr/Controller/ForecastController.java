package com.example.golfvejr.Controller;

import com.example.golfvejr.Model.Details;
import com.example.golfvejr.Model.Golfclub;
import com.example.golfvejr.Model.TimeSeries;
import com.example.golfvejr.Service.DateSelectorService;
import com.example.golfvejr.Service.GolfClubService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Controller
public class ForecastController {

    private final DateSelectorService dateSelectorService;
    private final GolfClubService golfClubService;

    @GetMapping("/")
    public String showFrontPage(Model model){
        List<Golfclub> golfClubs = golfClubService.getAllClubs();
        model.addAttribute("golfClubs", golfClubs);
        return "index";
    }

    @PostMapping("/")
    public String chooseClub(@RequestParam("clubId") Long id) {
        return "redirect:/forecast/" + id;
    }

    @GetMapping("/forecast/{id}")
    public String getForecastForLocation(Model model, @PathVariable Long id, @RequestParam(value = "date", required = false) LocalDateTime date) {
        Golfclub club = golfClubService.getClubById(id);
        LocalDateTime now = LocalDateTime.now();

        List<List<TimeSeries>> forecastNext10Days = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            List<TimeSeries> oneDay = dateSelectorService
                    .getForecastForDateAndLocation(now.plusDays(i), club.getLatitude(), club.getLongitude());
            for (var hourEntry : oneDay) {
                Details forecastForHour = hourEntry.getData().getInstant().getDetails();
                forecastForHour.setStatus(dateSelectorService.golfingConditions(forecastForHour));
            }

            forecastNext10Days.add(oneDay);
        }

        /*
        Gemmer denne, hvis man ønsker at vælge for specifik dato
        List<TimeSeries> forecastForDate = dateSelectorService.getForecastForDateAndLocation(date, club.getLatitude(), club.getLongitude());
        */

        model.addAttribute("club", club);
        model.addAttribute("forecastNext10Days", forecastNext10Days);
        return "forecast";
    }

    // Koordinater skal opdateres til en @requestParam og dropdown menu for hver golfklub. Pt. er koordinater København
    @GetMapping("/forecast-copenhagen")
    public String getForecastCopenhagen(Model model) {
        List<List<TimeSeries>> forecastNext10Days = new ArrayList<>();
        LocalDateTime dateAndTime = LocalDateTime.now();
        List<Golfclub> golfClubs = golfClubService.getAllClubs();

        for (int i = 0; i < 9; i++){
            List<TimeSeries> oneDayForecast = dateSelectorService.getForecastForDate(dateAndTime.plusDays(i));
            forecastNext10Days.add(oneDayForecast);
        }

        model.addAttribute("forecastNext10Days", forecastNext10Days);
        model.addAttribute("golfClubs", golfClubs);

        return "forecast";
    }
}

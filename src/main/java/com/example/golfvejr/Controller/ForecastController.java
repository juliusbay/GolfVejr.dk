package com.example.golfvejr.Controller;

import com.example.golfvejr.Model.GolfClub;
import com.example.golfvejr.Model.TimeSeries;
import com.example.golfvejr.Service.DateSelectorService;
import com.example.golfvejr.Service.GolfClubService;
import com.example.golfvejr.Service.YrApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
        List<GolfClub> golfClubs = golfClubService.getAllClubs();
        model.addAttribute("golfClubs", golfClubs);
        return "index";
    }

    @PostMapping("/")
    public String chooseClub(@RequestParam("clubId") Long id) {
        return "redirect:/forecast/" + id;
    }

    @GetMapping("/forecast/{id}")
    public String getForeCastForLocation(@RequestParam Long id, Model model) {
        GolfClub club = golfClubService.getClubById(id);
        LocalDateTime now = LocalDateTime.now();

        List<List<TimeSeries>> forecastNext10Days = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            List<TimeSeries> oneDay = dateSelectorService
                    .getForecastForDateAndLocation(now.plusDays(i), club.getLatitude(), club.getLongitude());
            forecastNext10Days.add(oneDay);
        }

        model.addAttribute("club", club);
        model.addAttribute("forecastNext10Days", forecastNext10Days);
        return "forecast";
    }



    // Koordinater skal opdateres til en @requestParam og dropdown menu for hver golfklub. Pt. er koordinater København
    @GetMapping("/forecast-copenhagen")
    public String getForecastCopenhagen(Model model) {
        List<List<TimeSeries>> forecastNext10Days = new ArrayList<>();
        LocalDateTime dateAndTime = LocalDateTime.now();
        List<GolfClub> golfClubs = golfClubService.getAllClubs();

        for (int i = 0; i < 9; i++){
            List<TimeSeries> oneDayForecast = dateSelectorService.getForecastForDate(dateAndTime.plusDays(i));
            forecastNext10Days.add(oneDayForecast);
        }

        model.addAttribute("forecastNext10Days", forecastNext10Days);
        model.addAttribute("golfClubs", golfClubs);

        return "forecast";
    }
}

package com.example.golfvejr.Config;

import com.example.golfvejr.Model.Golfclub;
import com.example.golfvejr.Service.GolfClubService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class InitData implements CommandLineRunner {

    private final GolfClubService golfClubService;

    @Override
    public void run(String... args){

        Golfclub g1 = new Golfclub("Smørum Golfklub", 55.72877216085993, 12.30621635368797);
        Golfclub g2 = new Golfclub("Hvide Klit Golfklub", 57.62259714997986, 10.427879157481623);

        golfClubService.saveClub(g1);
        golfClubService.saveClub(g2);
    }

}

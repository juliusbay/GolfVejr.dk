package com.example.golfvejr.Config;

import com.example.golfvejr.Service.GolfClubImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class InitData implements CommandLineRunner {

    private final GolfClubImportService golfClubImportService;

    @Override
    public void run(String... args) {
        golfClubImportService.importGolfClubs();
    }
}

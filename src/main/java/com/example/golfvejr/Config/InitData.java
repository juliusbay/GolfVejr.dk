package com.example.golfvejr.Config;

import com.example.golfvejr.Repository.GolfClubRepository;
import com.example.golfvejr.Service.GolfClubImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class InitData implements CommandLineRunner {

    private final GolfClubImportService golfClubImportService;
    private final GolfClubRepository golfClubRepository;

    @Override
    public void run(String... args) {
        long count = golfClubRepository.count();
        if (count > 0) {
            log.info("Database already contains {} golf clubs — skipping Overpass import.", count);
            return;
        }
        golfClubImportService.importGolfClubs();
    }
}

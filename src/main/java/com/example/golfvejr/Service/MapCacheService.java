package com.example.golfvejr.Service;

import com.example.golfvejr.DTO.DailyGolfAssessmentDTO;
import com.example.golfvejr.DTO.MapMarkerDTO;
import com.example.golfvejr.Model.Golfclub;
import com.example.golfvejr.Repository.GolfClubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapCacheService {

    private final GolfClubRepository golfClubRepository;
    private final ForecastService forecastService;

    private volatile List<MapMarkerDTO> cache = Collections.emptyList();

    // Fire a background build immediately after the app is fully started.
    // .exceptionally() ensures any unchecked exception is logged rather than silently dropped.
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        CompletableFuture.runAsync(this::refreshCache)
                .exceptionally(ex -> {
                    log.error("Map cache startup build failed: {}", ex.getMessage(), ex);
                    return null;
                });
    }

    // Scheduled rebuild every 2 hours, measured from the end of the previous run.
    @Scheduled(fixedDelay = 2 * 60 * 60 * 1000L)
    public void refreshCache() {
        List<Golfclub> clubs = golfClubRepository.findAllByOrderByNameAsc();
        log.info("Starting map cache build for {} clubs...", clubs.size());

        List<MapMarkerDTO> result = new ArrayList<>();
        int skipped = 0;

        for (Golfclub club : clubs) {
            try {
                List<DailyGolfAssessmentDTO> forecast =
                        forecastService.getForecastForClub(club.getLatitude(), club.getLongitude());
                if (!forecast.isEmpty()) {
                    DailyGolfAssessmentDTO today = forecast.get(0);
                    result.add(new MapMarkerDTO(
                            club.getId(), club.getName(),
                            club.getLatitude(), club.getLongitude(),
                            today.overallStatus(), today.score(),
                            club.getStreet(), club.getCity(),
                            club.getWebsite(), club.getPhone()));
                }
            } catch (Exception e) {
                log.warn("Failed to fetch forecast for club '{}' (id={}): {}",
                        club.getName(), club.getId(), e.getMessage());
                skipped++;
            } finally {
                // Always sleep after every club — even failed ones — to protect the IP.
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Map cache build interrupted after {}/{} clubs ({} skipped)",
                            result.size(), clubs.size(), skipped);
                    return;
                }
            }
        }

        cache = Collections.unmodifiableList(result);
        log.info("Map cache build complete — {} markers cached, {} clubs skipped.", result.size(), skipped);
    }

    public List<MapMarkerDTO> getCachedMarkers() {
        return cache;
    }
}

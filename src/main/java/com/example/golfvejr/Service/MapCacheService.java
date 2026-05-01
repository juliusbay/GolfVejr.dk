package com.example.golfvejr.Service;

import com.example.golfvejr.DTO.DailyGolfAssessmentDTO;
import com.example.golfvejr.DTO.MapMarkerDTO;
import com.example.golfvejr.Model.Golfclub;
import com.example.golfvejr.Repository.GolfClubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class MapCacheService {

    private final GolfClubRepository golfClubRepository;
    private final ForecastService forecastService;

    private volatile List<MapMarkerDTO> cache = Collections.emptyList();

    // Trigger a background refresh after the application context is fully ready
    // so the HTTP server is up and the DB is populated before we start polling.
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        CompletableFuture.runAsync(this::refreshCache);
    }

    // Refresh every 2 hours (delay measured from the end of the previous run).
    @Scheduled(fixedDelay = 2 * 60 * 60 * 1000L)
    public void refreshCache() {
        List<Golfclub> clubs = golfClubRepository.findAllByOrderByNameAsc();
        List<MapMarkerDTO> result = new ArrayList<>();

        for (Golfclub club : clubs) {
            try {
                List<DailyGolfAssessmentDTO> forecast =
                        forecastService.getForecastForClub(club.getLatitude(), club.getLongitude());
                if (!forecast.isEmpty()) {
                    DailyGolfAssessmentDTO today = forecast.get(0);
                    result.add(new MapMarkerDTO(
                            club.getId(), club.getName(),
                            club.getLatitude(), club.getLongitude(),
                            today.overallStatus(), today.score()));
                }
            } catch (Exception ignored) {
                // Skip clubs whose forecast fails; they simply won't appear on the map.
            }

            // Strict rate-limit courtesy sleep between every Yr API call.
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return; // Abort without replacing a good cache with partial data.
            }
        }

        cache = Collections.unmodifiableList(result);
    }

    public List<MapMarkerDTO> getCachedMarkers() {
        return cache;
    }
}

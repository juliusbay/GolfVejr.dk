package com.example.golfvejr.Service;

import com.example.golfvejr.Model.Golfclub;
import com.example.golfvejr.Model.OverpassCenter;
import com.example.golfvejr.Model.OverpassElement;
import com.example.golfvejr.Model.OverpassResponse;
import com.example.golfvejr.Repository.GolfClubRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class GolfClubImportService {

    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    // area["ISO3166-1"="DK"] uses Denmark's actual OSM boundary, not a bounding box.
    // A bounding box would include parts of Sweden and Germany.
    private static final String OVERPASS_QUERY =
            "[out:json][timeout:25];\n" +
            "area[\"ISO3166-1\"=\"DK\"]->.denmark;\n" +
            "(\n" +
            "  way[\"leisure\"=\"golf_course\"](area.denmark);\n" +
            "  relation[\"leisure\"=\"golf_course\"](area.denmark);\n" +
            ");\n" +
            "out center;";

    private final RestTemplate restTemplate;
    private final GolfClubRepository golfClubRepository;

    public GolfClubImportService(RestTemplateBuilder builder, GolfClubRepository golfClubRepository) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .build();
        this.golfClubRepository = golfClubRepository;
    }

    public int importGolfClubs() {
        log.info("Starting golf club import from OpenStreetMap...");

        List<OverpassElement> elements = fetchFromOverpass();
        if (elements.isEmpty()) {
            log.warn("No elements returned from Overpass API — import skipped.");
            return 0;
        }

        int saved = 0;
        for (OverpassElement element : elements) {
            String name = extractName(element);
            if (name == null) {
                log.debug("Skipping unnamed element id={}", element.getId());
                continue;
            }

            if (!isDanishByTag(element)) {
                log.warn("Skipping '{}': addr:country tag indicates non-Danish club", name);
                continue;
            }

            if (golfClubRepository.existsByName(name)) {
                log.debug("Skipping duplicate: {}", name);
                continue;
            }

            double lat = extractLat(element);
            double lon = extractLon(element);

            if (!hasValidCoordinates(lat, lon)) {
                log.warn("Skipping '{}': missing or zero coordinates", name);
                continue;
            }

            golfClubRepository.save(new Golfclub(name, lat, lon));
            saved++;
        }

        log.info("Import complete. Saved {} golf clubs (skipped duplicates/invalid).", saved);
        return saved;
    }

    private List<OverpassElement> fetchFromOverpass() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("User-Agent", "GolfVejrApp/1.0");

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("data", OVERPASS_QUERY);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<OverpassResponse> response = restTemplate.exchange(
                    OVERPASS_URL, HttpMethod.POST, entity, OverpassResponse.class);

            if (response.getBody() == null || response.getBody().getElements() == null) {
                log.warn("Overpass API returned an empty body.");
                return List.of();
            }

            log.info("Overpass returned {} elements.", response.getBody().getElements().size());
            return response.getBody().getElements();

        } catch (Exception e) {
            log.error("Failed to reach Overpass API: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractName(OverpassElement element) {
        if (element.getTags() == null) return null;
        String name = element.getTags().get("name");
        if (name == null || name.isBlank()) return null;
        return name.trim();
    }

    // Ways and relations carry center coordinates; nodes carry lat/lon directly.
    private double extractLat(OverpassElement element) {
        OverpassCenter center = element.getCenter();
        if (center != null && center.getLat() != null) return center.getLat();
        return element.getLat() != null ? element.getLat() : 0.0;
    }

    private double extractLon(OverpassElement element) {
        OverpassCenter center = element.getCenter();
        if (center != null && center.getLon() != null) return center.getLon();
        return element.getLon() != null ? element.getLon() : 0.0;
    }

    // Secondary defence: if addr:country is present it must be Danish.
    // If the tag is absent we trust the Overpass area filter.
    private boolean isDanishByTag(OverpassElement element) {
        if (element.getTags() == null) return true;
        String country = element.getTags().get("addr:country");
        if (country == null || country.isBlank()) return true;
        String c = country.trim();
        return c.equalsIgnoreCase("DK")
                || c.equalsIgnoreCase("Danmark")
                || c.equalsIgnoreCase("Denmark");
    }

    private boolean hasValidCoordinates(double lat, double lon) {
        return lat != 0.0 && lon != 0.0;
    }
}

package com.example.golfvejr.Service;

import com.example.golfvejr.Exception.WeatherApiException;
import com.example.golfvejr.Model.CompleteForecast;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class YrApiService {

    private static final long TTL_MS = 45 * 60 * 1000L; // 45 minutes

    private record CacheEntry(CompleteForecast forecast, long fetchedAt) {}
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate;
    private final String userAgent;

    public YrApiService(RestTemplateBuilder builder,
                        @Value("${api.met-norway.user-agent}") String userAgent) {
        this.restTemplate = builder.build();
        this.userAgent    = userAgent;
    }

    public CompleteForecast getForecast(double lat, double lon) {
        String key = String.format("%.3f,%.3f", lat, lon);
        CacheEntry hit = cache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.fetchedAt() < TTL_MS) {
            log.debug("Forecast cache hit for {}", key);
            return hit.forecast();
        }

        String url = String.format(
                "https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=%s&lon=%s", lat, lon);

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", userAgent);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<CompleteForecast> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, CompleteForecast.class);

            if (response.getBody() == null) {
                throw new WeatherApiException("Vejr-API returnerede tomt svar");
            }
            cache.put(key, new CacheEntry(response.getBody(), System.currentTimeMillis()));
            return response.getBody();
        } catch (WeatherApiException e) {
            throw e;
        } catch (Exception e) {
            throw new WeatherApiException("Kunne ikke hente vejrdata fra API", e);
        }
    }
}

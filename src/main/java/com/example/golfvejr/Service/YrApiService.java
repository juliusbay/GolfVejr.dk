package com.example.golfvejr.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class YrApiService {

    private final RestTemplate restTemplate;

    public YrApiService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    // Parametre er latitude og longitude koordinater
    public String getForecast(double lat, double lon) {
        String url = String.format("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%s&lon=%s", lat, lon);

        HttpHeaders headers = new HttpHeaders();

        // Header kræver ikke API, men der skal være en identifier/user-agent
        headers.set("User-Agent", "GolfVejrApp/1.0 ");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
        );

        return response.getBody();
    }
}
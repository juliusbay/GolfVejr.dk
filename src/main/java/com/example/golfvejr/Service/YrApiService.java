package com.example.golfvejr.Service;

import com.example.golfvejr.Exception.WeatherApiException;
import com.example.golfvejr.Model.CompleteForecast;
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

    public CompleteForecast getForecast(double lat, double lon) {
        String url = String.format(
                "https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=%s&lon=%s", lat, lon);

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "GolfVejrApp/1.0");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<CompleteForecast> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, CompleteForecast.class);

            if (response.getBody() == null) {
                throw new WeatherApiException("Vejr-API returnerede tomt svar");
            }
            return response.getBody();
        } catch (WeatherApiException e) {
            throw e;
        } catch (Exception e) {
            throw new WeatherApiException("Kunne ikke hente vejrdata fra API", e);
        }
    }
}

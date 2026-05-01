package com.example.golfvejr.Controller;

import com.example.golfvejr.DTO.MapMarkerDTO;
import com.example.golfvejr.Service.MapCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/map-data")
@RequiredArgsConstructor
public class MapController {

    private final MapCacheService mapCacheService;

    @GetMapping
    public List<MapMarkerDTO> getMapData() {
        return mapCacheService.getCachedMarkers();
    }
}

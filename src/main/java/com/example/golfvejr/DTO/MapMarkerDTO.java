package com.example.golfvejr.DTO;

public record MapMarkerDTO(
        Long id,
        String name,
        double lat,
        double lon,
        String status,
        int score
) {}

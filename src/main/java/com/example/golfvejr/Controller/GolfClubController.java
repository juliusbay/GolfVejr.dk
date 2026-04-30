package com.example.golfvejr.Controller;

import com.example.golfvejr.DTO.GolfClubDTO;
import com.example.golfvejr.Service.GolfClubService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/golfclubs")
@RequiredArgsConstructor
public class GolfClubController {

    private final GolfClubService golfClubService;

    @GetMapping
    public List<GolfClubDTO> getAllClubs() {
        return golfClubService.getAllClubs().stream()
                .map(c -> new GolfClubDTO(c.getId(), c.getName(), c.getLatitude(), c.getLongitude()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public GolfClubDTO getClubById(@PathVariable Long id) {
        var c = golfClubService.getClubById(id);
        return new GolfClubDTO(c.getId(), c.getName(), c.getLatitude(), c.getLongitude());
    }
}

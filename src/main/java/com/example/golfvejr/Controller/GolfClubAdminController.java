package com.example.golfvejr.Controller;

import com.example.golfvejr.Service.GolfClubImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class GolfClubAdminController {

    private final GolfClubImportService golfClubImportService;

    @PostMapping("/import-golfclubs")
    public ResponseEntity<Map<String, Object>> importGolfClubs() {
        int count = golfClubImportService.importGolfClubs();
        return ResponseEntity.ok(Map.of(
                "message", "Import gennemført",
                "imported", count
        ));
    }
}

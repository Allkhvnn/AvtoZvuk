package AvtoZvuk.kz.api;

import AvtoZvuk.kz.common.Database;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final Database database;

    @GetMapping("/api/health")
    Map<String, String> health() {
        database.one("SELECT 1 AS value");
        return Map.of("status", "UP");
    }
}


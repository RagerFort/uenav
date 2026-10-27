package com.uenav.server;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Server is up. Does not touch the database. */
    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    /** Runs SELECT 1 against MySQL. 503 if the database is unreachable. */
    @GetMapping("/db")
    public ResponseEntity<Map<String, String>> db() {
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            String name = jdbc.queryForObject("SELECT DATABASE()", String.class);
            if (one != null && one == 1) {
                return ResponseEntity.ok(Map.of("status", "UP", "database", String.valueOf(name)));
            }
            return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of("status", "DOWN", "error", e.getClass().getSimpleName()));
        }
    }
}

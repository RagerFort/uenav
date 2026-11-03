package com.uenav.server.snapshot;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/snapshot")
public class SnapshotController {

    private final SnapshotService service;

    public SnapshotController(SnapshotService service) {
        this.service = service;
    }

    /** The whole map: buildings, floors, nodes, edges, plus a version string. */
    @GetMapping
    public Snapshot snapshot() {
        return service.snapshot();
    }

    /** Just the version, so the phone can check cheaply whether its copy is out of date. */
    @GetMapping("/version")
    public Map<String, String> version() {
        return Map.of("version", service.version());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> databaseDown(DataAccessException e) {
        return ResponseEntity.status(503).body(Map.of("status", "DOWN", "error", "database unavailable"));
    }
}

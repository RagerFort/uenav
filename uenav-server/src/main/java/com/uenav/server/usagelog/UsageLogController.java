package com.uenav.server.usagelog;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the phone sends its saved searches and feedback. Visitors do not log in, so this address is open
 * (it only accepts checked log entries; it cannot read anything).
 */
@RestController
@RequestMapping("/api/usage")
public class UsageLogController {

    private final UsageLogService service;

    public UsageLogController(UsageLogService service) {
        this.service = service;
    }

    @PostMapping
    public Usage.IngestResult send(@RequestBody Usage.Batch batch) {
        return service.ingest(batch == null ? null : batch.entries());
    }

    @ExceptionHandler(UsageLogService.BadRequestException.class)
    public ResponseEntity<Map<String, String>> bad(UsageLogService.BadRequestException e) {
        return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> databaseDown(DataAccessException e) {
        return ResponseEntity.status(503).body(Map.of("error", "database unavailable"));
    }
}

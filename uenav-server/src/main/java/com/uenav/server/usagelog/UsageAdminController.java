package com.uenav.server.usagelog;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin reading of the usage log. Everything under /api/admin is behind the admin login. */
@RestController
@RequestMapping("/api/admin")
public class UsageAdminController {

    public record StatusChange(String status) { }

    private final UsageLogService service;

    public UsageAdminController(UsageLogService service) {
        this.service = service;
    }

    @GetMapping("/usage/summary")
    public Usage.Summary summary() {
        return service.summary();
    }

    @GetMapping("/usage/most-searched")
    public List<Usage.TopPlace> mostSearched(@RequestParam(name = "limit", defaultValue = "5") int limit) {
        return service.mostSearched(limit);
    }

    @GetMapping("/usage/recent")
    public List<Usage.Event> recent(@RequestParam(name = "limit", defaultValue = "10") int limit) {
        return service.recent(limit);
    }

    @GetMapping("/usage/export.csv")
    public ResponseEntity<String> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"uenav-usage-log.csv\"")
                .body(service.exportCsv());
    }

    @GetMapping("/reports")
    public List<Usage.Event> reports() {
        return service.reports();
    }

    @PutMapping("/reports/{id}/status")
    public Map<String, String> setStatus(@PathVariable("id") int id, @RequestBody StatusChange change) {
        service.setReportStatus(id, change.status());
        return Map.of("status", "updated");
    }

    @ExceptionHandler(UsageLogService.BadRequestException.class)
    public ResponseEntity<Map<String, String>> bad(UsageLogService.BadRequestException e) {
        return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UsageLogService.NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(UsageLogService.NotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> databaseDown(DataAccessException e) {
        return ResponseEntity.status(503).body(Map.of("error", "database unavailable"));
    }
}

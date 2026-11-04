package com.uenav.server.usagelog;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Checks and stores usage log entries from the phone, and reads them back for the admin. */
@Service
public class UsageLogService {

    static final int MAX_BATCH = 200;
    static final int MAX_NOTE = 255;
    static final Set<String> EVENT_TYPES = Set.of("ROUTE", "FEEDBACK");
    static final Set<String> ROUTE_PREFS = Set.of("ANY", "STAIRS", "ELEVATOR");
    static final Set<String> FEEDBACK_TYPES = Set.of("HELPFUL", "WRONG_ROUTE");
    static final Set<String> REPORT_STATUSES = Set.of("NEW", "REVIEWED");

    /** The request is not acceptable. Becomes HTTP 400. */
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    /** The record does not exist. Becomes HTTP 404. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    private final UsageLogRepository repo;
    private final Clock clock;

    @Autowired
    public UsageLogService(UsageLogRepository repo) {
        this(repo, Clock.systemUTC());
    }

    UsageLogService(UsageLogRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** Stores every valid entry. A bad entry does not stop the others; it is listed in the result. */
    public Usage.IngestResult ingest(List<Usage.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new BadRequestException("No entries were sent");
        }
        if (entries.size() > MAX_BATCH) {
            throw new BadRequestException("Send at most " + MAX_BATCH + " entries at a time");
        }
        int accepted = 0;
        int duplicates = 0;
        List<Usage.Rejected> rejected = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            try {
                Usage.Row row = check(entries.get(i));
                if (repo.insert(row)) {
                    accepted++;
                } else {
                    duplicates++;
                }
            } catch (BadRequestException e) {
                rejected.add(new Usage.Rejected(i, e.getMessage()));
            }
        }
        return new Usage.IngestResult(accepted, duplicates, rejected);
    }

    private Usage.Row check(Usage.Entry e) {
        if (e == null) {
            throw new BadRequestException("Empty entry");
        }
        String id = e.clientLogId() == null ? "" : e.clientLogId().trim();
        try {
            id = UUID.fromString(id).toString();
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("clientLogId must be a UUID");
        }
        String type = upper(e.eventType());
        if (!EVENT_TYPES.contains(type)) {
            throw new BadRequestException("eventType must be ROUTE or FEEDBACK");
        }
        if (e.fromNodeId() == null || e.toNodeId() == null) {
            throw new BadRequestException("fromNodeId and toNodeId are required");
        }
        if (e.fromNodeId().equals(e.toNodeId())) {
            throw new BadRequestException("fromNodeId and toNodeId must be different");
        }
        if (!repo.nodeExists(e.fromNodeId()) || !repo.nodeExists(e.toNodeId())) {
            throw new BadRequestException("Unknown place in fromNodeId or toNodeId");
        }
        String pref = e.routePref() == null || e.routePref().isBlank() ? "ANY" : upper(e.routePref());
        if (!ROUTE_PREFS.contains(pref)) {
            throw new BadRequestException("routePref must be ANY, STAIRS or ELEVATOR");
        }
        String feedback = null;
        String status = null;
        if (type.equals("FEEDBACK")) {
            feedback = upper(e.feedbackType());
            if (!FEEDBACK_TYPES.contains(feedback)) {
                throw new BadRequestException("feedbackType must be HELPFUL or WRONG_ROUTE");
            }
            status = "NEW";
        } else if (e.feedbackType() != null && !e.feedbackType().isBlank()) {
            throw new BadRequestException("feedbackType is only allowed on FEEDBACK entries");
        }
        String note = e.note() == null || e.note().isBlank() ? null : e.note().trim();
        if (note != null && note.length() > MAX_NOTE) {
            throw new BadRequestException("note must be at most " + MAX_NOTE + " characters");
        }
        return new Usage.Row(id, when(e.createdAt()), type, e.fromNodeId(), e.toNodeId(), pref, feedback, status, note);
    }

    private LocalDateTime when(String createdAt) {
        Instant now = clock.instant();
        if (createdAt == null || createdAt.isBlank()) {
            return LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        }
        Instant at;
        try {
            at = Instant.parse(createdAt.trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("createdAt must look like 2026-11-03T08:15:00Z");
        }
        if (at.isAfter(now.plus(Duration.ofDays(1)))) {
            throw new BadRequestException("createdAt is in the future");
        }
        return LocalDateTime.ofInstant(at, ZoneOffset.UTC);
    }

    private static String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    // ---- admin reads
    public Usage.Summary summary() {
        return repo.summary();
    }

    public List<Usage.TopPlace> mostSearched(int limit) {
        return repo.mostSearched(Math.max(1, Math.min(limit, 20)));
    }

    public List<Usage.Event> recent(int limit) {
        return repo.recent(Math.max(1, Math.min(limit, 50)));
    }

    public List<Usage.Event> reports() {
        return repo.reports();
    }

    public void setReportStatus(int logId, String status) {
        String s = upper(status);
        if (!REPORT_STATUSES.contains(s)) {
            throw new BadRequestException("status must be NEW or REVIEWED");
        }
        if (!repo.setReportStatus(logId, s)) {
            throw new NotFoundException("Report " + logId + " was not found");
        }
    }

    /** The whole log as CSV text. Starts with a byte-order mark so Excel reads the names correctly. */
    public String exportCsv() {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(Csv.line(List.of("log_id", "created_at_utc", "event_type", "from_node_id", "from_name",
                "to_node_id", "to_name", "route_pref", "feedback_type", "status", "note")));
        for (Usage.Event e : repo.all()) {
            sb.append(Csv.line(java.util.Arrays.asList(e.id(), e.createdAt(), e.eventType(), e.fromNodeId(),
                    e.fromName(), e.toNodeId(), e.toName(), e.routePref(), e.feedbackType(), e.status(), e.note())));
        }
        return sb.toString();
    }
}

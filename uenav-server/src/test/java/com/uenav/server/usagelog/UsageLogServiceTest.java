package com.uenav.server.usagelog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UsageLogServiceTest {

    private static final String ID1 = "11111111-1111-4111-8111-111111111111";
    private static final String ID2 = "22222222-2222-4222-8222-222222222222";

    private static class FakeRepo implements UsageLogRepository {
        final Set<Integer> nodes = new HashSet<>(Set.of(1, 2, 3));
        final List<Usage.Row> rows = new ArrayList<>();
        final Map<Integer, String> statusById = new HashMap<>();
        int lastLimit;

        public boolean nodeExists(int id) { return nodes.contains(id); }

        public boolean insert(Usage.Row r) {
            for (Usage.Row x : rows) {
                if (x.clientLogId().equals(r.clientLogId())) {
                    return false;
                }
            }
            rows.add(r);
            return true;
        }

        public Usage.Summary summary() { return new Usage.Summary(1, 2, 3, 4); }
        public List<Usage.TopPlace> mostSearched(int limit) { lastLimit = limit; return List.of(); }
        public List<Usage.Event> recent(int limit) { lastLimit = limit; return List.of(); }
        public List<Usage.Event> reports() { return List.of(); }

        public List<Usage.Event> all() {
            return List.of(
                    new Usage.Event(1, "2026-11-03T08:00:00Z", "ROUTE", 1, "Gate", 2, "Registrar, \"Main\"", "ANY", null, null, null),
                    new Usage.Event(2, "2026-11-03T08:05:00Z", "FEEDBACK", 1, "Gate", 2, "Registrar, \"Main\"", "ANY",
                            "WRONG_ROUTE", "NEW", "=HYPERLINK(\"http://x\")"));
        }

        public boolean setReportStatus(int id, String status) {
            if (id != 7) {
                return false;
            }
            statusById.put(id, status);
            return true;
        }
    }

    private static final Clock NOW = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return Instant.parse("2026-11-03T12:00:00Z"); }
    };

    private final FakeRepo repo = new FakeRepo();
    private final UsageLogService service = new UsageLogService(repo, NOW);

    private static Usage.Entry route(String id) {
        return new Usage.Entry(id, "2026-11-03T08:15:00Z", "ROUTE", 1, 2, "ANY", null, null);
    }

    private static Usage.Entry feedback(String id, String type) {
        return new Usage.Entry(id, null, "FEEDBACK", 1, 2, "elevator", type, "  stairs were closed  ");
    }

    private String rejectedMessage(Usage.Entry e) {
        Usage.IngestResult r = service.ingest(List.of(e));
        assertEquals(0, r.accepted());
        assertEquals(1, r.rejected().size());
        return r.rejected().get(0).error();
    }

    @Test
    void validRouteIsStoredWithItsOwnTime() {
        Usage.IngestResult r = service.ingest(List.of(route(ID1)));
        assertEquals(1, r.accepted());
        assertEquals(LocalDateTime.of(2026, 11, 3, 8, 15), repo.rows.get(0).createdAtUtc());
        assertEquals(null, repo.rows.get(0).status());
    }

    @Test
    void sendingTheSameEntryTwiceStoresItOnce() {
        service.ingest(List.of(route(ID1)));
        Usage.IngestResult again = service.ingest(List.of(route(ID1), route(ID2)));
        assertEquals(1, again.accepted());
        assertEquals(1, again.duplicates());
        assertEquals(2, repo.rows.size());
    }

    @Test
    void feedbackIsStoredAsNewAndTidied() {
        service.ingest(List.of(feedback(ID1, "wrong_route")));
        Usage.Row r = repo.rows.get(0);
        assertEquals("WRONG_ROUTE", r.feedbackType());
        assertEquals("NEW", r.status());
        assertEquals("ELEVATOR", r.routePref());
        assertEquals("stairs were closed", r.note());
        assertEquals(LocalDateTime.of(2026, 11, 3, 12, 0), r.createdAtUtc());
    }

    @Test
    void oneBadEntryDoesNotStopTheOthers() {
        Usage.Entry bad = new Usage.Entry("not-a-uuid", null, "ROUTE", 1, 2, null, null, null);
        Usage.IngestResult r = service.ingest(List.of(route(ID1), bad));
        assertEquals(1, r.accepted());
        assertEquals(1, r.rejected().get(0).index());
    }

    @Test
    void badEntriesAreRejectedWithClearMessages() {
        assertTrue(rejectedMessage(new Usage.Entry(ID1, null, "ROUTE", 1, 99, null, null, null)).contains("Unknown place"));
        assertTrue(rejectedMessage(new Usage.Entry(ID1, null, "ROUTE", 2, 2, null, null, null)).contains("different"));
        assertTrue(rejectedMessage(new Usage.Entry(ID1, null, "VISIT", 1, 2, null, null, null)).contains("eventType"));
        assertTrue(rejectedMessage(new Usage.Entry(ID1, null, "ROUTE", 1, 2, "FLY", null, null)).contains("routePref"));
        assertTrue(rejectedMessage(new Usage.Entry(ID1, null, "FEEDBACK", 1, 2, null, "LOVED_IT", null)).contains("feedbackType"));
        assertTrue(rejectedMessage(new Usage.Entry(ID1, null, "ROUTE", 1, 2, null, "HELPFUL", null)).contains("only allowed"));
        assertTrue(rejectedMessage(new Usage.Entry(ID1, null, "ROUTE", null, 2, null, null, null)).contains("required"));
        assertTrue(rejectedMessage(new Usage.Entry(ID1, "yesterday", "ROUTE", 1, 2, null, null, null)).contains("createdAt"));
        assertTrue(rejectedMessage(new Usage.Entry(ID1, "2030-01-01T00:00:00Z", "ROUTE", 1, 2, null, null, null)).contains("future"));
        assertTrue(rejectedMessage(new Usage.Entry(ID1, null, "ROUTE", 1, 2, null, null, "x".repeat(256))).contains("note"));
        assertEquals(0, repo.rows.size());
    }

    @Test
    void emptyOrHugeBatchesAreRefused() {
        assertThrows(UsageLogService.BadRequestException.class, () -> service.ingest(List.of()));
        assertThrows(UsageLogService.BadRequestException.class, () -> service.ingest(null));
        List<Usage.Entry> many = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            many.add(route(ID1));
        }
        assertThrows(UsageLogService.BadRequestException.class, () -> service.ingest(many));
    }

    @Test
    void reportStatusCanBeChangedOnlyToKnownValuesOnExistingReports() {
        service.setReportStatus(7, "reviewed");
        assertEquals("REVIEWED", repo.statusById.get(7));
        assertThrows(UsageLogService.BadRequestException.class, () -> service.setReportStatus(7, "DONE"));
        assertThrows(UsageLogService.NotFoundException.class, () -> service.setReportStatus(8, "NEW"));
    }

    @Test
    void listLimitsAreKeptSensible() {
        service.recent(1000);
        assertEquals(50, repo.lastLimit);
        service.mostSearched(0);
        assertEquals(1, repo.lastLimit);
        service.mostSearched(5);
        assertEquals(5, repo.lastLimit);
    }

    @Test
    void csvHasHeaderQuotesAndDefusesFormulas() {
        String csv = service.exportCsv();
        assertTrue(csv.startsWith("\uFEFFlog_id,created_at_utc,event_type"));
        assertTrue(csv.contains("\"Registrar, \"\"Main\"\"\""));
        assertTrue(csv.contains("'=HYPERLINK("));
        assertFalse(csv.contains(",=HYPERLINK("));
        assertEquals(3, csv.split("\r\n").length);
    }
}

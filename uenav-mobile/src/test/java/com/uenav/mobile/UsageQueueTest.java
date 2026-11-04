package com.uenav.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uenav.core.RoutePreference;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UsageQueueTest {

    private final MemoryStore store = new MemoryStore();
    private final AtomicInteger counter = new AtomicInteger();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T08:15:30.123Z"), ZoneOffset.UTC);

    private UsageQueue queue() {
        return new UsageQueue(store, clock, () -> "id-" + counter.incrementAndGet());
    }

    @Test
    void aSearchIsSavedWithAnIdAndTime() {
        UsageEntry e = queue().addRoute(1, 4, RoutePreference.ELEVATOR);
        assertEquals("id-1", e.clientLogId());
        assertEquals("2026-11-03T08:15:30Z", e.createdAt());
        assertEquals("ROUTE", e.eventType());
        assertEquals("ELEVATOR", e.routePref());
        assertEquals(null, e.feedbackType());
    }

    @Test
    void feedbackHasATypeAndOnlyKnownTypesAreAllowed() {
        UsageQueue q = queue();
        assertEquals("WRONG_ROUTE", q.addFeedback(1, 4, RoutePreference.ANY, "WRONG_ROUTE").feedbackType());
        assertEquals("FEEDBACK", q.pending(10).get(0).eventType());
        assertThrows(IllegalArgumentException.class, () -> q.addFeedback(1, 4, RoutePreference.ANY, "LOVED_IT"));
    }

    @Test
    void entriesSurviveClosingTheApp() {
        UsageQueue first = queue();
        first.addRoute(1, 4, RoutePreference.ANY);
        first.addFeedback(1, 4, RoutePreference.ANY, "HELPFUL");
        UsageQueue reopened = queue();
        assertEquals(2, reopened.size());
        assertEquals("HELPFUL", reopened.pending(10).get(1).feedbackType());
        assertEquals(first.pending(10), reopened.pending(10));
    }

    @Test
    void pendingGivesTheOldestFirstAndRemoveForgetsOnlyThoseSent() {
        UsageQueue q = queue();
        for (int i = 0; i < 5; i++) {
            q.addRoute(1, 2 + i, RoutePreference.ANY);
        }
        List<UsageEntry> firstTwo = q.pending(2);
        assertEquals(List.of("id-1", "id-2"), firstTwo.stream().map(UsageEntry::clientLogId).toList());
        q.remove(firstTwo);
        assertEquals(3, q.size());
        assertEquals("id-3", q.pending(10).get(0).clientLogId());
        assertEquals(3, queue().size());
    }

    @Test
    void damagedSavedDataStartsEmptyAndOneBadEntryDoesNotLoseTheOthers() {
        store.text = "{{{ not json";
        assertEquals(0, queue().size());

        store.text = "{\"entries\":[{\"clientLogId\":\"a\",\"createdAt\":\"2026-11-03T08:00:00Z\",\"eventType\":\"ROUTE\","
                + "\"fromNodeId\":1,\"toNodeId\":2,\"routePref\":\"ANY\",\"feedbackType\":null},"
                + "{\"clientLogId\":\"b\"},\"junk\"]}";
        UsageQueue q = queue();
        assertEquals(1, q.size());
        assertEquals("a", q.pending(5).get(0).clientLogId());
    }

    @Test
    void theQueueKeepsAtMostAThousandEntries() {
        UsageQueue q = queue();
        for (int i = 0; i < UsageQueue.MAX_ENTRIES + 5; i++) {
            q.addRoute(1, 2, RoutePreference.ANY);
        }
        assertEquals(UsageQueue.MAX_ENTRIES, q.size());
        assertEquals("id-6", q.pending(1).get(0).clientLogId());
    }

    @Test
    void aStorageProblemDoesNotStopTheApp() {
        UsageQueue q = queue();
        store.failSaves = true;
        q.addRoute(1, 2, RoutePreference.ANY);
        assertEquals(1, q.size());
        assertTrue(q.saveFailed());
        store.failSaves = false;
        q.addRoute(1, 3, RoutePreference.ANY);
        assertFalse(q.saveFailed());
    }

    @Test
    void theSavedFormMatchesWhatTheServerExpects() {
        UsageEntry e = queue().addFeedback(3, 10, RoutePreference.STAIRS, "WRONG_ROUTE");
        assertEquals(List.of("clientLogId", "createdAt", "eventType", "fromNodeId", "toNodeId", "routePref", "feedbackType"),
                List.copyOf(e.toMap().keySet()));
        assertEquals(3, e.toMap().get("fromNodeId"));
        assertEquals("STAIRS", e.toMap().get("routePref"));
    }
}

package com.uenav.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UsageFormatTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    private static AdminModels.UsageEvent event(String type, String pref, String feedback) {
        return new AdminModels.UsageEvent(1, "2026-11-03T11:58:00Z", type, "Hills Gate", "Registrar's Office",
                pref, feedback, null, null);
    }

    @Test
    void timeAgoUsesTheShortestSensibleUnit() {
        assertEquals("just now", TimeAgo.format(Instant.parse("2026-11-03T11:59:30Z"), NOW));
        assertEquals("2m ago", TimeAgo.format(Instant.parse("2026-11-03T11:58:00Z"), NOW));
        assertEquals("59m ago", TimeAgo.format(Instant.parse("2026-11-03T11:01:00Z"), NOW));
        assertEquals("3h ago", TimeAgo.format(Instant.parse("2026-11-03T09:00:00Z"), NOW));
        assertEquals("2d ago", TimeAgo.format(Instant.parse("2026-11-01T12:00:00Z"), NOW));
    }

    @Test
    void timeAgoNeverShowsTheFutureAndToleratesBadText() {
        assertEquals("just now", TimeAgo.format(Instant.parse("2026-11-03T12:05:00Z"), NOW));
        assertEquals("not a time", TimeAgo.ofIso("not a time", NOW));
        assertEquals("", TimeAgo.ofIso(null, NOW));
        assertEquals("2m ago", TimeAgo.ofIso("2026-11-03T11:58:00Z", NOW));
    }

    @Test
    void activityLinesReadLikeTheFigmaDashboard() {
        assertEquals("Route: Hills Gate \u2192 Registrar's Office", UsageText.describe(event("ROUTE", "ANY", null)));
        assertEquals("Route: Hills Gate \u2192 Registrar's Office (elevator)", UsageText.describe(event("ROUTE", "ELEVATOR", null)));
        assertEquals("Route: Hills Gate \u2192 Registrar's Office (stairs)", UsageText.describe(event("ROUTE", "STAIRS", null)));
        assertEquals("Feedback: wrong route \u00B7 Registrar's Office", UsageText.describe(event("FEEDBACK", "ANY", "WRONG_ROUTE")));
        assertEquals("Feedback: helpful \u00B7 Registrar's Office", UsageText.describe(event("FEEDBACK", "ANY", "HELPFUL")));
    }
}

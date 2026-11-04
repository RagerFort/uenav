package com.uenav.client;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Short "2m ago" style times, as in the Figma dashboard. */
final class TimeAgo {

    private TimeAgo() { }

    static String format(Instant then, Instant now) {
        long seconds = Math.max(0, Duration.between(then, now).getSeconds());
        if (seconds < 60) {
            return "just now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        return (hours / 24) + "d ago";
    }

    /** Same, from the server's text time. Unreadable text is returned unchanged. */
    static String ofIso(String iso, Instant now) {
        if (iso == null) {
            return "";
        }
        try {
            return format(Instant.parse(iso), now);
        } catch (DateTimeParseException e) {
            return iso;
        }
    }
}

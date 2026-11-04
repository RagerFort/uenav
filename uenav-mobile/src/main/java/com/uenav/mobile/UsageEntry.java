package com.uenav.mobile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One saved search or feedback tap, waiting to be sent to the server.
 * The fields match what POST /api/usage expects. feedbackType is null for searches.
 */
public record UsageEntry(String clientLogId, String createdAt, String eventType, int fromNodeId,
                         int toNodeId, String routePref, String feedbackType) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clientLogId", clientLogId);
        m.put("createdAt", createdAt);
        m.put("eventType", eventType);
        m.put("fromNodeId", fromNodeId);
        m.put("toNodeId", toNodeId);
        m.put("routePref", routePref);
        m.put("feedbackType", feedbackType);
        return m;
    }

    /** @throws IllegalArgumentException if a field is missing or has the wrong kind of value */
    public static UsageEntry fromMap(Map<String, Object> m) {
        return new UsageEntry(text(m, "clientLogId", true), text(m, "createdAt", true), text(m, "eventType", true),
                number(m, "fromNodeId"), number(m, "toNodeId"), text(m, "routePref", true),
                text(m, "feedbackType", false));
    }

    private static String text(Map<String, Object> m, String key, boolean required) {
        Object v = m.get(key);
        if (v == null) {
            if (required) {
                throw new IllegalArgumentException("Missing " + key);
            }
            return null;
        }
        if (!(v instanceof String)) {
            throw new IllegalArgumentException(key + " must be text");
        }
        return (String) v;
    }

    private static int number(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof Double)) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return ((Double) v).intValue();
    }
}

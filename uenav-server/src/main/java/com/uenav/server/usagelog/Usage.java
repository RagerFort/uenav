package com.uenav.server.usagelog;

import java.util.List;

/** Data shapes for the usage log. */
public final class Usage {

    private Usage() { }

    /** One log row sent by the phone. Status is never sent: the server sets it. */
    public record Entry(String clientLogId, String createdAt, String eventType, Integer fromNodeId,
                        Integer toNodeId, String routePref, String feedbackType, String note) { }

    public record Batch(List<Entry> entries) { }

    /** A checked entry, ready to store. createdAt is UTC. */
    public record Row(String clientLogId, java.time.LocalDateTime createdAtUtc, String eventType,
                      int fromNodeId, int toNodeId, String routePref, String feedbackType,
                      String status, String note) { }

    public record Rejected(int index, String error) { }

    public record IngestResult(int accepted, int duplicates, List<Rejected> rejected) { }

    public record Summary(long searches, long helpful, long openReports, long totalReports) { }

    public record TopPlace(int nodeId, String name, long count) { }

    /** A log row with place names, for the dashboard, reports page and CSV export. createdAt is an ISO UTC instant. */
    public record Event(int id, String createdAt, String eventType, int fromNodeId, String fromName,
                        int toNodeId, String toName, String routePref, String feedbackType,
                        String status, String note) { }
}

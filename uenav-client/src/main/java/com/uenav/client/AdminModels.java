package com.uenav.client;

import com.uenav.core.SnapshotReader;

public final class AdminModels {

    private AdminModels() { }

    public record Building(int id, String name, String category, String description) { }

    /** What the Add/Edit building form sends to the server. */
    public record BuildingForm(String name, String category, String description) { }

    /** What the Add/Edit floor form sends to the server. */
    public record FloorForm(int buildingId, int floorNumber, String floorName) { }

    /** A walkway between two places. It can be walked in both directions. */
    public record Edge(int id, int fromNodeId, int toNodeId, double distanceM, String edgeType, boolean accessible) { }

    /** What the Add/Edit walkway form sends to the server. */
    public record EdgeForm(int fromNodeId, int toNodeId, double distanceM, String edgeType, boolean accessible) { }

    public record Floor(int id, int buildingId, int floorNumber, String floorName) { }

    /** One row of the node table, as shown in the Places list. */
    public record Place(int id, Integer buildingId, Integer floorId, String name, String nodeType, int x, int y) {
        public boolean isSearchable() {
            return SnapshotReader.isSearchable(nodeType);
        }
    }

    /** What the Add/Edit place form sends to the server. */
    public record NodeForm(Integer buildingId, Integer floorId, String name, String nodeType, int x, int y) { }

    public record UsageSummary(long searches, long helpful, long openReports, long totalReports) { }

    public record TopPlace(int nodeId, String name, long count) { }

    /** One usage log row with place names. createdAt is an ISO UTC time like 2026-11-03T08:15:00Z. */
    public record UsageEvent(int id, String createdAt, String eventType, String fromName, String toName,
                             String routePref, String feedbackType, String status, String note) { }
}

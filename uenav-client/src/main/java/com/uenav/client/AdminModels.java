package com.uenav.client;

import com.uenav.core.SnapshotReader;

public final class AdminModels {

    private AdminModels() { }

    public record Building(int id, String name) { }

    public record Floor(int id, int buildingId, int floorNumber, String floorName) { }

    /** One row of the node table, as shown in the Places list. */
    public record Place(int id, Integer buildingId, Integer floorId, String name, String nodeType, int x, int y) {
        public boolean isSearchable() {
            return SnapshotReader.isSearchable(nodeType);
        }
    }

    /** What the Add/Edit place form sends to the server. */
    public record NodeForm(Integer buildingId, Integer floorId, String name, String nodeType, int x, int y) { }
}

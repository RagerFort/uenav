package com.uenav.server.snapshot;

import java.util.List;

/** The whole campus map as one data set. This is the "data file" the phone downloads (D9). */
public record Snapshot(String version,
                       List<BuildingRow> buildings,
                       List<FloorRow> floors,
                       List<NodeRow> nodes,
                       List<EdgeRow> edges) {

    public record BuildingRow(int id, String name, String category, String description,
                              Integer mapX, Integer mapY, String imagePath) { }

    public record FloorRow(int id, int  buildingId, int floorNumber, String floorName,
                           String planImagePath) { }

    public record NodeRow(int id, Integer buildingId, Integer floorId, String name,
                          String nodeType, int x, int y) { }

    public record EdgeRow(int id, int fromNodeId, int toNodeId, double distanceM,
                          String edgeType, boolean accessible) { }
}

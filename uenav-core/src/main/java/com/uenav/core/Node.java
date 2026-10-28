package com.uenav.core;

/**
 * One row of the Node table. Rooms, places, gates, junctions, stairs and elevators are all nodes.
 * buildingId and floorId are null for outdoor nodes.
 */
public record Node(int id, Integer buildingId, Integer floorId, String name,
                   int x, int y, String nodeType) {

    public boolean isOutdoor() {
        return buildingId == null;
    }
}

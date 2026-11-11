package com.uenav.core;

/**
 * One row of the Node table. Rooms, places, gates, junctions, stairs and elevators are all nodes.
 * buildingId and floorId are null for outdoor nodes. description ("what this office handles") and openHours
 * ("Mon-Fri 8:00 AM - 5:00 PM") are optional and shown on the place's info card; both may be null.
 */
public record Node(int id, Integer buildingId, Integer floorId, String name,
                   int x, int y, String nodeType, String description, String openHours) {

    /** A node without a description or opening hours. */
    public Node(int id, Integer buildingId, Integer floorId, String name, int x, int y, String nodeType) {
        this(id, buildingId, floorId, name, x, y, nodeType, null, null);
    }

    /** True when there is a description or opening hours to show. */
    public boolean hasInfo() {
        return (description != null && !description.isBlank()) || (openHours != null && !openHours.isBlank());
    }
}

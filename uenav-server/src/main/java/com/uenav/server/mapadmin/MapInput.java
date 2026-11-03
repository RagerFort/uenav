package com.uenav.server.mapadmin;

/** What the admin sends when adding or changing map data. Boxed types so a missing field is detected. */
public final class MapInput {

    private MapInput() { }

    public record BuildingInput(String name, String category, String description,
                                Integer mapX, Integer mapY, String imagePath) { }

    public record FloorInput(Integer buildingId, Integer floorNumber, String floorName, String planImagePath) { }

    public record NodeInput(Integer buildingId, Integer floorId, String name, String nodeType,
                            Integer x, Integer y) { }

    public record EdgeInput(Integer fromNodeId, Integer toNodeId, Double distanceM, String edgeType,
                            Boolean accessible) { }
}

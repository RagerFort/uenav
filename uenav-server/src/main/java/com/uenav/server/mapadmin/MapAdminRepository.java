package com.uenav.server.mapadmin;

import java.util.Optional;

/** Writes to the map tables. Reading the lists is done by SnapshotRepository. */
public interface MapAdminRepository {

    int insertBuilding(MapInput.BuildingInput b);
    boolean updateBuilding(int id, MapInput.BuildingInput b);
    boolean deleteBuilding(int id);

    int insertFloor(MapInput.FloorInput f);
    boolean updateFloor(int id, MapInput.FloorInput f);
    boolean deleteFloor(int id);

    int insertNode(MapInput.NodeInput n);
    boolean updateNode(int id, MapInput.NodeInput n);
    boolean deleteNode(int id);

    int insertEdge(MapInput.EdgeInput e);
    boolean updateEdge(int id, MapInput.EdgeInput e);
    boolean deleteEdge(int id);

    boolean buildingExists(int id);

    /** The building a floor belongs to, or empty if the floor does not exist. */
    Optional<Integer> floorBuildingId(int floorId);

    boolean nodeExists(int id);

    /** True if an edge of this type already links the two nodes, in either direction. */
    boolean edgeExists(int nodeA, int nodeB, String edgeType, Integer excludeEdgeId);
}

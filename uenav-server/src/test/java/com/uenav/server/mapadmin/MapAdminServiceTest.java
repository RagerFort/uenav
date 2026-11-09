package com.uenav.server.mapadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MapAdminServiceTest {

    /** In-memory stand-in for MySQL. */
    private static class FakeRepo implements MapAdminRepository {
        int nextId = 100;
        final Set<Integer> buildings = new HashSet<>();
        final Map<Integer, Integer> floorToBuilding = new HashMap<>();
        final Set<Integer> nodes = new HashSet<>();
        final Map<Integer, MapInput.EdgeInput> edges = new HashMap<>();
        MapInput.NodeInput lastNode;
        MapInput.EdgeInput lastEdge;

        public int insertBuilding(MapInput.BuildingInput b) { buildings.add(nextId); return nextId++; }
        public boolean updateBuilding(int id, MapInput.BuildingInput b) { return buildings.contains(id); }
        public boolean deleteBuilding(int id) { return buildings.remove(id); }

        public int insertFloor(MapInput.FloorInput f) { floorToBuilding.put(nextId, f.buildingId()); return nextId++; }
        public boolean updateFloor(int id, MapInput.FloorInput f) { return floorToBuilding.containsKey(id); }
        public boolean deleteFloor(int id) { return floorToBuilding.remove(id) != null; }

        public int insertNode(MapInput.NodeInput n) { lastNode = n; nodes.add(nextId); return nextId++; }
        public boolean updateNode(int id, MapInput.NodeInput n) { lastNode = n; return nodes.contains(id); }
        public boolean deleteNode(int id) { return nodes.remove(id); }

        public int insertEdge(MapInput.EdgeInput e) { lastEdge = e; edges.put(nextId, e); return nextId++; }
        public boolean updateEdge(int id, MapInput.EdgeInput e) { lastEdge = e; return edges.containsKey(id); }
        public boolean deleteEdge(int id) { return edges.remove(id) != null; }

        public boolean buildingExists(int id) { return buildings.contains(id); }
        public Optional<Integer> floorBuildingId(int floorId) { return Optional.ofNullable(floorToBuilding.get(floorId)); }
        public boolean nodeExists(int id) { return nodes.contains(id); }

        public boolean edgeExists(int a, int b, String type, Integer exclude) {
            for (Map.Entry<Integer, MapInput.EdgeInput> en : edges.entrySet()) {
                MapInput.EdgeInput e = en.getValue();
                boolean same = (e.fromNodeId() == a && e.toNodeId() == b) || (e.fromNodeId() == b && e.toNodeId() == a);
                if (same && e.edgeType().equals(type) && !en.getKey().equals(exclude)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final FakeRepo repo = new FakeRepo();
    private final MapAdminService service = new MapAdminService(repo);

    private int building() {
        return service.createBuilding(new MapInput.BuildingInput("Engineering", "Building", null, null, null, null));
    }

    private int floor(int buildingId) {
        return service.createFloor(new MapInput.FloorInput(buildingId, 1, "1st floor", null));
    }

    private int node(String name) {
        return service.createNode(new MapInput.NodeInput(null, null, name, "JUNCTION", 0, 0, null, null));
    }

    private static MapInput.EdgeInput edge(int a, int b, double d, String type) {
        return new MapInput.EdgeInput(a, b, d, type, null);
    }

    private static List<String> errorsOf(Runnable r) {
        return assertThrows(MapAdminService.ValidationException.class, r::run).errors();
    }

    @Test
    void validBuildingIsStored() {
        assertTrue(building() > 0);
        assertEquals(1, repo.buildings.size());
    }

    @Test
    void allProblemsAreReportedTogether() {
        List<String> errors = errorsOf(() ->
                service.createBuilding(new MapInput.BuildingInput(" ", null, null, null, null, null)));
        assertEquals(2, errors.size());
        assertEquals(0, repo.buildings.size());
    }

    @Test
    void floorNeedsAnExistingBuilding() {
        assertTrue(errorsOf(() -> service.createFloor(new MapInput.FloorInput(999, 1, "1F", null)))
                .get(0).contains("does not exist"));
        assertTrue(errorsOf(() -> service.createFloor(new MapInput.FloorInput(null, 1, "1F", null)))
                .get(0).contains("required"));
    }

    @Test
    void nodeFloorMustBelongToTheNodesBuilding() {
        int b1 = building();
        int b2 = building();
        int f1 = floor(b1);
        assertTrue(service.createNode(new MapInput.NodeInput(b1, f1, "EN 101", "room", 1, 1, null, null)) > 0);
        assertTrue(errorsOf(() -> service.createNode(new MapInput.NodeInput(b2, f1, "X", "ROOM", 1, 1, null, null)))
                .get(0).contains("does not belong"));
        assertTrue(errorsOf(() -> service.createNode(new MapInput.NodeInput(null, f1, "X", "ROOM", 1, 1, null, null)))
                .get(0).contains("needs a buildingId"));
    }

    @Test
    void nodeTypeIsTidiedUp() {
        service.createNode(new MapInput.NodeInput(null, null, "  Main Gate ", "building entrance", 5, 6, null, null));
        assertEquals("BUILDING_ENTRANCE", repo.lastNode.nodeType());
        assertEquals("Main Gate", repo.lastNode.name());
    }

    @Test
    void officeInfoIsOptionalTrimmedAndLimited() {
        service.createNode(new MapInput.NodeInput(null, null, "Registrar", "OFFICE", 1, 1,
                "  Records and transcripts ", "   "));
        assertEquals("Records and transcripts", repo.lastNode.description());
        assertEquals("", repo.lastNode.openHours()); // blank = clear (stored as NULL)
        service.createNode(new MapInput.NodeInput(null, null, "Old app", "OFFICE", 1, 1, null, null));
        assertEquals(null, repo.lastNode.description()); // not sent = keep what is saved
        assertTrue(errorsOf(() -> service.createNode(new MapInput.NodeInput(null, null, "X", "OFFICE", 1, 1,
                "x".repeat(501), null))).get(0).contains("description"));
        assertTrue(errorsOf(() -> service.createNode(new MapInput.NodeInput(null, null, "X", "OFFICE", 1, 1,
                null, "x".repeat(101)))).get(0).contains("openHours"));
    }

    @Test
    void edgeNeedsTwoDifferentExistingNodes() {
        int a = node("A");
        int b = node("B");
        assertTrue(errorsOf(() -> service.createEdge(edge(a, 999, 5, "WALK"))).get(0).contains("does not exist"));
        assertTrue(errorsOf(() -> service.createEdge(edge(a, a, 5, "WALK"))).get(0).contains("two different"));
        assertTrue(service.createEdge(edge(a, b, 5, "WALK")) > 0);
    }

    @Test
    void edgeDistanceAndTypeAreChecked() {
        int a = node("A");
        int b = node("B");
        assertTrue(errorsOf(() -> service.createEdge(edge(a, b, 0, "WALK"))).get(0).contains("greater than 0"));
        assertTrue(errorsOf(() -> service.createEdge(edge(a, b, -3, "WALK"))).get(0).contains("greater than 0"));
        assertTrue(errorsOf(() -> service.createEdge(edge(a, b, 5, "TELEPORT"))).get(0).contains("WALK, STAIRS"));
        assertEquals(2, errorsOf(() -> service.createEdge(edge(a, b, 0, "BAD"))).size());
    }

    @Test
    void duplicateEdgeIsRejectedInEitherDirectionButOtherTypesAreFine() {
        int a = node("A");
        int b = node("B");
        service.createEdge(edge(a, b, 5, "walk"));
        assertTrue(errorsOf(() -> service.createEdge(edge(b, a, 7, "WALK"))).get(0).contains("already exists"));
        assertTrue(service.createEdge(edge(a, b, 7, "STAIRS")) > 0);
    }

    @Test
    void editingAnEdgeDoesNotClashWithItself() {
        int a = node("A");
        int b = node("B");
        int id = service.createEdge(edge(a, b, 5, "WALK"));
        service.updateEdge(id, edge(a, b, 9, "WALK"));
        assertEquals(9.0, repo.lastEdge.distanceM());
    }

    @Test
    void accessibleDefaultsToTrue() {
        int a = node("A");
        int b = node("B");
        service.createEdge(edge(a, b, 5, "WALK"));
        assertTrue(repo.lastEdge.accessible());
        int c = node("C");
        service.createEdge(new MapInput.EdgeInput(a, c, 5.0, "STAIRS", false));
        assertFalse(repo.lastEdge.accessible());
    }

    @Test
    void changingOrDeletingMissingRecordsGivesNotFound() {
        assertThrows(MapAdminService.NotFoundException.class, () -> service.deleteNode(12345));
        assertThrows(MapAdminService.NotFoundException.class, () -> service.deleteEdge(12345));
        assertThrows(MapAdminService.NotFoundException.class, () -> service.updateBuilding(12345,
                new MapInput.BuildingInput("X", "Y", null, null, null, null)));
        int id = node("A");
        service.deleteNode(id);
        assertEquals(0, repo.nodes.size());
    }
}

package com.uenav.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphCheckTest {

    private static List<String> titles(List<GraphCheck.Issue> issues) {
        return issues.stream().map(GraphCheck.Issue::title).toList();
    }

    private static CampusData read(String nodes, String edges) {
        return SnapshotReader.read("""
            {"version":"g",
             "buildings":[{"id":1,"name":"EN","category":"Building","description":null,"mapX":null,"mapY":null,
                           "imagePath":null}],
             "floors":[{"id":1,"buildingId":1,"floorNumber":1,"floorName":"1st floor","planImagePath":null},
                       {"id":2,"buildingId":1,"floorNumber":2,"floorName":"2nd floor","planImagePath":null}],
             "nodes":[%s], "edges":[%s]}
            """.formatted(nodes, edges));
    }

    private static String node(int id, Integer floor, String name, String type, int x, int y) {
        return """
            {"id":%d,"buildingId":%s,"floorId":%s,"name":"%s","nodeType":"%s","x":%d,"y":%d,
             "openHours":"8-5"}""".formatted(id, floor == null ? "null" : "1", floor == null ? "null" : floor,
                name, type, x, y);
    }

    private static String edge(int id, int a, int b, double m, String type, boolean accessible) {
        return """
            {"id":%d,"fromNodeId":%d,"toNodeId":%d,"distanceM":%s,"edgeType":"%s","accessible":%s}"""
                .formatted(id, a, b, m, type, accessible);
    }

    @Test
    void healthyMapHasNoIssues() {
        CampusData d = read(String.join(",",
                        node(1, null, "Gate", "GATE", 0, 0),
                        node(2, null, "Office", "OFFICE", 100, 0),
                        node(3, 1, "Lift 1F", "ELEVATOR", 100, 0),
                        node(4, 2, "Lift 2F", "ELEVATOR", 100, 0),
                        node(5, 2, "Room", "ROOM", 120, 0)),
                String.join(",",
                        edge(1, 1, 2, 27, "WALK", true),
                        edge(2, 2, 3, 1, "WALK", true),
                        edge(3, 3, 4, 10, "ELEVATOR", true),
                        edge(4, 4, 5, 6, "WALK", true)));
        assertEquals(List.of(), titles(GraphCheck.check(d)));
    }

    @Test
    void findsDisconnectedPlacesAndFloorsWithoutStairs() {
        CampusData d = read(String.join(",",
                        node(1, null, "Gate", "GATE", 0, 0),
                        node(2, null, "Office", "OFFICE", 100, 0),
                        node(3, null, "Lonely", "FACILITY", 50, 50),
                        node(4, 2, "Room A", "ROOM", 0, 0),
                        node(5, 2, "Room B", "ROOM", 10, 0)),
                String.join(",",
                        edge(1, 1, 2, 27, "WALK", true),
                        edge(2, 4, 5, 3, "WALK", true)));
        List<GraphCheck.Issue> issues = GraphCheck.check(d);
        List<String> t = titles(issues);
        assertTrue(t.contains("Lonely has no walkways"), t.toString());
        assertTrue(t.contains("Room A is cut off from the campus"), t.toString());
        assertTrue(t.contains("EN 1st floor has no stairs or elevator"), t.toString());
        assertEquals(GraphCheck.Severity.PROBLEM, issues.get(0).severity());
    }

    @Test
    void findsStairsOnlyPlacesFlatStairsAndOddDistances() {
        CampusData d = read(String.join(",",
                        node(1, null, "Gate", "GATE", 0, 0),
                        node(2, null, "Office", "OFFICE", 100, 0),
                        node(3, 1, "Stairs 1F", "STAIRS", 100, 0),
                        node(4, 2, "Stairs 2F", "STAIRS", 100, 0),
                        node(5, 2, "Room", "ROOM", 120, 0),
                        node(6, 2, "Room 2", "ROOM", 130, 0)),
                String.join(",",
                        edge(1, 1, 2, 300, "WALK", true),
                        edge(2, 2, 3, 1, "WALK", true),
                        edge(3, 3, 4, 10, "STAIRS", false),
                        edge(4, 4, 5, 6, "WALK", true),
                        edge(5, 5, 6, 3, "STAIRS", false)));
        List<String> t = titles(GraphCheck.check(d));
        assertTrue(t.contains("2 places have no step-free route"), t.toString());
        assertTrue(t.contains("Stairs link Room – Room 2 stays on one floor"), t.toString());
        assertTrue(t.contains("Walkway Gate – Office may have a wrong distance"), t.toString());
    }

    @Test
    void findsDuplicateNamesAndOfficesWithoutHours() {
        CampusData d = SnapshotReader.read("""
            {"version":"d","nodes":[
              {"id":1,"buildingId":null,"floorId":null,"name":"Clinic","nodeType":"FACILITY","x":0,"y":0},
              {"id":2,"buildingId":null,"floorId":null,"name":"clinic","nodeType":"FACILITY","x":10,"y":0},
              {"id":3,"buildingId":null,"floorId":null,"name":"Registrar","nodeType":"OFFICE","x":20,"y":0}],
             "edges":[
              {"id":1,"fromNodeId":1,"toNodeId":2,"distanceM":3,"edgeType":"WALK","accessible":true},
              {"id":2,"fromNodeId":2,"toNodeId":3,"distanceM":3,"edgeType":"WALK","accessible":true}]}
            """);
        List<String> t = titles(GraphCheck.check(d));
        assertTrue(t.contains("2 places are named \"Clinic\""), t.toString());
        assertTrue(t.contains("1 office has no office hours"), t.toString());
    }
}

package com.uenav.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnapshotReaderTest {

    private static final String JSON = """
        {"version":"abc123","buildings":[],"floors":[],
         "nodes":[
           {"id":1,"buildingId":null,"floorId":null,"name":"Gate \\"A\\"","nodeType":"GATE","x":0,"y":0},
           {"id":2,"buildingId":null,"floorId":null,"name":"Junction","nodeType":"JUNCTION","x":10,"y":0},
           {"id":3,"buildingId":1,"floorId":2,"name":"EN 420","nodeType":"ROOM","x":20,"y":5}],
         "edges":[
           {"id":1,"fromNodeId":1,"toNodeId":2,"distanceM":10.5,"edgeType":"WALK","accessible":true},
           {"id":2,"fromNodeId":2,"toNodeId":3,"distanceM":4,"edgeType":"STAIRS","accessible":false}]}
        """;

    @Test
    void readsVersionNodesAndEdges() {
        CampusData d = SnapshotReader.read(JSON);
        assertEquals("abc123", d.version());
        assertEquals(3, d.graph().nodeCount());
        assertEquals("Gate \"A\"", d.graph().node(1).orElseThrow().name());
        assertNull(d.graph().node(1).orElseThrow().buildingId());
        assertEquals(1, d.graph().node(3).orElseThrow().buildingId());
    }

    @Test
    void junctionsAreNotSearchablePlaces() {
        CampusData d = SnapshotReader.read(JSON);
        assertEquals(List.of(1, 3), d.places().stream().map(Node::id).toList());
    }

    @Test
    void routesOverTheLoadedGraph() {
        CampusData d = SnapshotReader.read(JSON);
        Route r = PathFinder.findRoute(d.graph(), 1, 3, RoutePreference.ANY).orElseThrow();
        assertEquals(14.5, r.totalDistanceM(), 1e-9);
        assertEquals(java.util.Optional.empty(),
                PathFinder.findRoute(d.graph(), 1, 3, RoutePreference.ELEVATOR));
    }

    @Test
    void badInputIsRejectedWithAClearError() {
        assertThrows(IllegalArgumentException.class, () -> SnapshotReader.read("not json"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotReader.read("{\"nodes\":[],\"edges\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotReader.read(
                "{\"version\":\"v\",\"nodes\":[{\"id\":\"one\",\"name\":\"x\",\"nodeType\":\"ROOM\",\"x\":0,\"y\":0}],\"edges\":[]}"));
    }

    @Test
    void miniJsonHandlesEscapesNumbersAndNesting() {
        Object o = MiniJson.parse("\uFEFF {\"a\":[1,-2.5e1,true,false,null,\"\\u0041\\n\"],\"b\":{}} ");
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) o;
        assertEquals(List.of(1.0, -25.0, true, false), ((List<?>) m.get("a")).subList(0, 4));
        assertEquals("A\n", ((List<?>) m.get("a")).get(5));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\":1,}"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("[1 2]"));
    }
}

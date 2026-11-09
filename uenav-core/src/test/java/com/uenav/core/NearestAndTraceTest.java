package com.uenav.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NearestAndTraceTest {

    private Graph g;

    private static List<Integer> ids(Route r) {
        return r.nodes().stream().map(Node::id).toList();
    }

    /*
     *  restroom 10 --30-- 1 --10-- 2 --10-- 3 --5 stairs-- 4 (restroom 11 upstairs)
     *                                        3 --40 elevator-- 4
     *  2 --15-- 12 exit gate
     *  20 isolated restroom
     */
    @BeforeEach
    void setUp() {
        g = new Graph();
        g.addNode(new Node(1, null, null, "Start", 0, 0, "JUNCTION"));
        g.addNode(new Node(2, null, null, "Mid", 10, 0, "JUNCTION"));
        g.addNode(new Node(3, null, null, "Stair foot", 20, 0, "STAIRS"));
        g.addNode(new Node(4, 1, 2, "Stair top", 20, 0, "STAIRS"));
        g.addNode(new Node(10, null, null, "Restroom A", -30, 0, "RESTROOM"));
        g.addNode(new Node(11, 1, 2, "Restroom B", 25, 0, "RESTROOM"));
        g.addNode(new Node(12, null, null, "Exit gate", 10, 15, "GATE"));
        g.addNode(new Node(20, null, null, "Far restroom", 99, 99, "RESTROOM"));
        g.addEdge(new Edge(1, 1, 10, 30, EdgeType.WALK, true));
        g.addEdge(new Edge(2, 1, 2, 10, EdgeType.WALK, true));
        g.addEdge(new Edge(3, 2, 3, 10, EdgeType.WALK, true));
        g.addEdge(new Edge(4, 3, 4, 5, EdgeType.STAIRS, false));
        g.addEdge(new Edge(5, 3, 4, 40, EdgeType.ELEVATOR, true));
        g.addEdge(new Edge(6, 4, 11, 3, EdgeType.WALK, true));
        g.addEdge(new Edge(7, 2, 12, 15, EdgeType.WALK, true));
    }

    @Test
    void nearestPicksTheClosestTarget() {
        Route r = PathFinder.findNearest(g, 1, List.of(10, 11, 20), RoutePreference.ANY).orElseThrow();
        assertEquals(List.of(1, 2, 3, 4, 11), ids(r)); // 28 m upstairs beats 30 m outside
        assertEquals(28.0, r.totalDistanceM(), 1e-9);
    }

    @Test
    void nearestHonoursAvoidStairs() {
        Route r = PathFinder.findNearest(g, 1, List.of(10, 11), RoutePreference.ELEVATOR).orElseThrow();
        assertEquals(List.of(1, 10), ids(r)); // the elevator way is 63 m, so the outside restroom wins
    }

    @Test
    void nearestNeverReturnsTheStartAndHandlesNoTargets() {
        Route r = PathFinder.findNearest(g, 10, List.of(10, 11), RoutePreference.ANY).orElseThrow();
        assertEquals(11, ids(r).get(ids(r).size() - 1));
        assertEquals(Optional.empty(), PathFinder.findNearest(g, 1, List.of(), RoutePreference.ANY));
        assertEquals(Optional.empty(), PathFinder.findNearest(g, 1, List.of(20), RoutePreference.ANY));
        assertEquals(Optional.empty(), PathFinder.findNearest(g, 1, List.of(999), RoutePreference.ANY));
    }

    @Test
    void traceListsSettledPlacesInOrderOfDistance() {
        PathFinder.Trace t = PathFinder.trace(g, 1, 12, RoutePreference.ANY);
        assertEquals(List.of(1, 2, 12), ids(t.route().orElseThrow()));
        assertEquals(1, t.visited().get(0));
        assertEquals(12, t.visited().get(t.visited().size() - 1));
        assertTrue(t.visited().containsAll(List.of(2, 3)));
        assertFalse(t.visited().contains(20));
    }

    @Test
    void traceWithNoRouteStillReportsWhatWasChecked() {
        PathFinder.Trace t = PathFinder.trace(g, 1, 20, RoutePreference.ANY);
        assertTrue(t.route().isEmpty());
        assertEquals(7, t.visited().size());
    }

    @Test
    void distancesFromGiveEveryReachablePlace() {
        java.util.Map<Integer, Double> d = PathFinder.distancesFrom(g, 1, RoutePreference.ANY);
        assertEquals(0.0, d.get(1), 1e-9);
        assertEquals(28.0, d.get(11), 1e-9);
        assertEquals(25.0, d.get(12), 1e-9);
        assertFalse(d.containsKey(20));
        assertEquals(63.0, PathFinder.distancesFrom(g, 1, RoutePreference.ELEVATOR).get(11), 1e-9);
    }

    @Test
    void reachableFollowsThePreference() {
        assertEquals(Set.of(1, 2, 3, 4, 10, 11, 12), PathFinder.reachable(g, 1, RoutePreference.ANY));
        assertTrue(PathFinder.reachable(g, 1, RoutePreference.ELEVATOR).contains(11));
        g.addNode(new Node(30, 1, 2, "Stairs only", 0, 0, "ROOM"));
        g.addEdge(new Edge(8, 3, 30, 5, EdgeType.STAIRS, false));
        assertFalse(PathFinder.reachable(g, 1, RoutePreference.ELEVATOR).contains(30));
    }
}

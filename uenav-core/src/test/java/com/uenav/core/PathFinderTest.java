package com.uenav.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PathFinderTest {

    private Graph g;

    private static Node n(int id) {
        return new Node(id, null, null, "N" + id, id * 10, 0, "JUNCTION");
    }

    private static List<Integer> ids(Route r) {
        return r.nodes().stream().map(Node::id).toList();
    }

    /*
     *  1 --10-- 2 --10-- 3        (walk, all accessible)
     *  1 --50-------------3       (long walk)
     *  3 --5 stairs-- 4
     *  3 --20 elevator-- 4
     *  5 isolated
     */
    @BeforeEach
    void setUp() {
        g = new Graph();
        for (int i = 1; i <= 5; i++) {
            g.addNode(n(i));
        }
        g.addEdge(new Edge(1, 1, 2, 10, EdgeType.WALK, true));
        g.addEdge(new Edge(2, 2, 3, 10, EdgeType.WALK, true));
        g.addEdge(new Edge(3, 1, 3, 50, EdgeType.WALK, true));
        g.addEdge(new Edge(4, 3, 4, 5, EdgeType.STAIRS, false));
        g.addEdge(new Edge(5, 3, 4, 20, EdgeType.ELEVATOR, true));
    }

    @Test
    void picksShortestPath() {
        Route r = PathFinder.findRoute(g, 1, 3, RoutePreference.ANY).orElseThrow();
        assertEquals(List.of(1, 2, 3), ids(r));
        assertEquals(20.0, r.totalDistanceM(), 1e-9);
    }

    @Test
    void worksInReverseDirection() {
        Route r = PathFinder.findRoute(g, 3, 1, RoutePreference.ANY).orElseThrow();
        assertEquals(List.of(3, 2, 1), ids(r));
    }

    @Test
    void anyPreferenceTakesShortStairs() {
        Route r = PathFinder.findRoute(g, 1, 4, RoutePreference.ANY).orElseThrow();
        assertEquals(25.0, r.totalDistanceM(), 1e-9);
        assertEquals(EdgeType.STAIRS, r.edges().get(r.edges().size() - 1).edgeType());
    }

    @Test
    void elevatorPreferenceAvoidsStairs() {
        Route r = PathFinder.findRoute(g, 1, 4, RoutePreference.ELEVATOR).orElseThrow();
        assertEquals(40.0, r.totalDistanceM(), 1e-9);
        assertEquals(EdgeType.ELEVATOR, r.edges().get(r.edges().size() - 1).edgeType());
    }

    @Test
    void stairsPreferenceAvoidsElevator() {
        Route r = PathFinder.findRoute(g, 1, 4, RoutePreference.STAIRS).orElseThrow();
        assertEquals(25.0, r.totalDistanceM(), 1e-9);
    }

    @Test
    void noRouteReturnsEmpty() {
        assertEquals(Optional.empty(), PathFinder.findRoute(g, 1, 5, RoutePreference.ANY));
    }

    @Test
    void sameStartAndDestinationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PathFinder.findRoute(g, 1, 1, RoutePreference.ANY));
    }

    @Test
    void unknownNodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PathFinder.findRoute(g, 1, 99, RoutePreference.ANY));
        assertThrows(IllegalArgumentException.class, () -> PathFinder.findRoute(g, 99, 1, RoutePreference.ANY));
    }

    @Test
    void edgeToMissingNodeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> g.addEdge(new Edge(9, 1, 99, 5, EdgeType.WALK, true)));
    }

    @Test
    void routeEndpointsAreStartAndDestination() {
        Route r = PathFinder.findRoute(g, 1, 3, RoutePreference.ANY).orElseThrow();
        assertTrue(r.nodes().get(0).id() == 1 && r.nodes().get(r.nodes().size() - 1).id() == 3);
        assertEquals(r.nodes().size() - 1, r.edges().size());
    }
}

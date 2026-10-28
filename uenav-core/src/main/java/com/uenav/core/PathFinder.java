package com.uenav.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/** Dijkstra shortest path over a Graph, weighted by Edge.distanceM. */
public final class PathFinder {

    private PathFinder() { }

    /**
     * @throws IllegalArgumentException if start equals destination or either node is unknown
     * @return the shortest route, or empty if no route exists under the given preference
     */
    public static Optional<Route> findRoute(Graph graph, int startId, int destId, RoutePreference pref) {
        if (startId == destId) {
            throw new IllegalArgumentException("Start and destination must be different");
        }
        Node start = graph.node(startId).orElseThrow(() -> new IllegalArgumentException("Unknown start node " + startId));
        graph.node(destId).orElseThrow(() -> new IllegalArgumentException("Unknown destination node " + destId));

        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Edge> cameBy = new HashMap<>();
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        dist.put(start.id(), 0.0);
        queue.add(new double[] {0.0, start.id()});

        while (!queue.isEmpty()) {
            double[] top = queue.poll();
            double d = top[0];
            int current = (int) top[1];
            if (d > dist.getOrDefault(current, Double.MAX_VALUE)) {
                continue; // stale queue entry
            }
            if (current == destId) {
                break;
            }
            for (Edge e : graph.edgesAt(current)) {
                if (!allowed(e, pref)) {
                    continue;
                }
                int next = e.fromNodeId() == current ? e.toNodeId() : e.fromNodeId();
                double nd = d + e.distanceM();
                if (nd < dist.getOrDefault(next, Double.MAX_VALUE)) {
                    dist.put(next, nd);
                    cameBy.put(next, e);
                    queue.add(new double[] {nd, next});
                }
            }
        }

        if (!dist.containsKey(destId)) {
            return Optional.empty();
        }
        return Optional.of(build(graph, startId, destId, cameBy, dist.get(destId)));
    }

    /**
     * Assumed rules (change here if the project decides otherwise):
     * ANY = every edge. STAIRS = no elevator edges. ELEVATOR = no stairs edges and only accessible edges.
     */
    static boolean allowed(Edge e, RoutePreference pref) {
        return switch (pref) {
            case ANY -> true;
            case STAIRS -> e.edgeType() != EdgeType.ELEVATOR;
            case ELEVATOR -> e.edgeType() != EdgeType.STAIRS && e.accessible();
        };
    }

    private static Route build(Graph graph, int startId, int destId, Map<Integer, Edge> cameBy, double total) {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        int at = destId;
        nodes.add(graph.node(at).orElseThrow());
        while (at != startId) {
            Edge e = cameBy.get(at);
            edges.add(e);
            at = e.fromNodeId() == at ? e.toNodeId() : e.fromNodeId();
            nodes.add(graph.node(at).orElseThrow());
        }
        Collections.reverse(nodes);
        Collections.reverse(edges);
        return new Route(List.copyOf(nodes), List.copyOf(edges), total);
    }
}

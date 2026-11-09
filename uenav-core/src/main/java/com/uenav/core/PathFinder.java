package com.uenav.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Dijkstra shortest path over a Graph (adjacency list + PriorityQueue), weighted by Edge.distanceM.
 * Also finds the nearest of several places (quick-access buttons, emergency exit) and can report the order in which
 * places were checked, so the graph view can show how the algorithm spreads out from the start.
 */
public final class PathFinder {

    /** A route search together with the places Dijkstra settled, in the order it settled them. */
    public record Trace(Optional<Route> route, List<Integer> visited) { }

    private PathFinder() { }

    /**
     * @throws IllegalArgumentException if start equals destination or either node is unknown
     * @return the shortest route, or empty if no route exists under the given preference
     */
    public static Optional<Route> findRoute(Graph graph, int startId, int destId, RoutePreference pref) {
        return trace(graph, startId, destId, pref).route();
    }

    /** Like findRoute, but also returns the places Dijkstra checked before it reached the destination. */
    public static Trace trace(Graph graph, int startId, int destId, RoutePreference pref) {
        if (startId == destId) {
            throw new IllegalArgumentException("Start and destination must be different");
        }
        graph.node(startId).orElseThrow(() -> new IllegalArgumentException("Unknown start node " + startId));
        graph.node(destId).orElseThrow(() -> new IllegalArgumentException("Unknown destination node " + destId));
        return search(graph, startId, id -> id == destId, pref);
    }

    /**
     * The shortest route to whichever of the targets is closest (for "nearest restroom" or "nearest exit").
     * The start itself is never a target.
     * @throws IllegalArgumentException if the start node is unknown
     * @return empty if there are no targets or none can be reached under the preference
     */
    public static Optional<Route> findNearest(Graph graph, int startId, Collection<Integer> targetIds,
                                              RoutePreference pref) {
        graph.node(startId).orElseThrow(() -> new IllegalArgumentException("Unknown start node " + startId));
        Set<Integer> targets = new HashSet<>(targetIds);
        targets.remove(startId);
        targets.removeIf(id -> graph.node(id).isEmpty());
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        return search(graph, startId, targets::contains, pref).route();
    }

    /**
     * Walking distance in metres from the start to every node it can reach (the start itself is 0), for lists sorted
     * "nearest first". Nodes that cannot be reached are left out.
     */
    public static Map<Integer, Double> distancesFrom(Graph graph, int startId, RoutePreference pref) {
        Map<Integer, Double> dist = new HashMap<>();
        if (graph.node(startId).isEmpty()) {
            return dist;
        }
        Set<Integer> settled = new HashSet<>();
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        dist.put(startId, 0.0);
        queue.add(new double[] {0.0, startId});
        while (!queue.isEmpty()) {
            double[] top = queue.poll();
            int current = (int) top[1];
            if (!settled.add(current)) {
                continue;
            }
            for (Edge e : graph.edgesAt(current)) {
                if (!allowed(e, pref)) {
                    continue;
                }
                int next = e.fromNodeId() == current ? e.toNodeId() : e.fromNodeId();
                double nd = top[0] + e.distanceM();
                if (nd < dist.getOrDefault(next, Double.MAX_VALUE)) {
                    dist.put(next, nd);
                    queue.add(new double[] {nd, next});
                }
            }
        }
        return dist;
    }

    /** Every node that can be reached from the start under the preference (the start included). */
    public static Set<Integer> reachable(Graph graph, int startId, RoutePreference pref) {
        Set<Integer> seen = new HashSet<>();
        List<Integer> stack = new ArrayList<>();
        if (graph.node(startId).isEmpty()) {
            return seen;
        }
        stack.add(startId);
        seen.add(startId);
        while (!stack.isEmpty()) {
            int at = stack.remove(stack.size() - 1);
            for (Edge e : graph.edgesAt(at)) {
                if (!allowed(e, pref)) {
                    continue;
                }
                int next = e.fromNodeId() == at ? e.toNodeId() : e.fromNodeId();
                if (seen.add(next)) {
                    stack.add(next);
                }
            }
        }
        return seen;
    }

    private static Trace search(Graph graph, int startId, IntPredicate isTarget, RoutePreference pref) {
        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Edge> cameBy = new HashMap<>();
        Set<Integer> settled = new HashSet<>();
        List<Integer> visited = new ArrayList<>();
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        dist.put(startId, 0.0);
        queue.add(new double[] {0.0, startId});
        Integer reached = null;

        while (!queue.isEmpty()) {
            double[] top = queue.poll();
            double d = top[0];
            int current = (int) top[1];
            if (!settled.add(current)) {
                continue; // stale queue entry
            }
            visited.add(current);
            if (current != startId && isTarget.test(current)) {
                reached = current;
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

        if (reached == null) {
            return new Trace(Optional.empty(), List.copyOf(visited));
        }
        return new Trace(Optional.of(build(graph, startId, reached, cameBy, dist.get(reached))),
                List.copyOf(visited));
    }

    /**
     * Assumed rules (change here if the project decides otherwise):
     * ANY = every edge. STAIRS = no elevator edges. ELEVATOR = no stairs edges and only accessible edges
     * (this is also the "avoid stairs" accessibility mode).
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

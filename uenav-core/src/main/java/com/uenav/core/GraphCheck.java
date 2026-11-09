package com.uenav.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Looks for mistakes in the map data that would give visitors wrong or missing routes: places nothing connects to,
 * floors with no stairs or elevator, places only reachable by stairs, stairs drawn on one floor, and outdoor
 * walkways whose distance does not fit the map. Used by the admin dashboard ("Map health").
 */
public final class GraphCheck {

    /** Metres per pixel of the outdoor campus map (campus area 4.86 ha / outline area; see the seed data). */
    public static final double CAMPUS_METERS_PER_PIXEL = 0.269;

    public enum Severity { PROBLEM, WARNING }

    /** One finding. nodeIds are the places involved, so the admin app can show them. */
    public record Issue(Severity severity, String title, String detail, List<Integer> nodeIds) {

        public Issue {
            nodeIds = List.copyOf(nodeIds);
        }
    }

    private GraphCheck() { }

    /** Every finding, problems first. An empty list means the map looks healthy. */
    public static List<Issue> check(CampusData data) {
        Graph g = data.graph();
        List<Issue> problems = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        if (g.nodeCount() == 0) {
            problems.add(new Issue(Severity.PROBLEM, "The map is empty", "Add places and walkways first.", List.of()));
            return problems;
        }

        // 1. places with no walkway at all
        Set<Integer> lonely = new HashSet<>();
        for (Node n : g.nodes()) {
            if (g.edgesAt(n.id()).isEmpty()) {
                lonely.add(n.id());
                problems.add(new Issue(Severity.PROBLEM, n.name() + " has no walkways",
                        "No route can start or end here. Add a walkway to the nearest junction.", List.of(n.id())));
            }
        }

        // 2. places cut off from the main network (the largest connected part)
        Set<Integer> main = largestPart(g);
        for (Node n : sorted(g.nodes())) {
            if (!main.contains(n.id()) && !lonely.contains(n.id())) {
                problems.add(new Issue(Severity.PROBLEM, n.name() + " is cut off from the campus",
                        "It has walkways, but none of them lead to the rest of the map.", List.of(n.id())));
            }
        }

        // 3. step-free access: places reachable from the campus only by stairs
        Integer anchor = anchor(data, main);
        if (anchor != null) {
            Set<Integer> stepFree = PathFinder.reachable(g, anchor, RoutePreference.ELEVATOR);
            List<Integer> stairsOnly = new ArrayList<>();
            for (Node n : sorted(data.places())) {
                if (main.contains(n.id()) && !stepFree.contains(n.id())) {
                    stairsOnly.add(n.id());
                }
            }
            if (!stairsOnly.isEmpty()) {
                warnings.add(new Issue(Severity.WARNING, stairsOnly.size() + (stairsOnly.size() == 1
                        ? " place has" : " places have") + " no step-free route",
                        "Wheelchair users and \"Avoid stairs\" routes cannot reach: " + names(g, stairsOnly, 5)
                                + ". Add an elevator or ramp, or mark accessible walkways.", stairsOnly));
            }
        }

        // 4. stairs/elevator edges that do not change floor, and floors without a way up or down
        Map<Integer, Integer> liftsPerFloor = new HashMap<>();
        for (Edge e : g.edges()) {
            Node a = g.node(e.fromNodeId()).orElseThrow();
            Node b = g.node(e.toNodeId()).orElseThrow();
            if (e.edgeType() != EdgeType.WALK) {
                if (a.floorId() != null && a.floorId().equals(b.floorId())) {
                    warnings.add(new Issue(Severity.WARNING,
                            word(e.edgeType()) + " link " + a.name() + " – " + b.name() + " stays on one floor",
                            "Stairs and elevator walkways should join two different floors.", List.of(a.id(), b.id())));
                }
                if (a.floorId() != null) {
                    liftsPerFloor.merge(a.floorId(), 1, Integer::sum);
                }
                if (b.floorId() != null) {
                    liftsPerFloor.merge(b.floorId(), 1, Integer::sum);
                }
            }
        }
        for (Building b : data.buildings()) {
            List<Floor> floors = data.floorsOf(b.id());
            if (floors.size() < 2) {
                continue;
            }
            for (Floor f : floors) {
                if (!liftsPerFloor.containsKey(f.id())) {
                    warnings.add(new Issue(Severity.WARNING, b.name() + " " + f.floorName() + " has no stairs or elevator",
                            "Rooms on this floor can only be reached if a STAIRS or ELEVATOR walkway joins it to "
                                    + "another floor.", List.of()));
                }
            }
        }

        // 5. outdoor walkway distances that do not fit the map picture
        for (Edge e : g.edges()) {
            Node a = g.node(e.fromNodeId()).orElseThrow();
            Node b = g.node(e.toNodeId()).orElseThrow();
            if (e.edgeType() != EdgeType.WALK || a.floorId() != null || b.floorId() != null) {
                continue;
            }
            double straight = Math.hypot(a.x() - b.x(), a.y() - b.y()) * CAMPUS_METERS_PER_PIXEL;
            boolean tooShort = e.distanceM() + 15 < straight * 0.9;
            boolean tooLong = e.distanceM() > straight * 2.5 + 15;
            if (tooShort || tooLong) {
                warnings.add(new Issue(Severity.WARNING,
                        "Walkway " + a.name() + " – " + b.name() + " may have a wrong distance",
                        String.format(Locale.ROOT, "It says %.0f m, but the two points are about %.0f m apart on the "
                                + "map. Check the distance or the positions.", e.distanceM(), straight),
                        List.of(a.id(), b.id())));
            }
        }

        // 6. the same name twice (visitors cannot tell them apart in the search list)
        Map<String, List<Integer>> byName = new HashMap<>();
        for (Node n : sorted(data.places())) {
            byName.computeIfAbsent(n.name().trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(n.id());
        }
        for (List<Integer> ids : byName.values()) {
            if (ids.size() > 1) {
                String name = g.node(ids.get(0)).orElseThrow().name();
                warnings.add(new Issue(Severity.WARNING, ids.size() + " places are named \"" + name + "\"",
                        "Add the building or floor to the names so visitors can tell them apart.", ids));
            }
        }

        // 7. offices without opening hours
        List<Integer> noHours = new ArrayList<>();
        for (Node n : sorted(data.places())) {
            if ("OFFICE".equalsIgnoreCase(n.nodeType()) && (n.openHours() == null || n.openHours().isBlank())) {
                noHours.add(n.id());
            }
        }
        if (!noHours.isEmpty()) {
            warnings.add(new Issue(Severity.WARNING, noHours.size() + (noHours.size() == 1 ? " office has"
                    : " offices have") + " no office hours", "Visitors see the hours on the office's info card: "
                    + names(g, noHours, 5) + ".", noHours));
        }

        problems.addAll(warnings);
        return problems;
    }

    /** The nodes of the largest connected part of the graph (all edge types). */
    static Set<Integer> largestPart(Graph g) {
        Set<Integer> best = Set.of();
        Set<Integer> seen = new HashSet<>();
        for (Node n : sorted(g.nodes())) {
            if (seen.contains(n.id())) {
                continue;
            }
            Set<Integer> part = PathFinder.reachable(g, n.id(), RoutePreference.ANY);
            seen.addAll(part);
            if (part.size() > best.size()) {
                best = part;
            }
        }
        return best;
    }

    /** Where step-free checks start: a gate on the main network, else any outdoor node on it. */
    private static Integer anchor(CampusData data, Set<Integer> main) {
        Integer outdoor = null;
        for (Node n : sorted(data.graph().nodes())) {
            if (!main.contains(n.id()) || n.floorId() != null) {
                continue;
            }
            if ("GATE".equalsIgnoreCase(n.nodeType())) {
                return n.id();
            }
            if (outdoor == null) {
                outdoor = n.id();
            }
        }
        return outdoor;
    }

    private static List<Node> sorted(List<Node> nodes) {
        List<Node> list = new ArrayList<>(nodes);
        list.sort((a, b) -> Integer.compare(a.id(), b.id()));
        return list;
    }

    private static String names(Graph g, List<Integer> ids, int max) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(max, ids.size()); i++) {
            names.add(g.node(ids.get(i)).map(Node::name).orElse("#" + ids.get(i)));
        }
        String s = String.join(", ", names);
        return ids.size() > max ? s + " and " + (ids.size() - max) + " more" : s;
    }

    private static String word(EdgeType t) {
        return t == EdgeType.STAIRS ? "Stairs" : "Elevator";
    }
}

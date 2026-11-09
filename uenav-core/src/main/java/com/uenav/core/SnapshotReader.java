package com.uenav.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns the server's snapshot JSON (GET /api/snapshot) into a Graph the app can route over. */
public final class SnapshotReader {

    /** Node types that are only waypoints and are not offered in the start/destination lists. */
    private static final Set<String> NOT_SEARCHABLE = Set.of("JUNCTION", "STAIRS", "ELEVATOR");

    private SnapshotReader() { }

    /** @throws IllegalArgumentException if the JSON is malformed or a required field is missing */
    public static CampusData read(String json) {
        Map<String, Object> root = asObject(MiniJson.parse(json), "snapshot");
        String version = getString(root, "version");

        Graph graph = new Graph();
        List<Node> places = new ArrayList<>();
        for (Object o : asList(root.get("nodes"), "nodes")) {
            Map<String, Object> n = asObject(o, "node");
            Node node = new Node(getInt(n, "id"), getNullableInt(n, "buildingId"),
                    getNullableInt(n, "floorId"), getString(n, "name"),
                    getInt(n, "x"), getInt(n, "y"), getString(n, "nodeType"),
                    getNullableString(n, "description"), getNullableString(n, "openHours"));
            graph.addNode(node);
            if (isSearchable(node.nodeType())) {
                places.add(node);
            }
        }
        for (Object o : asList(root.get("edges"), "edges")) {
            Map<String, Object> e = asObject(o, "edge");
            EdgeType type;
            try {
                type = EdgeType.valueOf(getString(e, "edgeType"));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown edgeType: " + e.get("edgeType"));
            }
            graph.addEdge(new Edge(getInt(e, "id"), getInt(e, "fromNodeId"), getInt(e, "toNodeId"),
                    getDouble(e, "distanceM"), type, getBool(e, "accessible")));
        }
        List<Building> buildings = new ArrayList<>();
        if (root.get("buildings") != null) {
            for (Object o : asList(root.get("buildings"), "buildings")) {
                Map<String, Object> b = asObject(o, "building");
                buildings.add(new Building(getInt(b, "id"), getString(b, "name"), getNullableString(b, "category"),
                        getNullableString(b, "description"), getNullableInt(b, "mapX"), getNullableInt(b, "mapY"),
                        getNullableString(b, "imagePath")));
            }
        }
        List<Floor> floors = new ArrayList<>();
        if (root.get("floors") != null) {
            for (Object o : asList(root.get("floors"), "floors")) {
                Map<String, Object> f = asObject(o, "floor");
                floors.add(new Floor(getInt(f, "id"), getInt(f, "buildingId"), getInt(f, "floorNumber"),
                        getString(f, "floorName"), getNullableString(f, "planImagePath")));
            }
        }
        return new CampusData(version, graph, places, buildings, floors);
    }

    /** False for waypoints (junctions, stairs, elevators) that are not offered as destinations. */
    public static boolean isSearchable(String nodeType) {
        return !NOT_SEARCHABLE.contains(nodeType);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object o, String what) {
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object for " + what);
        }
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o, String what) {
        if (!(o instanceof List)) {
            throw new IllegalArgumentException("Expected a JSON array for " + what);
        }
        return (List<Object>) o;
    }

    private static Object required(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return v;
    }

    private static String getString(Map<String, Object> m, String key) {
        Object v = required(m, key);
        if (!(v instanceof String)) {
            throw new IllegalArgumentException("Field " + key + " must be text");
        }
        return (String) v;
    }

    private static double getDouble(Map<String, Object> m, String key) {
        Object v = required(m, key);
        if (!(v instanceof Double)) {
            throw new IllegalArgumentException("Field " + key + " must be a number");
        }
        return (Double) v;
    }

    private static int getInt(Map<String, Object> m, String key) {
        return (int) getDouble(m, key);
    }

    private static Integer getNullableInt(Map<String, Object> m, String key) {
        return m.get(key) == null ? null : getInt(m, key);
    }

    private static String getNullableString(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (!(v instanceof String)) {
            throw new IllegalArgumentException("Field " + key + " must be text");
        }
        return (String) v;
    }

    private static boolean getBool(Map<String, Object> m, String key) {
        Object v = required(m, key);
        if (!(v instanceof Boolean)) {
            throw new IllegalArgumentException("Field " + key + " must be true or false");
        }
        return (Boolean) v;
    }
}
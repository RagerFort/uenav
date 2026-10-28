package com.uenav.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The campus map as nodes and edges. Edges can be walked in both directions. */
public class Graph {

    private final Map<Integer, Node> nodes = new HashMap<>();
    private final Map<Integer, List<Edge>> edgesByNode = new HashMap<>();

    public void addNode(Node node) {
        nodes.put(node.id(), node);
        edgesByNode.computeIfAbsent(node.id(), k -> new ArrayList<>());
    }

    public void addEdge(Edge edge) {
        if (!nodes.containsKey(edge.fromNodeId()) || !nodes.containsKey(edge.toNodeId())) {
            throw new IllegalArgumentException("Edge " + edge.id() + " refers to a node that is not in the graph");
        }
        edgesByNode.get(edge.fromNodeId()).add(edge);
        edgesByNode.get(edge.toNodeId()).add(edge);
    }

    public Optional<Node> node(int id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public List<Edge> edgesAt(int nodeId) {
        return edgesByNode.getOrDefault(nodeId, List.of());
    }

    public int nodeCount() {
        return nodes.size();
    }
}

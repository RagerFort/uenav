package com.uenav.mobile;

import com.uenav.core.Edge;
import com.uenav.core.EdgeType;
import com.uenav.core.Graph;
import com.uenav.core.Node;

/**
 * Tiny made-up campus used only to prove that routing runs on the phone with no network.
 * Names follow the Figma screens. Distances are NOT real measurements.
 */
public final class SampleData {

    private SampleData() { }

    private static final int ENG = 1; // building id of the Engineering Building

    public static Graph graph() {
        Graph g = new Graph();
        g.addNode(new Node(1, null, null, "University Hills Gate", 0, 0, "GATE"));
        g.addNode(new Node(2, null, null, "Campus junction", 120, 0, "JUNCTION"));
        g.addNode(new Node(3, null, null, "Registrar's Office", 120, -60, "OFFICE"));
        g.addNode(new Node(4, ENG, 1, "Engineering Building entrance", 250, 0, "ENTRANCE"));
        g.addNode(new Node(5, ENG, 1, "EN lobby (1F)", 255, 10, "JUNCTION"));
        g.addNode(new Node(6, ENG, 1, "EN stairs (1F)", 260, 20, "STAIRS"));
        g.addNode(new Node(7, ENG, 1, "EN elevator (1F)", 270, 10, "ELEVATOR"));
        g.addNode(new Node(8, ENG, 4, "EN stairs (4F)", 260, 20, "STAIRS"));
        g.addNode(new Node(9, ENG, 4, "EN elevator (4F)", 270, 10, "ELEVATOR"));
        g.addNode(new Node(10, ENG, 4, "EN 420 (4F)", 300, 10, "ROOM"));

        g.addEdge(new Edge(1, 1, 2, 120, EdgeType.WALK, true));
        g.addEdge(new Edge(2, 2, 3, 60, EdgeType.WALK, true));
        g.addEdge(new Edge(3, 2, 4, 130, EdgeType.WALK, true));
        g.addEdge(new Edge(4, 4, 5, 10, EdgeType.WALK, true));
        g.addEdge(new Edge(5, 5, 6, 10, EdgeType.WALK, true));
        g.addEdge(new Edge(6, 5, 7, 15, EdgeType.WALK, true));
        g.addEdge(new Edge(7, 6, 8, 25, EdgeType.STAIRS, false));
        g.addEdge(new Edge(8, 7, 9, 30, EdgeType.ELEVATOR, true));
        g.addEdge(new Edge(9, 8, 10, 25, EdgeType.WALK, true));
        g.addEdge(new Edge(10, 9, 10, 20, EdgeType.WALK, true));
        return g;
    }

    /** Nodes people can pick as start or destination, in display order. */
    public static java.util.List<Node> places(Graph g) {
        java.util.List<Node> list = new java.util.ArrayList<>();
        for (int id : new int[] {1, 3, 4, 10}) {
            list.add(g.node(id).orElseThrow());
        }
        return list;
    }
}

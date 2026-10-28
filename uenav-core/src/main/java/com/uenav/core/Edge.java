package com.uenav.core;

/** One row of the Edge table: a walkable link between two nodes. */
public record Edge(int id, int fromNodeId, int toNodeId, double distanceM,
                   EdgeType edgeType, boolean accessible) {

    public Edge {
        if (distanceM < 0) {
            throw new IllegalArgumentException("distanceM must not be negative");
        }
    }
}

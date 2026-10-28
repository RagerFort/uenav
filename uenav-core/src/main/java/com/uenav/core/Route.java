package com.uenav.core;

import java.util.List;

/** Result of a route search: nodes in walking order and the edges between them. */
public record Route(List<Node> nodes, List<Edge> edges, double totalDistanceM) { }

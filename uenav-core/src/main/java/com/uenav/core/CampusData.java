package com.uenav.core;

import java.util.List;

/** The map as the app uses it: a version, the routing graph, and the nodes people can pick. */
public record CampusData(String version, Graph graph, List<Node> places) { }

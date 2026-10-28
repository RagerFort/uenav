package com.uenav.mobile;

import com.uenav.core.Edge;
import com.uenav.core.EdgeType;
import com.uenav.core.Node;
import com.uenav.core.Route;
import java.util.Locale;

/** Turns a Route into plain text steps. Assumes a walking speed of 80 m per minute. */
public final class RouteFormatter {

    static final double METERS_PER_MINUTE = 80.0;

    private RouteFormatter() { }

    public static String describe(Route route) {
        StringBuilder sb = new StringBuilder();
        int minutes = Math.max(1, (int) Math.round(route.totalDistanceM() / METERS_PER_MINUTE));
        sb.append(String.format(Locale.ROOT, "~%d min walk, ~%d m%n%n", minutes, Math.round(route.totalDistanceM())));
        sb.append("Start at ").append(route.nodes().get(0).name()).append('\n');
        for (int i = 0; i < route.edges().size(); i++) {
            Edge e = route.edges().get(i);
            Node to = route.nodes().get(i + 1);
            long m = Math.round(e.distanceM());
            String verb = switch (e.edgeType()) {
                case WALK -> "Walk";
                case STAIRS -> "Take the stairs";
                case ELEVATOR -> "Take the elevator";
            };
            sb.append(i + 1).append(". ").append(verb).append(" to ").append(to.name())
              .append(" (").append(m).append(" m)").append('\n');
        }
        sb.append("Arrive at ").append(route.nodes().get(route.nodes().size() - 1).name());
        return sb.toString();
    }

    static boolean usesEdgeType(Route r, EdgeType t) {
        return r.edges().stream().anyMatch(e -> e.edgeType() == t);
    }
}

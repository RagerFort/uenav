package com.uenav.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns a Route into the step list shown to visitors ("Start walking", "Turn left", "Take the elevator to 4th floor",
 * "Arrive at ..."). Turns are worked out from node X/Y, which are map-picture pixels: X grows to the right and
 * Y grows downwards. Walking time assumes 80 m per minute; there is no separate time model for stairs or elevators.
 */
public final class Directions {

    public static final double METERS_PER_MINUTE = 80.0;

    /** Heading changes smaller than this (degrees) count as "straight on". */
    static final double STRAIGHT_DEGREES = 35.0;
    /** Heading changes larger than this (degrees) count as "turn around". */
    static final double BACK_DEGREES = 150.0;
    /** Walks shorter than this (metres) are small kinks in the walkway, not a step of their own. */
    static final double MIN_STEP_M = 20.0;

    public enum StepKind { START, STRAIGHT, LEFT, RIGHT, BACK, STAIRS, ELEVATOR, ARRIVE }

    /**
     * One line of the directions. at is the place where the step starts (the turn, the stairs, or the destination
     * for ARRIVE), so the map can switch to its floor and show it; it is null for steps made without a route.
     */
    public record Step(StepKind kind, String title, String detail, Node at) {

        public Step(StepKind kind, String title, String detail) {
            this(kind, title, detail, null);
        }
    }

    /** Everything the directions screen needs about one route. */
    public record Summary(int minutes, long meters, boolean indoor, boolean outdoor, int flights,
                          boolean usesStairs, boolean usesElevator, List<Step> steps) {

        /** "Outdoor route", "Indoor route" or "Outdoor + indoor". */
        public String areaLabel() {
            if (indoor && outdoor) {
                return "Outdoor + indoor";
            }
            return indoor ? "Indoor route" : "Outdoor route";
        }
    }

    /** turnFrom: first edge used to tell the direction of a leg (after any short kink folded into it). */
    private record Leg(EdgeType type, int from, int to, double meters, int turnFrom) {
        Leg(EdgeType type, int from, int to, double meters) {
            this(type, from, to, meters, from);
        }
    }

    private Directions() { }

    /** Walking minutes for a distance, at least 1. */
    public static int minutes(double meters) {
        return Math.max(1, (int) Math.round(meters / METERS_PER_MINUTE));
    }

    public static Summary summarize(Route route, CampusData data) {
        boolean indoor = false;
        boolean outdoor = false;
        for (Node n : route.nodes()) {
            if (n.floorId() == null) {
                outdoor = true;
            } else {
                indoor = true;
            }
        }
        int flights = 0;
        boolean stairs = false;
        boolean elevator = false;
        for (int i = 0; i < route.edges().size(); i++) {
            Edge e = route.edges().get(i);
            if (e.edgeType() == EdgeType.WALK) {
                continue;
            }
            stairs |= e.edgeType() == EdgeType.STAIRS;
            elevator |= e.edgeType() == EdgeType.ELEVATOR;
            flights += floorsBetween(route.nodes().get(i), route.nodes().get(i + 1), data);
        }
        return new Summary(minutes(route.totalDistanceM()), Math.round(route.totalDistanceM()), indoor, outdoor,
                flights, stairs, elevator, steps(route, data));
    }

    public static List<Step> steps(Route route, CampusData data) {
        List<Node> nodes = route.nodes();
        List<Edge> edges = route.edges();
        List<Step> steps = new ArrayList<>();
        Node last = nodes.get(nodes.size() - 1);
        List<Leg> legs = legs(nodes, edges);

        Leg previous = null;
        for (Leg leg : legs) {
            Node end = nodes.get(leg.to());
            if (leg.type() != EdgeType.WALK) {
                boolean isStairs = leg.type() == EdgeType.STAIRS;
                Node start = nodes.get(leg.from());
                String target = data.floor(end.floorId()).map(Floor::floorName).orElse(end.name());
                int floors = floorsBetween(start, end, data);
                String way = floorNumber(end, data) >= floorNumber(start, data) ? "Up" : "Down";
                String detail = floors > 0
                        ? way + " " + floors + (floors == 1 ? " floor" : " floors") + " · ~" + meters(leg.meters())
                        : "~" + meters(leg.meters());
                steps.add(new Step(isStairs ? StepKind.STAIRS : StepKind.ELEVATOR,
                        (isStairs ? "Take the stairs to " : "Take the elevator to ") + target, detail, start));
            } else {
                StringBuilder detail = new StringBuilder("Walk ~").append(meters(leg.meters()));
                if (end != last && SnapshotReader.isSearchable(end.nodeType())) {
                    detail.append(" to ").append(end.name());
                }
                List<String> passed = passed(nodes, leg);
                if (!passed.isEmpty()) {
                    detail.append(" · pass ").append(String.join(", ", passed));
                }
                StepKind kind;
                String title;
                if (previous == null) {
                    kind = StepKind.START;
                    title = "Start walking";
                } else if (previous.type() != EdgeType.WALK) {
                    kind = StepKind.STRAIGHT;
                    title = "Exit and continue walking";
                } else {
                    kind = turn(heading(nodes, previous, true), heading(nodes, leg, false));
                    title = switch (kind) {
                        case LEFT -> "Turn left";
                        case RIGHT -> "Turn right";
                        case BACK -> "Turn around";
                        default -> "Continue straight";
                    };
                    if (kind != StepKind.STRAIGHT) {
                        title += landmark(nodes, previous, leg);
                    }
                }
                steps.add(new Step(kind, title, detail.toString(), nodes.get(leg.from())));
            }
            previous = leg;
        }

        String where = place(last, data);
        steps.add(new Step(StepKind.ARRIVE, "Arrive at " + last.name(),
                where.isEmpty() ? "You've reached your destination" : where, last));
        return steps;
    }

    /**
     * " at Registrar's Office" when the turn is at a named place, " after the Gazebo" when a named place was passed
     * just before it, or "" when there is no landmark (junction names are waypoints, not landmarks).
     */
    private static String landmark(List<Node> nodes, Leg previous, Leg leg) {
        Node turnAt = nodes.get(leg.from());
        if (SnapshotReader.isSearchable(turnAt.nodeType())) {
            return " at " + turnAt.name();
        }
        for (int i = previous.to() - 1; i > previous.from(); i--) {
            Node n = nodes.get(i);
            if (SnapshotReader.isSearchable(n.nodeType())) {
                return " after " + n.name();
            }
        }
        return "";
    }

    /** "Engineering Building · 4th floor", or "" for an outdoor place. */
    public static String place(Node n, CampusData data) {
        Optional<Building> b = data.building(n.buildingId());
        Optional<Floor> f = data.floor(n.floorId());
        if (b.isPresent() && f.isPresent()) {
            return b.get().name() + " · " + f.get().floorName();
        }
        return b.map(Building::name).orElse(f.map(Floor::floorName).orElse(""));
    }

    /** Groups edges into legs: one per stairs/elevator edge, and walk edges merged while they keep going straight. */
    private static List<Leg> legs(List<Node> nodes, List<Edge> edges) {
        List<Leg> legs = new ArrayList<>();
        int legStart = 0;
        double legMeters = 0;
        double[] lastHeading = null;
        for (int i = 0; i < edges.size(); i++) {
            Edge e = edges.get(i);
            if (e.edgeType() != EdgeType.WALK) {
                if (i > legStart) {
                    legs.add(new Leg(EdgeType.WALK, legStart, i, legMeters));
                }
                legs.add(new Leg(e.edgeType(), i, i + 1, e.distanceM()));
                legStart = i + 1;
                legMeters = 0;
                lastHeading = null;
                continue;
            }
            double[] h = vector(nodes.get(i), nodes.get(i + 1));
            if (h != null && lastHeading != null && turn(lastHeading, h) != StepKind.STRAIGHT && i > legStart) {
                legs.add(new Leg(EdgeType.WALK, legStart, i, legMeters));
                legStart = i;
                legMeters = 0;
            }
            if (h != null) {
                lastHeading = h;
            }
            legMeters += e.distanceM();
        }
        if (edges.size() > legStart) {
            legs.add(new Leg(EdgeType.WALK, legStart, edges.size(), legMeters));
        }
        return joinStraightWalks(nodes, foldShortWalks(legs));
    }

    /** Joins two walks in a row that go the same way (left over after folding a kink) into one. */
    private static List<Leg> joinStraightWalks(List<Node> nodes, List<Leg> legs) {
        List<Leg> out = new ArrayList<>(legs);
        for (int i = 0; i + 1 < out.size(); i++) {
            Leg a = out.get(i);
            Leg b = out.get(i + 1);
            if (a.type() == EdgeType.WALK && b.type() == EdgeType.WALK
                    && turn(heading(nodes, a, true), heading(nodes, b, false)) == StepKind.STRAIGHT) {
                out.set(i, new Leg(EdgeType.WALK, a.from(), b.to(), a.meters() + b.meters(), a.turnFrom()));
                out.remove(i + 1);
                i--;
            }
        }
        return out;
    }

    /** Folds walks shorter than MIN_STEP_M into the next walk (or, at the end, into the walk before). */
    private static List<Leg> foldShortWalks(List<Leg> legs) {
        List<Leg> out = new ArrayList<>(legs);
        for (int i = 0; i + 1 < out.size(); i++) {
            Leg a = out.get(i);
            Leg b = out.get(i + 1);
            if (a.type() == EdgeType.WALK && b.type() == EdgeType.WALK && a.meters() < MIN_STEP_M) {
                out.set(i, new Leg(EdgeType.WALK, a.from(), b.to(), a.meters() + b.meters(), b.turnFrom()));
                out.remove(i + 1);
                i--;
            }
        }
        int last = out.size() - 1;
        if (last > 0) {
            Leg a = out.get(last - 1);
            Leg b = out.get(last);
            if (a.type() == EdgeType.WALK && b.type() == EdgeType.WALK && b.meters() < MIN_STEP_M) {
                out.set(last - 1, new Leg(EdgeType.WALK, a.from(), b.to(), a.meters() + b.meters(), a.turnFrom()));
                out.remove(last);
            }
        }
        return out;
    }

    /** First (or last) non-zero direction of travel in a leg; null if every node in it is at the same spot. */
    private static double[] heading(List<Node> nodes, Leg leg, boolean lastOne) {
        double[] found = null;
        for (int i = lastOne ? leg.from() : leg.turnFrom(); i < leg.to(); i++) {
            double[] v = vector(nodes.get(i), nodes.get(i + 1));
            if (v != null) {
                found = v;
                if (!lastOne) {
                    return v;
                }
            }
        }
        return found;
    }

    private static double[] vector(Node a, Node b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        return dx == 0 && dy == 0 ? null : new double[] {dx, dy};
    }

    /** Y grows downwards, so a positive cross product is a clockwise (right) turn. */
    static StepKind turn(double[] before, double[] after) {
        if (before == null || after == null) {
            return StepKind.STRAIGHT;
        }
        double cross = before[0] * after[1] - before[1] * after[0];
        double dot = before[0] * after[0] + before[1] * after[1];
        double degrees = Math.toDegrees(Math.atan2(Math.abs(cross), dot));
        if (degrees < STRAIGHT_DEGREES) {
            return StepKind.STRAIGHT;
        }
        if (degrees > BACK_DEGREES) {
            return StepKind.BACK;
        }
        return cross > 0 ? StepKind.RIGHT : StepKind.LEFT;
    }

    /** Named places walked past inside a leg (not its ends), at most two. */
    private static List<String> passed(List<Node> nodes, Leg leg) {
        List<String> names = new ArrayList<>();
        for (int i = leg.from() + 1; i < leg.to() && names.size() < 2; i++) {
            Node n = nodes.get(i);
            if (SnapshotReader.isSearchable(n.nodeType())) {
                names.add(n.name());
            }
        }
        return names;
    }

    private static int floorNumber(Node n, CampusData data) {
        return data.floor(n.floorId()).map(Floor::floorNumber).orElse(1);
    }

    private static int floorsBetween(Node a, Node b, CampusData data) {
        return Math.abs(floorNumber(b, data) - floorNumber(a, data));
    }

    private static String meters(double m) {
        return String.format(Locale.ROOT, "%d m", Math.round(m));
    }
}

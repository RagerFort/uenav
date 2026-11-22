package com.uenav.mobile;

import com.uenav.map.MapView;
import com.uenav.core.CampusData;
import com.uenav.core.Directions;
import com.uenav.core.Edge;
import com.uenav.core.EdgeType;
import com.uenav.core.Floor;
import com.uenav.core.Node;
import com.uenav.core.Route;
import java.util.List;
import java.util.Objects;
import javafx.animation.AnimationTimer;

/**
 * Plays a route on the map step by step (D23). A walker (a white disc with a red arrow) sets off from the start, the
 * part it has walked fills in red with arrows flowing along it, and the step it is on lights up in the list. At each
 * step it slows down, a ring pops and it waits a moment; at stairs, an elevator or a bridge a small tag says where it
 * goes ("Elevator up to 4F") and the map fades over to that floor. At the destination it fades out.
 *
 * The view stays still on each floor (fitted to that floor's part of the route), so each frame only redraws the
 * map's small animation layer: the map itself is drawn once per floor, while it is faded. That keeps it smooth on
 * a phone. Touching the map, a step or a floor tab stops it (Navigator).
 */
final class RoutePlayer {

    interface Host {
        /** Shows another level (null = campus) and selects its floor tab. */
        void level(Integer floorId);

        /** Lights up step {@code index} of the directions list. */
        void step(int index);

        /** The walk ended at the destination, or was stopped. */
        void finished();
    }

    /** Walking speed on screen, and the shortest and longest walk between two steps (seconds). */
    static final double PX_PER_S = 320;
    static final double MIN_WALK_S = 0.6;
    static final double MAX_WALK_S = 2.2;
    static final double INTRO_S = 0.45;
    static final double STEP_PAUSE_S = 0.3;
    static final double TRANSFER_HOLD_S = 0.75;
    static final double FADE_OUT_S = 0.16;
    static final double FADE_IN_S = 0.3;
    static final double END_HOLD_S = 0.9;
    static final double END_FADE_S = 0.45;
    /** How quickly the walker turns to face a new direction (higher is quicker). */
    private static final double TURN_RATE = 12;

    /** One part of the animation; returns true when it is over. elapsed: seconds since it began. */
    private interface Phase {
        boolean run(double elapsed);
    }

    private final MapView map;
    private final CampusData data;
    private final List<Node> nodes;
    private final List<Edge> edges;
    private final List<Integer> levels;
    private final int[] stepAt;
    private final Host host;
    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            // a slow frame (the map being drawn for a new floor) must not make the walker jump
            double dt = last == 0 ? 0 : Math.min(0.05, (now - last) / 1e9);
            last = now;
            tick(dt);
        }
    };
    private long last;
    private boolean running;

    /** The route index the walker is at, or walking away from. */
    private int at;
    private int done;
    private double t;
    private double x;
    private double y;
    private double angle;
    private double targetAngle;
    private double size;
    private double alpha = 1;
    private double clock;
    private int shownStep = -1;
    private double ringX;
    private double ringY;
    private double ringStart = -1;
    private String tag;
    private double tagAlpha;
    private Phase phase;
    private double phaseStart;
    private boolean arrived;

    RoutePlayer(Route route, List<Directions.Step> steps, CampusData data, MapView map, List<Integer> levels,
            Host host) {
        this.nodes = route.nodes();
        this.edges = route.edges();
        this.data = data;
        this.map = map;
        this.levels = List.copyOf(levels.stream().map(lv -> lv == null ? -1 : lv).toList());
        this.stepAt = stepIndexes(nodes, steps);
        this.host = host;
    }

    /** Where in the route each step begins (route node index), in order; the last step (Arrive) at the end. */
    static int[] stepIndexes(List<Node> nodes, List<Directions.Step> steps) {
        int[] at = new int[steps.size()];
        int from = 0;
        for (int s = 0; s < steps.size(); s++) {
            Node n = steps.get(s).at();
            int found = -1;
            for (int i = from; n != null && i < nodes.size(); i++) {
                if (nodes.get(i).id() == n.id()) {
                    found = i;
                    break;
                }
            }
            at[s] = found < 0 ? from : found;
            from = at[s];
        }
        if (at.length > 0) {
            at[at.length - 1] = nodes.size() - 1;
        }
        return at;
    }

    void start() {
        begin();
        last = 0;
        timer.start();
    }

    /** Sets up the first frame without starting the clock (start() does both; still pictures call tick()). */
    void begin() {
        running = true;
        if (nodes.size() < 2) {
            finish();
            return;
        }
        map.setPreviewing(true);
        Integer first = levelFor(0);
        if (!Objects.equals(map.level(), first)) {
            host.level(first);
        } else {
            map.resetView();
        }
        phase = intro();
        phaseStart = clock;
        frame(0);
    }

    boolean running() {
        return running;
    }

    /** Stops the frame clock but leaves the animation where it is (still pictures move it on with tick()). */
    void stopClock() {
        timer.stop();
    }

    /** Stops at once: the map shows the whole route again, as before the animation. */
    void stop() {
        if (running) {
            finish();
        }
    }

    /** Moves the animation on by dt seconds. */
    void tick(double dt) {
        if (!running) {
            return;
        }
        clock += dt;
        while (phase != null && phase.run(clock - phaseStart)) {
            phaseStart = clock;
            phase = next();
        }
        if (phase == null) {
            finish();
            return;
        }
        frame(dt);
    }

    private void finish() {
        running = false;
        timer.stop();
        map.setOpacity(1);
        map.clearPreview();
        map.setPreviewing(false);
        host.finished();
    }

    private void frame(double dt) {
        // turn smoothly toward the new direction, the shorter way round
        double diff = ((targetAngle - angle + 540) % 360) - 180;
        angle = (angle + diff * Math.min(1, dt * TURN_RATE) + 360) % 360;
        map.showPreview(new MapView.Preview(done, t, x, y, angle, size, alpha, clock, ringX, ringY,
                ringStart < 0 ? -1 : clock - ringStart, tag, tagAlpha));
    }

    // ---------------------------------------------------------------- the parts of the walk

    /** What comes next from where the walker stands. */
    private Phase next() {
        if (arrived) {
            return null;
        }
        if (at >= nodes.size() - 1) {
            return arrive();
        }
        int s = stepStartedBy(at);
        if (s > shownStep) {
            highlight(s);
            return pause(STEP_PAUSE_S);
        }
        if (map.shows(nodes.get(at)) && map.shows(nodes.get(at + 1))) {
            return walk();
        }
        return transfer();
    }

    /** The walker appears at the start with a little bounce, and the first step lights up. */
    private Phase intro() {
        at = 0;
        done = 0;
        t = 0;
        place(nodes.get(0));
        for (int i = 0; i + 1 < nodes.size(); i++) {
            if (length(nodes.get(i), nodes.get(i + 1)) > 0) {
                targetAngle = heading(nodes.get(i), nodes.get(i + 1));
                break;
            }
        }
        angle = targetAngle;
        size = 0;
        highlight(0);
        return el -> {
            double k = Math.min(1, el / INTRO_S);
            size = easeOutBack(k);
            return k >= 1;
        };
    }

    private static Phase pause(double seconds) {
        return el -> el >= seconds;
    }

    /** Walks from here to the next step (or to where this level's part ends), speeding up and slowing down. */
    private Phase walk() {
        int from = at;
        int to = at + 1;
        while (to + 1 < nodes.size() && map.shows(nodes.get(to + 1)) && !stepStartsAt(to)) {
            to++;
        }
        int end = to;
        double[] sum = new double[end - from + 1];
        for (int i = 1; i < sum.length; i++) {
            sum[i] = sum[i - 1] + length(nodes.get(from + i - 1), nodes.get(from + i));
        }
        double total = sum[sum.length - 1];
        double seconds = Math.max(MIN_WALK_S, Math.min(MAX_WALK_S, total * map.scale() / PX_PER_S));
        return el -> {
            double k = Math.min(1, el / seconds);
            double d = easeInOut(k) * total;
            int i = 0;
            while (i < sum.length - 2 && sum[i + 1] < d) {
                i++;
            }
            Node a = nodes.get(from + i);
            Node b = nodes.get(from + i + 1);
            double seg = sum[i + 1] - sum[i];
            double f = seg <= 0 ? 1 : Math.min(1, (d - sum[i]) / seg);
            x = a.x() + (b.x() - a.x()) * f;
            y = a.y() + (b.y() - a.y()) * f;
            done = from + i;
            t = f;
            if (seg > 0) {
                targetAngle = heading(a, b);
            }
            if (k >= 1) {
                at = end;
                done = end;
                t = 0;
                place(nodes.get(end));
                return true;
            }
            return false;
        };
    }

    /**
     * Stairs, an elevator, a bridge or a door to another level: a tag says where to, then the map fades over to that
     * level and the walker carries on there.
     */
    private Phase transfer() {
        int j = at;
        int k = j + 1;
        while (k < nodes.size() && !hasLevel(nodes.get(k))) {
            k++;
        }
        if (k >= nodes.size()) {
            at = nodes.size() - 1;
            return arrive();
        }
        Node there = nodes.get(k);
        Integer target = there.floorId();
        int arriveAt = k;
        tag = transferText(data, nodes.get(j), there, edges.get(j).edgeType(), map.level(), target);
        tagAlpha = 0;
        ring();
        boolean[] switched = {false};
        return el -> {
            if (el < TRANSFER_HOLD_S) {
                tagAlpha = Math.min(1, el / 0.22);
                return false;
            }
            double f = el - TRANSFER_HOLD_S;
            if (f < FADE_OUT_S) {
                map.setOpacity(1 - 0.8 * easeInOut(f / FADE_OUT_S));
                return false;
            }
            if (!switched[0]) {
                switched[0] = true;
                if (!Objects.equals(map.level(), target)) {
                    host.level(target);
                }
                // a door: the walker is on both levels and walks on from here; stairs and bridges: it appears there
                if (!(map.shows(nodes.get(j)) && map.shows(nodes.get(j + 1)))) {
                    at = arriveAt;
                    done = arriveAt;
                    t = 0;
                    place(nodes.get(arriveAt));
                }
                ring();
            }
            double g = Math.min(1, (f - FADE_OUT_S) / FADE_IN_S);
            map.setOpacity(0.2 + 0.8 * easeOut(g));
            tagAlpha = 1 - g;
            if (g >= 1) {
                map.setOpacity(1);
                tag = null;
                return true;
            }
            return false;
        };
    }

    /** At the destination: the last step lights up, the full route comes back in red, and the walker fades out. */
    private Phase arrive() {
        arrived = true;
        at = nodes.size() - 1;
        done = nodes.size();
        t = 0;
        place(nodes.get(at));
        highlight(stepAt.length - 1);
        boolean[] unmuted = {false};
        return el -> {
            if (el < END_HOLD_S) {
                return false;
            }
            if (!unmuted[0]) {
                unmuted[0] = true;
                map.setPreviewing(false);
            }
            double f = Math.min(1, (el - END_HOLD_S) / END_FADE_S);
            alpha = 1 - easeInOut(f);
            return f >= 1;
        };
    }

    // ---------------------------------------------------------------- helpers

    /** "Elevator up to 4F", "Stairs down to 1F", "Into Tan Yan Kee", "Bridge to Engineering 3F", "Out to campus". */
    static String transferText(CampusData data, Node from, Node to, EdgeType type, Integer fromLevel,
            Integer toLevel) {
        String where = toLevel == null ? "campus" : data.floor(toLevel).map(Floor::shortLabel).orElse("next floor");
        if (type == EdgeType.STAIRS || type == EdgeType.ELEVATOR) {
            String way = floorNumber(data, to) >= floorNumber(data, from) ? "up" : "down";
            return (type == EdgeType.ELEVATOR ? "Elevator " : "Stairs ") + way + " to " + where;
        }
        if (toLevel == null) {
            return "Out to campus";
        }
        Integer buildingHere = data.floor(fromLevel).map(Floor::buildingId).orElse(null);
        Integer buildingThere = data.floor(toLevel).map(Floor::buildingId).orElse(null);
        String building = data.building(buildingThere).map(b -> MapView.shortName(b.name())).orElse("the building");
        if (fromLevel == null) {
            return "Into " + building;
        }
        if (!Objects.equals(buildingHere, buildingThere)) {
            return "Bridge to " + building + " " + where;
        }
        return "To " + where;
    }

    private static int floorNumber(CampusData data, Node n) {
        return data.floor(n.floorId()).map(Floor::floorNumber).orElse(1);
    }

    /** The level that shows route node i: its floor when that floor has a tab, else the first level shown. */
    private Integer levelFor(int i) {
        for (int k = i; k < nodes.size(); k++) {
            if (hasLevel(nodes.get(k))) {
                return nodes.get(k).floorId();
            }
        }
        return levels.isEmpty() || levels.get(0) == -1 ? null : levels.get(0);
    }

    private boolean hasLevel(Node n) {
        return levels.contains(n.floorId() == null ? -1 : n.floorId());
    }

    /** The last step that begins at or before route node i (the Arrive step only at the end). */
    private int stepStartedBy(int i) {
        int s = 0;
        for (int k = 0; k < stepAt.length - 1; k++) {
            if (stepAt[k] <= i) {
                s = k;
            }
        }
        return s;
    }

    private boolean stepStartsAt(int i) {
        for (int k = 0; k < stepAt.length - 1; k++) {
            if (stepAt[k] == i) {
                return true;
            }
        }
        return false;
    }

    private void highlight(int s) {
        if (s < 0 || s >= stepAt.length) {
            return;
        }
        shownStep = s;
        host.step(s);
        ring();
    }

    private void ring() {
        ringX = x;
        ringY = y;
        ringStart = clock;
    }

    private void place(Node n) {
        x = n.x();
        y = n.y();
    }

    private static double length(Node a, Node b) {
        return Math.hypot(b.x() - a.x(), b.y() - a.y());
    }

    /** Degrees clockwise from up (screen y points down). */
    private static double heading(Node a, Node b) {
        return Math.toDegrees(Math.atan2(b.x() - a.x(), -(b.y() - a.y())));
    }

    static double easeInOut(double k) {
        return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
    }

    static double easeOut(double k) {
        return 1 - Math.pow(1 - k, 3);
    }

    /** Grows a little past full size and settles back, like a small bounce. */
    static double easeOutBack(double k) {
        double c = 1.7;
        return 1 + (c + 1) * Math.pow(k - 1, 3) + c * Math.pow(k - 1, 2);
    }
}

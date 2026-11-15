package com.uenav.mobile;

import com.uenav.core.Building;
import com.uenav.core.CampusData;
import com.uenav.core.Edge;
import com.uenav.core.Floor;
import com.uenav.core.Node;
import com.uenav.core.Route;
import com.uenav.core.SnapshotReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javafx.animation.PauseTransition;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * Draws one level of the campus (the outdoor campus, or one building floor) with the route on top (D6).
 * The outdoor campus is drawn in basic 3D shapes from /maps/campus-3d.json (see CampusArt). A floor uses its plan
 * picture, /maps/ + the floor's planImagePath, when the app contains one. Node X/Y are pixels of that picture. With no picture, a simple
 * plan is drawn from the node positions.
 *
 * Moving around works like Google Maps: drag to move, pinch (or mouse wheel) to zoom around the fingers or pointer,
 * double-tap to zoom in, and the + / - buttons. The view only changes when the user does one of these: touch drags
 * and the glide after a flick are not treated as zoom, and resizing the screen keeps the same zoom.
 *
 * Speed on a phone: drawing the whole map (every building, tree and name) for each finger move gave about 20
 * frames a second on a Galaxy A35. So the map is drawn a little larger than what shows (MARGIN on every side), and
 * while a finger drags or pinches, that finished picture is only slid and stretched, which costs almost nothing.
 * It is drawn again properly when the finger pauses (SETTLE_MS) or the picture no longer covers the screen.
 *
 * Arrows along the route show which way to walk. The route animation (RoutePlayer) draws its walker on a second,
 * see-through layer the size of the view: each frame redraws only that layer, never the map under it.
 */
public class MapView extends Region {

    private static final Color BRAND = Color.web("#C8102E");
    /** The route in dark mode: a lighter red that stands out on the dark map. */
    private static final Color BRAND_DARK = Color.web("#FF4D6A");
    private static final Color MUTED_LIGHT = Color.web("#B4BAC4");
    private static final Color MUTED_DARK = Color.web("#566072");
    /** Space between the arrows along the route, and how fast they flow along the walked part (screen units). */
    private static final double ARROW_GAP = 24;
    private static final double ARROW_SPEED = 30;
    /** How long the ring that pops at each step grows and fades (seconds). */
    static final double RING_S = 0.7;
    private static final Color INK = Color.web("#1F2330");
    private static final Color BLUE = Color.web("#2563EB");
    private static final Color GREEN = Color.web("#15803D");
    private static final Map<String, Optional<Image>> IMAGES = new HashMap<>();
    private static CampusArt campusArt;
    private static boolean artLoaded;
    /** Zoom per pixel of mouse-wheel movement (one wheel notch is about 40). */
    private static final double WHEEL_ZOOM = 1.004;
    /** Extra map drawn around the part that shows, so a drag can slide the finished picture (screen units). */
    static final double MARGIN = 120;
    /** The map is drawn again this long after the last finger move (ms). */
    static final double SETTLE_MS = 90;
    /** While pinching, a picture stretched more than this is drawn again at once (it would look too blurry). */
    private static final double MAX_STRETCH = 2.2;
    private static final Font LABEL_FONT = Font.font(null, FontWeight.NORMAL, 11);
    private static final Font LABEL_BOLD_FONT = Font.font(null, FontWeight.BOLD, 11);
    private static final Font TAG_FONT = Font.font(null, FontWeight.BOLD, 10);
    private static final Font SMALL_FONT = Font.font(null, FontWeight.BOLD, 9.5);
    private static final Color HALO_LIGHT = Color.web("#FFFFFF", 0.9);
    private static final Color HALO_DARK = Color.web("#0E1117", 0.85);
    private static final Color INK_DARK = Color.web("#E7E9EF");
    private static final Color SHADOW = Color.web("#000000", 0.10);
    private static final Color DOT_RING_DARK = Color.web("#1A1E27");
    private static final Color DOT_DARK = Color.web("#9CA4B4");
    private static final Color DOT_LIGHT = Color.web("#6B7280");
    private static final Pattern BRACKETS = Pattern.compile("\\s*\\(.*\\)");
    private static final Pattern LEADING_WORDS = Pattern.compile("^(College of|University)\\s+");
    private static final Pattern TRAILING_WORDS = Pattern.compile("\\s+(Building|Office)$");
    /** "Tan Yan Kee Academic Building" -> "Tan Yan Kee"; a one-word name keeps it ("Old Academic"). */
    private static final Pattern ACADEMIC = Pattern.compile("^(\\S+\\s+\\S+.*?)\\s+Academic Building$");
    private static final Map<String, String> SHORT_NAMES = new HashMap<>();

    private final Canvas canvas = new Canvas();
    /** The route animation's layer: exactly the size of the view, over the map, never catches taps. */
    private final Canvas overlay = new Canvas();
    private final Rectangle clip = new Rectangle();
    private final PauseTransition settle = new PauseTransition(Duration.millis(SETTLE_MS));
    /** Size of the part that shows (the canvas is MARGIN larger on every side). */
    private double viewW;
    private double viewH;
    // the view the canvas picture was last drawn for (painted is false until the map has been drawn once)
    private boolean painted;
    private double paintedX;
    private double paintedY;
    private double paintedScale = 1;
    private double paintedW;
    private double paintedH;
    private double paintedCentreY;
    // what is on the level shown now; worked out once per map and level, not for every drawing
    private Set<Integer> levelIds;
    private List<Node> levelNodes;
    private List<Edge> allEdges;
    private CampusData data;
    private Route route;
    private Integer level;
    private double padTop = 24;
    private double padBottom = 24;
    private Consumer<Node> onPlaceClicked;
    private boolean pickAnyNode;
    private boolean dark;
    private boolean graphView;
    private List<Integer> visited = List.of();
    private Node here;
    private Node focused;
    private List<Node> highlights = List.of();
    private List<Node> fitNodes;
    private boolean previewing;
    private Preview preview;
    private Runnable onUserTouch;

    // what the user sees: the map point at the middle of the free area, and screen pixels per map unit
    private double viewX;
    private double viewY;
    private double scale = 1;
    private boolean needsFit = true;
    private boolean userMoved;

    // gestures
    private double dragX;
    private double dragY;
    private boolean dragged;
    private boolean pinching;
    private boolean restartDrag;

    public MapView() {
        getStyleClass().add("nv-map-bg");
        canvas.setManaged(false);
        overlay.setManaged(false);
        overlay.setMouseTransparent(true);
        overlay.setVisible(false);
        getChildren().addAll(canvas, overlay);
        setClip(clip);
        setMinSize(0, 0);
        settle.setOnFinished(e -> {
            if (movedSincePaint()) {
                draw();
            }
        });
        // Listeners are on this view, not on the canvas: the canvas slides under the finger during a drag.
        setOnMousePressed(e -> {
            touched();
            dragX = e.getX();
            dragY = e.getY();
            dragged = false;
            restartDrag = false;
        });
        setOnMouseDragged(e -> {
            if (pinching) {
                return;
            }
            if (restartDrag) {
                // a finger stayed down after a pinch: continue from here instead of jumping
                dragX = e.getX();
                dragY = e.getY();
                restartDrag = false;
                return;
            }
            double dx = e.getX() - dragX;
            double dy = e.getY() - dragY;
            dragX = e.getX();
            dragY = e.getY();
            dragged |= Math.abs(dx) + Math.abs(dy) > 1;
            panBy(dx, dy, true);
        });
        setOnMouseClicked(e -> {
            if (dragged || !e.isStillSincePress()) {
                return;
            }
            if (e.getClickCount() == 2) {
                zoomAt(1.6, e.getX(), e.getY());
            } else if (e.getClickCount() == 1 && onPlaceClicked != null) {
                Node hit = placeAt(e.getX(), e.getY());
                if (hit != null) {
                    onPlaceClicked.accept(hit);
                }
            }
        });
        setOnScroll(e -> {
            e.consume();
            // On a touch screen every finger drag also sends scroll events, and a flick keeps sending them after
            // the finger is lifted. Those are moves, not zoom (the drag handler already moves the map).
            if (e.isDirect() || e.getTouchCount() > 0 || e.isInertia() || pinching) {
                return;
            }
            if (e.getDeltaY() != 0) {
                touched();
                zoomAt(Math.pow(WHEEL_ZOOM, e.getDeltaY()), e.getX(), e.getY());
            }
        });
        setOnZoomStarted(e -> {
            touched();
            pinching = true;
            dragged = true;
        });
        setOnZoom(e -> {
            if (e.getZoomFactor() > 0 && !Double.isNaN(e.getZoomFactor())) {
                zoomAt(e.getZoomFactor(), e.getX(), e.getY(), true);
            }
        });
        setOnZoomFinished(e -> {
            pinching = false;
            restartDrag = true;
        });
    }

    public void setData(CampusData data) {
        this.data = data;
        levelIds = null;
        allEdges = null;
        resetView();
    }

    public void setRoute(Route route) {
        this.route = route;
        resetView();
    }

    /** null shows the outdoor campus; otherwise a floor id. */
    public void setLevel(Integer floorId) {
        this.level = floorId;
        this.focused = null;
        levelIds = null;
        resetView();
    }

    public Integer level() {
        return level;
    }

    /** Space (pixels) kept free at the top and bottom for cards drawn over the map. */
    public void setPadding(double top, double bottom) {
        this.padTop = top;
        this.padBottom = bottom;
        if (!userMoved) {
            needsFit = true;
        }
        draw();
    }

    public void setOnPlaceClicked(Consumer<Node> handler) {
        this.onPlaceClicked = handler;
    }

    /** true while choosing a starting point: hallway corners, stairs and other waypoints can be tapped too. */
    public void setPickAnyNode(boolean any) {
        this.pickAnyNode = any;
    }

    /** Dark map colours (dark mode). */
    public void setDark(boolean dark) {
        this.dark = dark;
        draw();
    }

    /**
     * Graph view for explaining Dijkstra: every walkway with its length and every node with its id, and the nodes
     * the algorithm checked (visited, in order) shaded.
     */
    public void setGraphView(boolean on, List<Integer> visitedInOrder) {
        this.graphView = on;
        this.visited = visitedInOrder == null ? List.of() : List.copyOf(visitedInOrder);
        draw();
    }

    /** Places drawn as big labelled pins, such as the two gates on the "Where are you?" screen. */
    public void setHighlights(List<Node> nodes) {
        this.highlights = nodes == null ? List.of() : List.copyOf(nodes);
        draw();
    }

    /** Starts the campus view around these places (for example both gates) instead of the whole campus. */
    public void fitAround(List<Node> nodes) {
        this.fitNodes = nodes == null || nodes.isEmpty() ? null : List.copyOf(nodes);
        resetView();
    }

    /** The "You are here" dot when no route is shown (or null). */
    public void setHere(Node node) {
        this.here = node;
        draw();
    }

    /** Called when a finger (or the mouse) touches the map, so a playing route animation can stop. */
    public void setOnUserTouch(Runnable r) {
        this.onUserTouch = r;
    }

    private void touched() {
        if (onUserTouch != null) {
            onUserTouch.run();
        }
    }

    /**
     * One frame of the route animation (RoutePlayer). The walk has covered the route's segments before {@code done}
     * and the fraction {@code t} of segment {@code done}; the walker stands at (x, y) in map units, pointing
     * {@code angle} degrees clockwise from up, drawn {@code size} times its full size. {@code ringAge} is the age
     * (seconds) of the ring popping at (ringX, ringY), negative for none; {@code tag} a short note over the walker.
     */
    record Preview(int done, double t, double x, double y, double angle, double size, double alpha, double clock,
            double ringX, double ringY, double ringAge, String tag, double tagAlpha) { }

    /** While the route animation plays, the route not walked yet is drawn grey, under the walk drawn in red. */
    public void setPreviewing(boolean on) {
        if (previewing != on) {
            previewing = on;
            draw();
        }
    }

    /** Shows one frame of the route animation (redraws only the animation's layer). */
    void showPreview(Preview p) {
        preview = p;
        drawPreview();
    }

    void clearPreview() {
        preview = null;
        drawPreview();
    }

    /** True when the node is drawn on the level shown now. */
    public boolean shows(Node n) {
        return data != null && n != null && visibleIds().contains(n.id());
    }

    /** Moves the view to a node of the current level and zooms in, as when a direction step is tapped. */
    public void focusOn(Node node) {
        focused = node;
        if (node == null || data == null || viewW < 10) {
            draw();
            return;
        }
        if (needsFit) {
            fit();
        }
        double[] limits = scaleLimits();
        scale = Math.max(limits[0], Math.min(limits[1], Math.max(scale, fitScale(levelBounds(), 8) * 2.2)));
        viewX = node.x();
        viewY = node.y();
        userMoved = true;
        clampView();
        draw();
    }

    public void zoomIn() {
        zoomAt(1.5, viewW / 2, areaCentreY());
    }

    public void zoomOut() {
        zoomAt(1 / 1.5, viewW / 2, areaCentreY());
    }

    /** Back to the starting view: the route on this level, or the whole level. */
    public void resetView() {
        needsFit = true;
        userMoved = false;
        draw();
    }

    /** Screen pixels per map unit (for tests and the screen around the map). */
    public double scale() {
        return scale;
    }

    void panBy(double dx, double dy) {
        panBy(dx, dy, false);
    }

    /** @param byFinger true during a drag: the finished picture slides, and is drawn again when the finger pauses */
    private void panBy(double dx, double dy, boolean byFinger) {
        if (scale <= 0) {
            return;
        }
        viewX -= dx / scale;
        viewY -= dy / scale;
        userMoved = true;
        clampView();
        if (byFinger) {
            slide();
        } else {
            draw();
        }
    }

    /** Zooms by a factor, keeping the map point under (fx, fy) in the same place on screen. */
    void zoomAt(double factor, double fx, double fy) {
        zoomAt(factor, fx, fy, false);
    }

    /** @param byFinger true during a pinch: the finished picture stretches, and is drawn again when it pauses */
    private void zoomAt(double factor, double fx, double fy, boolean byFinger) {
        if (data == null || viewW < 10) {
            return;
        }
        double[] limits = scaleLimits();
        double newScale = Math.max(limits[0], Math.min(limits[1], scale * factor));
        double cx = viewW / 2;
        double cy = areaCentreY();
        double mapX = viewX + (fx - cx) / scale;
        double mapY = viewY + (fy - cy) / scale;
        scale = newScale;
        viewX = mapX - (fx - cx) / scale;
        viewY = mapY - (fy - cy) / scale;
        userMoved = true;
        clampView();
        if (byFinger) {
            slide();
        } else {
            draw();
        }
    }

    private boolean movedSincePaint() {
        return viewX != paintedX || viewY != paintedY || scale != paintedScale || viewW != paintedW
                || viewH != paintedH;
    }

    /**
     * Shows the view as it is now (after a drag, a pinch, or a change of size) by moving and stretching the picture
     * already drawn, without drawing the map again. Draws again at once only when that picture no longer covers
     * the view or is stretched too far; otherwise shortly after the last change.
     */
    private void slide() {
        if (!painted || viewW < 10 || viewH < 10) {
            draw();
            return;
        }
        double k = scale / paintedScale;
        // the map point drawn at the middle of the canvas, and where that point belongs on this view now
        double midX = paintedX;
        double midY = paintedY + (paintedH / 2 - paintedCentreY) / paintedScale;
        double cx = vx(midX);
        double cy = vy(midY);
        double halfW = k * (paintedW / 2 + MARGIN);
        double halfH = k * (paintedH / 2 + MARGIN);
        boolean covers = cx - halfW <= 0 && cx + halfW >= viewW && cy - halfH <= 0 && cy + halfH >= viewH;
        if (!covers || k > MAX_STRETCH || k < 1 / MAX_STRETCH) {
            draw();
            return;
        }
        canvas.setScaleX(k);
        canvas.setScaleY(k);
        canvas.setTranslateX(cx - paintedW / 2);
        canvas.setTranslateY(cy - paintedH / 2);
        settle.playFromStart();
        drawPreview();
    }

    /** Levels a route passes through, in walking order: null for the campus, then floor ids. */
    public static List<Integer> levelsOf(Route r, CampusData data) {
        Set<Integer> levels = new LinkedHashSet<>();
        for (int i = 0; i < r.nodes().size(); i++) {
            Node n = r.nodes().get(i);
            if (n.floorId() == null) {
                levels.add(null);
            } else {
                boolean linksOutside = (i > 0 && r.nodes().get(i - 1).floorId() == null)
                        || (i + 1 < r.nodes().size() && r.nodes().get(i + 1).floorId() == null);
                boolean onlyEntrance = linksOutside && r.nodes().stream()
                        .filter(x -> n.floorId().equals(x.floorId())).count() == 1;
                // a floor the route only climbs past on the stairs gets no tab of its own
                boolean passingThrough = i > 0 && i + 1 < r.nodes().size() && r.nodes().stream()
                        .filter(x -> n.floorId().equals(x.floorId()))
                        .allMatch(x -> isVertical(x) && x != r.nodes().get(0)
                                && x != r.nodes().get(r.nodes().size() - 1));
                if (!onlyEntrance && !passingThrough) {
                    levels.add(n.floorId());
                }
            }
        }
        return new ArrayList<>(levels);
    }

    private static boolean isVertical(Node n) {
        return "STAIRS".equals(n.nodeType()) || "ELEVATOR".equals(n.nodeType());
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        if (viewW != w || viewH != h) {
            // Until the user moves or zooms the map, fit it again to the new size: a map that slides open starts
            // almost 0 high, and keeping that first zoom left the campus as a tiny speck. After the user has
            // moved it, a size change keeps their zoom and middle point (no automatic re-zoom).
            viewW = w;
            viewH = h;
            clip.setWidth(w);
            clip.setHeight(h);
            overlay.setWidth(Math.max(0, w));
            overlay.setHeight(Math.max(0, h));
            if (!userMoved) {
                needsFit = true;
            }
            if (painted && data != null && w >= 10 && h >= 10) {
                // a panel sliding open, or the directions sheet being dragged: stretch the picture for now
                if (needsFit) {
                    fit();
                }
                slide();
            } else {
                draw();
            }
        }
    }

    // The canvas is larger than this view and not part of its layout: report the size this view was given, as
    // before, when the canvas was exactly that size.
    @Override
    protected double computePrefWidth(double height) {
        return viewW;
    }

    @Override
    protected double computePrefHeight(double width) {
        return viewH;
    }

    // ---------------------------------------------------------------- drawing

    /** Every walkway, listed once per map (asking the graph builds the list anew each time). */
    private List<Edge> edges() {
        if (allEdges == null) {
            allEdges = data == null ? List.of() : data.graph().edges();
        }
        return allEdges;
    }

    private Set<Integer> visibleIds() {
        if (levelIds == null) {
            levelIds = findVisibleIds();
            levelNodes = new ArrayList<>();
            if (data != null) {
                for (int id : levelIds) {
                    data.graph().node(id).ifPresent(levelNodes::add);
                }
            }
        }
        return levelIds;
    }

    private Set<Integer> findVisibleIds() {
        Set<Integer> ids = new HashSet<>();
        if (data == null) {
            return ids;
        }
        for (Node n : data.graph().nodes()) {
            if (level == null ? n.floorId() == null : level.equals(n.floorId())) {
                ids.add(n.id());
            }
        }
        if (level == null) {
            // building entrances: inside nodes linked by a walkway to the outside
            for (Edge e : edges()) {
                Node a = data.graph().node(e.fromNodeId()).orElse(null);
                Node b = data.graph().node(e.toNodeId()).orElse(null);
                if (a != null && b != null && (a.floorId() == null) != (b.floorId() == null)) {
                    ids.add(a.id());
                    ids.add(b.id());
                }
            }
        }
        return ids;
    }

    private Optional<Image> image() {
        if (level == null) {
            Optional<Image> jpg = load("campus.jpg");
            return jpg.isPresent() ? jpg : load("campus.png");
        }
        String path = data.floor(level).map(Floor::planImagePath).orElse(null);
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        return load(path);
    }

    private static Optional<Image> load(String path) {
        return IMAGES.computeIfAbsent(path, p -> {
            try (InputStream in = MapView.class.getResourceAsStream("/maps/" + p)) {
                return in == null ? Optional.empty() : Optional.of(new Image(in));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    private double areaCentreY() {
        return padTop + (viewH - padTop - padBottom) / 2;
    }

    /** Map point to a place on this view: (0, 0) is the top left corner of what shows. For taps and gestures. */
    private double vx(double x) {
        return viewW / 2 + (x - viewX) * scale;
    }

    private double vy(double y) {
        return areaCentreY() + (y - viewY) * scale;
    }

    /** Map point to a place on the canvas, which starts MARGIN up and left of what shows. For drawing. */
    private double sx(double x) {
        return MARGIN + vx(x);
    }

    private double sy(double y) {
        return MARGIN + vy(y);
    }

    /** minX, minY, maxX, maxY of the level: the picture, or the places on it. */
    private double[] levelBounds() {
        CampusArt art3d = art();
        if (art3d != null) {
            return art3d.bounds();
        }
        Integer floorOf = floorBuilding3d();
        if (floorOf != null) {
            return allArt().footprintBounds(floorOf);
        }
        Optional<Image> img = image();
        if (img.isPresent()) {
            return new double[] {0, 0, img.get().getWidth(), img.get().getHeight()};
        }
        List<Node> nodes = new ArrayList<>();
        for (int id : visibleIds()) {
            data.graph().node(id).ifPresent(nodes::add);
        }
        return bounds(nodes);
    }

    private static double[] bounds(List<Node> nodes) {
        if (nodes.isEmpty()) {
            return new double[] {0, 0, 1, 1};
        }
        return new double[] {
            nodes.stream().mapToDouble(Node::x).min().orElse(0),
            nodes.stream().mapToDouble(Node::y).min().orElse(0),
            nodes.stream().mapToDouble(Node::x).max().orElse(1),
            nodes.stream().mapToDouble(Node::y).max().orElse(1)};
    }

    /** Scale that shows the box b in the free area, with a margin. */
    private double fitScale(double[] b, double margin) {
        double areaW = Math.max(20, viewW - 2 * margin);
        double areaH = Math.max(20, viewH - padTop - padBottom - 2 * margin);
        return Math.min(areaW / Math.max(1, b[2] - b[0]), areaH / Math.max(1, b[3] - b[1]));
    }

    /** Smallest and largest zoom: from a bit smaller than the whole level to ten times closer than that. */
    private double[] scaleLimits() {
        double whole = fitScale(levelBounds(), 8);
        return new double[] {whole * 0.8, whole * 10};
    }

    /** Starting view: the part of the route on this level (like Google Maps), or the whole level. */
    private void fit() {
        double[] level = levelBounds();
        double[] target = level;
        if (route == null && this.level == null && fitNodes != null) {
            double[] b = bounds(fitNodes);
            double size = Math.max(Math.max(b[2] - b[0], b[3] - b[1]), 0.45 * Math.max(level[2] - level[0],
                    level[3] - level[1]));
            double cx = (b[0] + b[2]) / 2;
            double cy = (b[1] + b[3]) / 2;
            target = new double[] {cx - size / 2, cy - size / 2.4, cx + size / 2, cy + size / 2.4};
        } else if (route != null) {
            // start on the part of the route on this level (outdoors and on every floor: a whole long building
            // floor is too small to read on a phone)
            Set<Integer> ids = visibleIds();
            List<Node> onLevel = new ArrayList<>();
            for (Node n : route.nodes()) {
                if (ids.contains(n.id())) {
                    onLevel.add(n);
                }
            }
            if (!onLevel.isEmpty()) {
                target = bounds(onLevel);
                // do not zoom in too far on a short route
                double minSize = 0.25 * Math.max(level[2] - level[0], level[3] - level[1]);
                double cx = (target[0] + target[2]) / 2;
                double cy = (target[1] + target[3]) / 2;
                double halfW = Math.max(target[2] - target[0], minSize) / 2;
                double halfH = Math.max(target[3] - target[1], minSize / 2) / 2;
                target = new double[] {cx - halfW, cy - halfH, cx + halfW, cy + halfH};
            }
        }
        double[] limits = scaleLimits();
        double wanted = fitScale(target, 36);
        if (target == level && (art() != null || image().isPresent() || floorBuilding3d() != null)) {
            // Whole campus on a tall phone screen: halfway (geometric mean) between showing all of it as a thin
            // strip and filling the screen, so most of the campus shows at a readable size; drag to see the rest.
            double areaW = Math.max(20, viewW);
            double areaH = Math.max(20, viewH - padTop - padBottom);
            double cover = Math.max(areaW / (level[2] - level[0]), areaH / (level[3] - level[1]));
            wanted = Math.sqrt(wanted * cover);
        }
        scale = Math.max(limits[0], Math.min(limits[1], wanted));
        viewX = (target[0] + target[2]) / 2;
        viewY = (target[1] + target[3]) / 2;
        needsFit = false;
    }

    /** Keeps the middle of the view on the level, so the map cannot be dragged away. */
    private void clampView() {
        double[] b = levelBounds();
        viewX = Math.max(b[0], Math.min(b[2], viewX));
        viewY = Math.max(b[1], Math.min(b[3], viewY));
    }

    private void draw() {
        drawMap();
        drawPreview();
    }

    private void drawMap() {
        // the picture is drawn fresh for the view as it is now: put the canvas back where it belongs
        settle.stop();
        canvas.setScaleX(1);
        canvas.setScaleY(1);
        canvas.setTranslateX(0);
        canvas.setTranslateY(0);
        canvas.setLayoutX(-MARGIN);
        canvas.setLayoutY(-MARGIN);
        if (canvas.getWidth() != viewW + 2 * MARGIN || canvas.getHeight() != viewH + 2 * MARGIN) {
            canvas.setWidth(viewW + 2 * MARGIN);
            canvas.setHeight(viewH + 2 * MARGIN);
        }
        painted = false;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(dark ? Color.web("#0E1117") : Color.web("#E9ECEF"));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (data == null || viewW < 10 || viewH < 10) {
            return;
        }
        Set<Integer> ids = visibleIds();
        List<Node> nodes = levelNodes;
        Optional<Image> img = image();
        if (needsFit) {
            fit();
        }
        painted = scale > 0;
        paintedX = viewX;
        paintedY = viewY;
        paintedScale = scale;
        paintedW = viewW;
        paintedH = viewH;
        paintedCentreY = areaCentreY();
        double[] lb = levelBounds();
        double minX = lb[0];
        double minY = lb[1];
        double maxX = lb[2];
        double maxY = lb[3];

        CampusArt art3d = art();
        if (art3d != null) {
            // 3D campus: ground and field, walkways on the ground, then buildings and trees, then the route on top
            CampusArt.View view = view();
            art3d.drawGround(g, view);
            drawWalkways(g, ids, dark ? Color.web("#4A5263", 0.9) : Color.web("#FFFFFF", 0.85), 5);
            art3d.drawRaised(g, view);
            drawOverlays(g, ids, nodes, true);
            return;
        }
        Integer floorOf = floorBuilding3d();
        if (floorOf != null) {
            // 3D floor: the building outline as a slab, hallways on it, stairs and elevators as blocks
            CampusArt.View view = view();
            allArt().drawFloor(g, view, floorOf);
            drawWalkways(g, ids, dark ? Color.web("#3A4152") : Color.web("#D9DCE1"), 9);
            for (Node n : nodes) {
                if ("ELEVATOR".equals(n.nodeType())) {
                    CampusArt.cube(g, view, n.x(), n.y(), 16, 14, GREEN);
                }
            }
            drawOverlays(g, ids, nodes, false);
            return;
        }
        if (img.isPresent()) {
            g.drawImage(img.get(), sx(0), sy(0), img.get().getWidth() * scale, img.get().getHeight() * scale);
        } else {
            drawGround(g, minX, minY, maxX, maxY);
            // walkways on this level (a map picture already shows them)
            Color walk = dark ? Color.web("#3A4152") : level == null ? Color.web("#FFFFFF") : Color.web("#EEF0F3");
            drawWalkways(g, ids, walk, level == null ? 7 : 12);
        }
        if (level == null && !img.isPresent()) {
            drawBuildings(g, ids);
        }
        drawOverlays(g, ids, nodes, img.isPresent());
    }

    /** Route, graph view, place names, the "You are here" dot and the focused step, in that order. */
    private void drawOverlays(GraphicsContext g, Set<Integer> ids, List<Node> nodes, boolean onPicture) {
        drawRoute(g, ids);
        if (graphView) {
            drawGraph(g, ids);
        }
        drawPlaces(g, nodes, onPicture);
        for (Node h : highlights) {
            if (ids.contains(h.id())) {
                double x = sx(h.x());
                double y = sy(h.y());
                g.setFill(Color.web("#000000", 0.18));
                g.fillOval(x - 15, y - 13, 30, 30);
                g.setFill(Color.WHITE);
                g.fillOval(x - 15, y - 15, 30, 30);
                g.setFill(BLUE);
                g.fillOval(x - 12, y - 12, 24, 24);
                g.setFill(Color.WHITE);
                g.fillPolygon(new double[] {x - 5, x + 6, x - 5}, new double[] {y - 6, y, y + 6}, 3);
                tag(g, h.name(), x, y - 28, BLUE, Color.WHITE);
            }
        }
        if (route == null && here != null && ids.contains(here.id())) {
            youAreHere(g, sx(here.x()), sy(here.y()));
        }
        if (focused != null && ids.contains(focused.id())) {
            double x = sx(focused.x());
            double y = sy(focused.y());
            g.setStroke(BRAND);
            g.setLineWidth(3);
            g.strokeOval(x - 18, y - 18, 36, 36);
            g.setStroke(BRAND.deriveColor(0, 1, 1, 0.35));
            g.setLineWidth(8);
            g.strokeOval(x - 24, y - 24, 48, 48);
        }
    }

    /** Graph view: walkways with their lengths, nodes with ids; nodes Dijkstra settled are shaded in order. */
    private void drawGraph(GraphicsContext g, Set<Integer> ids) {
        Color edgeColor = dark ? Color.web("#7BA5FF", 0.9) : Color.web("#1D4ED8", 0.85);
        g.setLineDashes((double[]) null);
        g.setLineCap(StrokeLineCap.ROUND);
        for (Edge e : edges()) {
            boolean inA = ids.contains(e.fromNodeId());
            boolean inB = ids.contains(e.toNodeId());
            if (!inA || !inB) {
                continue;
            }
            Node a = data.graph().node(e.fromNodeId()).orElseThrow();
            Node b = data.graph().node(e.toNodeId()).orElseThrow();
            g.setStroke(e.edgeType() == com.uenav.core.EdgeType.WALK ? edgeColor : GREEN);
            g.setLineWidth(1.6);
            g.strokeLine(sx(a.x()), sy(a.y()), sx(b.x()), sy(b.y()));
            if (scale > 0.45) {
                double mx = (sx(a.x()) + sx(b.x())) / 2;
                double my = (sy(a.y()) + sy(b.y())) / 2;
                smallText(g, Math.round(e.distanceM()) + " m", mx, my, edgeColor);
            }
        }
        Map<Integer, Integer> order = new HashMap<>();
        for (int i = 0; i < visited.size(); i++) {
            order.put(visited.get(i), i + 1);
        }
        for (int id : ids) {
            Node n = data.graph().node(id).orElseThrow();
            double x = sx(n.x());
            double y = sy(n.y());
            Integer step = order.get(id);
            double r = step != null ? 7 : 4.5;
            g.setFill(step != null ? Color.web("#F59E0B") : (dark ? Color.web("#9CA4B4") : Color.web("#374151")));
            g.fillOval(x - r, y - r, 2 * r, 2 * r);
            g.setStroke(dark ? Color.web("#0E1117") : Color.WHITE);
            g.setLineWidth(1.5);
            g.strokeOval(x - r, y - r, 2 * r, 2 * r);
            if (scale > 0.45) {
                smallText(g, "#" + id + (step != null ? " (" + step + ")" : ""), x, y - 13, dark
                        ? Color.web("#FDE68A") : Color.web("#92400E"));
            }
        }
    }

    private void smallText(GraphicsContext g, String text, double x, double y, Color color) {
        g.setFont(SMALL_FONT);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        CampusArt.haloText(g, text, x, y, dark ? HALO_DARK : HALO_LIGHT, color);
    }

    private static void youAreHere(GraphicsContext g, double x, double y) {
        g.setFill(BLUE.deriveColor(0, 1, 1, 0.20));
        g.fillOval(x - 20, y - 20, 40, 40);
        g.setFill(Color.WHITE);
        g.fillOval(x - 10, y - 10, 20, 20);
        g.setFill(BLUE);
        g.fillOval(x - 7, y - 7, 14, 14);
    }

    private void drawWalkways(GraphicsContext g, Set<Integer> ids, Color color, double width) {
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.setStroke(color);
        g.setLineWidth(width);
        for (Edge e : edges()) {
            if (ids.contains(e.fromNodeId()) && ids.contains(e.toNodeId())) {
                Node a = data.graph().node(e.fromNodeId()).orElseThrow();
                Node b = data.graph().node(e.toNodeId()).orElseThrow();
                g.strokeLine(sx(a.x()), sy(a.y()), sx(b.x()), sy(b.y()));
            }
        }
    }

    /** The 3D campus shapes, for the campus level only (floors use a plan picture or a simple plan). */
    private CampusArt art() {
        return level != null ? null : allArt();
    }

    private static CampusArt allArt() {
        if (!artLoaded) {
            campusArt = CampusArt.load("/maps/campus-3d.json");
            artLoaded = true;
        }
        return campusArt;
    }

    /** The building whose floor is shown, when that building has 3D blocks and the floor has no plan picture. */
    private Integer floorBuilding3d() {
        if (level == null || image().isPresent() || allArt() == null) {
            return null;
        }
        Integer b = data.floor(level).map(Floor::buildingId).orElse(null);
        return b != null && allArt().footprintBounds(b) != null ? b : null;
    }

    private CampusArt.View view() {
        return new CampusArt.View() {
            @Override
            public double x(double mapX) {
                return sx(mapX);
            }

            @Override
            public double y(double mapY) {
                return sy(mapY);
            }

            @Override
            public double scale() {
                return scale;
            }

            @Override
            public boolean dark() {
                return dark;
            }
        };
    }

    private void drawGround(GraphicsContext g, double minX, double minY, double maxX, double maxY) {
        double pad = level == null ? 34 : 26;
        double x = sx(minX) - pad;
        double y = sy(minY) - pad;
        double rw = (maxX - minX) * scale + 2 * pad;
        double rh = (maxY - minY) * scale + 2 * pad;
        if (level == null) {
            g.setFill(dark ? Color.web("#1C1F26") : Color.web("#F6F1E7"));
            g.fillRoundRect(x, y, rw, rh, 18, 18);
            g.setStroke(BRAND.deriveColor(0, 1, 1, 0.7));
            g.setLineWidth(1.5);
            g.setLineDashes(6, 5);
            g.strokeRoundRect(x, y, rw, rh, 18, 18);
            g.setLineDashes((double[]) null);
        } else {
            g.setFill(dark ? Color.web("#1A1E27") : Color.WHITE);
            g.fillRoundRect(x, y, rw, rh, 14, 14);
            g.setStroke(dark ? Color.web("#2D3340") : Color.web("#D1D5DB"));
            g.setLineWidth(1.5);
            g.strokeRoundRect(x, y, rw, rh, 14, 14);
        }
    }

    /** Buildings as blocks: at mapX/mapY when set, otherwise around their entrances. */
    private void drawBuildings(GraphicsContext g, Set<Integer> ids) {
        for (Building b : data.buildings()) {
            double cx;
            double cy;
            if (b.mapX() != null && b.mapY() != null) {
                cx = sx(b.mapX());
                cy = sy(b.mapY());
            } else {
                List<Node> doors = new ArrayList<>();
                for (int id : ids) {
                    Node n = data.graph().node(id).orElseThrow();
                    if (n.buildingId() != null && n.buildingId() == b.id()) {
                        doors.add(n);
                    }
                }
                if (doors.isEmpty()) {
                    continue;
                }
                cx = sx(doors.stream().mapToDouble(Node::x).average().orElse(0));
                cy = sy(doors.stream().mapToDouble(Node::y).average().orElse(0));
            }
            double bw = 52;
            double bh = 38;
            g.setFill(Color.web("#000000", 0.10));
            g.fillRoundRect(cx - bw / 2 + 3, cy - bh / 2 + 4, bw, bh, 8, 8);
            g.setFill(Color.web("#F2B33D"));
            g.fillRoundRect(cx - bw / 2, cy - bh / 2, bw, bh, 8, 8);
            g.setFill(Color.web("#E39A1B"));
            g.fillRoundRect(cx - bw / 2, cy + bh / 2 - 8, bw, 8, 6, 6);
            label(g, shortName(b.name()), cx, cy + bh / 2 + 12, false);
        }
    }

    private void drawRoute(GraphicsContext g, Set<Integer> ids) {
        if (route == null || route.nodes().size() < 2) {
            return;
        }
        List<Node> rn = route.nodes();
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.setLineDashes((double[]) null);
        // white edge, the line (grey while the animation walks it in red), and arrows showing the way
        List<double[]> lines = routeLines(ids, rn.size(), 0, MARGIN);
        strokeLines(g, lines, Color.WHITE, 11);
        strokeLines(g, lines, previewing ? (dark ? MUTED_DARK : MUTED_LIGHT) : routeColor(), 7);
        for (double[] line : lines) {
            arrows(g, line, ARROW_GAP / 2, 10, Color.web("#FFFFFF", previewing ? 0.8 : 0.95));
        }

        // hand-over points to another level
        for (int i = 0; i + 1 < rn.size(); i++) {
            Node a = rn.get(i);
            Node b = rn.get(i + 1);
            if (ids.contains(a.id()) != ids.contains(b.id())) {
                Node here = ids.contains(a.id()) ? a : b;
                Node there = here == a ? b : a;
                String text = data.floor(there.floorId()).map(f -> "To " + otherBuilding(f) + f.shortLabel())
                        .orElse(there.floorId() == null ? "To campus" : "Continue");
                tag(g, text, sx(here.x()), sy(here.y()) - 22, BRAND, Color.WHITE);
            }
        }
        Node first = rn.get(0);
        Node last = rn.get(rn.size() - 1);
        if (ids.contains(first.id())) {
            double x = sx(first.x());
            double y = sy(first.y());
            g.setFill(BLUE.deriveColor(0, 1, 1, 0.18));
            g.fillOval(x - 16, y - 16, 32, 32);
            g.setFill(Color.WHITE);
            g.fillOval(x - 10, y - 10, 20, 20);
            g.setFill(BLUE);
            g.fillOval(x - 7, y - 7, 14, 14);
        }
        if (ids.contains(last.id())) {
            pin(g, sx(last.x()), sy(last.y()));
        }
    }

    private Color routeColor() {
        return dark ? BRAND_DARK : BRAND;
    }

    /**
     * The route on this level as lines of screen points {x0, y0, x1, y1, ...}: the segments before {@code done} and
     * the fraction {@code t} of segment {@code done}. offset is MARGIN for the map canvas, 0 for the animation layer.
     */
    private List<double[]> routeLines(Set<Integer> ids, int done, double t, double offset) {
        List<Node> rn = route.nodes();
        List<double[]> lines = new ArrayList<>();
        List<Double> points = new ArrayList<>();
        for (int i = 0; i <= done && i + 1 < rn.size(); i++) {
            Node a = rn.get(i);
            Node b = rn.get(i + 1);
            double f = i < done ? 1 : t;
            if (!ids.contains(a.id()) || !ids.contains(b.id()) || f <= 0) {
                addLine(lines, points);
                continue;
            }
            if (points.isEmpty()) {
                points.add(offset + vx(a.x()));
                points.add(offset + vy(a.y()));
            }
            points.add(offset + vx(a.x() + (b.x() - a.x()) * f));
            points.add(offset + vy(a.y() + (b.y() - a.y()) * f));
        }
        addLine(lines, points);
        return lines;
    }

    private static void addLine(List<double[]> lines, List<Double> points) {
        if (points.size() >= 4) {
            double[] line = new double[points.size()];
            for (int i = 0; i < line.length; i++) {
                line[i] = points.get(i);
            }
            lines.add(line);
        }
        points.clear();
    }

    private static void strokeLines(GraphicsContext g, List<double[]> lines, Color color, double width) {
        g.beginPath();
        for (double[] l : lines) {
            g.moveTo(l[0], l[1]);
            for (int i = 2; i + 1 < l.length; i += 2) {
                g.lineTo(l[i], l[i + 1]);
            }
        }
        g.setStroke(color);
        g.setLineWidth(width);
        g.stroke();
    }

    /**
     * Small white chevrons along a line, pointing the way to walk: every ARROW_GAP from {@code first} on, and none
     * in the last {@code keepClear} (the destination pin, or the walker).
     */
    private static void arrows(GraphicsContext g, double[] l, double first, double keepClear, Color color) {
        double total = 0;
        for (int i = 0; i + 3 < l.length; i += 2) {
            total += Math.hypot(l[i + 2] - l[i], l[i + 3] - l[i + 1]);
        }
        g.setStroke(color);
        g.setLineWidth(2);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        double next = first;
        double walked = 0;
        for (int i = 0; i + 3 < l.length; i += 2) {
            double seg = Math.hypot(l[i + 2] - l[i], l[i + 3] - l[i + 1]);
            if (seg < 1e-6) {
                continue;
            }
            double ux = (l[i + 2] - l[i]) / seg;
            double uy = (l[i + 3] - l[i + 1]) / seg;
            while (next <= walked + seg) {
                if (next > total - keepClear) {
                    return;
                }
                double px = l[i] + ux * (next - walked);
                double py = l[i + 1] + uy * (next - walked);
                g.strokePolyline(
                        new double[] {px - ux * 2.5 - uy * 3.5, px + ux * 2.5, px - ux * 2.5 + uy * 3.5},
                        new double[] {py - uy * 2.5 + ux * 3.5, py + uy * 2.5, py - uy * 2.5 - ux * 3.5}, 3);
                next += ARROW_GAP;
            }
            walked += seg;
        }
    }

    /**
     * The route animation's layer: the walked part of the route in red with arrows flowing along it, the ring that
     * pops at each step, the walker (a white disc with a red arrow, its halo breathing) and its tag.
     */
    private void drawPreview() {
        Preview p = preview;
        overlay.setVisible(p != null);
        GraphicsContext g = overlay.getGraphicsContext2D();
        g.clearRect(0, 0, overlay.getWidth(), overlay.getHeight());
        if (p == null || route == null || data == null || viewW < 10 || viewH < 10 || scale <= 0) {
            return;
        }
        Set<Integer> ids = visibleIds();
        g.save();
        g.setGlobalAlpha(p.alpha());
        g.setLineDashes((double[]) null);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        List<double[]> trail = routeLines(ids, p.done(), p.t(), 0);
        strokeLines(g, trail, Color.WHITE, 11);
        strokeLines(g, trail, routeColor(), 7);
        double flow = (p.clock() * ARROW_SPEED) % ARROW_GAP;
        for (double[] line : trail) {
            arrows(g, line, flow, 16, Color.WHITE);
        }
        if (p.ringAge() >= 0 && p.ringAge() < RING_S) {
            double k = p.ringAge() / RING_S;
            double r = 12 + 26 * (1 - Math.pow(1 - k, 3));
            g.setStroke(routeColor().deriveColor(0, 1, 1, 0.6 * (1 - k)));
            g.setLineWidth(3);
            g.strokeOval(vx(p.ringX()) - r, vy(p.ringY()) - r, 2 * r, 2 * r);
        }
        double x = vx(p.x());
        double y = vy(p.y());
        double s = p.size();
        if (s > 0.01) {
            double halo = (19 + 3 * Math.sin(p.clock() * 4.2)) * s;
            g.setFill(routeColor().deriveColor(0, 1, 1, 0.18));
            g.fillOval(x - halo, y - halo, 2 * halo, 2 * halo);
            g.setFill(Color.web("#000000", 0.22));
            g.fillOval(x - 12 * s, y - 12 * s + 2, 24 * s, 24 * s);
            g.setFill(Color.WHITE);
            g.fillOval(x - 12 * s, y - 12 * s, 24 * s, 24 * s);
            g.save();
            g.translate(x, y);
            g.rotate(p.angle());
            g.scale(s, s);
            g.setFill(routeColor());
            g.fillPolygon(new double[] {0, 6.5, 0, -6.5}, new double[] {-8, 7.5, 3.5, 7.5}, 4);
            g.restore();
        }
        if (p.tag() != null && p.tagAlpha() > 0.01) {
            g.setGlobalAlpha(p.alpha() * Math.min(1, p.tagAlpha()));
            double w = p.tag().length() * 6.2 + 14;
            double tx = Math.max(w / 2 + 8, Math.min(viewW - w / 2 - 8, x));
            tag(g, p.tag(), tx, y - 32 - 6 * (1 - Math.min(1, p.tagAlpha())), routeColor(), Color.WHITE);
        }
        g.restore();
    }

    /** With a map picture the picture has the names, so only small tap targets are drawn. */
    private void drawPlaces(GraphicsContext g, List<Node> nodes, boolean onPicture) {
        Node first = route == null ? null : route.nodes().get(0);
        Node last = route == null ? null : route.nodes().get(route.nodes().size() - 1);
        boolean floor3d = floorBuilding3d() != null;
        List<double[]> taken = new ArrayList<>();
        Set<Integer> pinned = new HashSet<>();
        highlights.forEach(h -> pinned.add(h.id()));
        // route ends first, so their names are never the ones left out
        List<Node> ordered = new ArrayList<>(nodes);
        ordered.sort((a, b) -> Boolean.compare(!isEnd(b, first, last), !isEnd(a, first, last)));
        for (Node n : ordered) {
            if (pinned.contains(n.id())) {
                continue;
            }
            double x = sx(n.x());
            double y = sy(n.y());
            boolean isEnd = n == first || n == last
                    || (first != null && n.id() == first.id()) || (last != null && n.id() == last.id());
            String type = n.nodeType();
            if ("STAIRS".equals(type) && !onPicture) {
                stairsIcon(g, x, y);
                continue;
            }
            if ("ELEVATOR".equals(type) && !onPicture) {
                if (!floor3d) {
                    g.setFill(GREEN);
                    g.fillRoundRect(x - 7, y - 7, 14, 14, 4, 4);
                }
                if (level != null) {
                    tag(g, "STAIRS".equals(type) ? "Stairs" : "Elevator", x, y + 18,
                            dark ? Color.web("#242a36") : Color.WHITE, dark ? Color.web("#E7E9EF") : INK);
                }
                continue;
            }
            if (!SnapshotReader.isSearchable(type)) {
                continue;
            }
            if (onPicture) {
                if (!isEnd) {
                    g.setFill(Color.WHITE);
                    g.fillOval(x - 5, y - 5, 10, 10);
                    g.setFill(BRAND);
                    g.fillOval(x - 3.5, y - 3.5, 7, 7);
                }
                continue;
            }
            if (level == null && "BUILDING_ENTRANCE".equals(type)) {
                continue;
            }
            if (!isEnd) {
                g.setFill(dark ? DOT_RING_DARK : Color.WHITE);
                g.fillOval(x - 6, y - 6, 12, 12);
                g.setFill(dark ? DOT_DARK : DOT_LIGHT);
                g.fillOval(x - 4, y - 4, 8, 8);
            }
            // names that would overlap one already drawn are left out (zoom in to see them)
            String text = shortName(n.name());
            double ly = y + (isEnd ? 16 : 14);
            double[] box = {x - text.length() * 3.1 - 2, ly - 7, x + text.length() * 3.1 + 2, ly + 7};
            boolean overlaps = false;
            for (double[] t : taken) {
                overlaps |= box[0] < t[2] && t[0] < box[2] && box[1] < t[3] && t[1] < box[3];
            }
            if (!overlaps || isEnd) {
                label(g, text, x, ly, isEnd);
                taken.add(box);
            }
        }
    }

    /** Stairs on a floor map: a small tile with a green staircase in it. */
    private void stairsIcon(GraphicsContext g, double x, double y) {
        double s = 24;
        g.setFill(Color.web("#000000", 0.15));
        g.fillRoundRect(x - s / 2 + 1, y - s / 2 + 2, s, s, 7, 7);
        g.setFill(dark ? Color.web("#1A1E27") : Color.WHITE);
        g.fillRoundRect(x - s / 2, y - s / 2, s, s, 7, 7);
        g.setStroke(GREEN);
        g.setLineWidth(1.6);
        g.strokeRoundRect(x - s / 2, y - s / 2, s, s, 7, 7);
        double a = x - 7.5;
        double b = y + 7;
        double w = 5;
        double h = 4.7;
        g.setFill(dark ? Color.web("#4ADE80") : GREEN);
        g.fillPolygon(new double[] {a, a, a + w, a + w, a + 2 * w, a + 2 * w, a + 3 * w, a + 3 * w},
                new double[] {b, b - h, b - h, b - 2 * h, b - 2 * h, b - 3 * h, b - 3 * h, b}, 8);
    }

    private static boolean isEnd(Node n, Node first, Node last) {
        return (first != null && n.id() == first.id()) || (last != null && n.id() == last.id());
    }

    private static void pin(GraphicsContext g, double x, double y) {
        g.setFill(Color.web("#000000", 0.18));
        g.fillOval(x - 6, y - 3, 12, 6);
        g.setFill(BRAND);
        g.beginPath();
        g.moveTo(x, y);
        g.lineTo(x - 8.5, y - 16);
        g.lineTo(x + 8.5, y - 16);
        g.closePath();
        g.fill();
        g.fillOval(x - 11, y - 33, 22, 22);
        g.setFill(Color.WHITE);
        g.fillOval(x - 4.5, y - 26.5, 9, 9);
    }

    private void label(GraphicsContext g, String text, double x, double y, boolean bold) {
        g.setFont(bold ? LABEL_BOLD_FONT : LABEL_FONT);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        CampusArt.haloText(g, text, x, y, dark ? HALO_DARK : HALO_LIGHT, dark ? INK_DARK : INK);
    }

    private static void tag(GraphicsContext g, String text, double x, double y, Color bg, Color fg) {
        g.setFont(TAG_FONT);
        double w = text.length() * 6.2 + 14;
        g.setFill(SHADOW);
        g.fillRoundRect(x - w / 2, y - 9 + 1.5, w, 18, 9, 9);
        g.setFill(bg);
        g.fillRoundRect(x - w / 2, y - 9, w, 18, 9, 9);
        g.setFill(fg);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(text, x, y);
    }

    /** "Engineering " when a floor of another building is meant (a bridge), "" for this building's floors. */
    private String otherBuilding(Floor there) {
        Integer here = level == null ? null : data.floor(level).map(Floor::buildingId).orElse(null);
        if (here == null || here == there.buildingId()) {
            return "";
        }
        return data.building(there.buildingId()).map(b -> shortName(b.name()) + " ").orElse("");
    }

    /** Keeps map labels short: "College of Engineering Building" -> "Engineering". */
    static String shortName(String name) {
        // worked out once per name: this is asked for every place each time the map is drawn
        return SHORT_NAMES.computeIfAbsent(name, n -> {
            String s = BRACKETS.matcher(n).replaceAll("");
            s = LEADING_WORDS.matcher(s).replaceFirst("");
            s = ACADEMIC.matcher(s).replaceFirst("$1");
            s = TRAILING_WORDS.matcher(s).replaceFirst("");
            return s.length() > 22 ? s.substring(0, 21) + "…" : s;
        });
    }

    private Node placeAt(double x, double y) {
        if (data == null) {
            return null;
        }
        Node best = null;
        double bestD = 22 * 22;
        for (int id : visibleIds()) {
            Node n = data.graph().node(id).orElseThrow();
            if (!pickAnyNode && !SnapshotReader.isSearchable(n.nodeType())) {
                continue;
            }
            double dx = vx(n.x()) - x;
            double dy = vy(n.y()) - y;
            double d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }
}

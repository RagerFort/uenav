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
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Draws one level of the campus (the outdoor campus, or one building floor) with the route on top (D6).
 * If the app contains a map picture for the level it is drawn underneath: /maps/campus.png for the campus, and
 * /maps/ + the floor's planImagePath for a floor. Node X/Y are pixels of that picture. With no picture, a simple
 * plan is drawn from the node positions. Drag to move, scroll or use zoomIn/zoomOut to zoom, tap a place to pick it.
 */
public class MapView extends Region {

    private static final Color BRAND = Color.web("#C8102E");
    private static final Color INK = Color.web("#1F2330");
    private static final Color BLUE = Color.web("#2563EB");
    private static final Color GREEN = Color.web("#15803D");
    private static final Map<String, Optional<Image>> IMAGES = new HashMap<>();

    private final Canvas canvas = new Canvas();
    private CampusData data;
    private Route route;
    private Integer level;
    private double zoom = 1;
    private double panX;
    private double panY;
    private double padTop = 24;
    private double padBottom = 24;
    private double dragX;
    private double dragY;
    private boolean dragged;
    private Consumer<Node> onPlaceClicked;

    // transform of the last drawing, for taps
    private double scale = 1;
    private double originX;
    private double originY;
    private double centreX;
    private double centreY;

    public MapView() {
        getStyleClass().add("nv-map-bg");
        getChildren().add(canvas);
        setMinSize(0, 0);
        canvas.setOnMousePressed(e -> {
            dragX = e.getX();
            dragY = e.getY();
            dragged = false;
        });
        canvas.setOnMouseDragged(e -> {
            panX += e.getX() - dragX;
            panY += e.getY() - dragY;
            dragged |= Math.abs(e.getX() - dragX) + Math.abs(e.getY() - dragY) > 2;
            dragX = e.getX();
            dragY = e.getY();
            draw();
        });
        canvas.setOnMouseClicked(e -> {
            if (!dragged && onPlaceClicked != null) {
                Node hit = placeAt(e.getX(), e.getY());
                if (hit != null) {
                    onPlaceClicked.accept(hit);
                }
            }
        });
        canvas.setOnScroll(e -> {
            if (e.getDeltaY() > 0) {
                zoomIn();
            } else if (e.getDeltaY() < 0) {
                zoomOut();
            }
        });
    }

    public void setData(CampusData data) {
        this.data = data;
        resetView();
    }

    public void setRoute(Route route) {
        this.route = route;
        resetView();
    }

    /** null shows the outdoor campus; otherwise a floor id. */
    public void setLevel(Integer floorId) {
        this.level = floorId;
        resetView();
    }

    public Integer level() {
        return level;
    }

    /** Space (pixels) kept free at the top and bottom for cards drawn over the map. */
    public void setPadding(double top, double bottom) {
        this.padTop = top;
        this.padBottom = bottom;
        draw();
    }

    public void setOnPlaceClicked(Consumer<Node> handler) {
        this.onPlaceClicked = handler;
    }

    public void zoomIn() {
        zoom = Math.min(6, zoom * 1.3);
        draw();
    }

    public void zoomOut() {
        zoom = Math.max(0.4, zoom / 1.3);
        draw();
    }

    public void resetView() {
        zoom = 1;
        panX = 0;
        panY = 0;
        draw();
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
                if (!onlyEntrance) {
                    levels.add(n.floorId());
                }
            }
        }
        return new ArrayList<>(levels);
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        if (canvas.getWidth() != w || canvas.getHeight() != h) {
            canvas.setWidth(w);
            canvas.setHeight(h);
            draw();
        }
    }

    // ---------------------------------------------------------------- drawing

    private Set<Integer> visibleIds() {
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
            for (Edge e : data.graph().edges()) {
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
        String path;
        if (level == null) {
            path = "campus.png";
        } else {
            path = data.floor(level).map(Floor::planImagePath).orElse(null);
        }
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        return IMAGES.computeIfAbsent(path, p -> {
            try (InputStream in = MapView.class.getResourceAsStream("/maps/" + p)) {
                return in == null ? Optional.empty() : Optional.of(new Image(in));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    private double sx(double x) {
        return originX + (x - centreX) * scale;
    }

    private double sy(double y) {
        return originY + (y - centreY) * scale;
    }

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#E9ECEF"));
        g.fillRect(0, 0, w, h);
        if (data == null || w < 10 || h < 10) {
            return;
        }
        Set<Integer> ids = visibleIds();
        List<Node> nodes = new ArrayList<>();
        for (int id : ids) {
            data.graph().node(id).ifPresent(nodes::add);
        }
        Optional<Image> img = image();

        // fit the level into the free area
        double minX;
        double minY;
        double maxX;
        double maxY;
        if (img.isPresent()) {
            minX = 0;
            minY = 0;
            maxX = img.get().getWidth();
            maxY = img.get().getHeight();
        } else if (!nodes.isEmpty()) {
            minX = nodes.stream().mapToDouble(Node::x).min().orElse(0);
            maxX = nodes.stream().mapToDouble(Node::x).max().orElse(0);
            minY = nodes.stream().mapToDouble(Node::y).min().orElse(0);
            maxY = nodes.stream().mapToDouble(Node::y).max().orElse(0);
        } else {
            minX = 0;
            minY = 0;
            maxX = 1;
            maxY = 1;
        }
        double margin = img.isPresent() ? 8 : 36;
        double areaW = Math.max(20, w - 2 * margin);
        double areaH = Math.max(20, h - padTop - padBottom - 2 * margin);
        double bw = Math.max(1, maxX - minX);
        double bh = Math.max(1, maxY - minY);
        double fit = Math.min(areaW / bw, areaH / bh);
        if (!img.isPresent()) {
            fit = Math.min(fit, 6);
        }
        scale = fit * zoom;
        centreX = (minX + maxX) / 2;
        centreY = (minY + maxY) / 2;
        originX = w / 2 + panX;
        originY = padTop + (h - padTop - padBottom) / 2 + panY;

        if (img.isPresent()) {
            g.drawImage(img.get(), sx(0), sy(0), img.get().getWidth() * scale, img.get().getHeight() * scale);
        } else {
            drawGround(g, minX, minY, maxX, maxY);
        }

        // walkways on this level
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        for (Edge e : data.graph().edges()) {
            if (ids.contains(e.fromNodeId()) && ids.contains(e.toNodeId())) {
                Node a = data.graph().node(e.fromNodeId()).orElseThrow();
                Node b = data.graph().node(e.toNodeId()).orElseThrow();
                g.setStroke(level == null ? Color.web("#FFFFFF") : Color.web("#EEF0F3"));
                g.setLineWidth(level == null ? 7 : 12);
                g.strokeLine(sx(a.x()), sy(a.y()), sx(b.x()), sy(b.y()));
            }
        }
        if (level == null && !img.isPresent()) {
            drawBuildings(g, ids);
        }
        drawRoute(g, ids);
        drawPlaces(g, nodes);
    }

    private void drawGround(GraphicsContext g, double minX, double minY, double maxX, double maxY) {
        double pad = level == null ? 34 : 26;
        double x = sx(minX) - pad;
        double y = sy(minY) - pad;
        double rw = (maxX - minX) * scale + 2 * pad;
        double rh = (maxY - minY) * scale + 2 * pad;
        if (level == null) {
            g.setFill(Color.web("#F6F1E7"));
            g.fillRoundRect(x, y, rw, rh, 18, 18);
            g.setStroke(BRAND.deriveColor(0, 1, 1, 0.7));
            g.setLineWidth(1.5);
            g.setLineDashes(6, 5);
            g.strokeRoundRect(x, y, rw, rh, 18, 18);
            g.setLineDashes((double[]) null);
        } else {
            g.setFill(Color.WHITE);
            g.fillRoundRect(x, y, rw, rh, 14, 14);
            g.setStroke(Color.web("#D1D5DB"));
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
            label(g, shortName(b.name()), cx, cy + bh / 2 + 12, INK, false);
        }
    }

    private void drawRoute(GraphicsContext g, Set<Integer> ids) {
        if (route == null || route.nodes().size() < 2) {
            return;
        }
        List<Node> rn = route.nodes();
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        for (int pass = 0; pass < 3; pass++) {
            g.beginPath();
            boolean open = false;
            for (int i = 0; i + 1 < rn.size(); i++) {
                Node a = rn.get(i);
                Node b = rn.get(i + 1);
                if (ids.contains(a.id()) && ids.contains(b.id())) {
                    if (!open) {
                        g.moveTo(sx(a.x()), sy(a.y()));
                        open = true;
                    }
                    g.lineTo(sx(b.x()), sy(b.y()));
                } else {
                    open = false;
                }
            }
            if (pass == 0) {
                g.setStroke(Color.WHITE);
                g.setLineWidth(11);
                g.setLineDashes((double[]) null);
            } else if (pass == 1) {
                g.setStroke(BRAND);
                g.setLineWidth(7);
            } else {
                g.setStroke(Color.WHITE);
                g.setLineWidth(2);
                g.setLineDashes(5, 7);
            }
            g.stroke();
        }
        g.setLineDashes((double[]) null);

        // hand-over points to another level
        for (int i = 0; i + 1 < rn.size(); i++) {
            Node a = rn.get(i);
            Node b = rn.get(i + 1);
            if (ids.contains(a.id()) != ids.contains(b.id())) {
                Node here = ids.contains(a.id()) ? a : b;
                Node there = here == a ? b : a;
                String text = data.floor(there.floorId()).map(f -> "To " + f.shortLabel())
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

    private void drawPlaces(GraphicsContext g, List<Node> nodes) {
        Node first = route == null ? null : route.nodes().get(0);
        Node last = route == null ? null : route.nodes().get(route.nodes().size() - 1);
        for (Node n : nodes) {
            double x = sx(n.x());
            double y = sy(n.y());
            boolean isEnd = n == first || n == last
                    || (first != null && n.id() == first.id()) || (last != null && n.id() == last.id());
            String type = n.nodeType();
            if ("STAIRS".equals(type) || "ELEVATOR".equals(type)) {
                g.setFill(GREEN);
                g.fillRoundRect(x - 7, y - 7, 14, 14, 4, 4);
                if (level != null) {
                    tag(g, "STAIRS".equals(type) ? "Stairs" : "Elevator", x, y + 18, Color.WHITE, INK);
                }
                continue;
            }
            if (!SnapshotReader.isSearchable(type)) {
                continue;
            }
            if (level == null && "BUILDING_ENTRANCE".equals(type)) {
                continue;
            }
            if (!isEnd) {
                g.setFill(Color.WHITE);
                g.fillOval(x - 6, y - 6, 12, 12);
                g.setFill(Color.web("#6B7280"));
                g.fillOval(x - 4, y - 4, 8, 8);
            }
            label(g, shortName(n.name()), x, y + (isEnd ? 16 : 14), INK, isEnd);
        }
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

    private static void label(GraphicsContext g, String text, double x, double y, Color color, boolean bold) {
        g.setFont(Font.font(null, bold ? FontWeight.BOLD : FontWeight.NORMAL, 11));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.setStroke(Color.web("#FFFFFF", 0.9));
        g.setLineWidth(3);
        g.strokeText(text, x, y);
        g.setFill(color);
        g.fillText(text, x, y);
    }

    private static void tag(GraphicsContext g, String text, double x, double y, Color bg, Color fg) {
        g.setFont(Font.font(null, FontWeight.BOLD, 10));
        double w = text.length() * 6.2 + 14;
        g.setFill(Color.web("#000000", 0.10));
        g.fillRoundRect(x - w / 2, y - 9 + 1.5, w, 18, 9, 9);
        g.setFill(bg);
        g.fillRoundRect(x - w / 2, y - 9, w, 18, 9, 9);
        g.setFill(fg);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(text, x, y);
    }

    /** Keeps map labels short: "College of Engineering Building" -> "Engineering". */
    static String shortName(String name) {
        String s = name.replaceAll("\\s*\\(.*\\)", "");
        s = s.replaceFirst("^(College of|University)\\s+", "");
        s = s.replaceFirst("\\s+(Building|Office)$", "");
        return s.length() > 22 ? s.substring(0, 21) + "…" : s;
    }

    private Node placeAt(double x, double y) {
        if (data == null) {
            return null;
        }
        Node best = null;
        double bestD = 22 * 22;
        for (int id : visibleIds()) {
            Node n = data.graph().node(id).orElseThrow();
            if (!SnapshotReader.isSearchable(n.nodeType())) {
                continue;
            }
            double dx = sx(n.x()) - x;
            double dy = sy(n.y()) - y;
            double d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }
}

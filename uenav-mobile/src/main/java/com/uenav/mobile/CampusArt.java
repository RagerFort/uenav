package com.uenav.mobile;

import com.uenav.core.MiniJson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * The campus drawn in basic 3D shapes (Figma: 3D campus map): roads, the campus ground, the field, and buildings as
 * raised blocks with shaded walls and trees. Shapes come from /maps/campus-3d.json, in the same pixel frame as the
 * node X/Y, so routes line up. Heights are drawn straight up the screen, like the campus map picture.
 */
final class CampusArt {

    /** Map units to screen: MapView's transform. */
    interface View {
        double x(double mapX);

        double y(double mapY);

        double scale();
    }

    record Shape(String kind, String label, Color color, double height, Integer buildingId, double[] xs, double[] ys) {

        double maxY() {
            double m = Double.NEGATIVE_INFINITY;
            for (double y : ys) {
                m = Math.max(m, y);
            }
            return m;
        }

        double cx() {
            double s = 0;
            for (double x : xs) {
                s += x;
            }
            return s / xs.length;
        }

        double cy() {
            double s = 0;
            for (double y : ys) {
                s += y;
            }
            return s / ys.length;
        }
    }

    private final double[] bounds;
    private final List<Shape> flat = new ArrayList<>();
    private final List<Shape> raised = new ArrayList<>();

    private CampusArt(double[] bounds, List<Shape> shapes) {
        this.bounds = bounds;
        for (Shape s : shapes) {
            ("building".equals(s.kind()) || "tree".equals(s.kind()) ? raised : flat).add(s);
        }
        // painter's order: shapes further back (smaller Y) first, so nearer blocks cover them
        raised.sort(Comparator.comparingDouble(Shape::maxY));
    }

    /** The shapes in the app, or null if the file is missing or cannot be read. */
    static CampusArt load(String resource) {
        try (InputStream in = CampusArt.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static CampusArt parse(String json) {
        Map<String, Object> root = (Map<String, Object>) MiniJson.parse(json);
        List<Object> b = (List<Object>) root.get("bounds");
        double[] bounds = {num(b.get(0)), num(b.get(1)), num(b.get(2)), num(b.get(3))};
        List<Shape> shapes = new ArrayList<>();
        for (Object o : (List<Object>) root.get("shapes")) {
            Map<String, Object> m = (Map<String, Object>) o;
            List<Object> pts = (List<Object>) m.get("points");
            double[] xs = new double[pts.size()];
            double[] ys = new double[pts.size()];
            for (int i = 0; i < pts.size(); i++) {
                List<Object> p = (List<Object>) pts.get(i);
                xs[i] = num(p.get(0));
                ys[i] = num(p.get(1));
            }
            Object label = m.get("label");
            Object bid = m.get("buildingId");
            shapes.add(new Shape((String) m.get("kind"), label instanceof String s ? s : null,
                    Color.web((String) m.get("color")), num(m.get("height")),
                    bid instanceof Double d ? Integer.valueOf(d.intValue()) : null, xs, ys));
        }
        return new CampusArt(bounds, shapes);
    }

    private static double num(Object o) {
        return o instanceof Double d ? d : 0;
    }

    double[] bounds() {
        return bounds.clone();
    }

    int buildingCount() {
        return (int) raised.stream().filter(s -> "building".equals(s.kind())).count();
    }

    /** The blocks that make up one building, for drawing its floors. */
    List<Shape> footprint(int buildingId) {
        List<Shape> list = new ArrayList<>();
        for (Shape sh : raised) {
            if (sh.buildingId() != null && sh.buildingId() == buildingId) {
                list.add(sh);
            }
        }
        return list;
    }

    /** minX, minY, maxX, maxY around a building's blocks (with a margin), or null if it has none. */
    double[] footprintBounds(int buildingId) {
        List<Shape> parts = footprint(buildingId);
        if (parts.isEmpty()) {
            return null;
        }
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Shape sh : parts) {
            for (int i = 0; i < sh.xs().length; i++) {
                b[0] = Math.min(b[0], sh.xs()[i]);
                b[1] = Math.min(b[1], sh.ys()[i]);
                b[2] = Math.max(b[2], sh.xs()[i]);
                b[3] = Math.max(b[3], sh.ys()[i]);
            }
        }
        double m = 20;
        return new double[] {b[0] - m, b[1] - m, b[2] + m, b[3] + m};
    }

    /** One floor of a building: its outline as a thin 3D slab, in a light version of the building colour. */
    void drawFloor(GraphicsContext g, View v, int buildingId) {
        for (Shape sh : footprint(buildingId)) {
            Color light = sh.color().interpolate(Color.WHITE, 0.78);
            block(g, v, new Shape("building", null, light, 7, buildingId, sh.xs(), sh.ys()));
        }
    }

    /** A small block standing on the map, such as stairs or an elevator on a floor plan. */
    static void cube(GraphicsContext g, View v, double mapX, double mapY, double size, double height, Color color) {
        double h = size / 2;
        block(g, v, new Shape("building", null, color, height, null,
                new double[] {mapX - h, mapX + h, mapX + h, mapX - h},
                new double[] {mapY - h, mapY - h, mapY + h, mapY + h}));
    }

    /** Roads, campus ground, field and other flat areas. */
    void drawGround(GraphicsContext g, View v) {
        for (Shape s : flat) {
            double[] px = new double[s.xs().length];
            double[] py = new double[s.ys().length];
            for (int i = 0; i < px.length; i++) {
                px[i] = v.x(s.xs()[i]);
                py[i] = v.y(s.ys()[i]);
            }
            g.setFill(s.color());
            g.fillPolygon(px, py, px.length);
            if ("ground".equals(s.kind())) {
                g.setStroke(Color.web("#C8102E", 0.75));
                g.setLineWidth(1.6);
                g.setLineDashes(7, 5);
                g.strokePolygon(px, py, px.length);
                g.setLineDashes((double[]) null);
            } else if ("area".equals(s.kind()) && s.label() != null) {
                g.setStroke(Color.web("#FFFFFF", 0.55));
                g.setLineWidth(1.2);
                g.strokePolygon(px, py, px.length);
            }
            if ("road".equals(s.kind()) && s.label() != null) {
                roadLabel(g, s, px, py, v.scale());
            } else if ("area".equals(s.kind()) && s.label() != null && v.scale() > 0.35) {
                text(g, s.label().toUpperCase(), v.x(s.cx()), v.y(s.cy()), Color.web("#FFFFFF", 0.9), null,
                        Math.max(10, Math.min(22, 26 * v.scale())), true);
            }
        }
    }

    /** Buildings and trees, back to front. */
    void drawRaised(GraphicsContext g, View v) {
        for (Shape s : raised) {
            if ("tree".equals(s.kind())) {
                tree(g, v, s);
            } else {
                block(g, v, s);
            }
        }
        // names last, so no roof covers them
        for (Shape s : raised) {
            if ("building".equals(s.kind()) && s.label() != null && v.scale() > 0.3) {
                double lift = s.height() * v.scale();
                text(g, s.label(), v.x(s.cx()), v.y(s.cy()) - lift, Color.web("#1F2330"), Color.web("#FFFFFF", 0.85),
                        11, true);
            }
        }
    }

    private static void block(GraphicsContext g, View v, Shape s) {
        int n = s.xs().length;
        double lift = s.height() * v.scale();
        double[] bx = new double[n];
        double[] by = new double[n];
        double[] ty = new double[n];
        for (int i = 0; i < n; i++) {
            bx[i] = v.x(s.xs()[i]);
            by[i] = v.y(s.ys()[i]);
            ty[i] = by[i] - lift;
        }
        // soft shadow on the ground, to the lower right
        double off = Math.min(10, lift * 0.35);
        double[] shx = new double[n];
        double[] shy = new double[n];
        for (int i = 0; i < n; i++) {
            shx[i] = bx[i] + off;
            shy[i] = by[i] + off * 0.5;
        }
        g.setFill(Color.web("#000000", 0.10));
        g.fillPolygon(shx, shy, n);

        // walls: further walls first; walls facing the viewer (down the screen) or the right are darker
        double cx = 0;
        double cy = 0;
        for (int i = 0; i < n; i++) {
            cx += bx[i] / n;
            cy += by[i] / n;
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        final double[] fby = by;
        java.util.Arrays.sort(order, Comparator.comparingDouble(i -> Math.max(fby[i], fby[(i + 1) % n])));
        g.setLineWidth(0.8);
        for (int i : order) {
            int j = (i + 1) % n;
            double mx = (bx[i] + bx[j]) / 2 - cx;
            double my = (by[i] + by[j]) / 2 - cy;
            if (my < -Math.abs(mx) * 0.2) {
                continue; // back wall: hidden by the roof
            }
            double shade = my > Math.abs(mx) ? 0.72 : (mx > 0 ? 0.62 : 0.84);
            Color wall = s.color().deriveColor(0, 1, shade, 1);
            double[] wx = {bx[i], bx[j], bx[j], bx[i]};
            double[] wy = {by[i], by[j], ty[j], ty[i]};
            g.setFill(wall);
            g.fillPolygon(wx, wy, 4);
            g.setStroke(wall.deriveColor(0, 1, 0.85, 1));
            g.strokePolygon(wx, wy, 4);
        }
        Color roof = s.color().deriveColor(0, 1, 1.06, 1);
        g.setFill(roof);
        g.fillPolygon(bx, ty, n);
        g.setStroke(s.color().deriveColor(0, 1, 0.8, 1));
        g.setLineWidth(1);
        g.strokePolygon(bx, ty, n);
    }

    private static void tree(GraphicsContext g, View v, Shape s) {
        double x = v.x(s.xs()[0]);
        double y = v.y(s.ys()[0]);
        double sc = Math.max(0.35, v.scale());
        double r = 9 * sc;
        double trunk = s.height() * sc;
        g.setFill(Color.web("#000000", 0.12));
        g.fillOval(x - r * 0.9 + 2, y - r * 0.35, r * 1.8, r * 0.7);
        g.setStroke(Color.web("#6B4F2A"));
        g.setLineWidth(Math.max(1.5, 2.5 * sc));
        g.setLineCap(StrokeLineCap.ROUND);
        g.strokeLine(x, y, x, y - trunk);
        g.setFill(s.color());
        g.fillOval(x - r, y - trunk - r * 1.6, r * 2, r * 2);
        g.setFill(s.color().deriveColor(0, 1, 1.35, 1));
        g.fillOval(x - r * 0.55, y - trunk - r * 1.4, r * 0.9, r * 0.8);
    }

    private static void roadLabel(GraphicsContext g, Shape s, double[] px, double[] py, double scale) {
        if (scale < 0.3 || px.length < 4) {
            return;
        }
        // middle of the band, turned along its long side
        double ax = (px[0] + px[3]) / 2;
        double ay = (py[0] + py[3]) / 2;
        double bx = (px[1] + px[2]) / 2;
        double by = (py[1] + py[2]) / 2;
        double angle = Math.toDegrees(Math.atan2(by - ay, bx - ax));
        if (angle > 90) {
            angle -= 180;
        } else if (angle < -90) {
            angle += 180;
        }
        g.save();
        g.translate((ax + bx) / 2, (ay + by) / 2);
        g.rotate(angle);
        text(g, s.label().toUpperCase(), 0, 0, Color.web("#6B7280"), null, 10, true);
        g.restore();
    }

    private static void text(GraphicsContext g, String t, double x, double y, Color fill, Color halo, double size,
                             boolean bold) {
        g.setFont(Font.font(null, bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        if (halo != null) {
            g.setStroke(halo);
            g.setLineWidth(3);
            g.strokeText(t, x, y);
        }
        g.setFill(fill);
        g.fillText(t, x, y);
    }
}

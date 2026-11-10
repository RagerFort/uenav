package com.uenav.mobile;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javax.imageio.ImageIO;

/**
 * Draws the UENav launcher icons from the same shapes as the logo on the loading screen (Logo.java):
 * legacy ic_launcher.png (red rounded tile), adaptive foreground (mark only) and monochrome (one colour, for
 * Android 13 themed icons). Every shape is built at the final pixel size, so curves stay smooth.
 * Developer tool, not a test. Run after mvn test-compile (like ScreenTour):
 *   java -cp "target/test-classes;<test classpath>" com.uenav.mobile.IconMaker src/android/res <preview folder>
 */
public final class IconMaker {

    private IconMaker() { }

    /** Icon colours: UE red tile, white route and pin, dark start dot. The dots are holes showing the tile. */
    static final Color BACKGROUND = Color.web("#C8102E");
    static final Color ROUTE = Color.WHITE;
    static final Color START = Color.web("#1F2330");
    static final Color PIN = Color.WHITE;
    static final String[] DENSITIES = {"ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    static final double[] FACTOR = {0.75, 1, 1.5, 2, 3, 4};
    /** Mark height in an adaptive layer, as a share of the logo's 100-unit frame per 108-unit layer. */
    static final double LAYER_MARK = 0.74;
    static final double TILE_MARK = 0.78;

    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        File preview = new File(args[1]);
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                for (int i = 0; i < DENSITIES.length; i++) {
                    String d = DENSITIES[i];
                    double f = FACTOR[i];
                    save(legacy(48 * f), 48 * f, new File(res, "mipmap-" + d + "/ic_launcher.png"));
                    save(layer(108 * f, false), 108 * f, new File(res, "mipmap-" + d + "/ic_launcher_foreground.png"));
                    save(layer(108 * f, true), 108 * f, new File(res, "mipmap-" + d + "/ic_launcher_monochrome.png"));
                }
                save(legacy(512), 512, new File(preview, "icon-512.png"));
                save(preview(432, false), 432, new File(preview, "adaptive-circle.png"));
                save(preview(432, true), 432, new File(preview, "adaptive-squircle.png"));
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    /**
     * The mark, k pixels per logo unit, with its middle at (cx, cy). The white parts of the logo (start dot centre,
     * station dots, pin eye) are holes, so the background shows through and a one-colour version is possible.
     */
    static Group mark(double k, double cx, double cy, boolean mono) {
        // the mark spans x 21..84, y 11..83 in the logo frame: its middle is (52.5, 47)
        double ox = cx - 52.5 * k;
        double oy = cy - 47 * k;
        SVGPath line = new SVGPath();
        line.setContent(path("M%s %s L%s %s Q%s %s %s %s Q%s %s %s %s L%s %s", k, ox, oy,
                30, 32, 30, 56, 30, 78, 50, 78, 70, 78, 70, 56, 70, 47));
        line.setFill(null);
        line.setStroke(Color.BLACK);
        line.setStrokeWidth(10 * k);
        line.setStrokeLineCap(StrokeLineCap.ROUND);
        line.setStrokeLineJoin(StrokeLineJoin.ROUND);
        line.setStroke(mono ? Color.BLACK : ROUTE);
        Circle start = circle(30, 30, 9, k, ox, oy); // on top of the route's first end
        start.setFill(mono ? Color.BLACK : START);
        SVGPath pin = new SVGPath();
        pin.setContent(path("M%s %s C%s %s %s %s %s %s A%s %s 0 0 1 %s %s C%s %s %s %s %s %s Z", k, ox, oy,
                70, 48, 70, 48, 56, 34, 56, 25, -14, -14, 84, 25, 84, 34, 70, 48, 70, 48));
        pin.setFill(mono ? Color.BLACK : PIN);
        Group g = new Group(line, start, pin);
        // The white dots are holes: a mask (everything minus the dots), so the curves above are drawn untouched.
        Shape mask = new Rectangle(cx - 60 * k, cy - 60 * k, 120 * k, 120 * k);
        for (double[] p : new double[][] {{30, 58, 3.2}, {50, 78, 3.2}, {70, 58, 3.2}, {30, 30, 3.6}, {70, 25, 5.2}}) {
            mask = Shape.subtract(mask, circle(p[0], p[1], p[2], k, ox, oy));
        }
        g.setClip(mask);
        return g;
    }

    /** Fills a path template with logo coordinates turned into pixels; a negative pair is a radius (A rx ry). */
    static String path(String template, double k, double ox, double oy, double... xy) {
        Object[] v = new Object[xy.length];
        for (int i = 0; i < xy.length; i += 2) {
            boolean radius = xy[i] < 0;
            v[i] = num(radius ? -xy[i] * k : ox + xy[i] * k);
            v[i + 1] = num(radius ? -xy[i + 1] * k : oy + xy[i + 1] * k);
        }
        return String.format(Locale.ROOT, template, v);
    }

    static String num(double d) {
        return String.format(Locale.ROOT, "%.4f", d);
    }

    static Circle circle(double x, double y, double r, double k, double ox, double oy) {
        return new Circle(ox + x * k, oy + y * k, r * k);
    }

    /** Older Androids: the whole icon, a red rounded tile with the mark, transparent corners. */
    static Group legacy(double px) {
        double u = px / 100;
        Rectangle tile = new Rectangle(4 * u, 4 * u, 92 * u, 92 * u);
        tile.setArcWidth(40 * u);
        tile.setArcHeight(40 * u);
        tile.setFill(BACKGROUND);
        return new Group(frame(px), tile, mark(u * TILE_MARK, px / 2, px / 2, false));
    }

    /**
     * Adaptive icon layer, 108 units square. Launchers cut it to a circle or squircle of the middle 72 units and
     * may move it around: the mark stays in the middle 66 (the safe zone).
     */
    static Group layer(double px, boolean mono) {
        double u = px / 108;
        return new Group(frame(px), mark(u * LAYER_MARK, px / 2, px / 2 + 0.5 * u, mono));
    }

    /** What a launcher shows: the background colour and the foreground, cut to a circle or a squircle. For checking. */
    static Group preview(double px, boolean squircle) {
        double u = px / 108;
        Rectangle bg = new Rectangle(0, 0, px, px);
        bg.setFill(BACKGROUND);
        Group both = new Group(bg, layer(px, false));
        if (squircle) {
            Rectangle r = new Rectangle(18 * u, 18 * u, 72 * u, 72 * u);
            r.setArcWidth(44 * u);
            r.setArcHeight(44 * u);
            both.setClip(r);
        } else {
            both.setClip(new Circle(54 * u, 54 * u, 36 * u));
        }
        Rectangle back = new Rectangle(0, 0, px, px);
        back.setFill(Color.web("#8a94a6"));
        return new Group(back, both);
    }

    static Rectangle frame(double px) {
        Rectangle r = new Rectangle(0, 0, px, px);
        r.setFill(Color.TRANSPARENT);
        return r;
    }

    static void save(Group g, double px, File file) throws Exception {
        file.getParentFile().mkdirs();
        SnapshotParameters p = new SnapshotParameters();
        p.setFill(Color.TRANSPARENT);
        p.setViewport(new javafx.geometry.Rectangle2D(0, 0, px, px));
        WritableImage img = g.snapshot(p, null);
        int w = (int) Math.round(img.getWidth());
        int h = (int) Math.round(img.getHeight());
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getPixelReader().getPixels(0, y, w, 1, PixelFormat.getIntArgbInstance(), row, 0, w);
            bi.setRGB(0, y, w, 1, row, 0, w);
        }
        ImageIO.write(bi, "png", file);
        System.out.println(file.getParentFile().getName() + "/" + file.getName() + " " + w + "x" + h);
    }
}

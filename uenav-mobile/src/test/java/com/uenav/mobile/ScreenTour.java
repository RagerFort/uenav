package com.uenav.mobile;

import com.uenav.core.CampusData;
import com.uenav.core.Node;
import com.uenav.core.PlaceCategory;
import com.uenav.core.RoutePreference;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javax.imageio.ImageIO;

/**
 * Developer tool, not a unit test: draws the visitor screens off-screen in a phone-sized window (390x820), in light
 * and dark mode, and saves them as PNG files, so changes can be checked without a phone.
 * Run (after mvn test-compile):  java -cp "target/test-classes;target/classes;<test classpath>"
 *   com.uenav.mobile.ScreenTour <output folder>
 */
public final class ScreenTour {

    private static File out;
    private static Navigator nav;
    private static Settings settings;
    private static CampusData data;

    private ScreenTour() { }

    public static void main(String[] args) throws Exception {
        out = new File(args.length > 0 ? args[0] : "screens");
        out.mkdirs();
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                tour(false);
                tour(true);
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
        System.out.println("Saved screens to " + out.getAbsolutePath());
    }

    private static void tour(boolean dark) throws Exception {
        String p = dark ? "dark-" : "light-";
        data = CampusStore.bundled();
        settings = new Settings(new MemoryStore());
        settings.setDarkMode(dark);
        PlacesMemory memory = new PlacesMemory(new MemoryStore());
        UsageLog log = new UsageLog() {
            @Override
            public void route(int fromNodeId, int toNodeId, RoutePreference pref) { }

            @Override
            public void feedback(int fromNodeId, int toNodeId, RoutePreference pref, String feedbackType) { }
        };
        nav = new Navigator(data, log, false, settings, memory);
        nav.setOnThemeToggle(() -> { });
        nav.setDark(dark);
        nav.openExplore();
        shot(p + "01-home");
        call("showPlanScreen", true, false); // final layout, without the slide-in animation
        shot(p + "01b-where-are-you");
        settingsShot(p + "01c-settings");

        call("showCategory", PlaceCategory.OFFICES);
        shot(p + "02-category-no-start");

        nav.handleCode("UENAV:1");
        shot(p + "03-home-you-are-here");

        call("showCategory", PlaceCategory.OFFICES);
        shot(p + "04-offices-nearest-first");

        nav.openPlan(node(22));
        shot(p + "05-directions-registrar");

        Node keep = nav.start();
        set("start", null);
        nav.openPlan(node(22));
        call("showPlanScreen", false, false);
        shot(p + "05b-plan-route-before-pick");
        call("pickStart", node(1), false);
        shot(p + "05c-plan-route-picked-gate");
        call("pickStart", node(42), false);
        shot(p + "05d-plan-route-picked-room-4f");
        set("start", keep);
        call("showCategory", PlaceCategory.CLASSROOMS);
        shot(p + "05e-classrooms-nearest-first");

        nav.openPlan(node(42));
        shot(p + "06-directions-en420");

        tapStep(3);
        shot(p + "07-step-tapped");

        set("graphOn", true);
        call("showDirections");
        shot(p + "08-graph-view");
        set("graphOn", false);

        call("findNearest", PlaceCategory.RESTROOMS, false);
        shot(p + "09-nearest-restroom");

        nav.handleCode("UENAV:42");
        call("findNearest", PlaceCategory.EXITS, true);
        shot(p + "10-emergency-from-en420");

        settings.setAvoidStairs(true);
        nav.handleCode("UENAV:1");
        nav.openPlan(node(42));
        shot(p + "11-avoid-stairs-route");
        settings.setAvoidStairs(false);

        call("showCodeEntry");
        shot(p + "12-type-qr-code");

        call("showPickOnMap", true);
        shot(p + "13-tap-where-you-are");

        nav.openExplore();
        set("exploreQuery", "cr");
        nav.openExplore();
        shot(p + "14-search-cr");
        set("exploreQuery", "en420");
        nav.openExplore();
        shot(p + "15-search-en420");
        set("exploreQuery", "");

        memory.toggleFavourite(22);
        nav.openExplore();
        tall(p + "16-home-full-page");
        nav.openPlan(node(22));
        tall(p + "18-directions-full-sheet");
        nav.openExplore();

        set("destination", null);
        set("routeChecked", false);
        nav.openMap();
        shot(p + "17-map-tab-here-dot");
    }

    /** The Settings tab, shown in place of the visitor screens for one screenshot. */
    private static void settingsShot(String name) throws Exception {
        SettingsScreen s = new SettingsScreen(settings, new SettingsScreen.Host() {
            @Override
            public void themeChanged() { }

            @Override
            public void openAdmin() { }

            @Override
            public String mapInfo() {
                return "Built into the app · version " + data.version() + " · " + data.places().size()
                        + " places";
            }
        });
        BorderPane root = new BorderPane(s.view());
        root.getStyleClass().add("nv-surface");
        Scene sc = new Scene(root, 390, 820);
        sc.getStylesheets().add(Ui.css());
        if (settings.darkMode()) {
            sc.getStylesheets().add(Ui.darkCss());
        }
        current = sc;
        save(sc, name);
        current = null;
    }

    private static Node node(int id) {
        return data.graph().node(id).orElseThrow();
    }

    private static void call(String name, Object... args) throws Exception {
        for (Method m : Navigator.class.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                m.invoke(nav, args);
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void set(String field, Object value) throws Exception {
        var f = Navigator.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(nav, value);
    }

    /** Taps the n-th direction step (its click handler ignores the event). */
    private static void tapStep(int n) {
        Scene s = scene();
        s.getRoot().applyCss();
        s.getRoot().layout();
        List<javafx.scene.Node> steps = List.copyOf(s.getRoot().lookupAll(".nv-step"));
        if (n < steps.size()) {
            steps.get(n).getOnMouseClicked().handle(null);
        }
    }

    private static Scene current;
    private static int height = 820;

    /** The whole scrolling page at once, in a very tall window. */
    private static void tall(String name) throws Exception {
        height = 1500;
        shot(name);
        height = 820;
    }

    private static Scene scene() {
        Parent view = nav.view();
        if (current != null && current.getRoot() instanceof BorderPane bp && bp.getCenter() == view) {
            return current;
        }
        if (view.getParent() instanceof BorderPane old) {
            old.setCenter(null);
        }
        HBox bar = new HBox(item("search", "Explore", nav.tab() == Navigator.Tab.EXPLORE),
                item("map", "Map", nav.tab() == Navigator.Tab.MAP), item("settings", "Settings", false));
        bar.getStyleClass().add("nv-navbar");
        BorderPane root = new BorderPane(view);
        root.setBottom(bar);
        root.getStyleClass().add("nv-canvas");
        current = new Scene(root, 390, height);
        current.getStylesheets().add(Ui.css());
        if (settings.darkMode()) {
            current.getStylesheets().add(Ui.darkCss());
        }
        return current;
    }

    private static VBox item(String icon, String text, boolean on) {
        VBox v = new VBox(4, Icons.icon(icon, 22), new Label(text));
        v.setAlignment(Pos.CENTER);
        v.getStyleClass().add("nv-navitem");
        if (on) {
            v.getStyleClass().add("nv-navitem-on");
        }
        HBox.setHgrow(v, Priority.ALWAYS);
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    private static void shot(String name) throws Exception {
        current = null;
        save(scene(), name);
    }

    private static void save(Scene s, String name) throws Exception {
        s.getRoot().applyCss();
        s.getRoot().layout();
        WritableImage img = s.snapshot(null);
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getPixelReader().getPixels(0, y, w, 1, PixelFormat.getIntArgbInstance(), row, 0, w);
            bi.setRGB(0, y, w, 1, row, 0, w);
        }
        ImageIO.write(bi, "png", new File(out, name + ".png"));
    }
}

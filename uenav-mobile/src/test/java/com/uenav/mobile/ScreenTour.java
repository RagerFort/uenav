package com.uenav.mobile;

import com.uenav.map.MapView;
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
        settings.setAnimateRoutes(false); // the route animation gets its own frames at the end
        PlacesMemory memory = new PlacesMemory(new MemoryStore());
        UsageLog log = new UsageLog() {
            @Override
            public void route(int fromNodeId, int toNodeId, RoutePreference pref) { }

            @Override
            public void feedback(int fromNodeId, int toNodeId, RoutePreference pref, String feedbackType) { }
        };
        nav = new Navigator(data, log, settings, memory);
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
        // like the phone: the map panel slides open from almost 0 high, and only then reaches its full size
        call("showPlanScreen", false, true);
        current = null;
        Scene grow = scene();
        for (double hgt : new double[] {0, 12, 40, 150, 380}) {
            for (javafx.scene.Node n : grow.getRoot().lookupAll(".nv-map-bg")) {
                if (n instanceof javafx.scene.layout.StackPane sp && sp.getChildren().get(0) instanceof MapView) {
                    sp.setPrefHeight(hgt);
                    sp.setOpacity(1); // the fade-in animation does not run here
                }
            }
            grow.getRoot().applyCss();
            grow.getRoot().layout();
        }
        save(grow, p + "05a-plan-route-after-slide-open");
        call("pickStart", node(1), false);
        shot(p + "05c-plan-route-picked-gate");
        call("pickStart", node(42), false);
        shot(p + "05d-plan-route-picked-room-4f");
        set("start", keep);
        call("showCategory", PlaceCategory.CLASSROOMS);
        shot(p + "05e-classrooms-nearest-first");

        nav.openPlan(node(42));
        shot(p + "06-directions-en420");
        var levelField = Navigator.class.getDeclaredField("directionsLevel");
        levelField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<Integer> showLevel = (java.util.function.Consumer<Integer>) levelField.get(nav);
        showLevel.accept(2); // the 4F tab
        save(scene(), p + "06b-en420-4f-tab");
        nav.openPlan(named("TYK 803"));
        shot(p + "06c-directions-tyk803");
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<Integer> showTykLevel = (java.util.function.Consumer<Integer>) levelField.get(nav);
        showTykLevel.accept(named("TYK 803").floorId()); // the 8F tab
        save(scene(), p + "06d-tyk803-8f-tab");
        // over the 4F bridge into the Engineering building
        nav.openPlan(named("EN 310"));
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<Integer> showBridgeLevel = (java.util.function.Consumer<Integer>) levelField.get(nav);
        showBridgeLevel.accept(named("Bridge to Engineering (TYK 4F)").floorId());
        save(scene(), p + "06e-bridge-tyk-4f");
        showBridgeLevel.accept(named("EN 310").floorId());
        save(scene(), p + "06f-bridge-en-3f");
        if (!dark) {
            // back history: Explore -> Classrooms -> directions, then back twice
            var key = Navigator.class.getDeclaredField("screenKey");
            key.setAccessible(true);
            nav.openExplore();
            call("showCategory", PlaceCategory.CLASSROOMS);
            nav.openPlan(node(42));
            String a = (String) key.get(nav);
            boolean b1 = nav.goBack();
            String b = (String) key.get(nav);
            boolean b2 = nav.goBack();
            String c = (String) key.get(nav);
            boolean b3 = nav.goBack();
            System.out.println("BACK CHECK: " + a + " -> " + b + " (" + b1 + ") -> " + c + " (" + b2 + ") -> at home "
                    + !b3);
            SplashScreen sp = new SplashScreen();
            sp.skipIntro();
            Scene splash = new Scene(sp.view(), 390, 820);
            splash.getStylesheets().add(Ui.css());
            save(splash, "00-loading-screen");
        }

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

        // the route animation, frame by frame: gate -> TYK elevator -> bridge -> EN stairs -> EN 420
        nav.handleCode("UENAV:1");
        nav.openPlan(node(42));
        Scene anim = scene();
        anim.getRoot().applyCss();
        anim.getRoot().layout();
        double[] times = dark ? new double[] {2.0, 6.0} : new double[] {0.2, 1.0, 2.0, 3.2, 4.4, 5.6, 6.8, 8.0,
            9.2, 10.4, 11.6, 12.8, 14.0, 15.2, 16.4, 17.6, 18.8, 20.0};
        for (double sec : times) {
            boolean playing = nav.routeAnimationAt(sec);
            save(anim, p + String.format(java.util.Locale.ROOT, "19-anim-%04.1fs", sec));
            if (!playing) {
                break;
            }
        }
        double total = 0;
        while (nav.routeAnimationAt(total)) {
            total += 0.5;
        }
        System.out.println("ROUTE ANIMATION: about " + total + " s");
        tapStep(0); // stops it
        if (!dark) {
            adminScreens(p);
        }
    }

    /** The Settings tab, shown in place of the visitor screens for one screenshot. */
    /**
     * The staff screens (Settings > Staff sign in): sign-in, dashboard, add place and route reports, against a stand-in
     * server that answers with the built-in map and made-up usage numbers.
     */
    @SuppressWarnings("unchecked")
    private static void adminScreens(String p) throws Exception {
        String snap = new String(ScreenTour.class.getResourceAsStream(CampusStore.BUNDLED).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) com.uenav.core.MiniJson.parse(snap);
        java.util.Map<String, String> routes = new java.util.HashMap<>();
        routes.put("POST /api/admin/login",
                "{\"token\":\"t\",\"fullName\":\"Campus administrator\",\"role\":\"ADMIN\",\"expiresInSeconds\":28800}");
        routes.put("GET /api/admin/usage/summary", "{\"searches\":128,\"helpful\":31,\"openReports\":2,\"totalReports\":5}");
        routes.put("GET /api/admin/usage/most-searched", com.uenav.core.MiniJson.write(List.of(
                java.util.Map.of("nodeId", 22.0, "name", "Registrar's Office", "count", 41.0),
                java.util.Map.of("nodeId", 20.0, "name", "Cashier's Office", "count", 33.0),
                java.util.Map.of("nodeId", 42.0, "name", "EN 420", "count", 19.0))));
        String event = "{\"id\":%d,\"createdAt\":\"2026-11-22T05:%02d:00Z\",\"eventType\":\"%s\",\"fromName\":\"Samson Road "
                + "Gate\",\"toName\":\"%s\",\"routePref\":\"ANY\",\"feedbackType\":%s,\"status\":%s,\"note\":null}";
        String recent = "[" + String.format(event, 1, 50, "ROUTE", "Registrar's Office", "null", "null") + ","
                + String.format(event, 2, 40, "FEEDBACK", "EN 420", "\"WRONG_ROUTE\"", "\"NEW\"") + ","
                + String.format(event, 3, 30, "FEEDBACK", "TYK 803", "\"WRONG_ROUTE\"", "\"NEW\"") + "]";
        routes.put("GET /api/admin/usage/recent", recent);
        routes.put("GET /api/admin/reports", "[" + String.format(event, 2, 40, "FEEDBACK", "EN 420", "\"WRONG_ROUTE\"",
                "\"NEW\"") + "," + String.format(event, 3, 30, "FEEDBACK", "TYK 803", "\"WRONG_ROUTE\"", "\"NEW\"")
                + "," + String.format(event, 4, 10, "FEEDBACK", "Cashier's Office", "\"WRONG_ROUTE\"", "\"REVIEWED\"")
                + "]");
        routes.put("GET /api/admin/nodes", com.uenav.core.MiniJson.write(m.get("nodes")));
        routes.put("GET /api/admin/buildings", com.uenav.core.MiniJson.write(m.get("buildings")));
        routes.put("GET /api/admin/floors", com.uenav.core.MiniJson.write(m.get("floors")));
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            ex.getRequestBody().readAllBytes();
            String body = routes.getOrDefault(ex.getRequestMethod() + " " + ex.getRequestURI().getPath(), "{}");
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        String keep = settings.serverUrl();
        settings.setServerUrl("http://127.0.0.1:" + server.getAddress().getPort());
        try {
            CampusStore store = new CampusStore(new MemoryStore());
            store.load();
            Parent[] shown = new Parent[1];
            AdminScreens admin = new AdminScreens(settings, store, new AdminScreens.Host() {
                @Override
                public void show(Parent screen) {
                    shown[0] = screen;
                }

                @Override
                public void continueAsVisitor() { }

                @Override
                public String syncStatus() {
                    return "Nothing waiting to be sent";
                }

                @Override
                public void syncNow(java.util.function.Consumer<String> done) {
                    done.accept("Nothing to send");
                }

                @Override
                public void mapUpdated(CampusData d) { }
            });
            Parent login = admin.open();
            screenShot(login, p + "20-admin-sign-in");
            for (javafx.scene.Node n : login.lookupAll(".text-field")) {
                if (n instanceof javafx.scene.control.PasswordField pf) {
                    pf.setText("pw"); // the stand-in server takes any password
                } else if (n instanceof javafx.scene.control.TextField tf && "admin".equals(tf.getPromptText())) {
                    tf.setText("admin");
                }
            }
            for (javafx.scene.Node n : login.lookupAll(".button")) {
                if (n instanceof javafx.scene.control.Button b && "Sign in".equals(b.getText())) {
                    b.fire();
                }
            }
            pump(2500); // the sign-in and the dashboard's numbers arrive in the background
            screenShot(shown[0], p + "21-admin-dashboard");
            Method form = AdminScreens.class.getDeclaredMethod("placeForm", MobileAdminApi.Place.class);
            form.setAccessible(true);
            Parent add = (Parent) form.invoke(admin, (Object) null);
            pump(1500);
            screenShot(add, p + "22-admin-add-place");
            Method reports = AdminScreens.class.getDeclaredMethod("reportsScreen");
            reports.setAccessible(true);
            Parent rep = (Parent) reports.invoke(admin);
            pump(1500);
            screenShot(rep, p + "23-admin-route-reports");
        } finally {
            settings.setServerUrl(keep);
            server.stop(0);
        }
    }

    /**
     * Lets JavaFX handle events for a while (the tour itself runs on JavaFX's thread). The tour runs inside
     * Platform.startup, before JavaFX's animation clock starts, so a sleeping thread ends the wait, not a transition.
     */
    private static void pump(long ms) {
        Object key = new Object();
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException ignored) {
                // end the wait early
            }
            Platform.runLater(() -> Platform.exitNestedEventLoop(key, null));
        });
        t.setDaemon(true);
        t.start();
        Platform.enterNestedEventLoop(key);
    }

    private static void screenShot(Parent screen, String name) throws Exception {
        if (screen.getParent() instanceof javafx.scene.layout.Pane old) {
            old.getChildren().remove(screen);
        } else if (screen.getScene() != null) {
            screen.getScene().setRoot(new javafx.scene.layout.Pane());
        }
        BorderPane root = new BorderPane(screen);
        root.getStyleClass().add("nv-canvas");
        Scene sc = new Scene(root, 390, 820);
        sc.getStylesheets().add(Ui.css());
        current = sc;
        save(sc, name);
        current = null;
    }

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

    private static Node named(String name) {
        return data.graph().nodes().stream().filter(n -> n.name().equals(name)).findFirst().orElseThrow();
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

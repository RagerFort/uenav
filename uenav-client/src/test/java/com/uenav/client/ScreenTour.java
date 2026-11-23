package com.uenav.client;

import com.sun.net.httpserver.HttpServer;
import com.uenav.core.MiniJson;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;

/**
 * Developer tool, not a unit test: opens the admin pages off-screen against a small stand-in server (map data from
 * the phone's campus-snapshot.json, made-up usage numbers) and saves light and dark PNG screenshots.
 * Run (after mvn test-compile):  java -cp "target/test-classes;target/classes;<test classpath>"
 *   com.uenav.client.ScreenTour <output folder> <path to campus-snapshot.json>
 * Settings are kept in a temporary folder, so the real ~/.uenav/admin.properties is not touched.
 */
public final class ScreenTour {

    private static File out;
    private static Scene scene;

    private ScreenTour() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("uenav-tour").toString());
        out = new File(args[0]);
        out.mkdirs();
        String snapshot = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
        AdminApi api = server(snapshot);
        AdminApi.LoginInfo info = api.login("admin", "pw");

        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        // the tour's pages have no window; a form dialog is the only one, and closing it must not end JavaFX
        Platform.setImplicitExit(false);

        for (boolean dark : new boolean[] {false, true}) {
            String p = dark ? "dark-" : "light-";
            fx(() -> Theme.setDark(dark));
            fx(() -> show(new LoginView("http://localhost:8080", null, (a, i) -> { }, s -> { })));
            pause(300);
            shot(p + "01-login");

            AdminShell[] shell = new AdminShell[1];
            fx(() -> {
                shell[0] = new AdminShell(api, info, m -> { });
                show(shell[0]);
            });
            pause(1500);
            shot(p + "02-dashboard");

            fx(() -> click("QR codes"));
            pause(1200);
            fx(() -> {
                for (javafx.scene.Node n : scene.getRoot().lookupAll(".list-view")) {
                    ListView<?> lv = (ListView<?>) n;
                    lv.getSelectionModel().select(1);
                }
            });
            pause(400);
            shot(p + "04-qr-codes");

            fx(() -> click("Map editor"));
            pause(1800);
            shot(p + "06-map-editor-campus");
            fx(() -> editor().pickForTour("Dr. Lucio C. Tan · 2nd floor", "Guidance Office (LCT)", false));
            pause(500);
            shot(p + "06b-map-editor-place");
            fx(() -> editor().pickForTour("Engineering · 3rd floor", "The Dawn Office", true));
            pause(500);
            shot(p + "06c-map-editor-walkway");
            if (!dark) {
                editorChecks(api);
            }

            fx(() -> click("Dashboard"));
            pause(1200);
            fx(() -> scene = new Scene(swapRoot(), 1366, 689)); // a maximized window on a 1366x768 laptop
            fx(() -> Theme.apply(scene));
            shot(p + "02b-dashboard-1366x689");

            fx(() -> click("Places"));
            pause(1200);
            shot(p + "05-places");

            for (String page : new String[] {"Walkways", "Buildings", "Floors", "Route reports"}) {
                fx(() -> click(page));
                pause(1200);
                shot(p + "07-" + page.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
            }
            if (!dark) {
                for (String page : new String[] {"Places", "Walkways", "Buildings", "Floors"}) {
                    formShot(page, p + "08-form-" + page.toLowerCase(java.util.Locale.ROOT));
                }
                listPageChecks();
            }
        }
        Platform.exit();
        System.out.println("Saved screens to " + out.getAbsolutePath());
        System.exit(0);
    }

    /** Every change the editor sent to the stand-in server: "PUT /api/admin/nodes/12 {...}". */
    private static final List<String> REQUESTS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Uses the Map editor like a person would, with mouse events on its map, and prints what it sent: dragging a
     * place, connecting two places, and adding a place.
     */
    private static void editorChecks(AdminApi api) throws Exception {
        com.uenav.core.CampusData d = api.snapshot();
        java.util.function.Function<String, com.uenav.core.Node> node = name -> d.graph().nodes().stream()
                .filter(n -> n.name().equals(name)).findFirst().orElseThrow();
        fx(() -> editor().pickForTour("Dr. Lucio C. Tan · 2nd floor", "Guidance Office (LCT)", false));
        pause(400);
        com.uenav.core.Node guidance = node.apply("Guidance Office (LCT)");
        REQUESTS.clear();
        fx(() -> drag(guidance, 0, 40));
        pause(1500);
        System.out.println("DRAG Guidance Office 40 px down:");
        REQUESTS.forEach(r -> System.out.println("  " + r));
        undo("UNDO the drag with Ctrl+Z:", true);

        REQUESTS.clear();
        fx(() -> clickTool("Connect"));
        com.uenav.core.Node c1 = node.apply("LCT Classroom 1");
        com.uenav.core.Node c5 = node.apply("LCT Classroom 5");
        fx(() -> click(c1));
        fx(() -> click(c5));
        pause(1500);
        System.out.println("CONNECT Classroom 1 to Classroom 5:");
        REQUESTS.forEach(r -> System.out.println("  " + r));
        undo("UNDO the walkway with Ctrl+Z:", true);

        REQUESTS.clear();
        fx(() -> clickTool("Add place"));
        fx(() -> clickAt(mapView().getWidth() * 0.5, mapView().getHeight() * 0.3));
        pause(300);
        shot("light-06d-map-editor-new-place");
        fx(() -> {
            for (javafx.scene.Node n : editor().lookupAll(".text-field")) {
                if (n instanceof javafx.scene.control.TextField t && t.getText().isEmpty()) {
                    t.setText("Test Room");
                    break;
                }
            }
            for (javafx.scene.Node n : editor().lookupAll(".primary-button")) {
                if (n instanceof javafx.scene.control.Button b && "Add place".equals(b.getText())) {
                    b.fire();
                }
            }
        });
        pause(1500);
        System.out.println("ADD a place by clicking the map:");
        REQUESTS.forEach(r -> System.out.println("  " + r));
        shot("light-06e-map-editor-undo");
        undo("UNDO the new place with the Undo button:", false);
        undo("UNDO with nothing left (Ctrl+Z):", true);
        fx(() -> clickTool("Select"));
    }

    /**
     * Undoes the last change, with Ctrl+Z (on the Map editor when it is open, else on the window) or the Undo button
     * in the top bar, and prints what it sent and what the editor or the top bar said.
     */
    private static void undo(String title, boolean key) throws Exception {
        REQUESTS.clear();
        fx(() -> {
            if (key) {
                java.util.Set<javafx.scene.Node> pages = scene.getRoot().lookupAll(".canvas");
                javafx.scene.Node target = pages.stream().filter(n -> n instanceof MapEditorView).findFirst()
                        .orElseGet(() -> pages.stream().filter(n -> n instanceof AdminShell).findFirst().orElseThrow());
                target.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                        javafx.scene.input.KeyCode.Z, false, true, false, false));
            } else {
                scene.getRoot().lookupAll(".small-button").stream()
                        .filter(n -> n instanceof javafx.scene.control.Button b && "Undo".equals(b.getText()))
                        .findFirst().ifPresent(n -> ((javafx.scene.control.Button) n).fire());
            }
        });
        pause(1500);
        System.out.println(title);
        REQUESTS.forEach(r -> System.out.println("  " + r));
        fx(() -> scene.getRoot().lookupAll(".label").stream()
                .filter(n -> n instanceof javafx.scene.control.Label l && l.getText() != null
                        && (l.getText().startsWith("Undid") || l.getText().startsWith("Nothing")
                        || l.getText().startsWith("Could not")))
                .forEach(n -> System.out.println("  status: " + ((javafx.scene.control.Label) n).getText())));
    }

    /**
     * Undo on a list page: adds a building with the form and undoes it with Ctrl+Z, then deletes a building and
     * brings it back with the Undo button; prints what was sent each time.
     */
    private static void listPageChecks() throws Exception {
        fx(() -> click("Buildings"));
        pause(1200);
        REQUESTS.clear();
        fx(() -> scene.getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button b && "+ Add building".equals(b.getText()))
                .findFirst().ifPresent(n -> Platform.runLater(((javafx.scene.control.Button) n)::fire)));
        pause(1000);
        fx(() -> {
            for (javafx.stage.Window w : new java.util.ArrayList<>(javafx.stage.Window.getWindows())) {
                if (w.isShowing() && w.getScene() != null && w.getScene() != scene) {
                    w.getScene().getRoot().lookupAll(".text-field").stream()
                            .filter(n -> n instanceof javafx.scene.control.TextField t
                                    && "e.g. College of Engineering Building".equals(t.getPromptText()))
                            .findFirst().ifPresent(n -> ((javafx.scene.control.TextField) n).setText("Test Hall"));
                }
            }
        });
        windowButton("Save");
        pause(1500);
        System.out.println("ADD building Test Hall on the Buildings page:");
        REQUESTS.forEach(r -> System.out.println("  " + r));
        undo("UNDO the new building with Ctrl+Z:", true);

        REQUESTS.clear();
        fx(() -> {
            scene.getRoot().applyCss(); // the table makes its rows when it is laid out (the tour has no window)
            scene.getRoot().layout();
            java.util.List<javafx.scene.Node> dels = scene.getRoot().lookupAll(".danger").stream()
                    .filter(n -> n instanceof javafx.scene.control.Button b && "Delete".equals(b.getText())
                            && b.getScene() != null && b.localToScene(b.getBoundsInLocal()).getMinY() > 0)
                    .toList();
            System.out.println("  delete buttons on screen: " + dels.size());
            dels.stream().findFirst().ifPresent(n -> Platform.runLater(((javafx.scene.control.Button) n)::fire));
        });
        pause(800);
        fx(() -> {
            for (javafx.stage.Window w : new java.util.ArrayList<>(javafx.stage.Window.getWindows())) {
                if (w.isShowing() && w.getScene() != null && w.getScene() != scene
                        && w.getScene().getRoot() instanceof javafx.scene.control.DialogPane d) {
                    System.out.println("  asks: " + d.getContentText());
                }
            }
        });
        windowButton("OK");
        pause(1500);
        System.out.println("DELETE the first building on the Buildings page:");
        REQUESTS.forEach(r -> System.out.println("  " + r));
        shot("light-09-buildings-undo");
        undo("UNDO the delete with the Undo button:", false);
        undo("UNDO with nothing left (Ctrl+Z):", true);
    }

    /** Presses a button in the open dialog (a form or a question). */
    private static void windowButton(String text) throws Exception {
        fx(() -> {
            for (javafx.stage.Window w : new java.util.ArrayList<>(javafx.stage.Window.getWindows())) {
                if (w.isShowing() && w.getScene() != null && w.getScene() != scene) {
                    w.getScene().getRoot().lookupAll(".button").stream()
                            .filter(b -> b instanceof javafx.scene.control.Button bt && text.equals(bt.getText()))
                            .findFirst().ifPresent(b -> ((javafx.scene.control.Button) b).fire());
                }
            }
        });
    }

    private static com.uenav.map.MapView mapView() {
        return (com.uenav.map.MapView) editor().lookupAll(".nv-map-bg").stream()
                .filter(n -> n instanceof com.uenav.map.MapView).findFirst().orElseThrow();
    }

    private static void clickTool(String text) {
        for (javafx.scene.Node n : editor().lookupAll(".tool-button")) {
            if (n instanceof javafx.scene.control.ToggleButton t && text.equals(t.getText())) {
                t.setSelected(true);
            }
        }
    }

    private static void drag(com.uenav.core.Node n, double dx, double dy) {
        double[] p = mapView().toView(n.x(), n.y());
        mouse(javafx.scene.input.MouseEvent.MOUSE_PRESSED, p[0], p[1]);
        for (int i = 1; i <= 5; i++) {
            mouse(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, p[0] + dx * i / 5, p[1] + dy * i / 5);
        }
        mouse(javafx.scene.input.MouseEvent.MOUSE_RELEASED, p[0] + dx, p[1] + dy);
    }

    private static void click(com.uenav.core.Node n) {
        double[] p = mapView().toView(n.x(), n.y());
        clickAt(p[0], p[1]);
    }

    private static void clickAt(double x, double y) {
        mouse(javafx.scene.input.MouseEvent.MOUSE_PRESSED, x, y);
        mouse(javafx.scene.input.MouseEvent.MOUSE_RELEASED, x, y);
        mouse(javafx.scene.input.MouseEvent.MOUSE_CLICKED, x, y);
    }

    private static void mouse(javafx.event.EventType<javafx.scene.input.MouseEvent> type, double x, double y) {
        boolean down = type != javafx.scene.input.MouseEvent.MOUSE_RELEASED
                && type != javafx.scene.input.MouseEvent.MOUSE_CLICKED;
        javafx.geometry.Point2D s = mapView().localToScene(x, y); // the event takes window (scene) coordinates
        javafx.event.Event.fireEvent(mapView(), new javafx.scene.input.MouseEvent(type, s.getX(), s.getY(), s.getX(),
                s.getY(),
                javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false, down, false, false, false,
                false, true, null));
    }

    private static MapEditorView editor() {
        return (MapEditorView) scene.getRoot().lookupAll(".canvas").stream()
                .filter(n -> n instanceof MapEditorView).findFirst().orElseThrow();
    }

    /** Presses a sidebar button by its text. */
    private static void click(String text) {
        for (javafx.scene.Node n : scene.getRoot().lookupAll(".nav-item")) {
            if (n instanceof javafx.scene.control.Button b && (text.equals(b.getText()) || (b.getGraphic() != null
                    && b.getGraphic().lookupAll(".label").stream().anyMatch(
                            l -> l instanceof javafx.scene.control.Label lb && text.equals(lb.getText()))))) {
                b.fire(); // "Route reports" shows its name in its graphic, beside the badge
                return;
            }
        }
        throw new IllegalStateException("No menu item " + text);
    }

    /** Takes the current page out of its window so it can be shown in a window of another size. */
    private static Parent swapRoot() {
        Parent root = scene.getRoot();
        scene.setRoot(new javafx.scene.layout.Pane());
        return root;
    }

    private static void show(Parent root) {
        scene = new Scene(root, 1180, 760);
        Theme.apply(scene);
    }

    private static void fx(Runnable r) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } finally {
                done.countDown();
            }
        });
        done.await();
    }

    private static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    /**
     * Opens a page's "+ Add ..." form, which is a dialog window of its own, saves a picture of that window and closes
     * it. The button is pressed without waiting, because the dialog waits for an answer.
     */
    private static void formShot(String page, String name) throws Exception {
        fx(() -> click(page));
        pause(1000);
        javafx.scene.control.Button[] add = new javafx.scene.control.Button[1];
        fx(() -> {
            for (javafx.scene.Node n : scene.getRoot().lookupAll(".button")) {
                if (n instanceof javafx.scene.control.Button b && b.getText() != null && b.getText().startsWith("+ Add")
                        && b.isVisible()) {
                    add[0] = b;
                }
            }
        });
        if (add[0] == null) {
            System.out.println("No add button on " + page);
            return;
        }
        System.out.println("form: " + page + " (" + add[0].getText() + ")");
        Platform.runLater(add[0]::fire);
        pause(1000);
        fx(() -> {
            for (javafx.stage.Window w : new java.util.ArrayList<>(javafx.stage.Window.getWindows())) {
                if (w.isShowing() && w.getScene() != null && w.getScene() != scene) {
                    saveScene(w.getScene(), name);
                    System.out.println("  saved " + name);
                    // close it the way a person would, so the dialog stops waiting for an answer
                    w.getScene().getRoot().lookupAll(".button").stream()
                            .filter(b -> b instanceof javafx.scene.control.Button bt && "Cancel".equals(bt.getText()))
                            .findFirst().ifPresentOrElse(b -> ((javafx.scene.control.Button) b).fire(), w::hide);
                }
            }
        });
        pause(500);
    }

    private static void saveScene(Scene s, String name) {
        try {
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
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void shot(String name) throws Exception {
        fx(() -> {
            try {
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                WritableImage img = scene.snapshot(null);
                int w = (int) img.getWidth();
                int h = (int) img.getHeight();
                BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                int[] row = new int[w];
                for (int y = 0; y < h; y++) {
                    img.getPixelReader().getPixels(0, y, w, 1, PixelFormat.getIntArgbInstance(), row, 0, w);
                    bi.setRGB(0, y, w, 1, row, 0, w);
                }
                ImageIO.write(bi, "png", new File(out, name + ".png"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static AdminApi server(String snapshot) throws Exception {
        Map<String, Object> snap = (Map<String, Object>) MiniJson.parse(snapshot);
        Map<String, String> routes = new HashMap<>();
        routes.put("POST /api/admin/login",
                "{\"token\":\"t\",\"fullName\":\"Campus administrator\",\"role\":\"ADMIN\",\"expiresInSeconds\":28800}");
        routes.put("GET /api/snapshot", snapshot);
        routes.put("GET /api/admin/buildings", MiniJson.write(snap.get("buildings")));
        routes.put("GET /api/admin/floors", MiniJson.write(snap.get("floors")));
        routes.put("GET /api/admin/nodes", MiniJson.write(snap.get("nodes")));
        routes.put("GET /api/admin/edges", MiniJson.write(snap.get("edges")));
        routes.put("GET /api/admin/usage/summary",
                "{\"searches\":128,\"helpful\":31,\"openReports\":2,\"totalReports\":5}");
        routes.put("GET /api/admin/usage/most-searched", MiniJson.write(List.of(
                Map.of("nodeId", 22.0, "name", "Registrar's Office", "count", 41.0),
                Map.of("nodeId", 20.0, "name", "Cashier's Office", "count", 33.0),
                Map.of("nodeId", 42.0, "name", "EN 420 (4F)", "count", 19.0))));
        String event = "{\"id\":%d,\"createdAt\":\"2026-11-09T05:%02d:00Z\",\"eventType\":\"%s\",\"fromName\":\"University "
                + "Hills Gate\",\"toName\":\"%s\",\"routePref\":\"ANY\",\"feedbackType\":%s,\"status\":%s,\"note\":null}";
        String recent = "[" + String.format(event, 1, 50, "ROUTE", "Registrar's Office", "null", "null") + ","
                + String.format(event, 2, 40, "FEEDBACK", "EN 420 (4F)", "\"WRONG_ROUTE\"", "\"NEW\"") + "]";
        routes.put("GET /api/admin/usage/recent", recent);
        routes.put("GET /api/admin/reports", recent);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String sent = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String body = routes.getOrDefault(ex.getRequestMethod() + " " + ex.getRequestURI().getPath(),
                    "{\"error\":\"no such route\"}");
            if (!"GET".equals(ex.getRequestMethod()) && body.startsWith("{\"error")) {
                REQUESTS.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " " + sent);
                body = "{\"id\":9999}"; // the editor's changes: accepted, and recorded for editorChecks
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(body.startsWith("{\"error") ? 404 : 200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        return new AdminApi("http://127.0.0.1:" + server.getAddress().getPort());
    }
}

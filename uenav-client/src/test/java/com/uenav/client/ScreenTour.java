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

            fx(() -> click("Map health"));
            pause(1200);
            shot(p + "03-map-health");

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

            fx(() -> click("Dashboard"));
            pause(1200);
            fx(() -> scene = new Scene(swapRoot(), 1366, 689)); // a maximized window on a 1366x768 laptop
            fx(() -> Theme.apply(scene));
            shot(p + "02b-dashboard-1366x689");

            fx(() -> click("Places"));
            pause(1200);
            shot(p + "05-places");
        }
        Platform.exit();
        System.out.println("Saved screens to " + out.getAbsolutePath());
        System.exit(0);
    }

    /** Presses a sidebar button by its text. */
    private static void click(String text) {
        for (javafx.scene.Node n : scene.getRoot().lookupAll(".nav-item")) {
            if (n instanceof javafx.scene.control.Button b && text.equals(b.getText())) {
                b.fire();
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
            ex.getRequestBody().readAllBytes();
            String body = routes.getOrDefault(ex.getRequestMethod() + " " + ex.getRequestURI().getPath(),
                    "{\"error\":\"no such route\"}");
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

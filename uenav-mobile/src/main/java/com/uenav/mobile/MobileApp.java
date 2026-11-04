package com.uenav.mobile;

import com.uenav.core.CampusData;
import java.io.File;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * The phone app (Figma: Explore, Map, Admin tabs). Routes are found on the phone from the campus data inside the
 * app, so it works with no network (D5, D9). When a server address is set it also downloads a newer map and sends
 * the saved searches and feedback (usage log).
 */
public class MobileApp extends Application {

    private enum Tab { EXPLORE, MAP, ADMIN }

    private UsageQueue queue;
    private Settings settings;
    private SyncService sync;
    private CampusStore store;
    private Navigator navigator;
    private AdminScreens admin;
    private final StackPane content = new StackPane();
    private final HBox navBar = new HBox();
    private Tab tab = Tab.EXPLORE;

    @Override
    public void start(Stage stage) {
        File dir = AppStorage.directory();
        queue = new UsageQueue(new FileTextStore(new File(dir, "usage-queue.json")));
        settings = new Settings(new FileTextStore(new File(dir, "settings.json")), BuiltInServer.url());
        sync = new SyncService(queue, new HttpSyncClient());
        store = new CampusStore(new FileTextStore(new File(dir, "campus-snapshot.json")));

        CampusData data;
        try {
            data = store.load();
        } catch (Exception e) {
            Label err = Ui.wrap("Could not load the campus map.\n" + e.getMessage(), "nv-error");
            VBox box = new VBox(err);
            box.setPadding(new Insets(16));
            show(stage, box);
            return;
        }

        navigator = new Navigator(data, UsageLog.of(queue, this::syncAsync), false);
        navigator.setOnTabChanged(t -> {
            if (tab != Tab.ADMIN) {
                tab = t == Navigator.Tab.MAP ? Tab.MAP : Tab.EXPLORE;
                refreshNavBar();
            }
        });
        admin = new AdminScreens(settings, store, new AdminScreens.Host() {
            @Override
            public void show(Parent screen) {
                if (tab == Tab.ADMIN) {
                    content.getChildren().setAll(screen);
                }
            }

            @Override
            public void continueAsVisitor() {
                select(Tab.EXPLORE);
            }

            @Override
            public String syncStatus() {
                return statusText();
            }

            @Override
            public void syncNow(Consumer<String> done) {
                String url = settings.serverUrl();
                Thread t = new Thread(() -> {
                    SyncService.Outcome o = sync.syncNow(url);
                    Platform.runLater(() -> done.accept(o.message()));
                });
                t.setDaemon(true);
                t.start();
            }

            @Override
            public void mapUpdated(CampusData fresh) {
                navigator.setData(fresh);
            }
        });

        navBar.getStyleClass().add("nv-navbar");
        BorderPane root = new BorderPane(content);
        root.setBottom(navBar);
        root.getStyleClass().add("nv-canvas");
        select(Tab.EXPLORE);
        show(stage, root);

        checkForMapUpdate();
        syncAsync();
        startAutoConnect();
    }

    /**
     * Keeps trying in the background while the app is open: sends waiting entries every minute (so they go out as
     * soon as the phone has internet again, on any Wi-Fi or mobile data) and looks for a newer map every 30 minutes.
     */
    private void startAutoConnect() {
        Thread t = new Thread(() -> {
            long minutes = 0;
            while (true) {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    return;
                }
                minutes++;
                String url = settings.serverUrl();
                if (url.isEmpty()) {
                    continue;
                }
                if (queue.size() > 0) {
                    sync.syncNow(url);
                }
                if (minutes % 30 == 0) {
                    CampusStore.Check c = store.checkForUpdate(url);
                    if (c.updated() != null) {
                        Platform.runLater(() -> navigator.setData(c.updated()));
                    }
                }
            }
        }, "uenav-auto-connect");
        t.setDaemon(true);
        t.start();
    }

    private void show(Stage stage, Parent root) {
        // On a phone the window is the whole screen; on a PC it is a phone-sized window.
        boolean desktop = !isAndroidOrIos();
        double w = desktop ? 390 : Screen.getPrimary().getVisualBounds().getWidth();
        double h = desktop ? 820 : Screen.getPrimary().getVisualBounds().getHeight();
        Scene scene = new Scene(root, w, h);
        String css = Ui.css();
        if (css != null) {
            scene.getStylesheets().add(css);
        }
        stage.setScene(scene);
        stage.setTitle("UENav");
        stage.show();
    }

    private static boolean isAndroidOrIos() {
        try {
            return !com.gluonhq.attach.util.Platform.isDesktop();
        } catch (Throwable notAvailable) {
            return false;
        }
    }

    private void select(Tab t) {
        tab = t;
        switch (t) {
            case EXPLORE -> {
                navigator.openExplore();
                content.getChildren().setAll(navigator.view());
            }
            case MAP -> {
                navigator.openMap();
                content.getChildren().setAll(navigator.view());
            }
            case ADMIN -> content.getChildren().setAll(admin.open());
        }
        refreshNavBar();
    }

    private void refreshNavBar() {
        navBar.getChildren().setAll(
                navItem("search", "Explore", Tab.EXPLORE),
                navItem("map", "Map", Tab.MAP),
                navItem("shield", "Admin", Tab.ADMIN));
    }

    private VBox navItem(String icon, String text, Tab t) {
        VBox item = new VBox(4, Icons.icon(icon, 22), new Label(text));
        item.setAlignment(Pos.CENTER);
        item.getStyleClass().add("nv-navitem");
        if (tab == t) {
            item.getStyleClass().add("nv-navitem-on");
        }
        HBox.setHgrow(item, Priority.ALWAYS);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setOnMouseClicked(e -> select(t));
        return item;
    }

    private String statusText() {
        int n = queue.size();
        StringBuilder sb = new StringBuilder();
        sb.append(n == 0 ? "No usage entries waiting" : n + (n == 1 ? " entry" : " entries") + " waiting to be sent");
        if (settings.serverUrl().isEmpty()) {
            sb.append(" · no server address set");
        }
        if (queue.saveFailed()) {
            sb.append("\nCould not save on this phone.");
        }
        return sb.toString();
    }

    /** Looks for a newer map on the server in the background. Does nothing if no server address is set. */
    private void checkForMapUpdate() {
        String url = settings.serverUrl();
        if (url.isEmpty()) {
            return;
        }
        Thread t = new Thread(() -> {
            CampusStore.Check c = store.checkForUpdate(url);
            if (c.updated() != null) {
                Platform.runLater(() -> navigator.setData(c.updated()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /** Tries to send the saved entries in the background. Does nothing if no server address is set. */
    private void syncAsync() {
        String url = settings.serverUrl();
        if (url.isEmpty()) {
            return;
        }
        Thread t = new Thread(() -> sync.syncNow(url));
        t.setDaemon(true);
        t.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

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
import javafx.scene.layout.Region;
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

    private enum Tab { EXPLORE, MAP, SETTINGS }

    private UsageQueue queue;
    private Settings settings;
    private SyncService sync;
    private CampusStore store;
    private Navigator navigator;
    private AdminScreens admin;
    private final StackPane content = new StackPane();
    private final HBox navBar = new HBox();
    private Tab tab = Tab.EXPLORE;
    /** True while the admin screens are open (they live under the Settings tab). */
    private boolean inAdmin;
    private SettingsScreen settingsScreen;
    private Scene scene;
    private long lastBackAtHome;

    @Override
    public void start(Stage stage) {
        File dir = AppStorage.directory();
        queue = new UsageQueue(new FileTextStore(new File(dir, "usage-queue.json")));
        settings = new Settings(new FileTextStore(new File(dir, "settings.json")), BuiltInServer.url());
        sync = new SyncService(queue, new HttpSyncClient());
        store = new CampusStore(new FileTextStore(new File(dir, "campus-snapshot.json")));

        // The loading screen shows at once; the map and screens are built behind it, then it fades away.
        StackPane shell = new StackPane();
        SplashScreen splash = new SplashScreen();
        shell.getChildren().addAll(Ui.focusRest(), splash.view());
        show(stage, shell);
        long shownAt = System.nanoTime();
        javafx.animation.PauseTransition firstFrame = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(80));
        firstFrame.setOnFinished(e -> {
            CampusData data;
            try {
                data = store.load();
            } catch (Exception ex) {
                splash.showError("Could not load the campus map.\n" + ex.getMessage());
                return;
            }
            shell.getChildren().add(1, buildApp(dir, data)); // above the focus rest, under the loading screen
            double waited = (System.nanoTime() - shownAt) / 1e6;
            javafx.animation.PauseTransition hold = new javafx.animation.PauseTransition(
                    javafx.util.Duration.millis(Math.max(0, SplashScreen.MIN_MILLIS - waited)));
            hold.setOnFinished(x -> splash.fadeOut(() -> shell.getChildren().remove(splash.view())));
            hold.play();
            checkForMapUpdate();
            syncAsync();
            startAutoConnect();
        });
        firstFrame.play();
    }

    /** Builds the tabs and screens (Explore, Map, Settings) once the campus map is loaded. */
    private Parent buildApp(File dir, CampusData data) {
        PlacesMemory memory = new PlacesMemory(new FileTextStore(new File(dir, "places.json")));
        navigator = new Navigator(data, UsageLog.of(queue, this::syncAsync), settings, memory);
        navigator.setDark(dark());
        navigator.setOnTabChanged(t -> {
            if (tab != Tab.SETTINGS) {
                tab = t == Navigator.Tab.MAP ? Tab.MAP : Tab.EXPLORE;
                refreshNavBar();
            }
        });
        admin = new AdminScreens(settings, store, new AdminScreens.Host() {
            @Override
            public void show(Parent screen) {
                if (tab == Tab.SETTINGS && inAdmin) {
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

            @Override
            public boolean dark() {
                return MobileApp.this.dark();
            }

            @Override
            public void toggleDark() {
                settings.setDarkMode(!MobileApp.this.dark());
                applyTheme();
                navigator.setDark(MobileApp.this.dark());
            }
        });
        settingsScreen = new SettingsScreen(settings, new SettingsScreen.Host() {
            @Override
            public void themeChanged() {
                applyTheme();
                navigator.setDark(dark());
                select(Tab.SETTINGS);
            }

            @Override
            public void openAdmin() {
                inAdmin = true;
                content.getChildren().setAll(admin.open());
            }

            @Override
            public String mapInfo() {
                CampusData d = store.current();
                String where = store.isDownloaded() ? "Downloaded from the server" : "Built into the app";
                return where + " · version " + (d == null ? "?" : d.version()) + " · "
                        + (d == null ? 0 : d.places().size()) + " places";
            }
        });

        navBar.getStyleClass().add("nv-navbar");
        BorderPane root = new BorderPane(content);
        root.setBottom(navBar);
        root.getStyleClass().add("nv-surface");
        Insets insets = phoneInsets();
        if (insets.getTop() > 0) {
            // The phone draws its clock and battery icons in white on top of the app: give them a coloured strip
            // (UENav red in Light mode), or they vanish on the white header.
            Region statusBar = new Region();
            statusBar.getStyleClass().add("nv-statusbar");
            statusBar.setMinHeight(insets.getTop());
            statusBar.setPrefHeight(insets.getTop());
            root.setTop(statusBar);
        }
        root.setPadding(new Insets(0, 0, insets.getBottom(), 0));
        select(Tab.EXPLORE);
        return root;
    }

    /**
     * The phone's back button (Android sends it as ESCAPE): goes back one screen, or from the staff screens back to
     * Settings. Returns false when there is nowhere to go back to.
     */
    private boolean goBack() {
        if (tab == Tab.SETTINGS && inAdmin) {
            select(Tab.SETTINGS);
            return true;
        }
        if (tab == Tab.SETTINGS) {
            select(Tab.EXPLORE);
            return true;
        }
        return navigator != null && navigator.goBack();
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
        javafx.geometry.Rectangle2D visual = Screen.getPrimary().getVisualBounds();
        double w = desktop ? 390 : visual.getWidth();
        double h = desktop ? 820 : visual.getHeight();
        scene = new Scene(root, w, h);
        applyTheme();
        TapGuard.install(scene);
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            boolean back = e.getCode() == javafx.scene.input.KeyCode.ESCAPE
                    || e.getCode() == javafx.scene.input.KeyCode.BACK_SPACE && !(e.getTarget()
                    instanceof javafx.scene.control.TextInputControl);
            if (!back) {
                return;
            }
            e.consume();
            if (goBack()) {
                return;
            }
            // nothing left to go back to: a second press within 2 seconds closes the app (usual Android behaviour)
            long now = System.currentTimeMillis();
            if (now - lastBackAtHome < 2000) {
                Platform.exit();
                System.exit(0);
            }
            lastBackAtHome = now;
            if (navigator != null) {
                navigator.showMessage("Press back again to close UENav");
            }
        });
        stage.setScene(scene);
        stage.setTitle("UENav");
        stage.show();
    }

    /**
     * The app is drawn under the phone's status bar and gesture bar: keep the screens clear of them (unless the
     * system already left that space out of the window). Nothing on a PC.
     */
    private static Insets phoneInsets() {
        if (!isAndroidOrIos()) {
            return Insets.EMPTY;
        }
        javafx.geometry.Rectangle2D all = Screen.getPrimary().getBounds();
        javafx.geometry.Rectangle2D visual = Screen.getPrimary().getVisualBounds();
        double top = visual.getMinY() > 0 ? 0 : STATUS_BAR;
        double bottom = all.getMaxY() > visual.getMaxY() ? 0 : GESTURE_BAR;
        return new Insets(top, 0, bottom, 0);
    }

    /** Room left for the phone's status bar (clock, battery) and its gesture bar, in screen units (dp). */
    static final double STATUS_BAR = 34;
    static final double GESTURE_BAR = 14;

    /** Whether the app looks dark now: the chosen theme, or the phone's own setting for "Same as phone". */
    private boolean dark() {
        return settings != null && SystemTheme.dark(settings.theme());
    }

    /** Light stylesheet always, the dark one on top for dark mode, and the light-weight phone one on phones. */
    private void applyTheme() {
        if (scene == null) {
            return;
        }
        String css = Ui.css();
        String darkSheet = Ui.darkCss();
        String phone = Ui.phoneCss();
        scene.getStylesheets().removeIf(s -> s.equals(css) || s.equals(darkSheet) || s.equals(phone));
        if (css != null) {
            scene.getStylesheets().add(css);
        }
        if (darkSheet != null && dark()) {
            scene.getStylesheets().add(darkSheet);
        }
        if (phone != null && isAndroidOrIos()) {
            scene.getStylesheets().add(phone);
        }
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
            case SETTINGS -> {
                inAdmin = false;
                content.getChildren().setAll(settingsScreen.view());
            }
        }
        refreshNavBar();
    }

    private void refreshNavBar() {
        navBar.getChildren().setAll(
                navItem("search", "Explore", Tab.EXPLORE),
                navItem("map", "Map", Tab.MAP),
                navItem("settings", "Settings", Tab.SETTINGS));
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

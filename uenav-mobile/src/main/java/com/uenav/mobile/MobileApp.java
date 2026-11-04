package com.uenav.mobile;

import com.uenav.core.CampusData;
import com.uenav.core.Node;
import com.uenav.core.PathFinder;
import com.uenav.core.Route;
import com.uenav.core.RoutePreference;
import com.uenav.core.SnapshotReader;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * Offline app: loads the campus map from the copy bundled inside the app, finds routes on the device with no
 * network, and saves each search and Helpful / Wrong route tap on the phone. Saved entries are sent to the
 * server whenever it can be reached.
 */
public class MobileApp extends Application {

    private static final String BUNDLED_SNAPSHOT = "/campus-snapshot.json";

    /** The route the visitor is looking at, so a feedback tap can refer to it. */
    private record RouteContext(int fromId, int toId, RoutePreference pref) { }

    private UsageQueue queue;
    private Settings settings;
    private SyncService sync;
    private RouteContext current;

    private final Label syncStatus = new Label();
    private final Label ask = new Label();
    private final Button helpful = new Button("Helpful");
    private final Button wrong = new Button("Wrong route");
    private final VBox feedbackBox = new VBox(8);

    @Override
    public void start(Stage stage) {
        CampusData data;
        try {
            data = loadBundled();
        } catch (Exception e) {
            Label err = new Label("Could not load the campus map.\n" + e.getMessage());
            err.setWrapText(true);
            VBox box = new VBox(err);
            box.setPadding(new Insets(16));
            stage.setScene(new Scene(box, 360, 640));
            stage.show();
            return;
        }
        final CampusData campus = data;

        File dir = AppStorage.directory();
        queue = new UsageQueue(new FileTextStore(new File(dir, "usage-queue.json")));
        settings = new Settings(new FileTextStore(new File(dir, "settings.json")));
        sync = new SyncService(queue, new HttpSyncClient());

        ComboBox<Node> from = placeBox(campus);
        ComboBox<Node> to = placeBox(campus);
        from.setPromptText("From");
        to.setPromptText("To");

        ComboBox<RoutePreference> pref = new ComboBox<>();
        pref.getItems().addAll(RoutePreference.values());
        pref.setValue(RoutePreference.ANY);
        pref.setMaxWidth(Double.MAX_VALUE);

        Button go = new Button("Find route");
        go.setMaxWidth(Double.MAX_VALUE);

        Label result = new Label("Pick a start and a destination.");
        result.setWrapText(true);

        // feedback row, shown only after a route was found
        ask.setText("Was this route helpful?");
        helpful.setMaxWidth(Double.MAX_VALUE);
        wrong.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(helpful, Priority.ALWAYS);
        HBox.setHgrow(wrong, Priority.ALWAYS);
        feedbackBox.getChildren().addAll(ask, new HBox(8, helpful, wrong));
        hideFeedback();
        helpful.setOnAction(ev -> sendFeedback("HELPFUL"));
        wrong.setOnAction(ev -> sendFeedback("WRONG_ROUTE"));

        go.setOnAction(ev -> {
            Node a = from.getValue();
            Node b = to.getValue();
            if (a == null || b == null) {
                result.setText("Please choose both places.");
                return;
            }
            if (a.id() == b.id()) {
                result.setText("Choose a different place.");
                return;
            }
            RoutePreference p = pref.getValue();
            Optional<Route> r = PathFinder.findRoute(campus.graph(), a.id(), b.id(), p);
            result.setText(r.map(RouteFormatter::describe).orElse("No route found."));
            queue.addRoute(a.id(), b.id(), p);
            if (r.isPresent()) {
                current = new RouteContext(a.id(), b.id(), p);
                ask.setText("Was this route helpful?");
                helpful.setDisable(false);
                wrong.setDisable(false);
                feedbackBox.setVisible(true);
                feedbackBox.setManaged(true);
            } else {
                hideFeedback();
            }
            refreshStatus(null);
            syncAsync();
        });
        from.valueProperty().addListener((o, x, y) -> hideFeedback());
        to.valueProperty().addListener((o, x, y) -> hideFeedback());
        pref.valueProperty().addListener((o, x, y) -> hideFeedback());

        Label version = new Label("Map data: " + campus.version());
        version.setStyle("-fx-font-size: 11;");

        // sending settings
        Label syncTitle = new Label("Send usage to the server");
        syncTitle.setStyle("-fx-font-weight: bold;");
        TextField server = new TextField(settings.serverUrl());
        server.setPromptText("http://192.168.1.10:8080");
        Button save = new Button("Save & send");
        save.setMaxWidth(Double.MAX_VALUE);
        syncStatus.setWrapText(true);
        syncStatus.setStyle("-fx-font-size: 11;");
        save.setOnAction(ev -> {
            try {
                settings.setServerUrl(server.getText());
                server.setText(settings.serverUrl());
                syncAsync();
            } catch (IllegalArgumentException e) {
                syncStatus.setText(e.getMessage());
            }
        });

        VBox root = new VBox(12, new Label("UENav"), from, to, pref, go, result, feedbackBox, version,
                syncTitle, server, save, syncStatus);
        root.setPadding(new Insets(16));
        ScrollPane page = new ScrollPane(root);
        page.setFitToWidth(true);

        stage.setScene(new Scene(page, 360, 640));
        stage.setTitle("UENav");
        stage.show();

        refreshStatus(null);
        syncAsync();
    }

    private void hideFeedback() {
        current = null;
        feedbackBox.setVisible(false);
        feedbackBox.setManaged(false);
    }

    private void sendFeedback(String type) {
        RouteContext c = current;
        if (c == null) {
            return;
        }
        queue.addFeedback(c.fromId(), c.toId(), c.pref(), type);
        current = null;
        ask.setText("Thank you for your feedback.");
        helpful.setDisable(true);
        wrong.setDisable(true);
        refreshStatus(null);
        syncAsync();
    }

    /** Tries to send the saved entries in the background. Does nothing if no server address is set. */
    private void syncAsync() {
        String url = settings.serverUrl();
        if (url.isEmpty()) {
            refreshStatus(null);
            return;
        }
        Thread t = new Thread(() -> {
            SyncService.Outcome outcome = sync.syncNow(url);
            Platform.runLater(() -> refreshStatus(outcome));
        });
        t.setDaemon(true);
        t.start();
    }

    private void refreshStatus(SyncService.Outcome outcome) {
        StringBuilder sb = new StringBuilder("Waiting to be sent: ").append(queue.size());
        if (settings.serverUrl().isEmpty()) {
            sb.append("\nNo server address set, so nothing is sent yet.");
        } else if (outcome != null) {
            sb.append("\n").append(outcome.message());
        }
        if (queue.saveFailed()) {
            sb.append("\nCould not save on this phone.");
        }
        syncStatus.setText(sb.toString());
    }

    private static CampusData loadBundled() throws Exception {
        try (InputStream in = MobileApp.class.getResourceAsStream(BUNDLED_SNAPSHOT)) {
            if (in == null) {
                throw new IllegalStateException("campus-snapshot.json is not inside the app");
            }
            return SnapshotReader.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static ComboBox<Node> placeBox(CampusData campus) {
        ComboBox<Node> box = new ComboBox<>();
        box.getItems().addAll(campus.places());
        box.setMaxWidth(Double.MAX_VALUE);
        box.setConverter(new StringConverter<>() {
            @Override public String toString(Node n) { return n == null ? "" : n.name(); }
            @Override public Node fromString(String s) { return null; }
        });
        return box;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package com.uenav.mobile;

import com.uenav.core.CampusData;
import com.uenav.core.Node;
import com.uenav.core.PathFinder;
import com.uenav.core.Route;
import com.uenav.core.RoutePreference;
import com.uenav.core.SnapshotReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * Offline app: loads the campus map from the copy bundled inside the app (campus-snapshot.json),
 * then finds routes on the device with no network and no database.
 */
public class MobileApp extends Application {

    private static final String BUNDLED_SNAPSHOT = "/campus-snapshot.json";

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
        ScrollPane scroll = new ScrollPane(result);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(300);

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
            Optional<Route> r = PathFinder.findRoute(campus.graph(), a.id(), b.id(), pref.getValue());
            result.setText(r.map(RouteFormatter::describe).orElse("No route found."));
        });

        Label version = new Label("Map data: " + campus.version());
        version.setStyle("-fx-font-size: 11;");

        VBox root = new VBox(12, new Label("UENav"), from, to, pref, go, scroll, version);
        root.setPadding(new Insets(16));
        stage.setScene(new Scene(root, 360, 640));
        stage.setTitle("UENav");
        stage.show();
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

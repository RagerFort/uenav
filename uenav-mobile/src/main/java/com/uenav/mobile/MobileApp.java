package com.uenav.mobile;

import com.uenav.core.Graph;
import com.uenav.core.Node;
import com.uenav.core.PathFinder;
import com.uenav.core.Route;
import com.uenav.core.RoutePreference;
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
 * Offline spike: pick start, destination and stairs/elevator, then press Find route.
 * No network and no database. Routing runs on the device through uenav-core.
 */
public class MobileApp extends Application {

    @Override
    public void start(Stage stage) {
        Graph graph = SampleData.graph();

        ComboBox<Node> from = placeBox(graph);
        ComboBox<Node> to = placeBox(graph);
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
            Optional<Route> r = PathFinder.findRoute(graph, a.id(), b.id(), pref.getValue());
            result.setText(r.map(RouteFormatter::describe).orElse("No route found."));
        });

        VBox root = new VBox(12, new Label("UENav (offline test)"), from, to, pref, go, scroll);
        root.setPadding(new Insets(16));
        stage.setScene(new Scene(root, 360, 640));
        stage.setTitle("UENav");
        stage.show();
    }

    private static ComboBox<Node> placeBox(Graph graph) {
        ComboBox<Node> box = new ComboBox<>();
        box.getItems().addAll(SampleData.places(graph));
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

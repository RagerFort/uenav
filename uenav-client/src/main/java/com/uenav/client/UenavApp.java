package com.uenav.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class UenavApp extends Application {

    @Override
    public void start(Stage stage) {
        String base = System.getProperty("uenav.api", "http://localhost:8080");
        ApiClient api = new ApiClient(base);

        Label server = new Label("Server: checking...");
        Label db = new Label("Database: checking...");
        VBox root = new VBox(10, new Label("UENav"), server, db);
        root.setStyle("-fx-padding: 24;");

        stage.setScene(new Scene(root, 320, 160));
        stage.setTitle("UENav");
        stage.show();

        Thread t = new Thread(() -> {
            String s = api.status("/api/health");
            String d = api.status("/api/health/db");
            Platform.runLater(() -> {
                server.setText("Server: " + s);
                db.setText("Database: " + d);
            });
        });
        t.setDaemon(true);
        t.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package com.uenav.client;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** UENav admin app for the PC. Server address: -Duenav.api=http://host:8080 (default http://localhost:8080). */
public class UenavApp extends Application {

    private Stage stage;
    private String server;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        server = System.getProperty("uenav.api", "http://localhost:8080");
        stage.setTitle("UENav Admin");
        stage.setMinWidth(980);
        stage.setMinHeight(640);
        showLogin(null);
        stage.show();
    }

    private void showLogin(String message) {
        AdminApi api = new AdminApi(server);
        setRoot(new LoginView(api, server, message, info -> showShell(api, info)));
    }

    private void showShell(AdminApi api, AdminApi.LoginInfo info) {
        setRoot(new AdminShell(api, info, this::showLogin));
    }

    private void setRoot(Parent root) {
        if (stage.getScene() == null) {
            Scene scene = new Scene(root, 1180, 760);
            String css = Theme.css();
            if (css != null) {
                scene.getStylesheets().add(css);
            }
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(root);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

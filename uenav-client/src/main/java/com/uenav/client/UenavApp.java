package com.uenav.client;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * UENav admin app for the PC (admins only; visitors use the phone app). The server address is typed on the login
 * screen and remembered in ~/.uenav/admin.properties; -Duenav.api=http://host:8080 overrides it.
 */
public class UenavApp extends Application {

    private Stage stage;
    private String server;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        server = System.getProperty("uenav.api", AdminSettings.serverUrl());
        stage.setTitle("UENav Admin");
        // Fit the screen: on a 1366x768 laptop a fixed 1180x760 window plus its title bar is taller than the space
        // above the taskbar, and Windows pushed the title bar off the top. Small screens start maximized.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setMinWidth(Math.min(980, screen.getWidth()));
        stage.setMinHeight(Math.min(640, screen.getHeight() - TITLE_BAR));
        showLogin(null);
        if (screen.getWidth() < 1280 || screen.getHeight() < WIDE.getHeight() + TITLE_BAR + 40) {
            stage.setMaximized(true);
        } else {
            stage.centerOnScreen();
        }
        stage.show();
    }

    /** The window's size on a big enough screen. */
    static final Rectangle2D WIDE = new Rectangle2D(0, 0, 1180, 760);
    /** Room for the window's own title bar, which Windows adds on top of the scene. */
    static final double TITLE_BAR = 40;

    /** Window size for a screen: the usual size, made smaller when the screen (minus the title bar) is smaller. */
    static double[] windowSize(Rectangle2D screen) {
        return new double[] {
            Math.min(WIDE.getWidth(), screen.getWidth()),
            Math.min(WIDE.getHeight(), screen.getHeight() - TITLE_BAR)};
    }

    private void showLogin(String message) {
        setRoot(new LoginView(server, message, this::showShell, used -> {
            server = used;
            AdminSettings.saveServerUrl(used);
        }));
    }

    private void showShell(AdminApi api, AdminApi.LoginInfo info) {
        setRoot(new AdminShell(api, info, this::showLogin));
    }

    private void setRoot(Parent root) {
        if (stage.getScene() == null) {
            double[] size = windowSize(Screen.getPrimary().getVisualBounds());
            Scene scene = new Scene(root, size[0], size[1]);
            Theme.apply(scene);
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(root);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

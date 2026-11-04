package com.uenav.client;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Admin sign-in screen (Figma: Login). The PC app is for admins only, so there is no visitor option here. */
public class LoginView extends StackPane {

    public LoginView(AdminApi api, String serverUrl, String message, Consumer<AdminApi.LoginInfo> onSuccess) {
        getStyleClass().add("login-bg");

        StackPane logo = new StackPane(Icons.icon("pin", 26, "nv-icon-brand"));
        logo.setStyle("-fx-background-color: white; -fx-background-radius: 14;");
        logo.setMinSize(52, 52);
        logo.setMaxSize(52, 52);
        Label brand = new Label("UENav");
        brand.setStyle("-fx-text-fill: white; -fx-font-size: 30px; -fx-font-weight: bold;");
        Label sub = new Label("Campus Navigator \u00B7 UE Caloocan");
        sub.setStyle("-fx-text-fill: white; -fx-opacity: 0.85;");
        VBox header = new VBox(4, logo, brand, sub);
        VBox.setMargin(brand, new Insets(10, 0, 0, 0));
        header.getStyleClass().add("login-header");
        header.setPadding(new Insets(28, 28, 24, 28));

        Label title = new Label("Admin sign in");
        title.getStyleClass().add("card-title");
        title.setStyle("-fx-font-size: 20px;");
        Label hint = new Label("Manage places, routes, and the usage log.");
        hint.getStyleClass().add("muted");

        Label userLabel = new Label("USERNAME");
        userLabel.getStyleClass().add("field-label");
        TextField username = new TextField();
        username.setPromptText("admin");

        Label passLabel = new Label("PASSWORD");
        passLabel.getStyleClass().add("field-label");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        Button signIn = new Button("Sign in");
        signIn.getStyleClass().add("primary-button");
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setDefaultButton(true);

        Label error = new Label(message == null ? "" : message);
        error.getStyleClass().add("error-label");
        error.setWrapText(true);

        Label server = new Label("Server: " + serverUrl);
        server.getStyleClass().add("muted");
        server.setStyle("-fx-font-size: 11px;");

        Runnable submit = () -> {
            String u = username.getText().trim();
            String p = password.getText();
            if (u.isEmpty() || p.isEmpty()) {
                error.setText("Enter your username and password.");
                return;
            }
            signIn.setDisable(true);
            error.setText("");
            Async.run(() -> api.login(u, p),
                    info -> onSuccess.accept(info),
                    e -> {
                        signIn.setDisable(false);
                        password.clear();
                        error.setText(e.getMessage());
                    });
        };
        signIn.setOnAction(ev -> submit.run());
        password.setOnAction(ev -> submit.run());
        username.setOnAction(ev -> password.requestFocus());

        VBox body = new VBox(8, title, hint, userLabel, username, passLabel, password, signIn, error, server);
        body.setPadding(new Insets(24, 28, 24, 28));
        VBox.setMargin(userLabel, new Insets(10, 0, 0, 0));
        VBox.setMargin(passLabel, new Insets(6, 0, 0, 0));
        VBox.setMargin(signIn, new Insets(10, 0, 0, 0));

        VBox card = new VBox(header, body);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(400);
        card.setMaxHeight(VBox.USE_PREF_SIZE);

        setAlignment(Pos.CENTER);
        getChildren().add(card);
    }
}

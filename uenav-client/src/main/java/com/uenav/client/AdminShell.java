package com.uenav.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** The admin window after login: side menu, top bar, and the page in the middle (Figma: desktop dashboard). */
public class AdminShell extends BorderPane {

    private final AdminApi api;
    private final Consumer<String> onSignOut;
    private final StackPane content = new StackPane();
    private final Label crumb = new Label();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private boolean signedOut;

    public AdminShell(AdminApi api, AdminApi.LoginInfo info, Consumer<String> onSignOut) {
        this.api = api;
        this.onSignOut = onSignOut;
        getStyleClass().add("canvas");
        setLeft(buildSidebar());
        setTop(buildTopBar(info));
        setCenter(content);
        show("dashboard");
    }

    private VBox buildSidebar() {
        Label logoText = new Label("UE");
        logoText.getStyleClass().add("logo-text");
        StackPane logo = new StackPane(logoText);
        logo.getStyleClass().add("logo-box");
        logo.setMinSize(34, 34);
        logo.setMaxSize(34, 34);
        Label name = new Label("UENav");
        name.getStyleClass().add("card-title");
        Label campus = new Label("UE Caloocan");
        campus.getStyleClass().add("muted");
        campus.setStyle("-fx-font-size: 11px;");
        HBox brand = new HBox(10, logo, new VBox(0, name, campus));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(0, 0, 14, 4));

        VBox nav = new VBox(4, brand,
                section("ADMIN"),
                navItem("dashboard", "Dashboard", true),
                section("ADMINISTRATION"),
                navItem("places", "Places", true),
                navItem("reports", "Route reports", true),
                navItem("walkways", "Walkways  (soon)", false),
                navItem("buildings", "Buildings and floors  (soon)", false));

        Label cardTitle = new Label("CAMPUS NAVIGATOR");
        cardTitle.getStyleClass().add("field-label");
        Label cardName = new Label("UE Caloocan");
        cardName.getStyleClass().add("card-title");
        Label cardText = new Label("Outdoor places and indoor rooms");
        cardText.getStyleClass().add("muted");
        cardText.setWrapText(true);
        VBox info = new VBox(2, cardTitle, cardName, cardText);
        info.setPadding(new Insets(10));
        info.setStyle("-fx-background-color: #F3F4F6; -fx-background-radius: 10;");

        Button logout = new Button("Log out");
        logout.getStyleClass().add("nav-item");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(e -> {
            Thread t = new Thread(api::logout);
            t.setDaemon(true);
            t.start();
            signOut(null);
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox side = new VBox(8, nav, spacer, info, logout);
        side.getStyleClass().add("sidebar");
        side.setPadding(new Insets(18, 14, 14, 14));
        side.setPrefWidth(240);
        side.setMinWidth(240);
        return side;
    }

    private static Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("nav-section");
        l.setPadding(new Insets(12, 0, 2, 12));
        return l;
    }

    private Button navItem(String key, String label, boolean enabled) {
        Button b = new Button(label);
        b.getStyleClass().add("nav-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setDisable(!enabled);
        if (enabled) {
            b.setOnAction(e -> show(key));
        }
        navButtons.put(key, b);
        return b;
    }

    private HBox buildTopBar(AdminApi.LoginInfo info) {
        crumb.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label initials = new Label(initialsOf(info.fullName()));
        initials.getStyleClass().add("avatar-text");
        StackPane avatar = new StackPane(initials);
        avatar.getStyleClass().add("avatar");
        avatar.setMinSize(32, 32);
        avatar.setMaxSize(32, 32);
        Label who = new Label(info.fullName());
        who.getStyleClass().add("card-title");
        Label where = new Label("UE Caloocan");
        where.getStyleClass().add("muted");
        where.setStyle("-fx-font-size: 11px;");

        HBox bar = new HBox(12, crumb, spacer, new VBox(0, who, where), avatar);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("topbar");
        bar.setPadding(new Insets(12, 24, 12, 24));
        return bar;
    }

    static String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "AD";
        }
        String[] parts = fullName.trim().split("\\s+");
        String s = parts.length == 1
                ? parts[0].substring(0, Math.min(2, parts[0].length()))
                : parts[0].substring(0, 1) + parts[1].substring(0, 1);
        return s.toUpperCase();
    }

    private void show(String key) {
        Node page;
        String title;
        switch (key) {
            case "places" -> {
                page = new PlacesView(api, e -> expired());
                title = "Places";
            }
            case "reports" -> {
                page = placeholder("Route reports",
                        "Wrong-route reports will be listed here once the usage log is connected.");
                title = "Route reports";
            }
            default -> {
                page = new DashboardView(api, e -> {
                    if (e.status() == 401) {
                        expired();
                    }
                });
                title = "Dashboard";
            }
        }
        crumb.setText("Admin  /  " + title);
        content.getChildren().setAll(page);
        for (Map.Entry<String, Button> en : navButtons.entrySet()) {
            en.getValue().getStyleClass().remove("nav-item-active");
        }
        Button active = navButtons.get(key);
        if (active != null) {
            active.getStyleClass().add("nav-item-active");
        }
    }

    private static VBox placeholder(String title, String text) {
        Label eyebrow = new Label("ADMIN");
        eyebrow.getStyleClass().add("eyebrow");
        Label t = new Label(title);
        t.getStyleClass().add("page-title");
        Label m = new Label(text);
        m.getStyleClass().add("muted");
        m.setWrapText(true);
        VBox box = new VBox(6, new VBox(2, eyebrow, t), m);
        box.setPadding(new Insets(24));
        box.getStyleClass().add("canvas");
        return box;
    }

    private void expired() {
        signOut("Your session ended. Please sign in again.");
    }

    private void signOut(String message) {
        if (signedOut) {
            return;
        }
        signedOut = true;
        onSignOut.accept(message);
    }
}

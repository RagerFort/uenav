package com.uenav.client;

import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Admin dashboard (Figma: Dashboard). Only the Places number is live; usage numbers need the usage log. */
public class DashboardView extends ScrollPane {

    private final Label placesNumber = new Label("\u2026");

    public DashboardView(AdminApi api, Consumer<ApiException> onProblem) {
        setFitToWidth(true);
        getStyleClass().add("canvas");

        Label eyebrow = new Label("ADMIN");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Dashboard");
        title.getStyleClass().add("page-title");
        Label sub = new Label("An overview of campus navigation, searches, and places.");
        sub.getStyleClass().add("muted");

        HBox stats = new HBox(16,
                stat(placesNumber, "Places"),
                stat(new Label("\u2014"), "Searches"),
                stat(new Label("\u2014"), "Wrong-route reports"));

        HBox panels = new HBox(16,
                panel("Most searched", "The campus destinations people look for most."),
                panel("Recent activity", "Latest navigation and feedback."));

        VBox page = new VBox(16, new VBox(2, eyebrow, title, sub), stats, panels);
        page.setPadding(new Insets(24));
        setContent(page);

        Async.run(api::nodes,
                (List<AdminModels.Place> places) -> placesNumber.setText(
                        String.valueOf(places.stream().filter(AdminModels.Place::isSearchable).count())),
                e -> {
                    placesNumber.setText("?");
                    onProblem.accept(e);
                });
    }

    private static VBox stat(Label number, String caption) {
        number.getStyleClass().add("stat-number");
        Label cap = new Label(caption);
        cap.getStyleClass().add("muted");
        VBox card = new VBox(2, number, cap);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 20, 16, 20));
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static VBox panel(String heading, String subtitle) {
        Label h = new Label(heading);
        h.getStyleClass().add("card-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add("muted");
        Label soon = new Label("Nothing to show yet. This fills in once the usage log is connected.");
        soon.getStyleClass().add("muted");
        soon.setWrapText(true);
        VBox card = new VBox(6, h, s, soon);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 20, 20, 20));
        card.setMinHeight(150);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }
}

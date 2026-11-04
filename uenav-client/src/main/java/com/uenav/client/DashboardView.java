package com.uenav.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Admin dashboard (Figma: Dashboard): numbers, most searched places, recent activity. */
public class DashboardView extends ScrollPane {

    private record Data(long places, AdminModels.UsageSummary summary, List<AdminModels.TopPlace> top,
                        List<AdminModels.UsageEvent> recent) { }

    private final AdminApi api;
    private final Consumer<ApiException> onProblem;
    private final Label placesNumber = new Label("\u2026");
    private final Label searchesNumber = new Label("\u2026");
    private final Label reportsNumber = new Label("\u2026");
    private final VBox topRows = new VBox(10);
    private final VBox recentRows = new VBox(8);
    private final Label status = new Label();

    public DashboardView(AdminApi api, Consumer<ApiException> onProblem, Runnable openReports) {
        this.api = api;
        this.onProblem = onProblem;
        setFitToWidth(true);
        getStyleClass().add("canvas");

        Label eyebrow = new Label("ADMIN");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Dashboard");
        title.getStyleClass().add("page-title");
        Label sub = new Label("An overview of campus navigation, searches, and places.");
        sub.getStyleClass().add("muted");

        Button export = new Button("Export usage log (CSV)");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> {
            export.setDisable(true);
            Async.run(api::exportUsageCsv,
                    csv -> {
                        export.setDisable(false);
                        saveCsv(csv);
                    },
                    err -> {
                        export.setDisable(false);
                        problem(err);
                    });
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, new VBox(2, eyebrow, title, sub), spacer, export);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox stats = new HBox(16,
                stat(placesNumber, "Places"),
                stat(searchesNumber, "Searches"),
                stat(reportsNumber, "Wrong-route reports"));

        Button viewReports = new Button("View route reports");
        viewReports.getStyleClass().add("secondary-button");
        viewReports.setMaxWidth(Double.MAX_VALUE);
        viewReports.setOnAction(e -> openReports.run());

        HBox panels = new HBox(16,
                panel("Most searched", "The campus destinations people look for most.", topRows, null),
                panel("Recent activity", "Latest navigation and feedback.", recentRows, viewReports));

        status.getStyleClass().add("error-label");
        status.setWrapText(true);
        VBox page = new VBox(16, header, stats, panels, status);
        page.setPadding(new Insets(24));
        setContent(page);

        topRows.getChildren().add(muted("Loading\u2026"));
        recentRows.getChildren().add(muted("Loading\u2026"));

        Async.run(() -> {
                    long places = api.nodes().stream().filter(AdminModels.Place::isSearchable).count();
                    return new Data(places, api.usageSummary(), api.mostSearched(5), api.recentActivity(8));
                },
                this::show, this::problem);
    }

    private void show(Data d) {
        placesNumber.setText(String.valueOf(d.places()));
        searchesNumber.setText(String.valueOf(d.summary().searches()));
        reportsNumber.setText(String.valueOf(d.summary().openReports()));
        status.setText("");

        topRows.getChildren().clear();
        if (d.top().isEmpty()) {
            topRows.getChildren().add(muted("No searches yet."));
        }
        long max = d.top().stream().mapToLong(AdminModels.TopPlace::count).max().orElse(1);
        for (AdminModels.TopPlace t : d.top()) {
            Label name = new Label(t.name());
            Label count = new Label(String.valueOf(t.count()));
            count.getStyleClass().add("card-title");
            count.setStyle("-fx-text-fill: #C8102E;");
            Region grow = new Region();
            HBox.setHgrow(grow, Priority.ALWAYS);
            ProgressBar bar = new ProgressBar((double) t.count() / Math.max(1, max));
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setPrefHeight(8);
            topRows.getChildren().add(new VBox(4, new HBox(8, name, grow, count), bar));
        }

        recentRows.getChildren().clear();
        if (d.recent().isEmpty()) {
            recentRows.getChildren().add(muted("No activity yet."));
        }
        Instant now = Instant.now();
        for (AdminModels.UsageEvent e : d.recent()) {
            Label when = new Label(TimeAgo.ofIso(e.createdAt(), now));
            when.getStyleClass().add("muted");
            when.setMinWidth(64);
            Label what = new Label(UsageText.describe(e));
            what.setWrapText(true);
            recentRows.getChildren().add(new HBox(10, when, what));
        }
    }

    private void problem(ApiException e) {
        if (e.status() == 401) {
            onProblem.accept(e);
            return;
        }
        placesNumber.setText("?");
        searchesNumber.setText("?");
        reportsNumber.setText("?");
        status.setText(e.getMessage());
    }

    private void saveCsv(String csv) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save usage log");
        chooser.setInitialFileName("uenav-usage-log.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), csv, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Could not save the file: " + ex.getMessage(), ButtonType.OK);
            a.setHeaderText("Export failed");
            a.showAndWait();
        }
    }

    private static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
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

    private static VBox panel(String heading, String subtitle, VBox rows, Button action) {
        Label h = new Label(heading);
        h.getStyleClass().add("card-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add("muted");
        VBox card = new VBox(10, new VBox(2, h, s), rows);
        if (action != null) {
            card.getChildren().add(action);
        }
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 20, 20, 20));
        card.setMinHeight(160);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefWidth(300);
        return card;
    }
}

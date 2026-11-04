package com.uenav.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Admin dashboard (Figma: Dashboard): numbers, most searched places, recent activity. */
public class DashboardView extends ScrollPane {

    private record Data(List<AdminModels.Place> places, AdminModels.UsageSummary summary,
                        List<AdminModels.TopPlace> top, List<AdminModels.UsageEvent> recent) { }

    /** What the Places card asks the window to do (the add/edit forms live on the Places page). */
    public record PlaceAction(Kind kind, int id, String search) {
        public enum Kind { ADD, EDIT, DELETE, VIEW_ALL }
    }

    /** Rows shown in the Places card before "View all places". */
    static final int PLACE_ROWS = 4;

    private final AdminApi api;
    private final Consumer<ApiException> onProblem;
    private final Label placesNumber = new Label("\u2026");
    private final Label searchesNumber = new Label("\u2026");
    private final Label reportsNumber = new Label("\u2026");
    private final VBox topRows = new VBox(14);
    private final VBox recentRows = new VBox(8);
    private final Label status = new Label();
    private final VBox placeRows = new VBox();
    private final Label placesTotal = new Label();
    private final Label placesFooter = new Label();
    private final TextField placeSearch = new TextField();
    private final Consumer<PlaceAction> onPlace;
    private List<AdminModels.Place> placeList = List.of();
    private final Map<Integer, Long> searchCounts = new HashMap<>();

    public DashboardView(AdminApi api, Consumer<ApiException> onProblem, Runnable openReports,
                         Consumer<PlaceAction> onPlace) {
        this.api = api;
        this.onProblem = onProblem;
        this.onPlace = onPlace;
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
        VBox page = new VBox(16, header, stats, panels, placesCard(), status);
        page.setPadding(new Insets(24));
        setContent(page);

        topRows.getChildren().add(muted("Loading\u2026"));
        recentRows.getChildren().add(muted("Loading\u2026"));

        Async.run(() -> {
                    List<AdminModels.Place> places = api.nodes().stream().filter(AdminModels.Place::isSearchable)
                            .toList();
                    return new Data(places, api.usageSummary(), api.mostSearched(100), api.recentActivity(6));
                },
                this::show, this::problem);
    }

    private void show(Data d) {
        placesNumber.setText(String.valueOf(d.places().size()));
        searchCounts.clear();
        d.top().forEach(t -> searchCounts.put(t.nodeId(), t.count()));
        placeList = d.places().stream()
                .sorted((a, b) -> Long.compare(searchCounts.getOrDefault(b.id(), 0L),
                        searchCounts.getOrDefault(a.id(), 0L)))
                .toList();
        fillPlaces();
        searchesNumber.setText(String.valueOf(d.summary().searches()));
        reportsNumber.setText(String.valueOf(d.summary().openReports()));
        status.setText("");

        topRows.getChildren().clear();
        if (d.top().isEmpty()) {
            topRows.getChildren().add(muted("No searches yet."));
        }
        long max = d.top().stream().mapToLong(AdminModels.TopPlace::count).max().orElse(1);
        for (AdminModels.TopPlace t : d.top().subList(0, Math.min(5, d.top().size()))) {
            topRows.getChildren().add(bar(t.name(), t.count(), max));
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

    private VBox placesCard() {
        Label h = new Label("Places");
        h.getStyleClass().add("card-title");
        placesTotal.getStyleClass().add("chip");
        Label sub = new Label("Manage campus destinations and location details.");
        sub.getStyleClass().add("muted");
        placeSearch.setPromptText("Search places");
        placeSearch.setPrefWidth(220);
        placeSearch.textProperty().addListener((o, a, b) -> fillPlaces());
        Button add = new Button("+ Add place");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> onPlace.accept(new PlaceAction(PlaceAction.Kind.ADD, 0, null)));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(8, h, placesTotal);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox head = new HBox(12, new VBox(2, titleRow, sub), spacer, placeSearch, add);
        head.setAlignment(Pos.CENTER_LEFT);

        placesFooter.getStyleClass().add("muted");
        Button all = new Button("View all places \u2192");
        all.getStyleClass().add("link-button");
        all.setOnAction(e -> onPlace.accept(new PlaceAction(PlaceAction.Kind.VIEW_ALL, 0,
                placeSearch.getText().isBlank() ? null : placeSearch.getText())));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox foot = new HBox(placesFooter, grow, all);
        foot.setAlignment(Pos.CENTER_LEFT);

        placeRows.getChildren().add(muted("Loading\u2026"));
        VBox card = new VBox(12, head, new Separator(), placeRows, foot);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16, 20, 16, 20));
        return card;
    }

    private void fillPlaces() {
        String q = placeSearch.getText() == null ? "" : placeSearch.getText().trim().toLowerCase();
        List<AdminModels.Place> matches = placeList.stream()
                .filter(p -> q.isEmpty() || p.name().toLowerCase().contains(q)
                        || p.nodeType().toLowerCase().contains(q))
                .toList();
        placesTotal.setText(placeList.size() + " total");
        placeRows.getChildren().clear();
        int shown = Math.min(PLACE_ROWS, matches.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                placeRows.getChildren().add(new Separator());
            }
            placeRows.getChildren().add(placeRow(matches.get(i)));
        }
        if (matches.isEmpty()) {
            placeRows.getChildren().add(muted(placeList.isEmpty() ? "No places yet." : "No places match."));
        }
        placesFooter.setText("Showing " + shown + " of " + matches.size() + " places");
    }

    private HBox placeRow(AdminModels.Place p) {
        PlaceStyle style = PlaceStyle.of(p.nodeType());
        long n = searchCounts.getOrDefault(p.id(), 0L);
        Label name = new Label(p.name());
        name.getStyleClass().add("card-title");
        Label sub = new Label(style.label() + " \u00B7 " + n + (n == 1 ? " search" : " searches"));
        sub.getStyleClass().add("muted");
        sub.setStyle("-fx-font-size: 11px;");
        Button edit = iconButton("edit", "Edit");
        edit.setOnAction(e -> onPlace.accept(new PlaceAction(PlaceAction.Kind.EDIT, p.id(), null)));
        Button del = iconButton("delete", "Delete");
        del.getStyleClass().add("danger");
        del.setOnAction(e -> onPlace.accept(new PlaceAction(PlaceAction.Kind.DELETE, p.id(), null)));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox row = new HBox(12, bubble(style.icon(), style.tone(), 36), new VBox(2, name, sub), grow, edit, del);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 0, 8, 0));
        return row;
    }

    /** A soft coloured circle with an icon in it (Figma: place icons). */
    private static StackPane bubble(String icon, String tone, double size) {
        StackPane p = new StackPane(Icons.icon(icon, size * 0.46));
        p.getStyleClass().addAll("nv-bubble", "nv-tone-" + tone);
        p.setMinSize(size, size);
        p.setMaxSize(size, size);
        return p;
    }

    /** A "Most searched" row: name, count, and a red bar sized against the largest count. */
    private static VBox bar(String name, long count, long max) {
        Label n = new Label(name);
        Label c = new Label(String.valueOf(count));
        c.getStyleClass().add("bar-count");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Region track = new Region();
        track.getStyleClass().add("nv-bar-track");
        track.setMinHeight(6);
        track.setMaxHeight(6);
        Region fill = new Region();
        fill.getStyleClass().add("nv-bar-fill");
        fill.setMinHeight(6);
        fill.setMaxHeight(6);
        StackPane barBox = new StackPane(track, fill);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        fill.maxWidthProperty().bind(barBox.widthProperty().multiply((double) count / Math.max(1, max)));
        return new VBox(6, new HBox(8, n, grow, c), barBox);
    }

    private static Button iconButton(String icon, String text) {
        Button b = new Button();
        b.setGraphic(Icons.icon(icon, 16));
        b.getStyleClass().add("icon-button");
        b.setAccessibleText(text);
        b.setTooltip(new javafx.scene.control.Tooltip(text));
        return b;
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

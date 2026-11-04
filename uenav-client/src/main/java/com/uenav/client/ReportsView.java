package com.uenav.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Route reports: routes that visitors marked as wrong. The admin marks each one reviewed. */
public class ReportsView extends VBox {

    private static final DateTimeFormatter LOCAL =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());

    private final AdminApi api;
    private final Consumer<ApiException> onUnauthorized;
    private final ObservableList<AdminModels.UsageEvent> items = FXCollections.observableArrayList();
    private final FilteredList<AdminModels.UsageEvent> filtered = new FilteredList<>(items, e -> true);
    private final TableView<AdminModels.UsageEvent> table = new TableView<>();
    private final CheckBox showReviewed = new CheckBox("Show reviewed reports");
    private final Label total = new Label();
    private final Label footer = new Label();
    private final Label status = new Label();
    private final Button retry = new Button("Retry");

    public ReportsView(AdminApi api, Consumer<ApiException> onUnauthorized) {
        this.api = api;
        this.onUnauthorized = onUnauthorized;
        getStyleClass().add("canvas");
        setPadding(new Insets(24));
        setSpacing(16);

        Label eyebrow = new Label("ADMIN");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Route reports");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Routes that visitors marked as wrong. Check each one, fix the map data, then mark it reviewed.");
        sub.getStyleClass().add("muted");

        total.getStyleClass().add("chip");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, total, spacer, showReviewed);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        buildTable();

        footer.getStyleClass().add("muted");
        status.getStyleClass().add("error-label");
        retry.getStyleClass().add("small-button");
        retry.setVisible(false);
        retry.setManaged(false);
        retry.setOnAction(e -> reload());
        HBox statusRow = new HBox(10, status, retry);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, toolbar, table, footer, statusRow);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(card, Priority.ALWAYS);
        getChildren().addAll(new VBox(2, eyebrow, title, sub), card);

        filtered.setPredicate(e -> "NEW".equals(e.status()));
        showReviewed.selectedProperty().addListener((o, a, b) ->
                filtered.setPredicate(e -> b || "NEW".equals(e.status())));
        filtered.addListener((ListChangeListener<AdminModels.UsageEvent>) c -> updateCounts());
        reload();
    }

    private void buildTable() {
        table.getColumns().add(column("When", 190, e -> when(e.createdAt())));
        table.getColumns().add(column("From", 130, AdminModels.UsageEvent::fromName));
        table.getColumns().add(column("To", 130, AdminModels.UsageEvent::toName));
        table.getColumns().add(column("Route", 70, e -> e.routePref() == null ? "" : e.routePref().toLowerCase()));
        table.getColumns().add(column("Status", 80, e -> "REVIEWED".equals(e.status()) ? "Reviewed" : "New"));
        table.getColumns().add(column("Note", 170, e -> e.note() == null ? "" : e.note()));

        TableColumn<AdminModels.UsageEvent, Void> actions = new TableColumn<>("");
        actions.setSortable(false);
        actions.setMinWidth(140);
        actions.setMaxWidth(140);
        actions.setCellFactory(col -> new TableCell<AdminModels.UsageEvent, Void>() {
            private final Button toggle = new Button();

            {
                toggle.getStyleClass().add("small-button");
                toggle.setOnAction(e -> change(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                AdminModels.UsageEvent row = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                toggle.setText("REVIEWED".equals(row.status()) ? "Reopen" : "Mark reviewed");
                setGraphic(toggle);
            }
        });
        table.getColumns().add(actions);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No open reports."));
        table.setItems(filtered);
    }

    private static TableColumn<AdminModels.UsageEvent, String> column(
            String header, double width, java.util.function.Function<AdminModels.UsageEvent, String> value) {
        TableColumn<AdminModels.UsageEvent, String> c = new TableColumn<>(header);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(value.apply(cd.getValue())));
        c.setPrefWidth(width);
        c.setSortable(false);
        return c;
    }

    private static String when(String iso) {
        try {
            Instant at = Instant.parse(iso);
            return LOCAL.format(at) + "  (" + TimeAgo.format(at, Instant.now()) + ")";
        } catch (DateTimeParseException e) {
            return iso;
        }
    }

    private void updateCounts() {
        long open = items.stream().filter(e -> "NEW".equals(e.status())).count();
        total.setText(open + " open");
        footer.setText("Showing " + filtered.size() + " of " + items.size() + " reports");
    }

    private void reload() {
        status.setText("Loading\u2026");
        retry.setVisible(false);
        retry.setManaged(false);
        Async.run(api::reports,
                rows -> {
                    items.setAll(rows);
                    table.refresh();
                    updateCounts();
                    status.setText("");
                },
                this::failed);
    }

    private void change(AdminModels.UsageEvent row) {
        String next = "REVIEWED".equals(row.status()) ? "NEW" : "REVIEWED";
        Async.run(() -> {
                    api.setReportStatus(row.id(), next);
                    return Boolean.TRUE;
                },
                done -> reload(),
                this::failed);
    }

    private void failed(ApiException e) {
        if (e.status() == 401) {
            onUnauthorized.accept(e);
            return;
        }
        status.setText(e.getMessage());
        retry.setVisible(true);
        retry.setManaged(true);
    }
}

package com.uenav.client;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Shared layout for the list pages: title, search box, add button, table with Edit and Delete,
 * and the add/edit pop-up. A page fills in what is specific to it (columns, form, server calls).
 * The page must call init() at the end of its own constructor.
 */
abstract class ManagePage<T> extends VBox {

    /** One choice in a drop-down list. */
    record Option<V>(V value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** The server call to make when the form was filled in correctly. */
    interface FormSave {
        void save() throws ApiException;
    }

    private final String noun;
    private final String inUseMessage;
    private final Consumer<ApiException> onUnauthorized;

    private final ObservableList<T> items = FXCollections.observableArrayList();
    private final FilteredList<T> filtered = new FilteredList<>(items, r -> true);
    protected final TableView<T> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label total = new Label();
    private final Label footer = new Label();
    private final Label status = new Label();
    private final Button retry = new Button("Retry");

    ManagePage(String title, String subtitle, String noun, String addLabel, String inUseMessage,
               Consumer<ApiException> onUnauthorized) {
        this.noun = noun;
        this.inUseMessage = inUseMessage;
        this.onUnauthorized = onUnauthorized;
        getStyleClass().add("canvas");
        setPadding(new Insets(24));
        setSpacing(16);

        Label eyebrow = new Label("ADMIN");
        eyebrow.getStyleClass().add("eyebrow");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("page-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("muted");

        total.getStyleClass().add("chip");
        search.setPromptText("Search " + noun + "s");
        search.setPrefWidth(220);
        Button add = new Button(addLabel);
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> openForm(null));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, total, spacer, search, add);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        footer.getStyleClass().add("muted");
        status.getStyleClass().add("error-label");
        retry.getStyleClass().add("small-button");
        retry.setVisible(false);
        retry.setManaged(false);
        retry.setOnAction(e -> reload());
        HBox statusRow = new HBox(10, status, retry);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        table.setPlaceholder(new Label("Nothing here yet. Click \"" + addLabel + "\" to create one."));
        VBox card = new VBox(12, toolbar, table, footer, statusRow);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(card, Priority.ALWAYS);

        getChildren().addAll(new VBox(2, eyebrow, titleLabel, sub), card);
    }

    // ---- what each page provides
    protected abstract List<T> load() throws ApiException;

    protected abstract void configureColumns();

    protected abstract String searchText(T row);

    protected abstract String describe(T row);

    protected abstract void openForm(T existingOrNull);

    protected abstract void deleteRow(T row) throws ApiException;

    // ---- starting up
    protected final void init() {
        configureColumns();

        TableColumn<T, Void> actions = new TableColumn<>("");
        actions.setSortable(false);
        actions.setMinWidth(150);
        actions.setMaxWidth(150);
        actions.setCellFactory(col -> new TableCell<T, Void>() {
            private final Button edit = new Button("Edit");
            private final Button del = new Button("Delete");
            private final HBox box = new HBox(6, edit, del);

            {
                edit.getStyleClass().add("small-button");
                del.getStyleClass().addAll("small-button", "danger");
                edit.setOnAction(e -> openForm(getTableRow().getItem()));
                del.setOnAction(e -> confirmDelete(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                boolean show = !empty && getTableRow() != null && getTableRow().getItem() != null;
                setGraphic(show ? box : null);
            }
        });
        table.getColumns().add(actions);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        SortedList<T> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        search.textProperty().addListener((o, a, b) -> filtered.setPredicate(r -> matches(r, b)));
        filtered.addListener((ListChangeListener<T>) c -> updateCounts());
        reload();
    }

    private boolean matches(T row, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return searchText(row).toLowerCase().contains(query.trim().toLowerCase());
    }

    private void updateCounts() {
        total.setText(items.size() + " total");
        footer.setText("Showing " + filtered.size() + " of " + items.size() + " " + noun + "s");
    }

    protected final TableColumn<T, String> textColumn(String header, double width, java.util.function.Function<T, String> value) {
        TableColumn<T, String> c = new TableColumn<>(header);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(value.apply(cd.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    // ---- loading
    protected final void reload() {
        status.setText("Loading\u2026");
        retry.setVisible(false);
        retry.setManaged(false);
        Async.run(this::load,
                rows -> {
                    items.setAll(rows);
                    table.refresh();
                    updateCounts();
                    status.setText("");
                },
                this::loadFailed);
    }

    private void loadFailed(ApiException e) {
        if (e.status() == 401) {
            onUnauthorized.accept(e);
            return;
        }
        status.setText(e.getMessage());
        retry.setVisible(true);
        retry.setManaged(true);
    }

    // ---- add / edit pop-up
    /**
     * Shows the pop-up. validate runs when Save is pressed: it returns the server call to make, or
     * throws IllegalArgumentException with a message to show under the form.
     */
    protected final void showForm(String title, String[] labels, Region[] fields, String note,
                                  Supplier<FormSave> validate) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(title);
        DialogPane pane = dlg.getDialogPane();
        String css = Theme.css();
        if (css != null) {
            pane.getStylesheets().add(css);
        }
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        for (int i = 0; i < labels.length; i++) {
            fields[i].setPrefWidth(320);
            grid.add(new Label(labels[i]), 0, i);
            grid.add(fields[i], 1, i);
        }
        VBox content = new VBox(12, grid);
        if (note != null) {
            Label n = new Label(note);
            n.getStyleClass().add("muted");
            n.setWrapText(true);
            n.setMaxWidth(440);
            content.getChildren().add(n);
        }
        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMaxWidth(440);
        content.getChildren().add(error);
        content.setPadding(new Insets(16, 8, 4, 8));
        pane.setContent(content);

        Button ok = (Button) pane.lookupButton(saveType);
        ok.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume();
            FormSave action;
            try {
                action = validate.get();
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                return;
            }
            ok.setDisable(true);
            error.setText("");
            Async.run(() -> {
                        action.save();
                        return Boolean.TRUE;
                    },
                    done -> {
                        dlg.close();
                        reload();
                    },
                    e -> {
                        ok.setDisable(false);
                        if (e.status() == 401) {
                            dlg.close();
                            onUnauthorized.accept(e);
                        } else {
                            error.setText(e.getMessage());
                        }
                    });
        });
        dlg.showAndWait();
    }

    // ---- delete
    private void confirmDelete(T row) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + describe(row) + "\"? This cannot be undone.", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Delete " + noun);
        style(confirm);
        confirm.showAndWait().ifPresent(choice -> {
            if (choice != ButtonType.OK) {
                return;
            }
            Async.run(() -> {
                        deleteRow(row);
                        return Boolean.TRUE;
                    },
                    done -> reload(),
                    e -> {
                        if (e.status() == 401) {
                            onUnauthorized.accept(e);
                            return;
                        }
                        Alert err = new Alert(Alert.AlertType.ERROR,
                                e.status() == 409 ? inUseMessage : e.getMessage(), ButtonType.OK);
                        err.setHeaderText("Could not delete");
                        style(err);
                        err.showAndWait();
                    });
        });
    }

    private static void style(Alert a) {
        String css = Theme.css();
        if (css != null) {
            a.getDialogPane().getStylesheets().add(css);
        }
    }
}

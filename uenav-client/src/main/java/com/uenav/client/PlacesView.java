package com.uenav.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
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

/** The Places page: list, search, add, edit and delete (Figma: Dashboard "Places" panel, as a full page). */
public class PlacesView extends VBox {

    private static final List<String> TYPES = List.of(
            "ROOM", "OFFICE", "GATE", "BUILDING_ENTRANCE", "FACILITY", "JUNCTION", "STAIRS", "ELEVATOR");

    private record Option<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record Data(List<AdminModels.Place> places, List<AdminModels.Building> buildings,
                        List<AdminModels.Floor> floors) { }

    private final AdminApi api;
    private final Consumer<ApiException> onUnauthorized;

    private final ObservableList<AdminModels.Place> items = FXCollections.observableArrayList();
    private final FilteredList<AdminModels.Place> filtered = new FilteredList<>(items, p -> true);
    private final TableView<AdminModels.Place> table = new TableView<>();
    private final TextField search = new TextField();
    private final Label total = new Label();
    private final Label footer = new Label();
    private final Label status = new Label();
    private final Button retry = new Button("Retry");

    private List<AdminModels.Building> buildings = List.of();
    private List<AdminModels.Floor> floors = List.of();
    private final Map<Integer, String> buildingNames = new HashMap<>();
    private final Map<Integer, String> floorNames = new HashMap<>();

    public PlacesView(AdminApi api, Consumer<ApiException> onUnauthorized) {
        this.api = api;
        this.onUnauthorized = onUnauthorized;
        getStyleClass().add("canvas");
        setPadding(new Insets(24));
        setSpacing(16);

        Label eyebrow = new Label("ADMIN");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Places");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Manage campus destinations and location details.");
        sub.getStyleClass().add("muted");

        total.getStyleClass().add("chip");
        search.setPromptText("Search places");
        search.setPrefWidth(220);
        Button add = new Button("+ Add place");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> openForm(null));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, total, spacer, search, add);
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

        search.textProperty().addListener((o, a, b) -> filtered.setPredicate(p -> matches(p, b)));
        filtered.addListener((ListChangeListener<AdminModels.Place>) c -> updateCounts());

        reload();
    }

    // ---- table
    private void buildTable() {
        TableColumn<AdminModels.Place, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().name()));
        name.setPrefWidth(240);

        TableColumn<AdminModels.Place, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().nodeType()));
        type.setPrefWidth(140);

        TableColumn<AdminModels.Place, String> building = new TableColumn<>("Building");
        building.setCellValueFactory(cd -> new ReadOnlyStringWrapper(buildingOf(cd.getValue())));
        building.setPrefWidth(200);

        TableColumn<AdminModels.Place, String> floor = new TableColumn<>("Floor");
        floor.setCellValueFactory(cd -> new ReadOnlyStringWrapper(floorOf(cd.getValue())));
        floor.setPrefWidth(110);

        TableColumn<AdminModels.Place, Integer> x = new TableColumn<>("X");
        x.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().x()));
        x.setPrefWidth(70);

        TableColumn<AdminModels.Place, Integer> y = new TableColumn<>("Y");
        y.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().y()));
        y.setPrefWidth(70);

        TableColumn<AdminModels.Place, Void> actions = new TableColumn<>("");
        actions.setSortable(false);
        actions.setMinWidth(150);
        actions.setMaxWidth(150);
        actions.setCellFactory(col -> new TableCell<AdminModels.Place, Void>() {
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

        table.getColumns().add(name);
        table.getColumns().add(type);
        table.getColumns().add(building);
        table.getColumns().add(floor);
        table.getColumns().add(x);
        table.getColumns().add(y);
        table.getColumns().add(actions);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No places yet. Click \"+ Add place\" to create one."));

        SortedList<AdminModels.Place> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
    }

    private String buildingOf(AdminModels.Place p) {
        if (p.buildingId() == null) {
            return "Outdoor";
        }
        String n = buildingNames.get(p.buildingId());
        return n == null ? "#" + p.buildingId() : n;
    }

    private String floorOf(AdminModels.Place p) {
        if (p.floorId() == null) {
            return "\u2014";
        }
        String n = floorNames.get(p.floorId());
        return n == null ? "#" + p.floorId() : n;
    }

    private boolean matches(AdminModels.Place p, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.trim().toLowerCase();
        return p.name().toLowerCase().contains(q) || p.nodeType().toLowerCase().contains(q)
                || buildingOf(p).toLowerCase().contains(q) || floorOf(p).toLowerCase().contains(q);
    }

    private void updateCounts() {
        total.setText(items.size() + " total");
        footer.setText("Showing " + filtered.size() + " of " + items.size() + " places");
    }

    // ---- loading
    private void reload() {
        status.setText("Loading\u2026");
        retry.setVisible(false);
        retry.setManaged(false);
        Async.run(() -> new Data(api.nodes(), api.buildings(), api.floors()),
                data -> {
                    buildings = data.buildings();
                    floors = data.floors();
                    buildingNames.clear();
                    floorNames.clear();
                    for (AdminModels.Building b : buildings) {
                        buildingNames.put(b.id(), b.name());
                    }
                    for (AdminModels.Floor f : floors) {
                        floorNames.put(f.id(), f.floorName());
                    }
                    items.setAll(data.places());
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

    // ---- add / edit
    private void openForm(AdminModels.Place existing) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(existing == null ? "Add place" : "Edit place");
        DialogPane pane = dlg.getDialogPane();
        String css = Theme.css();
        if (css != null) {
            pane.getStylesheets().add(css);
        }
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField name = new TextField(existing == null ? "" : existing.name());
        name.setPromptText("e.g. Registrar's Office");

        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(TYPES));
        type.setEditable(true);
        type.setValue(existing == null ? "ROOM" : existing.nodeType());

        List<Option<AdminModels.Building>> buildingOptions = new ArrayList<>();
        buildingOptions.add(new Option<>(null, "(none, outdoor)"));
        for (AdminModels.Building b : buildings) {
            buildingOptions.add(new Option<>(b, b.name()));
        }
        ComboBox<Option<AdminModels.Building>> building =
                new ComboBox<>(FXCollections.observableArrayList(buildingOptions));
        building.getSelectionModel().selectFirst();
        if (existing != null && existing.buildingId() != null) {
            for (Option<AdminModels.Building> o : buildingOptions) {
                if (o.value() != null && o.value().id() == existing.buildingId()) {
                    building.getSelectionModel().select(o);
                }
            }
        }

        ComboBox<Option<AdminModels.Floor>> floor = new ComboBox<>();
        Runnable refreshFloors = () -> {
            Option<AdminModels.Building> sel = building.getValue();
            List<Option<AdminModels.Floor>> opts = new ArrayList<>();
            opts.add(new Option<>(null, "(none)"));
            if (sel != null && sel.value() != null) {
                for (AdminModels.Floor f : floors) {
                    if (f.buildingId() == sel.value().id()) {
                        opts.add(new Option<>(f, f.floorName()));
                    }
                }
            }
            floor.setItems(FXCollections.observableArrayList(opts));
            floor.getSelectionModel().selectFirst();
        };
        refreshFloors.run();
        if (existing != null && existing.floorId() != null) {
            for (Option<AdminModels.Floor> o : floor.getItems()) {
                if (o.value() != null && o.value().id() == existing.floorId()) {
                    floor.getSelectionModel().select(o);
                }
            }
        }
        building.valueProperty().addListener((o, a, b) -> refreshFloors.run());

        TextField xField = new TextField(existing == null ? "0" : String.valueOf(existing.x()));
        TextField yField = new TextField(existing == null ? "0" : String.valueOf(existing.y()));

        for (Region r : new Region[] {name, type, building, floor, xField, yField}) {
            r.setPrefWidth(300);
        }

        Label note = new Label("JUNCTION, STAIRS and ELEVATOR are waypoints. They are not offered in the visitor search list.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        note.setMaxWidth(420);
        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);
        error.setMaxWidth(420);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        String[] labels = {"Name *", "Type *", "Building", "Floor", "X *", "Y *"};
        Region[] fields = {name, type, building, floor, xField, yField};
        for (int i = 0; i < labels.length; i++) {
            grid.add(new Label(labels[i]), 0, i);
            grid.add(fields[i], 1, i);
        }
        VBox content = new VBox(12, grid, note, error);
        content.setPadding(new Insets(16, 8, 4, 8));
        pane.setContent(content);

        Button ok = (Button) pane.lookupButton(saveType);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            ev.consume();
            String n = name.getText().trim();
            String t = type.getEditor().getText().trim();
            if (n.isEmpty() || t.isEmpty()) {
                error.setText("Name and Type are required.");
                return;
            }
            int xv;
            int yv;
            try {
                xv = Integer.parseInt(xField.getText().trim());
                yv = Integer.parseInt(yField.getText().trim());
            } catch (NumberFormatException ex) {
                error.setText("X and Y must be whole numbers.");
                return;
            }
            Option<AdminModels.Building> bSel = building.getValue();
            Option<AdminModels.Floor> fSel = floor.getValue();
            Integer buildingId = bSel == null || bSel.value() == null ? null : bSel.value().id();
            Integer floorId = fSel == null || fSel.value() == null ? null : fSel.value().id();
            AdminModels.NodeForm form = new AdminModels.NodeForm(buildingId, floorId, n, t, xv, yv);

            ok.setDisable(true);
            error.setText("");
            Async.run(() -> {
                        if (existing == null) {
                            api.createNode(form);
                        } else {
                            api.updateNode(existing.id(), form);
                        }
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
    private void confirmDelete(AdminModels.Place p) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + p.name() + "\"? This cannot be undone.", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Delete place");
        styleAlert(confirm);
        confirm.showAndWait().ifPresent(choice -> {
            if (choice != ButtonType.OK) {
                return;
            }
            Async.run(() -> {
                        api.deleteNode(p.id());
                        return Boolean.TRUE;
                    },
                    done -> reload(),
                    e -> {
                        if (e.status() == 401) {
                            onUnauthorized.accept(e);
                            return;
                        }
                        String msg = e.status() == 409
                                ? "This place is still used by walkways or the usage log. Remove those first."
                                : e.getMessage();
                        Alert err = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
                        err.setHeaderText("Could not delete");
                        styleAlert(err);
                        err.showAndWait();
                    });
        });
    }

    private static void styleAlert(Alert a) {
        String css = Theme.css();
        if (css != null) {
            a.getDialogPane().getStylesheets().add(css);
        }
    }
}

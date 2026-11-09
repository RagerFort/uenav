package com.uenav.client;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;

/**
 * QR codes page: choose places and save printable "You are here" signs. Post them at entrances, stairwells and
 * hallway corners; scanning one in the phone app sets the starting point with no internet (D16).
 */
public class QrCodesView extends VBox {

    private static final String ALL = "All places";
    private static final String STARTS = "Good sign spots (gates, entrances, stairs, elevators, junctions)";
    private static final Set<String> START_TYPES = Set.of("GATE", "EXIT", "BUILDING_ENTRANCE", "STAIRS", "ELEVATOR",
            "JUNCTION");

    private record Data(List<AdminModels.Place> places, List<AdminModels.Building> buildings,
                        List<AdminModels.Floor> floors) { }

    private final Consumer<ApiException> onUnauthorized;
    private final ObservableList<AdminModels.Place> items = FXCollections.observableArrayList();
    private final FilteredList<AdminModels.Place> shown = new FilteredList<>(items, p -> true);
    private final Map<Integer, BooleanProperty> picked = new HashMap<>();
    private final ListView<AdminModels.Place> list = new ListView<>(shown);
    private final TextField search = new TextField();
    private final ComboBox<String> kind = new ComboBox<>(FXCollections.observableArrayList(ALL, STARTS));
    private final StackPane preview = new StackPane();
    private final Label status = new Label();
    private final Button saveSelected = new Button();
    private final Map<Integer, String> buildingNames = new HashMap<>();
    private final Map<Integer, String> floorNames = new HashMap<>();

    public QrCodesView(AdminApi api, Consumer<ApiException> onUnauthorized) {
        this.onUnauthorized = onUnauthorized;
        getStyleClass().add("canvas");
        setPadding(new Insets(24));
        setSpacing(16);

        Label eyebrow = new Label("TOOLS");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("QR codes");
        title.getStyleClass().add("page-title");
        Label sub = new Label("Print \"You are here\" signs for entrances, stairwells and hallway corners. Each code "
                + "holds only the place number, so scanning works with no internet.");
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);
        VBox header = new VBox(2, eyebrow, title, sub);

        search.setPromptText("Search places");
        search.textProperty().addListener((o, a, b) -> refilter());
        kind.setValue(STARTS);
        kind.valueProperty().addListener((o, a, b) -> refilter());
        kind.setMaxWidth(Double.MAX_VALUE);

        list.setCellFactory(CheckBoxListCell.forListView(p -> pickOf(p.id()), new StringConverter<>() {
            @Override
            public String toString(AdminModels.Place p) {
                return p == null ? "" : p.name() + "  ·  " + PlaceStyle.of(p.nodeType()).label() + where(p, true);
            }

            @Override
            public AdminModels.Place fromString(String s) {
                return null;
            }
        }));
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showPreview(b));
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setPlaceholder(new Label("Loading…"));

        Button all = new Button("Select all shown");
        all.getStyleClass().add("small-button");
        all.setOnAction(e -> shown.forEach(p -> pickOf(p.id()).set(true)));
        Button none = new Button("Clear");
        none.getStyleClass().add("small-button");
        none.setOnAction(e -> picked.values().forEach(b -> b.set(false)));
        HBox pickButtons = new HBox(8, all, none);

        VBox left = new VBox(10, search, kind, list, pickButtons);
        left.getStyleClass().add("card");
        left.setPadding(new Insets(16));
        left.setPrefWidth(420);
        left.setMinWidth(340);
        HBox.setHgrow(left, Priority.ALWAYS);

        saveSelected.getStyleClass().add("primary-button");
        saveSelected.setGraphic(Icons.icon("download", 16, "nv-icon-white"));
        saveSelected.setOnAction(e -> saveSigns(selectedPlaces()));
        Button saveOne = new Button("Save this sign…");
        saveOne.getStyleClass().add("secondary-button");
        saveOne.setOnAction(e -> {
            AdminModels.Place p = list.getSelectionModel().getSelectedItem();
            if (p != null) {
                saveSigns(List.of(p));
            }
        });
        saveOne.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        updateSaveButton();

        preview.getStyleClass().add("qr-preview");
        preview.setPadding(new Insets(12));
        preview.getChildren().setAll(muted("Pick a place on the left to see its sign."));
        ScrollPane previewScroll = new ScrollPane(preview);
        previewScroll.setFitToWidth(true);
        previewScroll.getStyleClass().add("canvas");
        VBox.setVgrow(previewScroll, Priority.ALWAYS);
        status.getStyleClass().add("muted");
        status.setWrapText(true);
        HBox actions = new HBox(10, saveSelected, saveOne);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox right = new VBox(10, label("Sign preview", "card-title"), previewScroll, actions, status);
        right.getStyleClass().add("card");
        right.setPadding(new Insets(16));
        right.setPrefWidth(420);
        right.setMinWidth(360);

        HBox columns = new HBox(16, left, right);
        VBox.setVgrow(columns, Priority.ALWAYS);
        getChildren().addAll(header, columns);

        Async.run(() -> new Data(api.nodes(), api.buildings(), api.floors()), this::show, e -> {
            if (e.status() == 401) {
                onUnauthorized.accept(e);
            } else {
                list.setPlaceholder(new Label(e.getMessage()));
            }
        });
    }

    private void show(Data d) {
        d.buildings().forEach(b -> buildingNames.put(b.id(), b.name()));
        d.floors().forEach(f -> floorNames.put(f.id(), f.floorName()));
        List<AdminModels.Place> sorted = new ArrayList<>(d.places());
        sorted.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        items.setAll(sorted);
        list.setPlaceholder(new Label("No places match."));
        refilter();
    }

    private BooleanProperty pickOf(int id) {
        return picked.computeIfAbsent(id, k -> {
            BooleanProperty b = new SimpleBooleanProperty(false);
            b.addListener((o, was, is) -> updateSaveButton());
            return b;
        });
    }

    private void refilter() {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        boolean startsOnly = STARTS.equals(kind.getValue());
        shown.setPredicate(p -> (!startsOnly || START_TYPES.contains(p.nodeType().toUpperCase(Locale.ROOT)))
                && (q.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(q)
                || p.nodeType().toLowerCase(Locale.ROOT).contains(q)));
    }

    private List<AdminModels.Place> selectedPlaces() {
        Set<AdminModels.Place> out = new LinkedHashSet<>();
        for (AdminModels.Place p : items) {
            BooleanProperty b = picked.get(p.id());
            if (b != null && b.get()) {
                out.add(p);
            }
        }
        return new ArrayList<>(out);
    }

    private void updateSaveButton() {
        int n = selectedPlaces().size();
        saveSelected.setText(n == 0 ? "Save selected signs" : "Save " + n + (n == 1 ? " sign…" : " signs…"));
        saveSelected.setDisable(n == 0);
    }

    /** "College of Engineering Building · 4th floor", with a leading separator when asked for. */
    private String where(AdminModels.Place p, boolean withDot) {
        List<String> parts = new ArrayList<>();
        if (p.buildingId() != null && buildingNames.containsKey(p.buildingId())
                && !buildingNames.get(p.buildingId()).equalsIgnoreCase(p.name())) {
            parts.add(buildingNames.get(p.buildingId()));
        }
        if (p.floorId() != null && floorNames.containsKey(p.floorId())) {
            parts.add(floorNames.get(p.floorId()));
        }
        String s = String.join(" · ", parts);
        return withDot && !s.isEmpty() ? "  ·  " + s : s;
    }

    private void showPreview(AdminModels.Place p) {
        if (p == null) {
            preview.getChildren().setAll(muted("Pick a place on the left to see its sign."));
            return;
        }
        javafx.scene.Node sign = QrSigns.sign(p.id(), p.name(), where(p, false));
        Group scaled = new Group(sign);
        sign.setScaleX(0.6);
        sign.setScaleY(0.6);
        preview.getChildren().setAll(new Group(scaled));
    }

    private void saveSigns(List<AdminModels.Place> places) {
        if (places.isEmpty()) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a folder for the QR signs");
        File dir = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
        if (dir == null) {
            return;
        }
        int saved = 0;
        List<String> failed = new ArrayList<>();
        for (AdminModels.Place p : places) {
            try {
                QrSigns.savePng(QrSigns.render(QrSigns.sign(p.id(), p.name(), where(p, false))),
                        new File(dir, QrSigns.fileName(p.id(), p.name())));
                saved++;
            } catch (IOException | RuntimeException ex) {
                failed.add(p.name());
            }
        }
        status.setText("Saved " + saved + (saved == 1 ? " sign" : " signs") + " to " + dir.getAbsolutePath()
                + (failed.isEmpty() ? ". Print them at 100% size." : ". Could not save: " + String.join(", ", failed)));
    }

    private static Label label(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        return l;
    }

    private static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }
}

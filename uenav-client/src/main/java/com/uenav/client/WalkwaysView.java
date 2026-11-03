package com.uenav.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;

/** The Walkways page: the links between places that routes are calculated over. */
public class WalkwaysView extends ManagePage<AdminModels.Edge> {

    private static final List<String> TYPES = List.of("WALK", "STAIRS", "ELEVATOR");

    private final AdminApi api;
    private List<AdminModels.Place> places = List.of();
    private final Map<Integer, String> placeLabels = new HashMap<>();

    public WalkwaysView(AdminApi api, Consumer<ApiException> onUnauthorized) {
        super("Walkways", "Manage the walkways, stairs and elevators that join places.", "walkway",
                "+ Add walkway", "This walkway is still used by other records.", onUnauthorized);
        this.api = api;
        init();
    }

    private static String labelOf(AdminModels.Place p) {
        return p.name() + "  #" + p.id();
    }

    @Override
    protected List<AdminModels.Edge> load() throws ApiException {
        List<AdminModels.Place> nodes = new ArrayList<>(api.nodes());
        nodes.sort(Comparator.comparing(AdminModels.Place::name, String.CASE_INSENSITIVE_ORDER));
        List<AdminModels.Edge> edges = api.edges();
        Map<Integer, String> labels = new HashMap<>();
        for (AdminModels.Place p : nodes) {
            labels.put(p.id(), labelOf(p));
        }
        places = nodes;
        placeLabels.clear();
        placeLabels.putAll(labels);
        return edges;
    }

    private String place(int id) {
        String s = placeLabels.get(id);
        return s == null ? "#" + id : s;
    }

    @Override
    protected void configureColumns() {
        table.getColumns().add(textColumn("From", 250, e -> place(e.fromNodeId())));
        table.getColumns().add(textColumn("To", 250, e -> place(e.toNodeId())));
        TableColumn<AdminModels.Edge, Double> distance = new TableColumn<>("Distance (m)");
        distance.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().distanceM()));
        distance.setPrefWidth(120);
        table.getColumns().add(distance);
        table.getColumns().add(textColumn("Type", 100, AdminModels.Edge::edgeType));
        table.getColumns().add(textColumn("Accessible", 100, e -> e.accessible() ? "Yes" : "No"));
    }

    @Override
    protected String searchText(AdminModels.Edge e) {
        return place(e.fromNodeId()) + " " + place(e.toNodeId()) + " " + e.edgeType();
    }

    @Override
    protected String describe(AdminModels.Edge e) {
        return place(e.fromNodeId()) + " to " + place(e.toNodeId());
    }

    @Override
    protected void deleteRow(AdminModels.Edge e) throws ApiException {
        api.deleteEdge(e.id());
    }

    private ComboBox<Option<AdminModels.Place>> placeBox(Integer selectedId) {
        List<Option<AdminModels.Place>> options = new ArrayList<>();
        for (AdminModels.Place p : places) {
            options.add(new Option<>(p, labelOf(p)));
        }
        ComboBox<Option<AdminModels.Place>> box = new ComboBox<>(FXCollections.observableArrayList(options));
        box.setPromptText("Choose a place");
        if (selectedId != null) {
            for (Option<AdminModels.Place> o : options) {
                if (o.value().id() == selectedId) {
                    box.getSelectionModel().select(o);
                }
            }
        }
        return box;
    }

    @Override
    protected void openForm(AdminModels.Edge existing) {
        ComboBox<Option<AdminModels.Place>> from = placeBox(existing == null ? null : existing.fromNodeId());
        ComboBox<Option<AdminModels.Place>> to = placeBox(existing == null ? null : existing.toNodeId());
        TextField distance = new TextField(existing == null ? "" : String.valueOf(existing.distanceM()));
        distance.setPromptText("e.g. 12.5");
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(TYPES));
        type.setValue(existing == null ? "WALK" : existing.edgeType());
        CheckBox accessible = new CheckBox("Wheelchair accessible");
        accessible.setSelected(existing == null || existing.accessible());

        showForm(existing == null ? "Add walkway" : "Edit walkway",
                new String[] {"From *", "To *", "Distance (m) *", "Type *", ""},
                new Region[] {from, to, distance, type, accessible},
                "A walkway can be walked in both directions. Use STAIRS or ELEVATOR for links between floors. "
                        + "The elevator route only uses accessible walkways.",
                () -> {
                    if (places.size() < 2) {
                        throw new IllegalArgumentException("Add at least two places first (Places page).");
                    }
                    Option<AdminModels.Place> f = from.getValue();
                    Option<AdminModels.Place> t = to.getValue();
                    if (f == null || t == null) {
                        throw new IllegalArgumentException("Choose both places.");
                    }
                    if (f.value().id() == t.value().id()) {
                        throw new IllegalArgumentException("A walkway must join two different places.");
                    }
                    double d;
                    try {
                        d = Double.parseDouble(distance.getText().trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Distance must be a number, like 12.5.");
                    }
                    if (Double.isNaN(d) || Double.isInfinite(d) || d <= 0) {
                        throw new IllegalArgumentException("Distance must be greater than 0.");
                    }
                    AdminModels.EdgeForm form = new AdminModels.EdgeForm(f.value().id(), t.value().id(), d,
                            type.getValue(), accessible.isSelected());
                    return () -> {
                        if (existing == null) {
                            api.createEdge(form);
                        } else {
                            api.updateEdge(existing.id(), form);
                        }
                    };
                });
    }
}

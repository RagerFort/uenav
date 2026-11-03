package com.uenav.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;

/** The Floors page. Each floor belongs to one building. */
public class FloorsView extends ManagePage<AdminModels.Floor> {

    private final AdminApi api;
    private List<AdminModels.Building> buildings = List.of();
    private final Map<Integer, String> buildingNames = new HashMap<>();

    public FloorsView(AdminApi api, Consumer<ApiException> onUnauthorized) {
        super("Floors", "Manage the floors of each building.", "floor", "+ Add floor",
                "This floor still has places on it. Move or delete those first.", onUnauthorized);
        this.api = api;
        init();
    }

    @Override
    protected List<AdminModels.Floor> load() throws ApiException {
        List<AdminModels.Building> b = api.buildings();
        Map<Integer, String> names = new HashMap<>();
        for (AdminModels.Building x : b) {
            names.put(x.id(), x.name());
        }
        List<AdminModels.Floor> floors = api.floors();
        buildings = b;
        buildingNames.clear();
        buildingNames.putAll(names);
        return floors;
    }

    private String buildingOf(AdminModels.Floor f) {
        String n = buildingNames.get(f.buildingId());
        return n == null ? "#" + f.buildingId() : n;
    }

    @Override
    protected void configureColumns() {
        table.getColumns().add(textColumn("Building", 280, this::buildingOf));
        TableColumn<AdminModels.Floor, Integer> number = new TableColumn<>("Floor number");
        number.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().floorNumber()));
        number.setPrefWidth(130);
        table.getColumns().add(number);
        table.getColumns().add(textColumn("Floor name", 260, AdminModels.Floor::floorName));
    }

    @Override
    protected String searchText(AdminModels.Floor f) {
        return buildingOf(f) + " " + f.floorName() + " " + f.floorNumber();
    }

    @Override
    protected String describe(AdminModels.Floor f) {
        return buildingOf(f) + ", " + f.floorName();
    }

    @Override
    protected void deleteRow(AdminModels.Floor f) throws ApiException {
        api.deleteFloor(f.id());
    }

    @Override
    protected void openForm(AdminModels.Floor existing) {
        List<Option<AdminModels.Building>> options = new ArrayList<>();
        for (AdminModels.Building b : buildings) {
            options.add(new Option<>(b, b.name()));
        }
        ComboBox<Option<AdminModels.Building>> building = new ComboBox<>(FXCollections.observableArrayList(options));
        building.setPromptText("Choose a building");
        if (existing != null) {
            for (Option<AdminModels.Building> o : options) {
                if (o.value().id() == existing.buildingId()) {
                    building.getSelectionModel().select(o);
                }
            }
        }
        TextField number = new TextField(existing == null ? "1" : String.valueOf(existing.floorNumber()));
        TextField name = new TextField(existing == null ? "" : existing.floorName());
        name.setPromptText("e.g. 1st floor");

        showForm(existing == null ? "Add floor" : "Edit floor",
                new String[] {"Building *", "Floor number *", "Floor name *"},
                new Region[] {building, number, name},
                "Floor number is used to order floors (use 0 or a negative number for ground or basement levels).",
                () -> {
                    Option<AdminModels.Building> sel = building.getValue();
                    if (sel == null) {
                        throw new IllegalArgumentException(buildings.isEmpty()
                                ? "Add a building first (Buildings page)." : "Choose a building.");
                    }
                    String fn = name.getText().trim();
                    if (fn.isEmpty()) {
                        throw new IllegalArgumentException("Floor name is required.");
                    }
                    int num;
                    try {
                        num = Integer.parseInt(number.getText().trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Floor number must be a whole number.");
                    }
                    AdminModels.FloorForm form = new AdminModels.FloorForm(sel.value().id(), num, fn);
                    return () -> {
                        if (existing == null) {
                            api.createFloor(form);
                        } else {
                            api.updateFloor(existing.id(), form);
                        }
                    };
                });
    }
}

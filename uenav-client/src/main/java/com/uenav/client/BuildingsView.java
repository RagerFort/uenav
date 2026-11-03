package com.uenav.client;

import java.util.List;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;

/** The Buildings page. Floors are managed on the Floors page. */
public class BuildingsView extends ManagePage<AdminModels.Building> {

    private static final List<String> CATEGORIES = List.of("Building", "Gate", "Facility", "Office", "Outdoor area");

    private final AdminApi api;

    public BuildingsView(AdminApi api, Consumer<ApiException> onUnauthorized) {
        super("Buildings", "Manage campus buildings. Add their floors on the Floors page.", "building",
                "+ Add building", "This building still has floors or places. Remove those first.", onUnauthorized);
        this.api = api;
        init();
    }

    @Override
    protected List<AdminModels.Building> load() throws ApiException {
        return api.buildings();
    }

    @Override
    protected void configureColumns() {
        table.getColumns().add(textColumn("Name", 280, AdminModels.Building::name));
        table.getColumns().add(textColumn("Category", 160, b -> b.category() == null ? "" : b.category()));
        table.getColumns().add(textColumn("Description", 380, b -> b.description() == null ? "" : b.description()));
    }

    @Override
    protected String searchText(AdminModels.Building b) {
        return b.name() + " " + (b.category() == null ? "" : b.category()) + " "
                + (b.description() == null ? "" : b.description());
    }

    @Override
    protected String describe(AdminModels.Building b) {
        return b.name();
    }

    @Override
    protected void deleteRow(AdminModels.Building b) throws ApiException {
        api.deleteBuilding(b.id());
    }

    @Override
    protected void openForm(AdminModels.Building existing) {
        TextField name = new TextField(existing == null ? "" : existing.name());
        name.setPromptText("e.g. College of Engineering Building");
        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        category.setEditable(true);
        category.setValue(existing == null || existing.category() == null ? "Building" : existing.category());
        TextField description = new TextField(existing == null || existing.description() == null ? "" : existing.description());

        showForm(existing == null ? "Add building" : "Edit building",
                new String[] {"Name *", "Category *", "Description"},
                new Region[] {name, category, description}, null,
                () -> {
                    String n = name.getText().trim();
                    String c = category.getEditor().getText().trim();
                    if (n.isEmpty() || c.isEmpty()) {
                        throw new IllegalArgumentException("Name and Category are required.");
                    }
                    String d = description.getText().trim();
                    AdminModels.BuildingForm form = new AdminModels.BuildingForm(n, c, d.isEmpty() ? null : d);
                    return () -> {
                        if (existing == null) {
                            api.createBuilding(form);
                        } else {
                            api.updateBuilding(existing.id(), form);
                        }
                    };
                });
    }
}

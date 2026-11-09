package com.uenav.mobile;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The Settings tab (it replaced the Admin tab in the bottom bar): theme (Same as phone / Light / Dark), Avoid stairs,
 * which map the phone uses, and a small "Staff sign in" row that opens the admin screens.
 */
final class SettingsScreen {

    interface Host {
        /** The theme changed: switch stylesheets and redraw. */
        void themeChanged();

        /** Opens the admin sign-in (staff only). */
        void openAdmin();

        /** "Built into the app" or "Downloaded from the server", with the map version. */
        String mapInfo();
    }

    private final Settings settings;
    private final Host host;

    SettingsScreen(Settings settings, Host host) {
        this.settings = settings;
        this.host = host;
    }

    Parent view() {
        VBox screen = new VBox(Ui.header("UENav · SETTINGS", "Settings", null, null));
        screen.getStyleClass().add("nv-canvas");

        Optional<Boolean> phone = SystemTheme.phoneIsDark();
        String phoneText = phone.map(d -> "Your phone is in " + (d ? "dark" : "light") + " mode now")
                .orElse("This phone does not tell apps its theme, so Light is used. Choose Dark below for dark.");
        VBox themes = new VBox(8,
                choice("Same as phone", phoneText, settings.theme() == Settings.Theme.SYSTEM, Settings.Theme.SYSTEM),
                choice("Light", "Always light", settings.theme() == Settings.Theme.LIGHT, Settings.Theme.LIGHT),
                choice("Dark", "Always dark, easier on the eyes at night", settings.theme() == Settings.Theme.DARK,
                        Settings.Theme.DARK));

        HBox stairs = new HBox(12, Ui.bubble("accessible", "green", 36),
                Ui.twoLines("Avoid stairs", settings.avoidStairs()
                        ? "On: routes use elevators, ramps and accessible walkways"
                        : "Off: routes may use stairs", "nv-strong"),
                Ui.toggleSwitch(settings.avoidStairs()));
        stairs.setAlignment(Pos.CENTER_LEFT);
        stairs.getStyleClass().add("nv-row");
        stairs.setPadding(new Insets(6, 0, 6, 0));
        stairs.setOnMouseClicked(e -> {
            settings.setAvoidStairs(!settings.avoidStairs());
            refresh(screen);
        });

        HBox map = new HBox(12, Ui.bubble("map", "blue", 36),
                Ui.twoLines("Campus map on this phone", host.mapInfo(), "nv-strong"));
        map.setAlignment(Pos.CENTER_LEFT);

        HBox staff = new HBox(12, Ui.bubble("shield", "grey", 36),
                Ui.twoLines("Staff sign in", "For campus admins: places, reports and the usage log", "nv-strong"),
                Icons.icon("chevron", 18, "nv-icon-muted"));
        staff.setAlignment(Pos.CENTER_LEFT);
        staff.getStyleClass().add("nv-row");
        staff.setPadding(new Insets(6, 0, 6, 0));
        staff.setOnMouseClicked(e -> host.openAdmin());

        VBox body = new VBox(14,
                section("APPEARANCE", themes),
                section("ROUTES", stairs),
                section("MAP", map, Ui.infoLine("Search and directions work without internet.")),
                section("STAFF", staff),
                Ui.label("UENav · Campus Navigator · UE Caloocan", "nv-muted"));
        body.setPadding(new Insets(14, 18, 18, 18));
        screen.getChildren().add(Ui.scroll(body));
        return screen;
    }

    private void refresh(VBox screen) {
        Parent fresh = view();
        if (screen.getParent() instanceof javafx.scene.layout.Pane p) {
            p.getChildren().setAll(fresh);
        }
    }

    private static VBox section(String title, javafx.scene.Node... content) {
        VBox card = Ui.card(content);
        card.setSpacing(10);
        return new VBox(8, Ui.label(title, "nv-label-caps"), card);
    }

    private HBox choice(String title, String detail, boolean on, Settings.Theme value) {
        Region radio = new Region();
        radio.getStyleClass().add("nv-radio");
        if (on) {
            radio.getStyleClass().add("nv-radio-on");
        }
        radio.setMinSize(22, 22);
        radio.setMaxSize(22, 22);
        HBox row = new HBox(12, Ui.twoLines(title, detail, "nv-strong"), radio);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 14, 12, 14));
        row.getStyleClass().add("nv-choice");
        if (on) {
            row.getStyleClass().add("nv-choice-selected");
        }
        row.setOnMouseClicked(e -> {
            settings.setTheme(value);
            host.themeChanged();
        });
        return row;
    }
}

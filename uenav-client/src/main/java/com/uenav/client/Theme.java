package com.uenav.client;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

/**
 * Light or dark look for the admin app. The light stylesheet (uenav.css) is always loaded; dark mode adds
 * uenav-dark.css on top, which only changes the colour variables. The choice is kept in ~/.uenav/admin.properties.
 */
final class Theme {

    private static boolean dark = AdminSettings.darkMode();
    private static final List<Scene> SCENES = new ArrayList<>();

    private Theme() { }

    /** Address of the stylesheet, or null if it is missing (the app still works, just unstyled). */
    static String css() {
        return address("/uenav.css");
    }

    static String darkCss() {
        return address("/uenav-dark.css");
    }

    private static String address(String resource) {
        URL url = Theme.class.getResource(resource);
        return url == null ? null : url.toExternalForm();
    }

    static boolean isDark() {
        return dark;
    }

    /** Switches every open window (and windows opened later) to the dark or light look, and remembers it. */
    static void setDark(boolean on) {
        dark = on;
        AdminSettings.saveDarkMode(on);
        for (Scene s : SCENES) {
            fill(s.getStylesheets());
        }
    }

    /** Gives a window the current look and keeps it updated when the look changes. */
    static void apply(Scene scene) {
        if (!SCENES.contains(scene)) {
            SCENES.add(scene);
        }
        fill(scene.getStylesheets());
    }

    /** Gives a pop-up the current look. */
    static void style(DialogPane pane) {
        fill(pane.getStylesheets());
    }

    private static void fill(ObservableList<String> sheets) {
        String base = css();
        String extra = darkCss();
        sheets.remove(base);
        sheets.remove(extra);
        if (base != null) {
            sheets.add(base);
        }
        if (dark && extra != null) {
            sheets.add(extra);
        }
    }
}

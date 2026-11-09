package com.uenav.mobile;

import java.net.URL;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Small builders shared by the screens, so each screen reads like the Figma frame it copies. */
public final class Ui {

    private Ui() { }

    /** Address of the visitor-screen stylesheet, or null if it is missing. */
    public static String css() {
        URL url = Ui.class.getResource("/uenav-nav.css");
        return url == null ? null : url.toExternalForm();
    }

    /** Address of the dark-mode stylesheet (loaded on top of css()), or null if it is missing. */
    public static String darkCss() {
        URL url = Ui.class.getResource("/uenav-nav-dark.css");
        return url == null ? null : url.toExternalForm();
    }

    /**
     * Address of the phone stylesheet (loaded last on Android and iOS), or null. It drops the soft shadows, which
     * phones redraw on every scroll frame, and uses thin borders instead, so lists scroll smoothly.
     */
    public static String phoneCss() {
        URL url = Ui.class.getResource("/uenav-nav-phone.css");
        return url == null ? null : url.toExternalForm();
    }

    /** An on/off switch drawn like a phone setting. */
    public static StackPane toggleSwitch(boolean on) {
        Region track = new Region();
        track.getStyleClass().add("nv-switch");
        if (on) {
            track.getStyleClass().add("nv-switch-on");
        }
        track.setMinSize(44, 26);
        track.setMaxSize(44, 26);
        Region knob = new Region();
        knob.getStyleClass().add("nv-switch-knob");
        knob.setMinSize(20, 20);
        knob.setMaxSize(20, 20);
        StackPane box = new StackPane(track, knob);
        StackPane.setAlignment(knob, on ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        StackPane.setMargin(knob, new Insets(0, 3, 0, 3));
        box.setMinSize(44, 26);
        box.setMaxSize(44, 26);
        return box;
    }

    public static Label label(String text, String... classes) {
        Label l = new Label(text);
        l.getStyleClass().addAll(classes);
        return l;
    }

    public static Label wrap(String text, String... classes) {
        Label l = label(text, classes);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    /** A soft coloured circle with an icon in it (Figma: place and step icons). */
    public static StackPane bubble(String icon, String tone, double size) {
        StackPane p = new StackPane(Icons.icon(icon, size * 0.46));
        p.getStyleClass().addAll("nv-bubble", "nv-tone-" + tone);
        p.setMinSize(size, size);
        p.setPrefSize(size, size);
        p.setMaxSize(size, size);
        return p;
    }

    public static Button primary(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("nv-primary");
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    public static Button secondary(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("nv-secondary");
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    /** A round icon-only button. */
    public static Button round(String icon, String tooltip, String... extra) {
        Button b = new Button();
        b.setGraphic(Icons.icon(icon, 18));
        b.getStyleClass().add("nv-round");
        b.getStyleClass().addAll(extra);
        b.setAccessibleText(tooltip);
        b.setFocusTraversable(false);
        return b;
    }

    public static VBox card(Node... children) {
        VBox c = new VBox(children);
        c.getStyleClass().add("nv-card");
        c.setPadding(new Insets(14, 16, 14, 16));
        return c;
    }

    public static Region sep() {
        Region r = new Region();
        r.getStyleClass().add("nv-sep");
        return r;
    }

    public static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    /** A small rounded label. It never shrinks into "..." (the row around it gives way instead). */
    public static Label chip(String text, String tone) {
        Label l = label(text, "nv-chip", "nv-chip-" + tone);
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    /** Title + subtitle stacked, the subtitle muted. */
    public static VBox twoLines(String title, String subtitle, String titleClass) {
        Label t = wrap(title, titleClass);
        VBox v = new VBox(2, t);
        if (subtitle != null && !subtitle.isEmpty()) {
            v.getChildren().add(wrap(subtitle, "nv-muted"));
        }
        HBox.setHgrow(v, Priority.ALWAYS);
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    /** Screen header (Figma: "UENav · VISITOR" eyebrow, big title, optional back button and right-side button). */
    public static HBox header(String eyebrow, String title, Runnable back, Node right) {
        VBox text = new VBox(0, label(eyebrow, "nv-eyebrow"), label(title, "nv-title"));
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        if (back != null) {
            Button b = round("back", "Back");
            b.setOnAction(e -> back.run());
            h.getChildren().add(b);
        }
        h.getChildren().addAll(text, grow());
        if (right != null) {
            h.getChildren().add(right);
        }
        h.getStyleClass().add("nv-header");
        h.setPadding(new Insets(14, 18, 12, 18));
        return h;
    }

    /** A vertical scroll area with no visible frame. */
    public static ScrollPane scroll(Node content) {
        ScrollPane s = new ScrollPane(content);
        s.setFitToWidth(true);
        s.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        s.getStyleClass().add("nv-scroll");
        VBox.setVgrow(s, Priority.ALWAYS);
        return s;
    }

    /** A text field inside a rounded box with an icon on the left (Figma: login and search fields). */
    public static HBox fieldBox(String icon, TextField field) {
        field.getStyleClass().add("nv-bare-field");
        HBox.setHgrow(field, Priority.ALWAYS);
        HBox box = new HBox(8, Icons.icon(icon, 18, "nv-icon-muted"), field);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 12, 0, 14));
        box.getStyleClass().add("nv-field-box");
        field.focusedProperty().addListener((o, was, is) -> {
            if (is) {
                box.getStyleClass().add("nv-field-box-focused");
            } else {
                box.getStyleClass().remove("nv-field-box-focused");
            }
        });
        return box;
    }

    /** Small "i" note under a section (Figma: "You'll choose a starting gate next..."). */
    public static HBox infoLine(String text) {
        HBox h = new HBox(8, Icons.icon("info", 14, "nv-icon-muted"), wrap(text, "nv-muted"));
        h.setAlignment(Pos.TOP_LEFT);
        return h;
    }

    /** A "Most searched" row: name, count, and a red bar sized against the largest count. */
    public static VBox bar(String name, long count, long max) {
        Label n = wrap(name, "nv-text");
        Label c = label(String.valueOf(count), "nv-count");
        HBox head = new HBox(8, n, grow(), c);
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
        return new VBox(6, head, barBox);
    }
}

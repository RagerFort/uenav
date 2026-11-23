package com.uenav.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The admin window after login (Figma: desktop admin dashboard): side menu, top bar, and the page in the middle.
 * The PC app is for admins only (proposal: Windows = admin app); visitors use the phone app.
 * Undo in the top bar (or Ctrl+Z) takes back the last change made on any page of this sign-in (EditHistory).
 */
public class AdminShell extends BorderPane {

    private static final KeyCombination UNDO_KEY = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);

    private final AdminApi api;
    private final AdminApi.LoginInfo info;
    private final Consumer<String> onSignOut;
    private final StackPane content = new StackPane();
    private final Label crumbArea = new Label();
    private final Label crumbPage = new Label();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Label reportsBadge = new Label();
    private final EditHistory history = new EditHistory();
    private final Label undoNote = new Label();
    private final PauseTransition undoNoteTimer = new PauseTransition(Duration.seconds(8));
    private Node currentPage;
    private String currentKey;
    private boolean signedOut;

    public AdminShell(AdminApi api, AdminApi.LoginInfo info, Consumer<String> onSignOut) {
        this.api = api;
        this.info = info;
        this.onSignOut = onSignOut;
        getStyleClass().add("canvas");
        setLeft(buildSidebar());
        VBox.setVgrow(content, Priority.ALWAYS);
        setCenter(new VBox(buildTopBar(), content));
        // in a text box Ctrl+Z undoes the typing; the Map editor handles its own Ctrl+Z first
        addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (UNDO_KEY.match(e) && !(e.getTarget() instanceof TextInputControl)) {
                e.consume();
                undo();
            }
        });
        show("dashboard");
    }

    private VBox buildSidebar() {
        StackPane logo = new StackPane(Icons.icon("navigation", 20, "nv-icon-white"));
        logo.getStyleClass().add("logo-box");
        logo.setMinSize(36, 36);
        logo.setMaxSize(36, 36);
        Label name = new Label("UENav");
        name.getStyleClass().add("brand-name");
        Label campus = new Label("UE Caloocan");
        campus.getStyleClass().add("muted");
        campus.setStyle("-fx-font-size: 11px;");
        HBox brand = new HBox(10, logo, new VBox(0, name, campus));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(0, 0, 14, 4));

        VBox nav = new VBox(4, brand, section("ADMIN"),
                navItem("dashboard", "Dashboard", "shield"),
                section("ADMINISTRATION"),
                navItem("editor", "Map editor", "map"),
                navItem("places", "Places", "pin"),
                navItem("walkways", "Walkways", "navigation"),
                navItem("buildings", "Buildings", "building"),
                navItem("floors", "Floors", "stairs"),
                navItem("reports", "Route reports", "flag"),
                section("TOOLS"),
                navItem("qr", "QR codes", "qr"));
        reportsBadge.getStyleClass().add("nav-badge");
        Button reports = navButtons.get("reports");
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox inner = new HBox(10, Icons.icon("flag", 18), new Label("Route reports"), grow, reportsBadge);
        inner.setAlignment(Pos.CENTER_LEFT);
        reports.setText(null);
        reports.setGraphic(inner);
        inner.prefWidthProperty().bind(reports.widthProperty().subtract(26));

        Label cardTitle = new Label("CAMPUS NAVIGATOR");
        cardTitle.getStyleClass().add("field-label");
        Label cardName = new Label("UE Caloocan");
        cardName.getStyleClass().add("card-title");
        Label cardText = new Label("Outdoor places & indoor rooms");
        cardText.getStyleClass().add("muted");
        cardText.setWrapText(true);
        VBox infoCard = new VBox(2, cardTitle, cardName, cardText);
        infoCard.setPadding(new Insets(10, 12, 12, 12));
        infoCard.getStyleClass().add("side-card");

        Button bottom = new Button("Log out");
        bottom.setGraphic(Icons.icon("logout", 18));
        bottom.setOnAction(e -> {
            Thread t = new Thread(api::logout);
            t.setDaemon(true);
            t.start();
            signOut(null);
        });
        bottom.getStyleClass().add("nav-item");
        bottom.setGraphicTextGap(10);
        bottom.setMaxWidth(Double.MAX_VALUE);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox side = new VBox(8, nav, spacer, infoCard, bottom);
        side.getStyleClass().add("sidebar");
        side.setPadding(new Insets(22, 14, 14, 14));
        side.setPrefWidth(232);
        side.setMinWidth(232);
        return side;
    }

    private static Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("nav-section");
        l.setPadding(new Insets(14, 0, 4, 4));
        return l;
    }

    private Button navItem(String key, String label, String icon) {
        Button b = new Button(label);
        b.setGraphic(Icons.icon(icon, 18));
        b.setGraphicTextGap(10);
        b.getStyleClass().add("nav-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> show(key));
        navButtons.put(key, b);
        return b;
    }

    private HBox buildTopBar() {
        crumbArea.getStyleClass().add("muted");
        Label slash = new Label("/");
        slash.getStyleClass().add("muted");
        crumbPage.getStyleClass().add("crumb-page");
        HBox crumb = new HBox(10, crumbArea, slash, crumbPage);
        crumb.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label initials = new Label(initialsOf(info.fullName()));
        initials.getStyleClass().add("avatar-text");
        StackPane avatar = new StackPane(initials);
        avatar.getStyleClass().add("avatar");
        avatar.setMinSize(36, 36);
        avatar.setMaxSize(36, 36);
        Label who = new Label(info.fullName());
        who.getStyleClass().add("card-title");
        Label where = new Label("UE Caloocan");
        where.getStyleClass().add("muted");
        where.setStyle("-fx-font-size: 11px;");
        Button theme = new Button();
        theme.getStyleClass().add("icon-button");
        Runnable showTheme = () -> {
            theme.setGraphic(Icons.icon(Theme.isDark() ? "sun" : "moon", 16));
            String tip = Theme.isDark() ? "Switch to light mode" : "Switch to dark mode";
            theme.setTooltip(new javafx.scene.control.Tooltip(tip));
            theme.setAccessibleText(tip);
        };
        showTheme.run();
        theme.setOnAction(e -> {
            Theme.setDark(!Theme.isDark());
            showTheme.run();
        });
        Button undo = new Button("Undo");
        undo.setGraphic(Icons.icon("undo", 14));
        undo.getStyleClass().add("small-button");
        undo.setMinWidth(Region.USE_PREF_SIZE);
        undo.setFocusTraversable(false);
        undo.disableProperty().bind(history.canUndoProperty().not());
        Tooltip undoTip = new Tooltip();
        undoTip.textProperty().bind(history.tipProperty());
        undo.setTooltip(undoTip);
        undo.setOnAction(e -> undo());
        undoNote.getStyleClass().add("muted");
        undoNote.setWrapText(true);
        undoNote.setMaxWidth(420);
        undoNoteTimer.setOnFinished(e -> undoNote.setText(""));
        HBox bar = new HBox(12, crumb, spacer, undoNote, undo, theme, new VBox(0, who, where), avatar);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("topbar");
        bar.setPadding(new Insets(14, 28, 14, 28));
        return bar;
    }

    static String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "AD";
        }
        String[] parts = fullName.trim().split("\\s+");
        String s = parts.length == 1
                ? parts[0].substring(0, Math.min(2, parts[0].length()))
                : parts[0].substring(0, 1) + parts[1].substring(0, 1);
        return s.toUpperCase();
    }

    /** Opens the Places page, then runs an action on it once it has loaded. */
    private void openPlaces(Consumer<PlacesView> action) {
        PlacesView page = new PlacesView(api, history, e -> expired());
        setPage("places", page, "Admin", "Places");
        if (action != null) {
            page.afterLoad(() -> action.accept(page));
        }
    }

    private void show(String key) {
        switch (key) {
            case "places" -> openPlaces(null);
            case "editor" -> setPage(key, new MapEditorView(api, history, e -> expired()), "Admin", "Map editor");
            case "walkways" -> setPage(key, new WalkwaysView(api, history, e -> expired()), "Admin", "Walkways");
            case "buildings" -> setPage(key, new BuildingsView(api, history, e -> expired()), "Admin", "Buildings");
            case "floors" -> setPage(key, new FloorsView(api, history, e -> expired()), "Admin", "Floors");
            case "reports" -> setPage(key, new ReportsView(api, e -> expired()), "Admin", "Route reports");
            case "qr" -> setPage(key, new QrCodesView(api, e -> expired()), "Tools", "QR codes");
            default -> setPage("dashboard", new DashboardView(api, e -> expired(), () -> show("reports"),
                    this::onDashboardPlace), "Admin", "Dashboard");
        }
        refreshBadge();
    }

    private void onDashboardPlace(DashboardView.PlaceAction action) {
        switch (action.kind()) {
            case ADD -> openPlaces(PlacesView::openAdd);
            case EDIT -> openPlaces(p -> p.openEdit(action.id()));
            case DELETE -> openPlaces(p -> p.askDelete(action.id()));
            case VIEW_ALL -> openPlaces(action.search() == null ? null : p -> p.setSearch(action.search()));
        }
    }

    private void setPage(String key, Node page, String area, String title) {
        crumbArea.setText(area);
        crumbPage.setText(title);
        content.getChildren().setAll(page);
        currentPage = page;
        currentKey = key;
        highlight(key);
    }

    /**
     * Takes back the last change, on whichever page it was made: the server is asked to put back what was there
     * before, then the page on screen loads again. The Map editor does this itself, to show the result on its map.
     */
    void undo() {
        if (currentPage instanceof MapEditorView editor) {
            editor.undo();
            return;
        }
        if (history.isBusy()) {
            return;
        }
        EditHistory.Entry e = history.pop();
        if (e == null) {
            note("Nothing to undo.", false);
            return;
        }
        history.setBusy(true);
        Integer before = e.selectNode() == null ? null : history.node(e.selectNode());
        Async.run(() -> {
            e.step().undo(history);
            return before == null ? null : history.node(e.selectNode());
        }, now -> {
            history.setBusy(false);
            String back = now != null && !now.equals(before) ? " It came back as place #" + now
                    + ": reprint its QR sign if it had one." : "";
            note("Undid the " + e.label() + "." + back, false);
            reloadPage();
        }, ex -> {
            history.setBusy(false);
            if (ex.status() == 401) {
                expired();
                return;
            }
            note("Could not undo the " + e.label() + ": " + ex.getMessage(), true);
            reloadPage();
        });
    }

    /** Shows what an undo did, next to the Undo button, for a few seconds. */
    private void note(String text, boolean error) {
        undoNote.setText(text);
        undoNote.getStyleClass().removeAll("muted", "error-label");
        undoNote.getStyleClass().add(error ? "error-label" : "muted");
        undoNoteTimer.playFromStart();
    }

    private void reloadPage() {
        if (currentPage instanceof EditHistory.Page page) {
            page.reloadAfterUndo();
        } else if (currentKey != null) {
            show(currentKey);
        }
    }

    private void highlight(String key) {
        for (Button b : navButtons.values()) {
            b.getStyleClass().remove("nav-item-active");
        }
        Button active = navButtons.get(key);
        if (active != null) {
            active.getStyleClass().add("nav-item-active");
        }
    }

    /** Number of new wrong-route reports next to "Route reports" in the menu. */
    private void refreshBadge() {
        Async.run(api::usageSummary, s -> {
            long n = s.openReports();
            reportsBadge.setText(n > 0 ? String.valueOf(n) : "");
            reportsBadge.setVisible(n > 0);
        }, e -> reportsBadge.setVisible(false));
    }

    private void expired() {
        signOut("Your session ended. Please sign in again.");
    }

    private void signOut(String message) {
        if (signedOut) {
            return;
        }
        signedOut = true;
        onSignOut.accept(message);
    }
}

package com.uenav.mobile;

import com.uenav.core.CampusData;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * The phone's Admin tab (Figma: 01 Login and 02 Dashboard). It talks to the server, so it needs a connection;
 * the visitor screens do not. The full admin tools (walkways, buildings, floors, CSV export) stay on the PC (D7).
 */
final class AdminScreens {

    /** What the screens need from the app around them. */
    interface Host {
        void show(Parent screen);

        void continueAsVisitor();

        /** e.g. "3 entries waiting to be sent". */
        String syncStatus();

        /** Sends waiting entries, then runs the callback on the screen thread with a message. */
        void syncNow(java.util.function.Consumer<String> done);

        void mapUpdated(CampusData data);
    }

    private static final List<String> TYPES = List.of("BUILDING_ENTRANCE", "OFFICE", "ROOM", "GATE", "FACILITY",
            "JUNCTION", "STAIRS", "ELEVATOR");

    /** A choice in a drop-down list. */
    private record Option(Integer id, String text) {
        @Override
        public String toString() {
            return text;
        }
    }

    private final Settings settings;
    private final CampusStore store;
    private final Host host;
    private MobileAdminApi api;

    AdminScreens(Settings settings, CampusStore store, Host host) {
        this.settings = settings;
        this.store = store;
        this.host = host;
    }

    /** The login screen, or the dashboard if already signed in. */
    Parent open() {
        return api != null && api.signedIn() ? dashboard() : login(null);
    }

    // ---------------------------------------------------------------- login

    private Parent login(String message) {
        StackPane hero = hero();

        TextField user = new TextField();
        user.setPromptText("admin");
        PasswordField pass = new PasswordField();
        pass.setPromptText("Password");
        Button signIn = Ui.primary("Sign in");
        signIn.setDefaultButton(true);
        Label error = Ui.wrap(message == null ? "" : message, "nv-error");

        Runnable submit = () -> {
            String u = user.getText().trim();
            String p = pass.getText();
            if (u.isEmpty() || p.isEmpty()) {
                error.setText("Enter your username and password.");
                return;
            }
            if (settings.serverUrl().isEmpty()) {
                error.setText("Set the server address below first.");
                return;
            }
            signIn.setDisable(true);
            error.setText("");
            MobileAdminApi attempt = new MobileAdminApi(settings.serverUrl());
            Background.run(() -> {
                attempt.login(u, p);
                return attempt;
            }, ok -> {
                api = ok;
                host.show(dashboard());
            }, e -> {
                signIn.setDisable(false);
                pass.clear();
                error.setText(e.getMessage());
            });
        };
        signIn.setOnAction(e -> submit.run());
        pass.setOnAction(e -> submit.run());
        user.setOnAction(e -> pass.requestFocus());

        HBox orLine = new HBox(10, line(), Ui.label("or", "nv-muted"), line());
        orLine.setAlignment(Pos.CENTER);
        Button visitor = Ui.secondary("Continue as visitor");
        visitor.setOnAction(e -> host.continueAsVisitor());

        VBox body = new VBox(8,
                Ui.label("Admin sign in", "nv-h2"),
                Ui.wrap("Manage places, routes, and the usage log.", "nv-muted"),
                gap(8), Ui.label("USERNAME", "nv-label-caps"), Ui.fieldBox("person", user),
                gap(4), Ui.label("PASSWORD", "nv-label-caps"), Ui.fieldBox("lock", pass),
                gap(8), signIn, error, orLine, visitor, gap(10), serverCard());
        body.setPadding(new Insets(22, 20, 24, 20));

        VBox page = new VBox(hero, body);
        page.getStyleClass().add("nv-canvas");
        page.setStyle("-fx-background-color: white;");
        return Ui.scroll(page);
    }

    private static Region gap(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        return r;
    }

    private static Region line() {
        Region r = Ui.sep();
        HBox.setHgrow(r, Priority.ALWAYS);
        r.setMaxWidth(Double.MAX_VALUE);
        return r;
    }

    private static StackPane hero() {
        Circle big = new Circle(110);
        big.getStyleClass().add("nv-hero-circle");
        StackPane.setAlignment(big, Pos.TOP_RIGHT);
        StackPane.setMargin(big, new Insets(-70, -60, 0, 0));
        Circle small = new Circle(70);
        small.getStyleClass().add("nv-hero-circle");
        StackPane.setAlignment(small, Pos.BOTTOM_LEFT);
        StackPane.setMargin(small, new Insets(0, 0, -40, -40));

        StackPane logo = new StackPane(Icons.icon("pin", 26, "nv-icon-brand"));
        logo.getStyleClass().add("nv-hero-logo");
        logo.setMinSize(56, 56);
        logo.setMaxSize(56, 56);
        VBox text = new VBox(6, logo, gap(10), Ui.label("UENav", "nv-hero-title"),
                Ui.label("Campus Navigator · UE Caloocan", "nv-hero-sub"));
        text.setAlignment(Pos.BOTTOM_LEFT);
        text.setPadding(new Insets(0, 20, 26, 20));
        StackPane hero = new StackPane(big, small, text);
        hero.getStyleClass().add("nv-hero");
        hero.setMinHeight(250);
        hero.setPrefHeight(250);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(56);
        clip.setArcHeight(56);
        clip.widthProperty().bind(hero.widthProperty());
        clip.heightProperty().bind(hero.heightProperty().add(28));
        clip.setTranslateY(-28);
        hero.setClip(clip);
        return hero;
    }

    /** Server address, map version and sending status. Shown under the login form. */
    private VBox serverCard() {
        TextField server = new TextField(settings.typedUrl());
        server.setPromptText(settings.builtInUrl().isEmpty() ? "https://your-server-address" : settings.builtInUrl());
        server.getStyleClass().add("nv-input");
        HBox.setHgrow(server, Priority.ALWAYS);
        Button save = new Button("Save");
        save.getStyleClass().add("nv-secondary");
        save.setStyle("-fx-padding: 10 16 10 16;");
        Label status = Ui.wrap(statusText(), "nv-muted");
        Label result = Ui.wrap("", "nv-muted");

        Button update = Ui.secondary("Check for map update");
        Button send = Ui.secondary("Send usage now");
        update.setStyle("-fx-font-size: 12px; -fx-padding: 9 10 9 10;");
        send.setStyle("-fx-font-size: 12px; -fx-padding: 9 10 9 10;");
        HBox.setHgrow(update, Priority.ALWAYS);
        HBox.setHgrow(send, Priority.ALWAYS);

        save.setOnAction(e -> {
            try {
                settings.setServerUrl(server.getText());
                server.setText(settings.serverUrl());
                result.setText(settings.serverUrl().isEmpty() ? "No server address: sending is off."
                        : settings.usingBuiltIn() ? "Using the address built into the app." : "Saved.");
                status.setText(statusText());
            } catch (IllegalArgumentException ex) {
                result.setText(ex.getMessage());
            }
        });
        update.setOnAction(e -> {
            update.setDisable(true);
            result.setText("Checking…");
            String url = settings.serverUrl();
            Background.run(() -> store.checkForUpdate(url), check -> {
                update.setDisable(false);
                result.setText(check.message());
                if (check.updated() != null) {
                    host.mapUpdated(check.updated());
                }
                status.setText(statusText());
            }, ex -> {
                update.setDisable(false);
                result.setText(ex.getMessage());
            });
        });
        send.setOnAction(e -> {
            send.setDisable(true);
            result.setText("Sending…");
            host.syncNow(msg -> {
                send.setDisable(false);
                result.setText(msg);
                status.setText(statusText());
            });
        });

        VBox card = Ui.card(Ui.label("SERVER & SYNC", "nv-label-caps"),
                Ui.wrap("Needed for admin sign-in, map updates and sending usage. Directions work without it. "
                        + "Leave empty to use the address built into the app.", "nv-muted"),
                new HBox(8, server, save), status, new HBox(8, update, send), result);
        card.setSpacing(8);
        card.setStyle("-fx-background-color: #F9FAFB; -fx-effect: null;");
        return card;
    }

    private String statusText() {
        CampusData d = store.current();
        String map = d == null ? "" : "Map data " + d.version() + (store.isDownloaded() ? " (downloaded)" : " (built in)");
        String server = settings.serverUrl().isEmpty() ? "Server: none set"
                : "Server: " + settings.serverUrl() + (settings.usingBuiltIn() ? " (built in)" : "");
        return server + "\n" + map + "\n" + host.syncStatus();
    }

    // ---------------------------------------------------------------- dashboard

    private Parent dashboard() {
        Button out = Ui.round("logout", "Log out");
        out.setOnAction(e -> {
            MobileAdminApi old = api;
            api = null;
            Background.run(() -> {
                old.logout();
                return true;
            }, ok -> { }, ex -> { });
            host.show(login(null));
        });
        VBox screen = new VBox(Ui.header("ADMIN", "Dashboard", null, out));
        screen.getStyleClass().add("nv-canvas");

        Label places = Ui.label("…", "nv-stat");
        Label searches = Ui.label("…", "nv-stat");
        Label reports = Ui.label("…", "nv-stat");
        VBox reportsCard = stat(reports, "Wrong-route reports");
        reportsCard.setStyle("-fx-cursor: hand;");
        reportsCard.setOnMouseClicked(e -> host.show(reportsScreen()));
        HBox stats = new HBox(10, stat(places, "Places"), stat(searches, "Searches"), reportsCard);

        VBox top = new VBox(12);
        VBox recent = new VBox(10);
        VBox placeRows = new VBox();
        Label error = Ui.wrap("", "nv-error");
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        top.getChildren().add(Ui.label("Loading…", "nv-muted"));

        Button add = Ui.primary("+ Add place");
        add.setMaxWidth(Region.USE_PREF_SIZE);
        add.setStyle("-fx-padding: 10 16 10 16; -fx-font-size: 13px;");
        add.setOnAction(e -> host.show(placeForm(null)));
        HBox placesHead = new HBox(Ui.label("Places", "nv-h3"), Ui.grow(), add);
        placesHead.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(14, stats,
                Ui.card(Ui.label("Most searched", "nv-h3"), gap(6), top),
                Ui.card(Ui.label("Recent activity", "nv-h3"), gap(6), recent),
                error, placesHead, placeRows);
        body.setPadding(new Insets(14, 18, 18, 18));
        screen.getChildren().add(Ui.scroll(body));

        record Data(List<MobileAdminApi.Place> places, MobileAdminApi.Summary summary,
                    List<MobileAdminApi.TopPlace> top, List<MobileAdminApi.Event> recent) { }
        MobileAdminApi a = api;
        Background.run(() -> new Data(a.places(), a.summary(), a.mostSearched(100), a.recent(6)), d -> {
            List<MobileAdminApi.Place> searchable = new ArrayList<>();
            for (MobileAdminApi.Place p : d.places()) {
                if (com.uenav.core.SnapshotReader.isSearchable(p.nodeType())) {
                    searchable.add(p);
                }
            }
            places.setText(String.valueOf(searchable.size()));
            searches.setText(String.valueOf(d.summary().searches()));
            reports.setText(String.valueOf(d.summary().openReports()));

            top.getChildren().clear();
            long max = d.top().stream().mapToLong(MobileAdminApi.TopPlace::count).max().orElse(1);
            for (MobileAdminApi.TopPlace t : d.top().subList(0, Math.min(3, d.top().size()))) {
                top.getChildren().add(Ui.bar(t.name(), t.count(), max));
            }
            if (d.top().isEmpty()) {
                top.getChildren().add(Ui.label("No searches yet.", "nv-muted"));
            }
            recent.getChildren().clear();
            Instant now = Instant.now();
            for (MobileAdminApi.Event ev : d.recent()) {
                Label when = Ui.label(ago(ev.createdAt(), now), "nv-muted");
                when.setMinWidth(64);
                HBox row = new HBox(10, when, Ui.wrap(describe(ev), "nv-text"));
                recent.getChildren().add(row);
            }
            if (d.recent().isEmpty()) {
                recent.getChildren().add(Ui.label("No activity yet.", "nv-muted"));
            }
            Map<Integer, Long> counts = new HashMap<>();
            d.top().forEach(t -> counts.put(t.nodeId(), t.count()));
            placeRows.getChildren().clear();
            searchable.sort((x, y) -> Long.compare(counts.getOrDefault(y.id(), 0L), counts.getOrDefault(x.id(), 0L)));
            for (MobileAdminApi.Place p : searchable) {
                placeRows.getChildren().add(placeRow(p, counts.getOrDefault(p.id(), 0L)));
            }
        }, e -> {
            if (e instanceof MobileAdminApi.Problem p && p.status() == 401) {
                api = null;
                host.show(login("Your session ended. Please sign in again."));
                return;
            }
            places.setText("?");
            searches.setText("?");
            reports.setText("?");
            top.getChildren().clear();
            error.setText(e.getMessage());
        });
        return screen;
    }

    private static VBox stat(Label number, String caption) {
        VBox card = Ui.card(number, Ui.wrap(caption, "nv-muted"));
        card.setSpacing(2);
        card.setPadding(new Insets(12, 12, 12, 12));
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefWidth(100);
        return card;
    }

    private HBox placeRow(MobileAdminApi.Place p, long searches) {
        PlaceStyle s = PlaceStyle.of(p.nodeType());
        Button edit = Ui.round("edit", "Edit", "nv-round-small");
        Button del = Ui.round("delete", "Delete", "nv-round-small", "nv-danger");
        HBox actions = new HBox(8, edit, del);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(12, Ui.bubble(s.icon(), s.tone(), 38),
                Ui.twoLines(p.name(), s.label() + " · " + searches + (searches == 1 ? " search" : " searches"),
                        "nv-strong"), actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("nv-card");
        row.setPadding(new Insets(10, 12, 10, 12));
        VBox.setMargin(row, new Insets(0, 0, 10, 0));
        edit.setOnAction(e -> host.show(placeForm(p)));
        del.setOnAction(e -> {
            Button yes = new Button("Delete");
            yes.getStyleClass().add("nv-primary");
            yes.setStyle("-fx-padding: 7 12 7 12; -fx-font-size: 12px;");
            Button no = new Button("Keep");
            no.getStyleClass().add("nv-secondary");
            no.setStyle("-fx-padding: 7 12 7 12; -fx-font-size: 12px;");
            actions.getChildren().setAll(yes, no);
            no.setOnAction(x -> actions.getChildren().setAll(edit, del));
            yes.setOnAction(x -> {
                yes.setDisable(true);
                MobileAdminApi a = api;
                Background.run(() -> {
                    a.deletePlace(p.id());
                    return true;
                }, ok -> host.show(dashboard()), ex -> {
                    actions.getChildren().setAll(edit, del);
                    Label msg = Ui.wrap(ex.getMessage(), "nv-error");
                    ((VBox) row.getChildren().get(1)).getChildren().add(msg);
                });
            });
        });
        return row;
    }

    // ---------------------------------------------------------------- add / edit place

    private Parent placeForm(MobileAdminApi.Place existing) {
        VBox screen = new VBox(Ui.header("ADMIN", existing == null ? "Add place" : "Edit place",
                () -> host.show(dashboard()), null));
        screen.getStyleClass().add("nv-canvas");

        TextField name = input(existing == null ? "" : existing.name(), "e.g. Registrar's Office");
        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll(TYPES);
        if (existing != null && !TYPES.contains(existing.nodeType())) {
            type.getItems().add(existing.nodeType());
        }
        type.setValue(existing == null ? "OFFICE" : existing.nodeType());
        ComboBox<Option> building = new ComboBox<>();
        ComboBox<Option> floor = new ComboBox<>();
        for (ComboBox<?> c : List.of(type, building, floor)) {
            c.getStyleClass().add("nv-combo");
            c.setMaxWidth(Double.MAX_VALUE);
        }
        TextField x = input(existing == null ? "0" : String.valueOf(existing.x()), "0");
        TextField y = input(existing == null ? "0" : String.valueOf(existing.y()), "0");
        HBox.setHgrow(x, Priority.ALWAYS);
        HBox.setHgrow(y, Priority.ALWAYS);
        Label error = Ui.wrap("", "nv-error");
        Button save = Ui.primary(existing == null ? "Add place" : "Save changes");
        save.setDisable(true);

        List<MobileAdminApi.Floor> allFloors = new ArrayList<>();
        Runnable fillFloors = () -> {
            Option b = building.getValue();
            floor.getItems().clear();
            if (b == null || b.id() == null) {
                floor.getItems().add(new Option(null, "Outdoor (no floor)"));
            } else {
                for (MobileAdminApi.Floor f : allFloors) {
                    if (f.buildingId() == b.id()) {
                        floor.getItems().add(new Option(f.id(), f.floorName()));
                    }
                }
            }
            Integer want = existing == null ? null : existing.floorId();
            floor.setValue(floor.getItems().stream().filter(o -> Objects.equals(o.id(), want)).findFirst()
                    .orElse(floor.getItems().isEmpty() ? null : floor.getItems().get(0)));
        };
        building.valueProperty().addListener((o, a, b) -> fillFloors.run());

        MobileAdminApi a = api;
        record Lists(List<MobileAdminApi.Building> buildings, List<MobileAdminApi.Floor> floors) { }
        Background.run(() -> new Lists(a.buildings(), a.floors()), lists -> {
            allFloors.addAll(lists.floors());
            building.getItems().add(new Option(null, "Outdoor (no building)"));
            for (MobileAdminApi.Building b : lists.buildings()) {
                building.getItems().add(new Option(b.id(), b.name()));
            }
            Integer want = existing == null ? null : existing.buildingId();
            building.setValue(building.getItems().stream().filter(o -> Objects.equals(o.id(), want)).findFirst()
                    .orElse(building.getItems().get(0)));
            save.setDisable(false);
        }, e -> error.setText(e.getMessage()));

        save.setOnAction(e -> {
            int px;
            int py;
            try {
                px = Integer.parseInt(x.getText().trim());
                py = Integer.parseInt(y.getText().trim());
            } catch (NumberFormatException ex) {
                error.setText("X and Y must be whole numbers.");
                return;
            }
            if (name.getText().trim().isEmpty()) {
                error.setText("Enter a name.");
                return;
            }
            Integer bId = building.getValue() == null ? null : building.getValue().id();
            Integer fId = floor.getValue() == null ? null : floor.getValue().id();
            if (bId != null && fId == null) {
                error.setText("This building has no floors yet. Add one on the PC first.");
                return;
            }
            save.setDisable(true);
            error.setText("");
            String n = name.getText().trim();
            String t = type.getValue();
            Background.run(() -> {
                a.savePlace(existing == null ? null : existing.id(), bId, fId, n, t, px, py);
                return true;
            }, ok -> host.show(dashboard()), ex -> {
                save.setDisable(false);
                error.setText(ex.getMessage());
            });
        });

        VBox xy = new VBox(4, Ui.label("MAP POSITION (X, Y)", "nv-label-caps"), new HBox(8, x, y),
                Ui.wrap("Pixels on the map picture: X to the right, Y downwards.", "nv-muted"));
        VBox body = new VBox(8,
                Ui.label("NAME", "nv-label-caps"), name,
                gap(4), Ui.label("TYPE", "nv-label-caps"), type,
                gap(4), Ui.label("BUILDING", "nv-label-caps"), building,
                gap(4), Ui.label("FLOOR", "nv-label-caps"), floor,
                gap(4), xy, gap(8), save, error);
        body.setPadding(new Insets(16, 18, 18, 18));
        screen.getChildren().add(Ui.scroll(body));
        return screen;
    }

    private static TextField input(String text, String prompt) {
        TextField f = new TextField(text);
        f.setPromptText(prompt);
        f.getStyleClass().add("nv-input");
        return f;
    }

    // ---------------------------------------------------------------- route reports

    private Parent reportsScreen() {
        VBox screen = new VBox(Ui.header("ADMIN", "Route reports", () -> host.show(dashboard()), null));
        screen.getStyleClass().add("nv-canvas");
        VBox list = new VBox(10, Ui.label("Loading…", "nv-muted"));
        list.setPadding(new Insets(14, 18, 18, 18));
        screen.getChildren().add(Ui.scroll(list));
        MobileAdminApi a = api;
        Background.run(a::reports, rows -> {
            list.getChildren().clear();
            if (rows.isEmpty()) {
                list.getChildren().add(Ui.label("No wrong-route reports.", "nv-muted"));
            }
            Instant now = Instant.now();
            for (MobileAdminApi.Event ev : rows) {
                boolean isNew = "NEW".equals(ev.status());
                Button toggle = new Button(isNew ? "Mark reviewed" : "Mark new");
                toggle.getStyleClass().add("nv-secondary");
                toggle.setStyle("-fx-padding: 7 12 7 12; -fx-font-size: 12px;");
                toggle.setOnAction(e -> {
                    toggle.setDisable(true);
                    Background.run(() -> {
                        a.setReportStatus(ev.id(), isNew ? "REVIEWED" : "NEW");
                        return true;
                    }, ok -> host.show(reportsScreen()), ex -> {
                        toggle.setDisable(false);
                        toggle.setText(ex.getMessage());
                    });
                });
                HBox foot = new HBox(8, Ui.chip(isNew ? "New" : "Reviewed", isNew ? "red" : "grey"),
                        Ui.label(ago(ev.createdAt(), now) + " · " + prefWords(ev.routePref()), "nv-muted"),
                        Ui.grow(), toggle);
                foot.setAlignment(Pos.CENTER_LEFT);
                VBox card = Ui.card(Ui.wrap(ev.fromName() + " → " + ev.toName(), "nv-strong"), foot);
                card.setSpacing(8);
                list.getChildren().add(card);
            }
        }, e -> {
            list.getChildren().setAll(Ui.wrap(e.getMessage(), "nv-error"));
        });
        return screen;
    }

    // ---------------------------------------------------------------- text helpers

    static String describe(MobileAdminApi.Event e) {
        if ("FEEDBACK".equals(e.eventType())) {
            String words = "WRONG_ROUTE".equals(e.feedbackType()) ? "wrong route" : "helpful";
            return "Feedback: " + words + " · " + e.toName();
        }
        return "Route: " + e.fromName() + " → " + e.toName();
    }

    private static String prefWords(String pref) {
        return switch (pref == null ? "ANY" : pref) {
            case "STAIRS" -> "by stairs";
            case "ELEVATOR" -> "by elevator";
            default -> "any way";
        };
    }

    static String ago(String iso, Instant now) {
        if (iso == null) {
            return "";
        }
        try {
            long s = Math.max(0, Duration.between(Instant.parse(iso), now).getSeconds());
            if (s < 60) {
                return "just now";
            }
            if (s < 3600) {
                return (s / 60) + "m ago";
            }
            if (s < 86400) {
                return (s / 3600) + "h ago";
            }
            return (s / 86400) + "d ago";
        } catch (DateTimeParseException e) {
            return iso;
        }
    }
}

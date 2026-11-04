package com.uenav.mobile;

import com.uenav.core.CampusData;
import com.uenav.core.Directions;
import com.uenav.core.Floor;
import com.uenav.core.Node;
import com.uenav.core.PathFinder;
import com.uenav.core.Route;
import com.uenav.core.RoutePreference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * The visitor screens from the Figma file: Explore, Plan a route, Directions (map + steps), Route unavailable and
 * the Map tab. Routes are found on the device with uenav-core, so this works offline (D5).
 * The phone shows it full screen; the PC app shows it inside its window with the wide layout.
 */
public final class Navigator {

    public enum Tab { EXPLORE, MAP }

    private final UsageLog log;
    private final boolean wide;
    private final StackPane view = new StackPane();
    private CampusData data;
    private Consumer<Tab> onTab = t -> { };
    private Tab tab = Tab.EXPLORE;

    private Node start;
    private Node destination;
    private RoutePreference pref = RoutePreference.ANY;
    private Route route;
    private boolean routeChecked;
    private String exploreQuery = "";

    /** @param wide true for the PC window (map and steps side by side, flat headers) */
    public Navigator(CampusData data, UsageLog log, boolean wide) {
        this.data = data;
        this.log = log;
        this.wide = wide;
        view.getStyleClass().add("nv-canvas");
        showExplore();
    }

    public Parent view() {
        return view;
    }

    public Tab tab() {
        return tab;
    }

    public void setOnTabChanged(Consumer<Tab> handler) {
        this.onTab = handler == null ? t -> { } : handler;
    }

    /** Replaces the campus data (for example after a newer copy was downloaded) and goes back to Explore. */
    public void setData(CampusData newData) {
        this.data = newData;
        start = null;
        destination = null;
        route = null;
        routeChecked = false;
        showExplore();
    }

    public void openExplore() {
        showExplore();
    }

    /** Shows the directions if a route was planned, otherwise the campus map. */
    public void openMap() {
        if (destination != null && start != null && routeChecked) {
            showDirections();
        } else {
            showMapHome();
        }
    }

    private void setScreen(Parent screen, Tab t) {
        view.getChildren().setAll(screen);
        if (tab != t) {
            tab = t;
        }
        onTab.accept(t);
    }

    private VBox page(Region header) {
        if (wide) {
            header.getStyleClass().add("nv-header-flat");
            header.setPadding(new Insets(24, 28, 4, 28));
        }
        VBox v = new VBox(header);
        v.getStyleClass().add("nv-canvas");
        return v;
    }

    private Insets bodyPad() {
        return wide ? new Insets(12, 28, 28, 28) : new Insets(14, 18, 18, 18);
    }

    private VBox limitWidth(VBox body) {
        if (wide) {
            body.setMaxWidth(760);
        }
        return body;
    }

    // ---------------------------------------------------------------- Explore

    private void showExplore() {
        VBox screen = page(Ui.header("UENav · VISITOR", "Explore", null, null));

        TextField search = new TextField(exploreQuery);
        search.setPromptText("Search destinations");
        search.setFocusTraversable(false);
        HBox searchBox = Ui.fieldBox("search", search);
        searchBox.getStyleClass().setAll("nv-search");

        VBox rows = new VBox();
        Label count = Ui.label("", "nv-muted");
        Runnable fill = () -> {
            exploreQuery = search.getText();
            List<Node> matches = filter(exploreQuery);
            rows.getChildren().clear();
            count.setText(matches.size() + (matches.size() == 1 ? " place" : " places"));
            for (int i = 0; i < matches.size(); i++) {
                if (i > 0) {
                    rows.getChildren().add(Ui.sep());
                }
                rows.getChildren().add(destinationRow(matches.get(i)));
            }
            if (matches.isEmpty()) {
                Label none = Ui.wrap("No destinations match “" + exploreQuery.trim() + "”.", "nv-muted");
                none.setPadding(new Insets(14, 0, 6, 0));
                rows.getChildren().add(none);
            }
        };
        search.textProperty().addListener((o, a, b) -> fill.run());
        fill.run();

        HBox listHead = new HBox(Ui.label("Destinations", "nv-h3"), Ui.grow(), count);
        listHead.setAlignment(Pos.CENTER_LEFT);
        listHead.setPadding(new Insets(0, 0, 6, 0));
        VBox list = Ui.card(listHead, rows);

        VBox body = limitWidth(new VBox(14,
                Ui.label("Campus Navigator · UE Caloocan", "nv-muted"),
                searchBox,
                new VBox(2, Ui.label("Where are you headed?", "nv-h2"),
                        Ui.wrap("Choose a destination to plan your route.", "nv-muted")),
                list,
                Ui.infoLine("You'll choose a starting gate next. No location access needed.")));
        body.setPadding(bodyPad());
        screen.getChildren().add(Ui.scroll(body));
        setScreen(screen, Tab.EXPLORE);
    }

    private List<Node> filter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Node> out = new ArrayList<>();
        for (Node n : data.places()) {
            String hay = (n.name() + " " + PlaceStyle.subtitle(n, data)).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || hay.contains(q)) {
                out.add(n);
            }
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    private HBox destinationRow(Node n) {
        PlaceStyle s = PlaceStyle.of(n.nodeType());
        HBox row = new HBox(12, Ui.bubble(s.icon(), s.tone(), 36),
                Ui.twoLines(n.name(), PlaceStyle.subtitle(n, data), "nv-strong"),
                Icons.icon("chevron", 18, "nv-icon-muted"));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 0, 10, 0));
        row.getStyleClass().add("nv-row");
        row.setOnMouseClicked(e -> openPlan(n));
        return row;
    }

    // ---------------------------------------------------------------- Plan a route

    /** Starts planning a route to this destination (Figma: Plan a route). */
    public void openPlan(Node dest) {
        if (start != null && start.id() == dest.id()) {
            start = null;
        }
        destination = dest;
        routeChecked = false;
        showPlan(false);
    }

    private void showPlan(boolean showAll) {
        VBox screen = page(Ui.header("UENav · VISITOR", "Plan a route", this::showExplore, null));

        List<Node> gates = new ArrayList<>();
        List<Node> others = new ArrayList<>();
        for (Node n : data.places()) {
            if (n.id() == destination.id()) {
                continue;
            }
            ("GATE".equals(n.nodeType()) ? gates : others).add(n);
        }
        others.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        boolean noGates = gates.isEmpty();
        boolean startIsOther = start != null && !"GATE".equals(start.nodeType());

        Button find = Ui.primary("Find directions");
        find.setDisable(start == null);
        find.setOnAction(e -> findDirections(true));

        VBox choices = new VBox(10);
        List<Node> shown = new ArrayList<>(gates);
        if (showAll || noGates || startIsOther) {
            shown.addAll(others);
        }
        for (Node n : shown) {
            choices.getChildren().add(choice(n, find, showAll));
        }

        VBox section = new VBox(2,
                Ui.label(noGates ? "Choose your starting point" : "Choose your starting gate", "nv-h3"),
                Ui.wrap(noGates ? "Select where you are starting from." : "Select the gate you're starting from.",
                        "nv-muted"));
        VBox body = limitWidth(new VBox(14, routeCard(), section, choices));
        if (!noGates && !showAll && !startIsOther && !others.isEmpty()) {
            Button more = new Button("Start from another place");
            more.getStyleClass().add("nv-link");
            more.setOnAction(e -> showPlan(true));
            body.getChildren().add(more);
        }
        HBox note = new HBox(10, Icons.icon("info", 16, "nv-icon-muted"),
                new VBox(2, Ui.label("No live location needed", "nv-strong"),
                        Ui.wrap("Your starting point is selected manually. You can change it before checking "
                                + "directions.", "nv-muted")));
        note.getStyleClass().add("nv-note");
        note.setPadding(new Insets(12, 14, 12, 14));
        body.getChildren().add(note);
        body.setPadding(bodyPad());

        VBox footer = limitWidth(new VBox(10,
                Ui.wrap("Check whether directions are available for this route.", "nv-muted"), find));
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(wide ? new Insets(0, 28, 24, 28) : new Insets(8, 18, 14, 18));
        screen.getChildren().addAll(Ui.scroll(body), footer);
        setScreen(screen, Tab.MAP);
    }

    private VBox routeCard() {
        PlaceStyle ds = PlaceStyle.of(destination.nodeType());
        HBox from = new HBox(12, Ui.bubble("up", "grey", 34),
                Ui.twoLines(start == null ? "Choose a starting point" : start.name(),
                        start == null ? "Starting point" : "Starting point · selected manually", "nv-strong"));
        from.setAlignment(Pos.CENTER_LEFT);
        HBox to = new HBox(12, Ui.bubble("flag", "red", 34),
                Ui.twoLines(destination.name(), "Destination · " + ds.label(), "nv-strong"));
        to.setAlignment(Pos.CENTER_LEFT);
        VBox card = Ui.card(Ui.label("YOUR ROUTE", "nv-label-caps"), from, Ui.sep(), to);
        card.setSpacing(10);
        return card;
    }

    private HBox choice(Node n, Button find, boolean showAll) {
        PlaceStyle s = PlaceStyle.of(n.nodeType());
        boolean on = start != null && start.id() == n.id();
        Region radio = new Region();
        radio.getStyleClass().add("nv-radio");
        if (on) {
            radio.getStyleClass().add("nv-radio-on");
        }
        radio.setMinSize(22, 22);
        radio.setMaxSize(22, 22);
        HBox row = new HBox(12, Ui.bubble(s.icon(), "GATE".equals(n.nodeType()) ? "blue" : s.tone(), 36),
                Ui.twoLines(n.name(), on ? "Selected starting point" : PlaceStyle.subtitle(n, data), "nv-strong"),
                radio);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 14, 12, 14));
        row.getStyleClass().add("nv-choice");
        if (on) {
            row.getStyleClass().add("nv-choice-selected");
        }
        row.setOnMouseClicked(e -> {
            start = n;
            routeChecked = false;
            showPlan(showAll);
        });
        return row;
    }

    // ---------------------------------------------------------------- Directions

    private void findDirections(boolean logIt) {
        pref = RoutePreference.ANY;
        route = PathFinder.findRoute(data.graph(), start.id(), destination.id(), pref).orElse(null);
        routeChecked = true;
        if (logIt) {
            log.route(start.id(), destination.id(), pref);
        }
        showDirections();
    }

    private void showDirections() {
        if (route == null) {
            showUnavailable();
            return;
        }
        Directions.Summary summary = Directions.summarize(route, data);
        List<Integer> levels = MapView.levelsOf(route, data);

        MapView map = new MapView();
        map.setData(data);
        map.setRoute(route);
        map.setLevel(levels.isEmpty() ? null : levels.get(0));
        StackPane mapStack = mapWithOverlays(map, levels);

        VBox sheet = sheet(summary);
        Parent screen;
        if (wide) {
            Region head = Ui.header("UENav · VISITOR", "Directions", () -> showPlan(false), null);
            mapStack.getStyleClass().add("nv-card");
            clip(mapStack, 16);
            HBox.setHgrow(mapStack, Priority.ALWAYS);
            ScrollPane side = Ui.scroll(sheet);
            topOnShow(side);
            side.setMinWidth(380);
            side.setPrefWidth(400);
            side.setMaxWidth(420);
            VBox sideBox = new VBox(side);
            sideBox.getStyleClass().addAll("nv-sheet", "nv-sheet-wide");
            sideBox.setPadding(new Insets(6, 0, 6, 0));
            HBox content = new HBox(16, mapStack, sideBox);
            content.setPadding(new Insets(12, 28, 24, 28));
            VBox.setVgrow(content, Priority.ALWAYS);
            VBox v = page(head);
            v.getChildren().add(content);
            screen = v;
        } else {
            ScrollPane sc = Ui.scroll(sheet);
            topOnShow(sc);
            VBox sheetBox = new VBox(sc);
            sheetBox.getStyleClass().add("nv-sheet");
            VBox.setVgrow(sheetBox, Priority.ALWAYS);
            VBox.setMargin(sheetBox, new Insets(-26, 0, 0, 0));
            VBox v = new VBox(mapStack, sheetBox);
            v.getStyleClass().add("nv-canvas");
            mapStack.prefHeightProperty().bind(view.heightProperty().multiply(0.54));
            mapStack.setMinHeight(260);
            screen = v;
        }
        setScreen(screen, Tab.MAP);
    }

    /** Keeps a scroll area at the top when it first appears (it would otherwise jump to a focused button). */
    private static void topOnShow(ScrollPane sc) {
        sc.vvalueProperty().addListener(new javafx.beans.value.ChangeListener<Number>() {
            private int left = 3;

            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends Number> o, Number a, Number b) {
                if (left-- > 0 && b.doubleValue() != 0) {
                    sc.setVvalue(0);
                } else if (left <= 0) {
                    sc.vvalueProperty().removeListener(this);
                }
            }
        });
    }

    private static void clip(Region r, double radius) {
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(r.widthProperty());
        clip.heightProperty().bind(r.heightProperty());
        r.setClip(clip);
    }

    /** Map with the From/To card, floor tabs, building chip and zoom buttons on top (Figma: Main Function). */
    private StackPane mapWithOverlays(MapView map, List<Integer> levels) {
        // From / To card
        HBox fromRow = new HBox(10, dot("#2563EB"),
                new VBox(0, Ui.label("FROM", "nv-label-caps"), Ui.label(start.name(), "nv-strong")));
        fromRow.setAlignment(Pos.CENTER_LEFT);
        fromRow.getStyleClass().add("nv-row");
        fromRow.setOnMouseClicked(e -> showPlan(false));
        HBox toRow = new HBox(10, Icons.icon("pin", 14, "nv-icon-brand"),
                new VBox(0, Ui.label("TO", "nv-label-caps"), Ui.label(destination.name(), "nv-strong")));
        toRow.setAlignment(Pos.CENTER_LEFT);
        toRow.getStyleClass().add("nv-row");
        toRow.setOnMouseClicked(e -> showExplore());
        Region line = Ui.sep();
        VBox rows = new VBox(8, fromRow, line, toRow);
        HBox.setHgrow(rows, Priority.ALWAYS);
        Button swap = Ui.round("swap", "Swap start and destination");
        swap.setOnAction(e -> {
            Node s = start;
            start = destination;
            destination = s;
            findDirections(true);
        });
        HBox card = new HBox(10, rows, swap);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("nv-floating");
        card.setPadding(new Insets(12, 12, 12, 16));
        card.setMaxHeight(Region.USE_PREF_SIZE);

        VBox top = new VBox(10, card);
        top.setPadding(new Insets(wide ? 14 : 12, 14, 0, 14));
        top.setPickOnBounds(false);
        top.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(top, Pos.TOP_CENTER);
        if (wide) {
            card.setMaxWidth(440);
            top.setAlignment(Pos.TOP_LEFT);
        }

        // floor tabs + building chip
        double padTop = 140;
        if (levels.size() > 1 || (levels.size() == 1 && levels.get(0) != null)) {
            HBox tabs = new HBox(2);
            tabs.getStyleClass().add("nv-tabs");
            HBox chipBox = new HBox();
            List<Button> buttons = new ArrayList<>();
            for (Integer lv : levels) {
                Button b = new Button(lv == null ? "Campus"
                        : data.floor(lv).map(Floor::shortLabel).orElse("Floor"));
                b.getStyleClass().add("nv-tab");
                b.setFocusTraversable(false);
                buttons.add(b);
                b.setOnAction(e -> {
                    map.setLevel(lv);
                    buttons.forEach(x -> x.getStyleClass().remove("nv-tab-on"));
                    b.getStyleClass().add("nv-tab-on");
                    chipBox.getChildren().setAll(buildingChip(lv));
                });
                tabs.getChildren().add(b);
            }
            buttons.get(0).getStyleClass().add("nv-tab-on");
            chipBox.getChildren().setAll(buildingChip(levels.get(0)));
            tabs.setMaxWidth(Region.USE_PREF_SIZE);
            HBox bar = new HBox(8, tabs, Ui.grow(), chipBox);
            bar.setAlignment(Pos.CENTER_LEFT);
            bar.setPickOnBounds(false);
            if (wide) {
                bar.setMaxWidth(440);
            }
            top.getChildren().add(bar);
            padTop = 190;
        }
        map.setPadding(padTop, wide ? 20 : 40);

        VBox zoom = new VBox(zoomButton("plus", map::zoomIn), Ui.sep(), zoomButton("minus", map::zoomOut));
        zoom.getStyleClass().add("nv-zoom");
        zoom.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(zoom, wide ? Pos.BOTTOM_RIGHT : Pos.CENTER_RIGHT);
        StackPane.setMargin(zoom, wide ? new Insets(0, 14, 14, 0) : new Insets(padTop - 40, 14, 0, 0));

        StackPane stack = new StackPane(map, top, zoom);
        stack.getStyleClass().add("nv-map-bg");
        return stack;
    }

    private Pane buildingChip(Integer level) {
        Optional<Floor> f = data.floor(level);
        if (f.isEmpty()) {
            return new Pane();
        }
        String name = data.building(f.get().buildingId()).map(b -> MapView.shortName(b.name())).orElse("Building");
        HBox chip = new HBox(6, Icons.icon("building", 14, "nv-icon-brand"), Ui.label(name, "nv-strong"));
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("nv-building-chip");
        chip.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return chip;
    }

    private static Button zoomButton(String icon, Runnable action) {
        Button b = new Button();
        b.setGraphic(Icons.icon(icon, 18));
        b.getStyleClass().add("nv-zoom-btn");
        b.setFocusTraversable(false);
        b.setOnAction(e -> action.run());
        return b;
    }

    private static Region dot(String color) {
        Region d = new Region();
        d.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 999;");
        d.setMinSize(10, 10);
        d.setMaxSize(10, 10);
        StackPane wrap = new StackPane(d);
        wrap.setMinWidth(14);
        return wrap;
    }

    /** The bottom sheet: destination, time and distance, stairs/elevator choice, steps and feedback. */
    private VBox sheet(Directions.Summary s) {
        VBox sheet = new VBox(14);
        sheet.setPadding(new Insets(wide ? 18 : 10, 18, 18, 18));
        if (!wide) {
            Region handle = new Region();
            handle.getStyleClass().add("nv-handle");
            handle.setMinSize(40, 5);
            handle.setMaxSize(40, 5);
            HBox h = new HBox(handle);
            h.setAlignment(Pos.CENTER);
            sheet.getChildren().add(h);
        }
        PlaceStyle ds = PlaceStyle.of(destination.nodeType());
        HBox title = new HBox(12, Ui.bubble(ds.icon(), ds.tone(), 44),
                Ui.twoLines(destination.name(), PlaceStyle.subtitle(destination, data), "nv-h3"));
        title.setAlignment(Pos.CENTER_LEFT);

        HBox chips = new HBox(8, Ui.chip("~" + s.minutes() + " min walk", "red"),
                Ui.chip("~" + s.meters() + " m", "grey"),
                Ui.chip(s.areaLabel(), s.indoor() && !s.outdoor() ? "green" : "blue"));
        sheet.getChildren().addAll(title, chips);

        VBox vertical = verticalChoice(s);
        if (vertical != null) {
            sheet.getChildren().add(vertical);
        }

        VBox steps = new VBox(14);
        for (Directions.Step st : s.steps()) {
            steps.getChildren().add(stepRow(st));
        }
        sheet.getChildren().add(steps);
        sheet.getChildren().add(feedbackBox());
        return sheet;
    }

    private VBox verticalChoice(Directions.Summary current) {
        if (!current.usesStairs() && !current.usesElevator()) {
            return null;
        }
        Optional<Route> byStairs = PathFinder.findRoute(data.graph(), start.id(), destination.id(),
                RoutePreference.STAIRS);
        Optional<Route> byElevator = PathFinder.findRoute(data.graph(), start.id(), destination.id(),
                RoutePreference.ELEVATOR);
        boolean elevatorOn = current.usesElevator() && !current.usesStairs();
        HBox options = new HBox(10);
        if (byStairs.isPresent()) {
            Directions.Summary st = Directions.summarize(byStairs.get(), data);
            options.getChildren().add(option("stairs", "Stairs",
                    st.flights() + (st.flights() == 1 ? " flight" : " flights") + " · ~" + st.minutes() + " min",
                    !elevatorOn, () -> choose(RoutePreference.STAIRS, byStairs.get())));
        }
        if (byElevator.isPresent()) {
            Directions.Summary el = Directions.summarize(byElevator.get(), data);
            options.getChildren().add(option("elevator", "Elevator", "Accessible · ~" + el.minutes() + " min",
                    elevatorOn, () -> choose(RoutePreference.ELEVATOR, byElevator.get())));
        }
        if (options.getChildren().isEmpty()) {
            return null;
        }
        return new VBox(8, Ui.label(goesUp() ? "GO UP BY" : "GO DOWN BY", "nv-label-caps"), options);
    }

    private boolean goesUp() {
        int a = data.floor(start.floorId()).map(Floor::floorNumber).orElse(1);
        int b = data.floor(destination.floorId()).map(Floor::floorNumber).orElse(1);
        return b >= a;
    }

    private void choose(RoutePreference p, Route r) {
        pref = p;
        route = r;
        showDirections();
    }

    private static VBox option(String icon, String title, String detail, boolean on, Runnable action) {
        HBox head = new HBox(8, Icons.icon(icon, 16, "nv-icon-muted"), Ui.label(title, "nv-strong"));
        head.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(4, head, Ui.wrap(detail, "nv-muted"));
        box.getStyleClass().add("nv-option");
        if (on) {
            box.getStyleClass().add("nv-option-on");
        }
        box.setPadding(new Insets(10, 12, 10, 12));
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setOnMouseClicked(e -> action.run());
        return box;
    }

    private static HBox stepRow(Directions.Step st) {
        String icon = switch (st.kind()) {
            case START, STRAIGHT -> "up";
            case LEFT -> "back";
            case RIGHT -> "forward";
            case BACK -> "uturn";
            case STAIRS -> "stairs";
            case ELEVATOR -> "elevator";
            case ARRIVE -> "flag";
        };
        HBox row = new HBox(12, Ui.bubble(icon, st.kind() == Directions.StepKind.ARRIVE ? "red" : "grey", 34),
                Ui.twoLines(st.title(), st.detail(), "nv-strong"));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox feedbackBox() {
        Button helpful = Ui.secondary("Helpful");
        Button wrong = Ui.secondary("Wrong route");
        helpful.setFocusTraversable(false);
        wrong.setFocusTraversable(false);
        HBox.setHgrow(helpful, Priority.ALWAYS);
        HBox.setHgrow(wrong, Priority.ALWAYS);
        HBox buttons = new HBox(10, helpful, wrong);
        VBox box = new VBox(8, buttons);
        box.setPadding(new Insets(4, 0, 0, 0));
        Consumer<String> send = type -> {
            log.feedback(start.id(), destination.id(), pref, type);
            HBox thanks = new HBox(8, Icons.icon("check", 18, "nv-icon-green"),
                    Ui.wrap("WRONG_ROUTE".equals(type)
                            ? "Thanks. The route was reported so an admin can check it."
                            : "Thanks for your feedback.", "nv-ok"));
            thanks.setAlignment(Pos.CENTER_LEFT);
            thanks.setPadding(new Insets(10, 0, 4, 0));
            box.getChildren().setAll(thanks);
        };
        helpful.setOnAction(e -> send.accept("HELPFUL"));
        wrong.setOnAction(e -> send.accept("WRONG_ROUTE"));
        return box;
    }

    // ---------------------------------------------------------------- Route unavailable

    private void showUnavailable() {
        VBox screen = page(Ui.header("UENav · VISITOR", "Directions", () -> showPlan(false), null));
        StackPane icon = Ui.bubble("warning", "orange", 56);
        VBox message = Ui.card(icon, Ui.label("Route unavailable", "nv-h2"),
                centred("Directions are not available from " + start.name() + " to " + destination.name() + "."),
                centred("Try a different starting gate, or return to Explore to browse destinations."));
        message.setAlignment(Pos.CENTER);
        message.setSpacing(10);
        message.setPadding(new Insets(24, 20, 24, 20));

        VBox body = limitWidth(new VBox(14, routeCard(), message));
        body.setPadding(bodyPad());

        Button change = Ui.primary("Change starting point");
        change.setOnAction(e -> showPlan(false));
        Button browse = Ui.secondary("Browse destinations");
        browse.setOnAction(e -> showExplore());
        VBox footer = limitWidth(new VBox(10, change, browse));
        footer.setPadding(wide ? new Insets(0, 28, 24, 28) : new Insets(8, 18, 14, 18));
        screen.getChildren().addAll(Ui.scroll(body), footer);
        setScreen(screen, Tab.MAP);
    }

    private static Label centred(String text) {
        Label l = Ui.wrap(text, "nv-muted");
        l.setStyle("-fx-text-alignment: center; -fx-font-size: 13px;");
        l.setAlignment(Pos.CENTER);
        return l;
    }

    // ---------------------------------------------------------------- Map tab with no route yet

    private void showMapHome() {
        VBox screen = page(Ui.header("UENav · VISITOR", "Campus map", null, null));
        MapView map = new MapView();
        map.setData(data);
        map.setOnPlaceClicked(this::openPlan);

        List<Integer> levels = new ArrayList<>();
        levels.add(null);
        data.buildings().forEach(b -> data.floorsOf(b.id()).forEach(f -> levels.add(f.id())));
        HBox tabs = new HBox(2);
        tabs.getStyleClass().add("nv-tabs");
        List<Button> buttons = new ArrayList<>();
        for (Integer lv : levels) {
            String text = lv == null ? "Campus" : data.floor(lv).map(f -> data.building(f.buildingId())
                    .map(b -> MapView.shortName(b.name()) + " " + f.shortLabel()).orElse(f.shortLabel())).orElse("");
            Button b = new Button(text);
            b.getStyleClass().add("nv-tab");
            b.setFocusTraversable(false);
            b.setOnAction(e -> {
                map.setLevel(lv);
                buttons.forEach(x -> x.getStyleClass().remove("nv-tab-on"));
                b.getStyleClass().add("nv-tab-on");
            });
            buttons.add(b);
            tabs.getChildren().add(b);
        }
        buttons.get(0).getStyleClass().add("nv-tab-on");
        ScrollPane tabScroll = new ScrollPane(tabs);
        tabScroll.getStyleClass().add("nv-tabs-scroll");
        tabScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tabScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tabScroll.setFitToHeight(true);
        tabScroll.setPadding(new Insets(6));
        tabScroll.setMaxHeight(Region.USE_PREF_SIZE);
        tabScroll.setMaxWidth(Region.USE_PREF_SIZE);
        tabScroll.setPannable(true);
        StackPane.setAlignment(tabScroll, Pos.TOP_LEFT);
        StackPane.setMargin(tabScroll, new Insets(6, 6, 0, 6));

        HBox hint = new HBox(10, Icons.icon("info", 16, "nv-icon-muted"),
                Ui.wrap("Tap a place on the map, or search in Explore, to plan a route.", "nv-muted"));
        hint.setAlignment(Pos.CENTER_LEFT);
        hint.getStyleClass().add("nv-floating");
        hint.setPadding(new Insets(12, 14, 12, 14));
        hint.setMaxHeight(Region.USE_PREF_SIZE);
        if (wide) {
            hint.setMaxWidth(440);
        }
        StackPane.setAlignment(hint, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hint, new Insets(0, 14, 14, 14));

        VBox zoom = new VBox(zoomButton("plus", map::zoomIn), Ui.sep(), zoomButton("minus", map::zoomOut));
        zoom.getStyleClass().add("nv-zoom");
        zoom.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(zoom, Pos.CENTER_RIGHT);
        StackPane.setMargin(zoom, new Insets(0, 14, 0, 0));
        map.setPadding(56, 70);

        StackPane stack = new StackPane(map, tabScroll, zoom, hint);
        stack.getStyleClass().add("nv-map-bg");
        VBox.setVgrow(stack, Priority.ALWAYS);
        if (wide) {
            stack.getStyleClass().add("nv-card");
            clip(stack, 16);
            VBox.setMargin(stack, new Insets(12, 28, 24, 28));
        }
        screen.getChildren().add(stack);
        setScreen(screen, Tab.MAP);
    }
}

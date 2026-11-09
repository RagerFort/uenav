package com.uenav.mobile;

import com.uenav.core.CampusData;
import com.uenav.core.Directions;
import com.uenav.core.Floor;
import com.uenav.core.Node;
import com.uenav.core.PathFinder;
import com.uenav.core.PlaceCategory;
import com.uenav.core.PlaceSearch;
import com.uenav.core.QrCodes;
import com.uenav.core.Route;
import com.uenav.core.RoutePreference;
import com.uenav.core.SnapshotReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * The visitor screens. The home screen copies a mall kiosk: a big search bar, "You are here" (scan the QR sign on the
 * wall, tap the map, or pick from a list), big category buttons, one-tap "nearest" buttons and an emergency exit
 * button. Then the Figma screens: Plan a route, Directions (map + steps), Route unavailable and the Map tab.
 * Routes are found on the device with uenav-core (Dijkstra), so all of this works offline (D5).
 * The phone shows it full screen; wide = true gives the side-by-side PC layout.
 */
public final class Navigator {

    public enum Tab { EXPLORE, MAP }

    /** Home screen category buttons, in kiosk order. */
    static final List<PlaceCategory> HOME_CATEGORIES = List.of(PlaceCategory.OFFICES, PlaceCategory.CLASSROOMS,
            PlaceCategory.RESTROOMS, PlaceCategory.FOOD, PlaceCategory.EXITS, PlaceCategory.FACILITIES);
    /** One-tap "nearest" buttons. */
    static final List<PlaceCategory> QUICK = List.of(PlaceCategory.RESTROOMS, PlaceCategory.EXITS,
            PlaceCategory.FOOD, PlaceCategory.CLINIC);

    private final UsageLog log;
    private final boolean wide;
    private final Settings settings;
    private final PlacesMemory memory;
    private final StackPane view = new StackPane();
    private CampusData data;
    private Consumer<Tab> onTab = t -> { };
    private Runnable onThemeToggle;
    private boolean dark;
    private Tab tab = Tab.EXPLORE;
    private Runnable redraw = () -> { };

    private Node start;
    private Node destination;
    private RoutePreference pref = RoutePreference.ANY;
    private Route route;
    private boolean routeChecked;
    private String exploreQuery = "";
    /** "Nearest restroom", "Emergency exit" or null for a route the visitor picked. */
    private String routeContext;
    private boolean emergency;
    private List<Integer> visited = List.of();
    private double searchMillis;
    private boolean graphOn;
    private int selectedStep = -1;
    /** What to do once a starting point is chosen (for example "find the nearest restroom"). */
    private Runnable afterStart;
    private Consumer<Integer> directionsLevel = lv -> { };

    /** @param wide true for the PC window (map and steps side by side, flat headers) */
    public Navigator(CampusData data, UsageLog log, boolean wide) {
        this(data, log, wide, null, null);
    }

    /**
     * @param settings for "Avoid stairs" (may be null: then routes may use stairs)
     * @param memory   recent and favourite destinations (may be null: then none are kept)
     */
    public Navigator(CampusData data, UsageLog log, boolean wide, Settings settings, PlacesMemory memory) {
        this.data = data;
        this.log = log;
        this.wide = wide;
        this.settings = settings;
        this.memory = memory;
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

    /** Shows a light/dark button on the home screen; the app switches the stylesheets and calls setDark. */
    public void setOnThemeToggle(Runnable handler) {
        this.onThemeToggle = handler;
    }

    /** Dark colours for the map; the screen is drawn again. */
    public void setDark(boolean dark) {
        this.dark = dark;
        redraw.run();
    }

    /** The current starting point ("You are here"), or null. */
    public Node start() {
        return start;
    }

    /** Replaces the campus data (for example after a newer copy was downloaded) and goes back to Explore. */
    public void setData(CampusData newData) {
        this.data = newData;
        start = start == null ? null : newData.graph().node(start.id()).orElse(null);
        destination = null;
        route = null;
        routeChecked = false;
        afterStart = null;
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

    private boolean avoidStairs() {
        return settings != null && settings.avoidStairs();
    }

    private void setScreen(Parent screen, Tab t, Runnable again) {
        view.getChildren().setAll(screen);
        redraw = again;
        tab = t;
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

    /** A short message at the bottom of the screen for a few seconds. */
    private void toast(String text) {
        Label l = Ui.wrap(text);
        HBox box = new HBox(l);
        box.getStyleClass().add("nv-toast");
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setMouseTransparent(true);
        StackPane.setAlignment(box, Pos.BOTTOM_CENTER);
        StackPane.setMargin(box, new Insets(0, 24, 26, 24));
        view.getChildren().add(box);
        PauseTransition wait = new PauseTransition(Duration.seconds(2.6));
        wait.setOnFinished(e -> view.getChildren().remove(box));
        wait.play();
    }

    // ---------------------------------------------------------------- Explore (home)

    private void showExplore() {
        Button theme = null;
        if (onThemeToggle != null) {
            theme = Ui.round(dark ? "sun" : "moon", dark ? "Light mode" : "Dark mode");
            theme.setOnAction(e -> onThemeToggle.run());
        }
        VBox screen = page(Ui.header("UENav · VISITOR", "Explore", null, theme));

        TextField search = new TextField(exploreQuery);
        search.setPromptText("Search a room, office or place");
        search.setFocusTraversable(false);
        HBox searchBox = Ui.fieldBox("search", search);
        searchBox.getStyleClass().setAll("nv-search");
        Button clear = Ui.round("close", "Clear search", "nv-round-small");
        clear.setOnAction(e -> search.clear());
        clear.visibleProperty().bind(search.textProperty().isNotEmpty());
        searchBox.getChildren().add(clear);

        VBox dynamic = new VBox(14);
        Runnable fill = () -> {
            exploreQuery = search.getText();
            if (exploreQuery == null || exploreQuery.isBlank()) {
                dynamic.getChildren().setAll(homeSections());
            } else {
                dynamic.getChildren().setAll(searchResults(exploreQuery));
            }
        };
        search.textProperty().addListener((o, a, b) -> fill.run());
        fill.run();

        VBox body = limitWidth(new VBox(14, searchBox, dynamic));
        body.setPadding(bodyPad());
        screen.getChildren().add(Ui.scroll(body));
        setScreen(screen, Tab.EXPLORE, this::showExplore);
    }

    private List<javafx.scene.Node> homeSections() {
        List<javafx.scene.Node> out = new ArrayList<>();
        out.add(hereCard());
        out.add(Ui.label("Find a place", "nv-h3"));
        out.add(categoryGrid());
        out.add(Ui.label("Nearest to you", "nv-h3"));
        out.add(quickRow());
        out.add(emergencyButton());
        VBox fav = placeCard("Favorites", "star", memory == null ? List.of() : memory.favourites());
        if (fav != null) {
            out.add(fav);
        }
        VBox recent = placeCard("Recent", "history", memory == null ? List.of() : memory.recent());
        if (recent != null) {
            out.add(recent);
        }
        if (settings != null) {
            out.add(avoidStairsRow());
        }
        out.add(Ui.infoLine("Every map is stored on this phone, so search and directions work without internet."));
        return out;
    }

    /** "You are here": the starting point, set by a QR sign, the map or a list. */
    private VBox hereCard() {
        Button scan = pill("qrscan", start == null ? "Scan QR sign" : "Scan again");
        scan.setOnAction(e -> scanQr());
        Button choose = pill(start == null ? "touch" : "edit", start == null ? "Choose spot" : "Change");
        choose.setOnAction(e -> chooseStartThen(null));
        HBox buttons = new HBox(8, scan, choose);
        VBox card;
        if (start == null) {
            HBox head = new HBox(12, Ui.bubble("here", "blue", 40),
                    Ui.twoLines("Where are you?", "Scan the UENav sign near you, or choose your spot. "
                            + "No GPS or internet needed.", "nv-here-title"));
            head.setAlignment(Pos.CENTER_LEFT);
            card = new VBox(12, head, buttons);
        } else {
            Button clear = Ui.round("close", "Forget my starting point", "nv-round-small");
            clear.setOnAction(e -> {
                start = null;
                routeChecked = false;
                showExplore();
            });
            HBox head = new HBox(12, Ui.bubble("here", "blue", 40),
                    Ui.twoLines("You are here: " + start.name(), PlaceStyle.subtitle(start, data), "nv-here-title"),
                    clear);
            head.setAlignment(Pos.CENTER_LEFT);
            card = new VBox(12, head, buttons);
        }
        card.getStyleClass().add("nv-here");
        card.setPadding(new Insets(14, 14, 14, 14));
        return card;
    }

    private static Button pill(String icon, String text) {
        Button b = new Button(text);
        b.setGraphic(Icons.icon(icon, 16));
        b.getStyleClass().add("nv-pill");
        b.setFocusTraversable(false);
        return b;
    }

    private GridPane categoryGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 3);
            grid.getColumnConstraints().add(cc);
        }
        for (int i = 0; i < HOME_CATEGORIES.size(); i++) {
            PlaceCategory cat = HOME_CATEGORIES.get(i);
            int n = cat.places(data).size();
            VBox tile = new VBox(6, Ui.bubble(cat.icon(), toneOf(cat), 40), Ui.label(cat.label(), "nv-tile-label"),
                    Ui.label(n == 0 ? "None yet" : n + (n == 1 ? " place" : " places"), "nv-tile-count"));
            tile.setAlignment(Pos.CENTER);
            tile.getStyleClass().add("nv-tile");
            tile.setPadding(new Insets(14, 6, 12, 6));
            tile.setMaxWidth(Double.MAX_VALUE);
            tile.setOnMouseClicked(e -> showCategory(cat));
            grid.add(tile, i % 3, i / 3);
        }
        return grid;
    }

    static String toneOf(PlaceCategory c) {
        return switch (c) {
            case OFFICES, FOOD -> "orange";
            case CLASSROOMS, BUILDINGS, CLINIC -> "red";
            case RESTROOMS, EXITS -> "blue";
            case FACILITIES -> "green";
        };
    }

    private GridPane quickRow() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        for (int c = 0; c < QUICK.size(); c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / QUICK.size());
            grid.getColumnConstraints().add(cc);
        }
        for (int i = 0; i < QUICK.size(); i++) {
            PlaceCategory cat = QUICK.get(i);
            String text = switch (cat) {
                case RESTROOMS -> "Restroom";
                case EXITS -> "Exit";
                case FOOD -> "Food";
                default -> cat.label();
            };
            VBox tile = new VBox(6, Ui.bubble(cat.icon(), toneOf(cat), 34), Ui.label(text, "nv-tile-label"));
            tile.setAlignment(Pos.CENTER);
            tile.getStyleClass().add("nv-quick");
            tile.setPadding(new Insets(10, 4, 10, 4));
            tile.setMaxWidth(Double.MAX_VALUE);
            tile.setOnMouseClicked(e -> findNearest(cat, false));
            grid.add(tile, i, 0);
        }
        return grid;
    }

    private HBox emergencyButton() {
        HBox box = new HBox(12, Ui.bubble("run", "red", 40),
                new VBox(2, Ui.label("Emergency exit", "nv-emergency-text"),
                        Ui.wrap("Fastest way out from where you are. Avoids elevators.", "nv-muted")));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("nv-emergency");
        box.setPadding(new Insets(12, 14, 12, 14));
        box.setOnMouseClicked(e -> findNearest(PlaceCategory.EXITS, true));
        return box;
    }

    /** Favorites or Recent: up to five places, or null if there are none. */
    private VBox placeCard(String title, String icon, List<Integer> ids) {
        List<Node> nodes = new ArrayList<>();
        for (int id : ids) {
            data.graph().node(id).filter(n -> SnapshotReader.isSearchable(n.nodeType())).ifPresent(nodes::add);
            if (nodes.size() == 5) {
                break;
            }
        }
        if (nodes.isEmpty()) {
            return null;
        }
        HBox head = new HBox(8, Icons.icon(icon, 16, "star".equals(icon) ? "nv-icon-star" : "nv-icon-muted"),
                Ui.label(title, "nv-h3"));
        head.setAlignment(Pos.CENTER_LEFT);
        head.setPadding(new Insets(0, 0, 4, 0));
        VBox rows = new VBox();
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                rows.getChildren().add(Ui.sep());
            }
            rows.getChildren().add(destinationRow(nodes.get(i), null));
        }
        return Ui.card(head, rows);
    }

    private HBox avoidStairsRow() {
        boolean on = avoidStairs();
        HBox row = new HBox(12, Ui.bubble("accessible", "green", 36),
                Ui.twoLines("Avoid stairs", on ? "On: routes use elevators, ramps and accessible walkways"
                        : "Off: routes may use stairs", "nv-strong"),
                Ui.toggleSwitch(on));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("nv-toggle-row");
        row.setPadding(new Insets(12, 14, 12, 14));
        row.setOnMouseClicked(e -> {
            settings.setAvoidStairs(!avoidStairs());
            routeChecked = false;
            showExplore();
            toast(avoidStairs() ? "Avoid stairs is on" : "Avoid stairs is off");
        });
        return row;
    }

    private List<javafx.scene.Node> searchResults(String query) {
        List<Node> matches = PlaceSearch.search(data, query);
        VBox rows = new VBox();
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) {
                rows.getChildren().add(Ui.sep());
            }
            rows.getChildren().add(destinationRow(matches.get(i), null));
        }
        if (matches.isEmpty()) {
            Label none = Ui.wrap("No places match “" + query.trim() + "”. Try a room code like EN 420, "
                    + "or words like registrar, cashier or restroom.", "nv-muted");
            none.setPadding(new Insets(14, 0, 6, 0));
            rows.getChildren().add(none);
        }
        Label count = Ui.label(matches.size() + (matches.size() == 1 ? " place" : " places"), "nv-muted");
        HBox head = new HBox(Ui.label("Results", "nv-h3"), Ui.grow(), count);
        head.setAlignment(Pos.CENTER_LEFT);
        head.setPadding(new Insets(0, 0, 6, 0));
        return List.of(Ui.card(head, rows));
    }

    /** @param meters walking distance from the start to show on the right, or null */
    private HBox destinationRow(Node n, Double meters) {
        PlaceStyle s = PlaceStyle.of(n.nodeType());
        HBox row = new HBox(12, Ui.bubble(s.icon(), s.tone(), 36),
                Ui.twoLines(n.name(), PlaceStyle.subtitle(n, data), "nv-strong"));
        if (meters != null) {
            row.getChildren().add(Ui.chip("~" + Math.round(meters) + " m", "grey"));
        }
        row.getChildren().add(Icons.icon("chevron", 18, "nv-icon-muted"));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 0, 10, 0));
        row.getStyleClass().add("nv-row");
        row.setOnMouseClicked(e -> openPlan(n));
        return row;
    }

    // ---------------------------------------------------------------- category list

    private void showCategory(PlaceCategory cat) {
        VBox screen = page(Ui.header("UENav · VISITOR", cat.label(), this::showExplore, null));
        List<Node> places = new ArrayList<>(cat.places(data));
        Map<Integer, Double> dist = start == null ? Map.of()
                : PathFinder.distancesFrom(data.graph(), start.id(), avoidStairs() ? RoutePreference.ELEVATOR
                : RoutePreference.ANY);
        if (start != null) {
            places.removeIf(n -> n.id() == start.id());
            places.sort((a, b) -> Double.compare(dist.getOrDefault(a.id(), Double.MAX_VALUE),
                    dist.getOrDefault(b.id(), Double.MAX_VALUE)));
        }
        VBox rows = new VBox();
        for (int i = 0; i < places.size(); i++) {
            if (i > 0) {
                rows.getChildren().add(Ui.sep());
            }
            Node n = places.get(i);
            rows.getChildren().add(destinationRow(n, dist.get(n.id())));
        }
        VBox body = limitWidth(new VBox(14));
        if (places.isEmpty()) {
            body.getChildren().add(Ui.card(Ui.bubble(cat.icon(), toneOf(cat), 48),
                    Ui.label("No " + cat.label().toLowerCase(Locale.ROOT) + " on the map yet", "nv-h3"),
                    Ui.wrap("An admin can add them in the UENav Admin app (Places > Add place).", "nv-muted")));
        } else {
            String hint = start == null ? "A to Z. Set where you are to sort them by walking distance."
                    : "Nearest first, from " + start.name() + ".";
            body.getChildren().addAll(Ui.infoLine(hint), Ui.card(rows));
            if (start == null) {
                Button set = Ui.secondary("Set where I am");
                set.setOnAction(e -> chooseStartThen(() -> showCategory(cat)));
                body.getChildren().add(set);
            }
        }
        body.setPadding(bodyPad());
        screen.getChildren().add(Ui.scroll(body));
        setScreen(screen, Tab.EXPLORE, () -> showCategory(cat));
    }

    // ---------------------------------------------------------------- starting point: list, map, QR

    /** Asks for the starting point, then runs next (or goes on to directions / home). */
    private void chooseStartThen(Runnable next) {
        afterStart = next;
        showPlan(false, true);
    }

    /** Sets "You are here" and carries on with whatever was waiting for it. */
    private void setStartAndContinue(Node node) {
        start = node;
        routeChecked = false;
        Runnable next = afterStart;
        afterStart = null;
        if (next != null) {
            next.run();
        } else if (destination != null && destination.id() != node.id()) {
            findDirections(true);
        } else {
            showExplore();
        }
        toast("You are here: " + node.name());
    }

    /**
     * Opens the camera scanner with the "type the code" screen behind it. A good scan moves on by itself; if the
     * scanner cannot open, cannot read the sign, or is closed, the visitor is left on the code screen (for example
     * when Google's scanner is not downloaded yet and the phone is offline).
     */
    private void scanQr() {
        showCodeEntry();
        QrScanner.scan(this::handleCode);
    }

    /** Handles the text of a scanned or typed QR code. @return an error message, or null if it worked */
    String handleCode(String text) {
        OptionalInt id = QrCodes.parse(text);
        if (id.isEmpty()) {
            toast("That is not a UENav code.");
            return "That is not a UENav code. It looks like UENAV:12.";
        }
        Optional<Node> node = data.graph().node(id.getAsInt());
        if (node.isEmpty()) {
            String msg = "Code " + id.getAsInt() + " is not on this phone's map yet. Connect to the internet once "
                    + "to update the map.";
            toast(msg);
            return msg;
        }
        setStartAndContinue(node.get());
        return null;
    }

    /** Type the code printed under the QR: behind the camera scanner, or instead of it when there is no camera. */
    private void showCodeEntry() {
        boolean camera = QrScanner.available();
        Runnable back = afterStart != null || destination != null ? () -> showPlan(false, afterStart != null || destination == null)
                : this::showExplore;
        VBox screen = page(Ui.header("UENav · VISITOR", "Scan QR sign", back, null));
        TextField code = new TextField();
        code.setPromptText("UENAV:12");
        code.getStyleClass().add("nv-code");
        HBox box = Ui.fieldBox("qrscan", code);
        Label error = Ui.wrap("", "nv-error");
        Button use = Ui.primary("Set as my starting point");
        Runnable submit = () -> {
            String problem = handleCode(code.getText());
            if (problem != null) {
                error.setText(problem);
            }
        };
        use.setOnAction(e -> submit.run());
        code.setOnAction(e -> submit.run());
        String help = camera
                ? "Camera did not open or could not read the sign? Type the short code printed under the QR, such as "
                        + "UENAV:12. It works with no internet."
                : "Every UENav sign has a short code printed under the QR, such as UENAV:12. It works with no "
                        + "internet.";
        VBox card = Ui.card(Ui.bubble("qrscan", "blue", 52), Ui.label("Type the code under the QR", "nv-h2"),
                Ui.wrap(help, "nv-muted"), box, error, use);
        card.setSpacing(10);
        card.setPadding(new Insets(20, 18, 20, 18));
        VBox body = limitWidth(new VBox(14, card));
        if (camera) {
            Button again = Ui.secondary("Scan with the camera again");
            again.setOnAction(e -> QrScanner.scan(this::handleCode));
            body.getChildren().add(again);
        } else {
            body.getChildren().add(Ui.infoLine("This device has no camera scanner, so the code is typed in."));
        }
        body.setPadding(bodyPad());
        screen.getChildren().add(Ui.scroll(body));
        setScreen(screen, Tab.EXPLORE, this::showCodeEntry);
    }

    /** Tap the map where you are (any point, including hallway corners and stairs). */
    private void showPickOnMap(boolean startOnly) {
        VBox screen = page(Ui.header("UENav · VISITOR", "Tap where you are", () -> showPlan(false, startOnly),
                null));
        MapView map = new MapView();
        map.setDark(dark);
        map.setData(data);
        map.setPickAnyNode(true);
        map.setOnPlaceClicked(this::setStartAndContinue);
        StackPane stack = levelBrowser(map);
        HBox hint = new HBox(10, Icons.icon("touch", 18, "nv-icon-blue"),
                Ui.wrap("Tap the place, door or hallway corner nearest to you. Use the floor tabs inside buildings.",
                        "nv-muted"));
        hint.setAlignment(Pos.CENTER_LEFT);
        hint.getStyleClass().add("nv-floating");
        hint.setPadding(new Insets(12, 14, 12, 14));
        hint.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(hint, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hint, new Insets(0, 14, 14, 14));
        stack.getChildren().add(hint);
        screen.getChildren().add(stack);
        setScreen(screen, Tab.MAP, () -> showPickOnMap(startOnly));
    }

    // ---------------------------------------------------------------- Plan a route

    /** Starts planning a route to this destination. With "You are here" set it goes straight to directions. */
    public void openPlan(Node dest) {
        if (start != null && start.id() == dest.id()) {
            start = null;
        }
        destination = dest;
        routeChecked = false;
        routeContext = null;
        emergency = false;
        afterStart = null;
        if (start != null) {
            findDirections(true);
        } else {
            showPlan(false, false);
        }
    }

    /** Text typed in the "Where are you?" search box, kept while the screen is redrawn. */
    private String startQuery = "";

    /**
     * "Where are you?" / "Plan a route": the top half is the campus map, which slides open, with both gates as big
     * labelled pins and every place tappable; the bottom half lists the gates, then other places with a search box.
     * @param showAll   kept for older callers (the search box replaced "Start from another place")
     * @param startOnly true when only the starting point is asked for (no destination yet)
     */
    private void showPlan(boolean showAll, boolean startOnly) {
        showPlanScreen(startOnly, true);
    }

    private void showPlanScreen(boolean startOnly, boolean animate) {
        boolean noDest = startOnly || destination == null;
        VBox screen = page(Ui.header("UENav · VISITOR", noDest ? "Where are you?" : "Plan a route",
                this::showExplore, null));

        List<Node> gates = new ArrayList<>();
        for (Node n : data.places()) {
            if ("GATE".equals(n.nodeType()) && (noDest || n.id() != destination.id())) {
                gates.add(n);
            }
        }

        // top half: the map
        MapView map = new MapView();
        map.setDark(dark);
        map.setData(data);
        map.setPickAnyNode(true);
        map.setHighlights(gates);
        map.fitAround(gates);
        map.setHere(start);
        map.setPadding(48, 16);
        map.setOnPlaceClicked(n -> pickStart(n, startOnly));
        HBox hint = new HBox(8, Icons.icon("touch", 16, "nv-icon-blue"),
                Ui.label(gates.isEmpty() ? "Tap your spot on the map" : "Tap a gate or your spot on the map",
                        "nv-strong"));
        hint.setAlignment(Pos.CENTER_LEFT);
        hint.getStyleClass().add("nv-floating");
        hint.setPadding(new Insets(8, 12, 8, 10));
        hint.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        hint.setMouseTransparent(true);
        StackPane.setAlignment(hint, Pos.TOP_LEFT);
        StackPane.setMargin(hint, new Insets(10, 0, 0, 12));
        VBox zoom = new VBox(zoomButton("plus", map::zoomIn), Ui.sep(), zoomButton("minus", map::zoomOut));
        zoom.getStyleClass().add("nv-zoom");
        zoom.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(zoom, Pos.CENTER_RIGHT);
        StackPane.setMargin(zoom, new Insets(0, 12, 0, 0));
        StackPane mapPanel = new StackPane(map, hint, zoom);
        mapPanel.getStyleClass().add("nv-map-bg");
        mapPanel.minHeightProperty().bind(mapPanel.prefHeightProperty());
        mapPanel.maxHeightProperty().bind(mapPanel.prefHeightProperty());
        clip(mapPanel, 0);

        // bottom half: gates, then other places
        Button scan = pill("qrscan", "Scan QR sign");
        scan.setOnAction(e -> scanQr());
        VBox gateRows = new VBox(10);
        for (Node g : gates) {
            gateRows.getChildren().add(choice(g, false, startOnly));
        }
        TextField search = new TextField(startQuery);
        search.setPromptText("Search another place (room, office, building)");
        search.setFocusTraversable(false);
        HBox searchBox = Ui.fieldBox("search", search);
        VBox others = new VBox(8);
        Runnable fillOthers = () -> {
            startQuery = search.getText() == null ? "" : search.getText();
            List<Node> found = PlaceSearch.search(data, startQuery);
            found.removeIf(n -> "GATE".equals(n.nodeType()) || (!noDest && n.id() == destination.id()));
            others.getChildren().clear();
            int max = startQuery.isBlank() ? 12 : 40;
            for (int i = 0; i < Math.min(max, found.size()); i++) {
                others.getChildren().add(choice(found.get(i), false, startOnly));
            }
            if (found.size() > max) {
                others.getChildren().add(Ui.infoLine((found.size() - max) + " more. Type a name to find yours."));
            }
            if (found.isEmpty()) {
                others.getChildren().add(Ui.wrap("No places match “" + startQuery.trim() + "”.",
                        "nv-muted"));
            }
        };
        search.textProperty().addListener((o, a, b) -> fillOthers.run());
        fillOthers.run();

        VBox list = new VBox(12);
        if (!noDest) {
            list.getChildren().add(routeCard());
        }
        if (!gates.isEmpty()) {
            list.getChildren().addAll(Ui.label(gates.size() == 2 ? "The two campus gates" : "Campus gates", "nv-h3"),
                    gateRows);
        }
        list.getChildren().addAll(new HBox(8, Ui.label("Somewhere else?", "nv-h3"), Ui.grow(), scan), searchBox,
                others, Ui.infoLine("UENav signs at entrances, stairwells and hallway corners have a QR code that "
                        + "sets your starting point instantly, even offline."));
        list.setPadding(new Insets(14, 18, 18, 18));
        ScrollPane listScroll = Ui.scroll(list);
        VBox sheet = new VBox(listScroll);
        sheet.getStyleClass().add("nv-sheet");
        VBox.setVgrow(sheet, Priority.ALWAYS);
        VBox.setMargin(sheet, new Insets(-18, 0, 0, 0));
        screen.getChildren().addAll(mapPanel, sheet);

        if (!noDest) {
            Button find = Ui.primary("Find directions");
            find.setDisable(start == null);
            find.setOnAction(e -> findDirections(true));
            VBox footer = new VBox(find);
            footer.getStyleClass().add("nv-surface");
            footer.setPadding(new Insets(8, 18, 12, 18));
            screen.getChildren().add(footer);
        }

        double target = Math.max(260, (view.getHeight() > 0 ? view.getHeight() : 760) * 0.46);
        if (animate) {
            // the map slides open to half the screen, so it is clear the starting point can be picked on it
            mapPanel.setPrefHeight(0);
            mapPanel.setOpacity(0);
            javafx.animation.Timeline open = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(Duration.millis(420),
                            new javafx.animation.KeyValue(mapPanel.prefHeightProperty(), target,
                                    javafx.animation.Interpolator.EASE_OUT),
                            new javafx.animation.KeyValue(mapPanel.opacityProperty(), 1)));
            open.play();
        } else {
            mapPanel.setPrefHeight(target);
        }
        setScreen(screen, noDest ? Tab.EXPLORE : Tab.MAP, () -> showPlanScreen(startOnly, false));
    }

    /** A place picked on the map or in the list as the starting point. */
    private void pickStart(Node n, boolean startOnly) {
        if (startOnly || destination == null) {
            setStartAndContinue(n);
            return;
        }
        if (destination.id() == n.id()) {
            toast("That is your destination. Pick where you are now.");
            return;
        }
        start = n;
        routeChecked = false;
        showPlanScreen(false, false);
    }

    private VBox routeCard() {
        PlaceStyle ds = PlaceStyle.of(destination.nodeType());
        HBox from = new HBox(12, Ui.bubble("here", "blue", 34),
                Ui.twoLines(start == null ? "Choose a starting point" : start.name(),
                        start == null ? "Starting point" : "Starting point · You are here", "nv-strong"));
        from.setAlignment(Pos.CENTER_LEFT);
        HBox to = new HBox(12, Ui.bubble("flag", "red", 34),
                Ui.twoLines(destination.name(), "Destination · " + ds.label(), "nv-strong"));
        to.setAlignment(Pos.CENTER_LEFT);
        VBox card = Ui.card(Ui.label("YOUR ROUTE", "nv-label-caps"), from, Ui.sep(), to);
        card.setSpacing(10);
        return card;
    }

    private HBox choice(Node n, boolean showAll, boolean startOnly) {
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
        row.setOnMouseClicked(e -> pickStart(n, startOnly));
        return row;
    }

    // ---------------------------------------------------------------- routes

    private RoutePreference normalPref() {
        return avoidStairs() ? RoutePreference.ELEVATOR : RoutePreference.ANY;
    }

    private void findDirections(boolean logIt) {
        routeContext = null;
        emergency = false;
        runRoute(normalPref(), logIt);
    }

    private void runRoute(RoutePreference p, boolean logIt) {
        pref = p;
        long t0 = System.nanoTime();
        PathFinder.Trace trace = PathFinder.trace(data.graph(), start.id(), destination.id(), pref);
        searchMillis = (System.nanoTime() - t0) / 1e6;
        route = trace.route().orElse(null);
        visited = trace.visited();
        routeChecked = true;
        selectedStep = -1;
        if (logIt) {
            log.route(start.id(), destination.id(), pref);
            if (memory != null && route != null && routeContext == null) {
                memory.visited(destination.id());
            }
        }
        showDirections();
    }

    /** One-tap "nearest restroom / exit / food / clinic", and the emergency exit route (never by elevator). */
    private void findNearest(PlaceCategory cat, boolean isEmergency) {
        if (start == null) {
            chooseStartThen(() -> findNearest(cat, isEmergency));
            return;
        }
        List<Integer> targets = cat.ids(data);
        String what = switch (cat) {
            case RESTROOMS -> "restroom";
            case EXITS -> "exit";
            case FOOD -> "place to eat";
            case CLINIC -> "clinic";
            default -> cat.label().toLowerCase(Locale.ROOT);
        };
        if (targets.isEmpty() || (targets.size() == 1 && targets.get(0) == start.id())) {
            toast("No " + what + " is on the map yet.");
            return;
        }
        RoutePreference p = isEmergency ? RoutePreference.STAIRS : normalPref();
        long t0 = System.nanoTime();
        Optional<Route> nearest = PathFinder.findNearest(data.graph(), start.id(), targets, p);
        if (nearest.isEmpty() && !isEmergency && p == RoutePreference.ELEVATOR) {
            toast("No step-free route to a " + what + " from here.");
            return;
        }
        if (nearest.isEmpty()) {
            toast("No " + what + " can be reached from " + start.name() + ".");
            return;
        }
        Route r = nearest.get();
        destination = r.nodes().get(r.nodes().size() - 1);
        emergency = isEmergency;
        routeContext = isEmergency ? "Emergency exit" : "Nearest " + what;
        pref = p;
        PathFinder.Trace trace = PathFinder.trace(data.graph(), start.id(), destination.id(), p);
        searchMillis = (System.nanoTime() - t0) / 1e6;
        route = r;
        visited = trace.visited();
        routeChecked = true;
        selectedStep = -1;
        log.route(start.id(), destination.id(), p);
        showDirections();
    }

    // ---------------------------------------------------------------- Directions

    private void showDirections() {
        if (route == null) {
            showUnavailable();
            return;
        }
        Directions.Summary summary = Directions.summarize(route, data);
        List<Integer> levels = MapView.levelsOf(route, data);

        MapView map = new MapView();
        map.setDark(dark);
        map.setData(data);
        map.setRoute(route);
        map.setLevel(levels.isEmpty() ? null : levels.get(0));
        map.setGraphView(graphOn, visited);
        StackPane mapStack = mapWithOverlays(map, levels);

        VBox sheet = sheet(summary, map, levels);
        Parent screen;
        if (wide) {
            Region head = Ui.header("UENav · VISITOR", "Directions", () -> showPlan(false, false), null);
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
            // Fixed height: the sheet below must never squeeze the map (that looked like random zooming).
            mapStack.prefHeightProperty().bind(view.heightProperty().multiply(0.50));
            mapStack.minHeightProperty().bind(mapStack.prefHeightProperty());
            mapStack.maxHeightProperty().bind(mapStack.prefHeightProperty());
            sheetBox.setMinHeight(0);
            screen = v;
        }
        setScreen(screen, Tab.MAP, this::showDirections);
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

    /** Map with the From/To card, floor tabs, building chip, zoom and graph buttons on top (Figma: Main Function). */
    private StackPane mapWithOverlays(MapView map, List<Integer> levels) {
        HBox fromRow = new HBox(10, dot("#2563EB"),
                new VBox(0, Ui.label("FROM", "nv-label-caps"), Ui.label(start.name(), "nv-strong")));
        fromRow.setAlignment(Pos.CENTER_LEFT);
        fromRow.getStyleClass().add("nv-row");
        fromRow.setOnMouseClicked(e -> showPlan(false, false));
        HBox toRow = new HBox(10, Icons.icon("pin", 14, "nv-icon-brand"),
                new VBox(0, Ui.label("TO", "nv-label-caps"), Ui.label(destination.name(), "nv-strong")));
        toRow.setAlignment(Pos.CENTER_LEFT);
        toRow.getStyleClass().add("nv-row");
        toRow.setOnMouseClicked(e -> showExplore());
        VBox rows = new VBox(8, fromRow, Ui.sep(), toRow);
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

        double padTop = 140;
        directionsLevel = map::setLevel;
        if (levels.size() > 1 || (levels.size() == 1 && levels.get(0) != null)) {
            HBox tabs = new HBox(2);
            tabs.getStyleClass().add("nv-tabs");
            HBox chipBox = new HBox();
            Map<Integer, Button> buttons = new HashMap<>();
            List<Button> all = new ArrayList<>();
            Consumer<Integer> select = lv -> {
                map.setLevel(lv);
                all.forEach(x -> x.getStyleClass().remove("nv-tab-on"));
                Button b = buttons.get(lv == null ? -1 : lv);
                if (b != null) {
                    b.getStyleClass().add("nv-tab-on");
                }
                chipBox.getChildren().setAll(buildingChip(lv));
            };
            for (Integer lv : levels) {
                Button b = new Button(lv == null ? "Campus" : data.floor(lv).map(Floor::shortLabel).orElse("Floor"));
                b.getStyleClass().add("nv-tab");
                b.setFocusTraversable(false);
                buttons.put(lv == null ? -1 : lv, b);
                all.add(b);
                b.setOnAction(e -> select.accept(lv));
                tabs.getChildren().add(b);
            }
            all.get(0).getStyleClass().add("nv-tab-on");
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
            directionsLevel = select;
        }
        map.setPadding(padTop, wide ? 20 : 40);

        Label graphText = Ui.label(String.format(Locale.ROOT, "Dijkstra checked %d of %d places in %.1f ms",
                visited.size(), data.graph().nodeCount(), searchMillis));
        HBox graphChip = new HBox(graphText);
        graphChip.getStyleClass().add("nv-graph-chip");
        graphChip.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        graphChip.setMouseTransparent(true);
        graphChip.setVisible(graphOn);
        StackPane.setAlignment(graphChip, Pos.BOTTOM_LEFT);
        StackPane.setMargin(graphChip, new Insets(0, 0, wide ? 14 : 40, 14));

        VBox zoom = zoomColumn(map, () -> {
            graphOn = !graphOn;
            map.setGraphView(graphOn, visited);
            graphChip.setVisible(graphOn);
        });
        StackPane.setAlignment(zoom, wide ? Pos.BOTTOM_RIGHT : Pos.CENTER_RIGHT);
        StackPane.setMargin(zoom, wide ? new Insets(0, 14, 14, 0) : new Insets(padTop - 30, 14, 0, 0));

        StackPane stack = new StackPane(map, top, zoom, graphChip);
        stack.getStyleClass().add("nv-map-bg");
        return stack;
    }

    /** +, - and the graph view button (shows the nodes and walkways Dijkstra works on). */
    private VBox zoomColumn(MapView map, Runnable toggleGraph) {
        Button graph = zoomButton("graph", null);
        if (graphOn) {
            graph.getStyleClass().add("nv-zoom-btn-on");
        }
        graph.setOnAction(e -> {
            toggleGraph.run();
            if (graphOn) {
                graph.getStyleClass().add("nv-zoom-btn-on");
            } else {
                graph.getStyleClass().remove("nv-zoom-btn-on");
            }
        });
        graph.setAccessibleText("Graph view");
        VBox zoom = new VBox(zoomButton("plus", map::zoomIn), Ui.sep(), zoomButton("minus", map::zoomOut), Ui.sep(),
                graph);
        zoom.getStyleClass().add("nv-zoom");
        zoom.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return zoom;
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
        if (action != null) {
            b.setOnAction(e -> action.run());
        }
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

    /** The bottom sheet: destination, info card, time and distance, stairs/elevator choice, steps and feedback. */
    private VBox sheet(Directions.Summary s, MapView map, List<Integer> levels) {
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
        if (emergency) {
            HBox banner = new HBox(10, Icons.icon("warning", 20),
                    new VBox(2, Ui.label("Emergency route", "nv-strong"),
                            Ui.wrap("Go to " + destination.name() + ". Use the stairs, not the elevator. Stay calm "
                                    + "and follow staff instructions.")));
            banner.getStyleClass().add("nv-banner-danger");
            banner.setAlignment(Pos.CENTER_LEFT);
            banner.setPadding(new Insets(12, 14, 12, 14));
            sheet.getChildren().add(banner);
        }
        PlaceStyle ds = PlaceStyle.of(destination.nodeType());
        VBox titleText = Ui.twoLines(destination.name(), PlaceStyle.subtitle(destination, data), "nv-h3");
        if (routeContext != null && !emergency) {
            titleText.getChildren().add(0, Ui.label(routeContext.toUpperCase(Locale.ROOT), "nv-eyebrow"));
        }
        HBox title = new HBox(12, Ui.bubble(ds.icon(), ds.tone(), 44), titleText);
        if (memory != null) {
            title.getChildren().add(starButton());
        }
        title.setAlignment(Pos.CENTER_LEFT);
        sheet.getChildren().add(title);

        if (destination.hasInfo()) {
            sheet.getChildren().add(infoCard(destination));
        }

        javafx.scene.layout.FlowPane chips = new javafx.scene.layout.FlowPane(8, 8,
                Ui.chip("~" + s.minutes() + " min walk", "red"),
                Ui.chip("~" + s.meters() + " m", "grey"),
                Ui.chip(s.areaLabel(), s.indoor() && !s.outdoor() ? "green" : "blue"));
        if (pref == RoutePreference.ELEVATOR && !emergency) {
            chips.getChildren().add(Ui.chip("Step-free", "green"));
        }
        sheet.getChildren().add(chips);

        VBox vertical = emergency ? null : verticalChoice(s);
        if (vertical != null) {
            sheet.getChildren().add(vertical);
        }

        VBox steps = new VBox(4);
        List<HBox> rows = new ArrayList<>();
        for (int i = 0; i < s.steps().size(); i++) {
            Directions.Step st = s.steps().get(i);
            HBox row = stepRow(st);
            rows.add(row);
            int index = i;
            row.setOnMouseClicked(e -> {
                selectedStep = index;
                rows.forEach(r -> r.getStyleClass().remove("nv-step-on"));
                row.getStyleClass().add("nv-step-on");
                showStepOnMap(st, map, levels);
            });
            if (i == selectedStep) {
                row.getStyleClass().add("nv-step-on");
            }
            steps.getChildren().add(row);
        }
        sheet.getChildren().addAll(new HBox(Ui.label("STEPS", "nv-label-caps"), Ui.grow(),
                Ui.label("Tap a step to see it on the map", "nv-muted")), steps);
        sheet.getChildren().add(feedbackBox());
        return sheet;
    }

    /** Switches the map to the step's floor and zooms in on where it happens. */
    private void showStepOnMap(Directions.Step st, MapView map, List<Integer> levels) {
        if (st.at() == null) {
            return;
        }
        Integer floor = st.at().floorId();
        Integer lv = floor != null && levels.contains(floor) ? floor : null;
        if (!levels.contains(lv) && !levels.isEmpty()) {
            lv = levels.get(0);
        }
        if (!java.util.Objects.equals(map.level(), lv)) {
            directionsLevel.accept(lv);
        }
        map.focusOn(st.at());
    }

    private Button starButton() {
        boolean fav = memory.isFavourite(destination.id());
        Button star = Ui.round(fav ? "star" : "star-outline", fav ? "Remove from favorites" : "Add to favorites");
        if (fav) {
            star.setGraphic(Icons.icon("star", 18, "nv-icon-star"));
        }
        star.setOnAction(e -> {
            boolean now = memory.toggleFavourite(destination.id());
            star.setGraphic(now ? Icons.icon("star", 18, "nv-icon-star") : Icons.icon("star-outline", 18));
            toast(now ? "Added to favorites" : "Removed from favorites");
        });
        return star;
    }

    /** Office info card: opening hours and what the office handles. */
    static VBox infoCard(Node n) {
        VBox info = new VBox(8);
        info.getStyleClass().add("nv-info");
        info.setPadding(new Insets(12, 14, 12, 14));
        if (n.openHours() != null && !n.openHours().isBlank()) {
            HBox h = new HBox(10, Icons.icon("clock", 16, "nv-icon-muted"),
                    new VBox(1, Ui.label("OFFICE HOURS", "nv-label-caps"), Ui.wrap(n.openHours(), "nv-text")));
            h.setAlignment(Pos.TOP_LEFT);
            info.getChildren().add(h);
        }
        if (n.description() != null && !n.description().isBlank()) {
            HBox d = new HBox(10, Icons.icon("info", 16, "nv-icon-muted"),
                    new VBox(1, Ui.label("WHAT IT HANDLES", "nv-label-caps"), Ui.wrap(n.description(), "nv-text")));
            d.setAlignment(Pos.TOP_LEFT);
            info.getChildren().add(d);
        }
        return info;
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
                    !elevatorOn, () -> choose(RoutePreference.STAIRS)));
        }
        if (byElevator.isPresent()) {
            Directions.Summary el = Directions.summarize(byElevator.get(), data);
            options.getChildren().add(option("elevator", "Elevator", "Accessible · ~" + el.minutes() + " min",
                    elevatorOn, () -> choose(RoutePreference.ELEVATOR)));
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

    private void choose(RoutePreference p) {
        runRoute(p, false);
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
        row.getStyleClass().add("nv-step");
        row.setPadding(new Insets(6, 8, 6, 6));
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
        VBox screen = page(Ui.header("UENav · VISITOR", "Directions", () -> showPlan(false, false), null));
        StackPane icon = Ui.bubble("warning", "orange", 56);
        boolean stepFree = pref == RoutePreference.ELEVATOR;
        VBox message = Ui.card(icon, Ui.label(stepFree ? "No step-free route" : "Route unavailable", "nv-h2"),
                centred((stepFree ? "There is no route without stairs from " : "Directions are not available from ")
                        + start.name() + " to " + destination.name() + "."),
                centred(stepFree ? "You can allow stairs for this trip, or choose another starting point."
                        : "Try a different starting point, or return to Explore to browse destinations."));
        message.setAlignment(Pos.CENTER);
        message.setSpacing(10);
        message.setPadding(new Insets(24, 20, 24, 20));

        VBox body = limitWidth(new VBox(14, routeCard(), message));
        body.setPadding(bodyPad());

        VBox footer = limitWidth(new VBox(10));
        if (stepFree) {
            Button stairs = Ui.primary("Allow stairs for this trip");
            stairs.setOnAction(e -> runRoute(RoutePreference.ANY, false));
            footer.getChildren().add(stairs);
        }
        Button change = stepFree ? Ui.secondary("Change starting point") : Ui.primary("Change starting point");
        change.setOnAction(e -> showPlan(false, false));
        Button browse = Ui.secondary("Browse destinations");
        browse.setOnAction(e -> showExplore());
        footer.getChildren().addAll(change, browse);
        footer.setPadding(wide ? new Insets(0, 28, 24, 28) : new Insets(8, 18, 14, 18));
        screen.getChildren().addAll(Ui.scroll(body), footer);
        setScreen(screen, Tab.MAP, this::showUnavailable);
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
        map.setDark(dark);
        map.setData(data);
        map.setHere(start);
        map.setOnPlaceClicked(this::openPlan);
        StackPane stack = levelBrowser(map);

        HBox hint = new HBox(10, Icons.icon("info", 16, "nv-icon-muted"),
                Ui.wrap(start == null ? "Tap a place on the map, or search in Explore, to plan a route."
                        : "The blue dot is you. Tap a place to get directions.", "nv-muted"));
        hint.setAlignment(Pos.CENTER_LEFT);
        hint.getStyleClass().add("nv-floating");
        hint.setPadding(new Insets(12, 14, 12, 14));
        hint.setMaxHeight(Region.USE_PREF_SIZE);
        if (wide) {
            hint.setMaxWidth(440);
        }
        StackPane.setAlignment(hint, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hint, new Insets(0, 14, 14, 14));
        stack.getChildren().add(hint);
        if (wide) {
            stack.getStyleClass().add("nv-card");
            clip(stack, 16);
            VBox.setMargin(stack, new Insets(12, 28, 24, 28));
        }
        screen.getChildren().add(stack);
        setScreen(screen, Tab.MAP, this::showMapHome);
    }

    /** A map with floor tabs for every level and the zoom / graph buttons (Map tab and "Tap where you are"). */
    private StackPane levelBrowser(MapView map) {
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

        map.setGraphView(graphOn, List.of());
        VBox zoom = zoomColumn(map, () -> {
            graphOn = !graphOn;
            map.setGraphView(graphOn, List.of());
        });
        StackPane.setAlignment(zoom, Pos.CENTER_RIGHT);
        StackPane.setMargin(zoom, new Insets(0, 14, 0, 0));
        map.setPadding(56, 70);

        StackPane stack = new StackPane(map, tabScroll, zoom);
        stack.getStyleClass().add("nv-map-bg");
        VBox.setVgrow(stack, Priority.ALWAYS);
        return stack;
    }
}

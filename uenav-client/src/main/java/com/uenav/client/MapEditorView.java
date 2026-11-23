package com.uenav.client;

import com.uenav.core.Building;
import com.uenav.core.CampusData;
import com.uenav.core.Edge;
import com.uenav.core.EdgeType;
import com.uenav.core.Floor;
import com.uenav.core.Node;
import com.uenav.map.MapView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

/**
 * Map editor page (D27): the campus map the phones show, drawn by the same code (uenav-map), with every place and
 * walkway on it, and edited by hand. Click a place or a walkway to change it in the panel on the right, drag a place
 * to move it (its walkways get new lengths), add places where you click, and connect two places with a walkway.
 * Stairs and elevators can be linked to the floor above or below. Every change is saved on the server at once; the
 * phones get it with their next map update. Undo (or Ctrl+Z) takes changes back one at a time, by asking the server
 * to put things back (see EditHistory). Desktop only: the phone app has no editor.
 */
public class MapEditorView extends VBox {

    private enum Tool { SELECT, ADD, CONNECT }

    /** A level of the map: the outdoor campus (floor null) or one building floor. */
    private record Level(Integer floorId, Integer buildingId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final Color PICK = Color.web("#F59E0B");
    private static final Color NEW = Color.web("#2563EB");
    /** Metres per map unit when a level has no walkways to measure from (about right for the campus). */
    private static final double DEFAULT_M_PER_UNIT = 0.24;
    private static final double FLIGHT_M = 15.0;
    private static final double LIFT_M = 6.0;
    private static final KeyCombination UNDO_KEY = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);

    /** A walkway as it was before a change, to put back on undo. */
    private record SavedWay(int id, AdminModels.EdgeForm form) { }

    private final AdminApi api;
    private final Consumer<ApiException> onUnauthorized;
    private final MapView map = new MapView();
    private final ComboBox<Level> levelBox = new ComboBox<>();
    private final ToggleGroup tools = new ToggleGroup();
    private final VBox panel = new VBox(10);
    private final Label hint = new Label();
    private final Label status = new Label();
    private final EditHistory history = new EditHistory();
    private final Button undo = new Button("Undo");
    private boolean undoing;

    private CampusData data;
    private Tool tool = Tool.SELECT;
    private Node selNode;
    private Edge selEdge;
    private Node connectFrom;
    private double[] pendingPlace;
    // dragging a place
    private Node dragNode;
    private double pressX;
    private double pressY;
    private boolean dragMoved;
    private double[] dragAt;
    private double[] mouseAt;
    private boolean handledPress;

    public MapEditorView(AdminApi api, Consumer<ApiException> onUnauthorized) {
        this.api = api;
        this.onUnauthorized = onUnauthorized;
        getStyleClass().add("canvas");
        setPadding(new Insets(24));
        setSpacing(16);

        Label eyebrow = new Label("ADMINISTRATION");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label("Map editor");
        title.getStyleClass().add("page-title");
        Label sub = new Label("The map the phones show, with every walkway. Click a place or a walkway to edit it, drag a "
                + "place to move it, or pick a tool. Changes are saved at once; Undo (Ctrl+Z) takes the last ones back.");
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);
        VBox header = new VBox(2, eyebrow, title, sub);

        levelBox.setPromptText("Loading…");
        levelBox.setPrefWidth(230);
        levelBox.setMinWidth(150);
        levelBox.valueProperty().addListener((o, a, b) -> {
            if (b != null && data != null) {
                clearSelection();
                map.setLevel(b.floorId());
            }
        });
        ToggleButton select = toolButton("Select", "navigation", Tool.SELECT);
        ToggleButton add = toolButton("Add place", "plus", Tool.ADD);
        ToggleButton connect = toolButton("Connect", "swap", Tool.CONNECT);
        select.setSelected(true);
        tools.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                a.setSelected(true); // one tool is always on
                return;
            }
            setTool((Tool) b.getUserData());
        });
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Button zoomOut = iconButton("minus", "Zoom out", map::zoomOut);
        Button zoomIn = iconButton("plus", "Zoom in", map::zoomIn);
        Button fit = new Button("Fit");
        fit.getStyleClass().add("small-button");
        fit.setMinWidth(Region.USE_PREF_SIZE);
        fit.setOnAction(e -> map.resetView());
        undo.setGraphic(Icons.icon("undo", 14));
        undo.getStyleClass().add("small-button");
        undo.setMinWidth(Region.USE_PREF_SIZE);
        undo.setFocusTraversable(false);
        undo.setOnAction(e -> undo());
        updateUndo();
        HBox toolbar = new HBox(8, levelBox, new Separator(javafx.geometry.Orientation.VERTICAL), select, add, connect,
                new Separator(javafx.geometry.Orientation.VERTICAL), undo, grow, zoomOut, zoomIn, fit);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        map.setDark(Theme.isDark());
        map.setGraphView(true, List.of());
        map.setPickAnyNode(true);
        map.setPadding(16, 16);
        map.setDecorations(this::decorate);
        StackPane mapBox = new StackPane(map);
        mapBox.getStyleClass().add("editor-map");
        mapBox.setMinSize(300, 300);
        VBox.setVgrow(mapBox, Priority.ALWAYS);
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        VBox left = new VBox(10, toolbar, mapBox, hint);
        left.getStyleClass().add("card");
        left.setPadding(new Insets(14));
        HBox.setHgrow(left, Priority.ALWAYS);

        status.getStyleClass().add("muted");
        status.setWrapText(true);
        ScrollPane panelScroll = new ScrollPane(panel);
        panelScroll.setFitToWidth(true);
        panelScroll.getStyleClass().add("canvas");
        VBox.setVgrow(panelScroll, Priority.ALWAYS);
        VBox right = new VBox(10, panelScroll, new Separator(), status);
        right.getStyleClass().add("card");
        right.setPadding(new Insets(16));
        right.setPrefWidth(340);
        right.setMinWidth(300);
        right.setMaxWidth(380);

        HBox columns = new HBox(16, left, right);
        VBox.setVgrow(columns, Priority.ALWAYS);
        getChildren().addAll(header, columns);

        installMouse();
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                connectFrom = null;
                pendingPlace = null;
                clearSelection();
            } else if (UNDO_KEY.match(e) && !(e.getTarget() instanceof TextInputControl)) {
                e.consume(); // in a text box, Ctrl+Z undoes the typing instead
                undo();
            }
        });
        setTool(Tool.SELECT);
        showPanel();
        reload(null, null);
    }

    private ToggleButton toolButton(String text, String icon, Tool t) {
        ToggleButton b = new ToggleButton(text);
        b.setGraphic(Icons.icon(icon, 14));
        b.getStyleClass().add("tool-button");
        b.setToggleGroup(tools);
        b.setUserData(t);
        b.setFocusTraversable(false);
        b.setMinWidth(Region.USE_PREF_SIZE); // the level list gives way, not the tool names
        return b;
    }

    private static Button iconButton(String icon, String help, Runnable action) {
        Button b = new Button();
        b.setGraphic(Icons.icon(icon, 14));
        b.getStyleClass().add("small-button");
        b.setAccessibleText(help);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> action.run());
        return b;
    }

    private void setTool(Tool t) {
        tool = t;
        connectFrom = null;
        pendingPlace = null;
        hint.setText(switch (t) {
            case SELECT -> "Click a place or walkway to edit it. Drag a place to move it. Drag empty map to look around; "
                    + "scroll to zoom.";
            case ADD -> "Click on the map where the new place goes, then name it on the right.";
            case CONNECT -> "Click the first place, then the second: a walkway joins them (its length is measured on "
                    + "the map). Esc cancels.";
        });
        if (t != Tool.SELECT) {
            clearSelection();
        }
        showPanel();
        map.redrawDecorations();
    }

    // ---------------------------------------------------------------- loading

    /** Loads the map again (after a change) and selects the given place or walkway, if any. */
    private void reload(Integer nodeId, Integer edgeId) {
        Async.run(api::snapshot, d -> {
            boolean first = data == null;
            data = d;
            if (first) {
                map.setData(d);
                fillLevels();
            } else {
                map.updateData(d);
            }
            Node n = nodeId == null ? null : d.graph().node(nodeId).orElse(null);
            if (n != null && !map.shows(n)) {
                showLevelOf(n); // an undo can bring back a place on another level
            }
            selNode = n;
            selEdge = edgeId == null ? null : findEdge(edgeId);
            showPanel();
            map.redrawDecorations();
        }, this::failed);
    }

    private void fillLevels() {
        List<Level> levels = new ArrayList<>();
        levels.add(new Level(null, null, "Campus (outdoor)"));
        List<Building> buildings = new ArrayList<>(data.buildings());
        buildings.sort(Comparator.comparing(Building::name));
        for (Building b : buildings) {
            for (Floor f : data.floorsOf(b.id())) {
                levels.add(new Level(f.id(), b.id(), MapView.shortName(b.name()) + " · " + f.floorName()));
            }
        }
        levelBox.setItems(FXCollections.observableArrayList(levels));
        levelBox.setValue(levels.get(0));
    }

    private Level level() {
        Level l = levelBox.getValue();
        return l == null ? new Level(null, null, "Campus (outdoor)") : l;
    }

    private Edge findEdge(int id) {
        return data.graph().edges().stream().filter(e -> e.id() == id).findFirst().orElse(null);
    }

    private void failed(ApiException e) {
        if (e.status() == 401) {
            onUnauthorized.accept(e);
        } else {
            status.setText(String.join("\n", e.messages().isEmpty() ? List.of(e.getMessage()) : e.messages()));
        }
    }

    // ---------------------------------------------------------------- mouse

    private void installMouse() {
        map.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            requestFocus();
            pressX = e.getX();
            pressY = e.getY();
            dragMoved = false;
            handledPress = false;
            if (data == null || !e.isPrimaryButtonDown()) {
                return;
            }
            Node hit = map.nodeAt(e.getX(), e.getY());
            if (tool == Tool.SELECT && hit != null) {
                dragNode = hit;
                dragAt = new double[] {hit.x(), hit.y()};
                handledPress = true;
                e.consume();
            } else if (tool == Tool.ADD || (tool == Tool.CONNECT && hit != null)) {
                handledPress = true;
                e.consume();
            }
        });
        map.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (dragNode != null) {
                dragMoved |= Math.hypot(e.getX() - pressX, e.getY() - pressY) > 4;
                if (dragMoved) {
                    dragAt = map.toMap(e.getX(), e.getY());
                    map.redrawDecorations();
                }
                e.consume();
            } else if (handledPress) {
                e.consume();
            }
        });
        map.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            boolean still = Math.hypot(e.getX() - pressX, e.getY() - pressY) <= 4;
            if (dragNode != null) {
                Node n = dragNode;
                double[] to = dragAt;
                dragNode = null;
                dragAt = null;
                if (dragMoved) {
                    moveNode(n, to);
                } else {
                    select(n, null);
                }
                e.consume();
            } else if (handledPress) {
                e.consume();
                if (!still) {
                    return;
                }
                if (tool == Tool.ADD) {
                    pendingPlace = map.toMap(e.getX(), e.getY());
                    showPanel();
                    map.redrawDecorations();
                } else if (tool == Tool.CONNECT) {
                    connectClick(map.nodeAt(e.getX(), e.getY()));
                }
            } else if (still && tool == Tool.SELECT && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                Edge hit = map.edgeAt(e.getX(), e.getY());
                select(null, hit);
            }
        });
        map.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (handledPress) {
                e.consume(); // no double-click zoom on a place being edited
            }
        });
        map.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (connectFrom != null) {
                mouseAt = new double[] {e.getX(), e.getY()};
                map.redrawDecorations();
            }
        });
    }

    private void select(Node n, Edge e) {
        selNode = n;
        selEdge = e;
        showPanel();
        map.redrawDecorations();
    }

    private void clearSelection() {
        selNode = null;
        selEdge = null;
        showPanel();
        map.redrawDecorations();
    }

    // ---------------------------------------------------------------- drawing on the map

    private void decorate(GraphicsContext g) {
        if (data == null) {
            return;
        }
        g.setLineCap(StrokeLineCap.ROUND);
        if (selEdge != null) {
            Optional<Node> a = data.graph().node(selEdge.fromNodeId());
            Optional<Node> b = data.graph().node(selEdge.toNodeId());
            if (a.isPresent() && b.isPresent() && map.shows(a.get()) && map.shows(b.get())) {
                double[] p = map.toView(a.get().x(), a.get().y());
                double[] q = map.toView(b.get().x(), b.get().y());
                g.setStroke(PICK.deriveColor(0, 1, 1, 0.55));
                g.setLineWidth(10);
                g.strokeLine(p[0], p[1], q[0], q[1]);
            }
        }
        if (selNode != null && map.shows(selNode)) {
            ring(g, map.toView(selNode.x(), selNode.y()), PICK);
        }
        if (connectFrom != null && map.shows(connectFrom)) {
            double[] p = map.toView(connectFrom.x(), connectFrom.y());
            ring(g, p, NEW);
            if (mouseAt != null) {
                g.setStroke(NEW);
                g.setLineWidth(2.5);
                g.setLineDashes(8, 6);
                g.strokeLine(p[0], p[1], mouseAt[0], mouseAt[1]);
                g.setLineDashes((double[]) null);
            }
        }
        if (dragNode != null && dragAt != null && dragMoved) {
            double[] p = map.toView(dragAt[0], dragAt[1]);
            g.setStroke(PICK);
            g.setLineWidth(2);
            g.setLineDashes(6, 5);
            for (Edge e : data.graph().edgesAt(dragNode.id())) {
                Node other = data.graph().node(e.fromNodeId() == dragNode.id() ? e.toNodeId() : e.fromNodeId())
                        .orElse(null);
                if (other != null && map.shows(other)) {
                    double[] q = map.toView(other.x(), other.y());
                    g.strokeLine(p[0], p[1], q[0], q[1]);
                }
            }
            g.setLineDashes((double[]) null);
            ring(g, p, PICK);
        }
        if (pendingPlace != null) {
            double[] p = map.toView(pendingPlace[0], pendingPlace[1]);
            g.setFill(NEW);
            g.fillOval(p[0] - 7, p[1] - 7, 14, 14);
            g.setStroke(Color.WHITE);
            g.setLineWidth(2);
            g.strokeOval(p[0] - 7, p[1] - 7, 14, 14);
        }
    }

    private static void ring(GraphicsContext g, double[] p, Color c) {
        g.setStroke(c);
        g.setLineWidth(3);
        g.strokeOval(p[0] - 13, p[1] - 13, 26, 26);
        g.setStroke(c.deriveColor(0, 1, 1, 0.3));
        g.setLineWidth(7);
        g.strokeOval(p[0] - 18, p[1] - 18, 36, 36);
    }

    // ---------------------------------------------------------------- the panel on the right

    private void showPanel() {
        panel.getChildren().clear();
        if (data == null) {
            panel.getChildren().add(muted("Loading the map…"));
        } else if (pendingPlace != null) {
            newPlaceForm();
        } else if (selNode != null) {
            placeForm(selNode);
        } else if (selEdge != null) {
            walkwayForm(selEdge);
        } else if (connectFrom != null) {
            panel.getChildren().addAll(cardTitle("Connect"), muted("From " + connectFrom.name()
                    + ". Now click the place the walkway goes to."));
        } else {
            long places = map.levelNodes().stream().filter(n -> !isWaypoint(n.nodeType())).count();
            panel.getChildren().addAll(cardTitle(level().label()),
                    muted(places + " places and " + map.levelNodes().size() + " points in all on this level."),
                    muted("Click a place or a walkway on the map to edit it. Small dots are hallway corners and other "
                            + "waypoints; numbers are place ids and walkway lengths in metres."));
        }
    }

    private void placeForm(Node n) {
        TextField name = new TextField(n.name());
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(PlacesView.TYPES));
        type.setValue(n.nodeType());
        type.setMaxWidth(Double.MAX_VALUE);
        TextArea desc = new TextArea(n.description() == null ? "" : n.description());
        desc.setPrefRowCount(3);
        desc.setWrapText(true);
        TextField hours = new TextField(n.openHours() == null ? "" : n.openHours());
        Button save = primary("Save");
        save.setOnAction(e -> {
            String nm = name.getText().trim();
            if (nm.isEmpty()) {
                status.setText("A place needs a name.");
                return;
            }
            AdminModels.NodeForm f = new AdminModels.NodeForm(n.buildingId(), n.floorId(), nm, type.getValue(),
                    n.x(), n.y(), blankToNull(desc.getText()), blankToNull(hours.getText()));
            AdminModels.NodeForm old = formOf(n);
            run("Saved " + nm + ".", () -> api.updateNode(n.id(), f), n.id(), null,
                    "changes to " + n.name(), n.id(), h -> api.updateNode(h.node(n.id()), old));
        });
        Button delete = danger("Delete place");
        delete.setOnAction(e -> deletePlace(n));
        HBox buttons = new HBox(8, save, delete);

        panel.getChildren().addAll(cardTitle("Place #" + n.id()), muted(where(n) + " · map point " + n.x() + ", "
                + n.y()), field("Name", name), field("Type", type), field("Description", desc),
                field("Office hours", hours), buttons);
        if ("STAIRS".equals(n.nodeType()) || "ELEVATOR".equals(n.nodeType())) {
            Button up = secondary("Link to the floor above");
            up.setOnAction(e -> linkFloor(n, +1));
            Button down = secondary("Link to the floor below");
            down.setOnAction(e -> linkFloor(n, -1));
            up.setDisable(n.floorId() == null);
            down.setDisable(n.floorId() == null);
            panel.getChildren().add(new VBox(6, label("Floors", "field-label"), new HBox(8, up, down),
                    muted("Joins it to the nearest " + n.nodeType().toLowerCase(Locale.ROOT) + " on that floor.")));
        }
        List<Edge> ways = data.graph().edgesAt(n.id());
        VBox list = new VBox(4);
        for (Edge w : ways) {
            Node other = otherEnd(w, n.id());
            Button b = new Button(String.format(Locale.ROOT, "%s  ·  %.1f m  ·  %s", other == null ? "?" : other.name(),
                    w.distanceM(), w.edgeType().name().toLowerCase(Locale.ROOT)));
            b.getStyleClass().add("link-button");
            b.setOnAction(e -> select(null, w));
            list.getChildren().add(b);
        }
        if (ways.isEmpty()) {
            list.getChildren().add(muted("No walkways yet: use Connect, or routes cannot reach it."));
        }
        panel.getChildren().addAll(new Separator(), label("Walkways (" + ways.size() + ")", "field-label"), list);
    }

    private void walkwayForm(Edge w) {
        Node a = data.graph().node(w.fromNodeId()).orElse(null);
        Node b = data.graph().node(w.toNodeId()).orElse(null);
        TextField dist = new TextField(String.format(Locale.ROOT, "%.1f", w.distanceM()));
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("WALK", "STAIRS", "ELEVATOR"));
        type.setValue(w.edgeType().name());
        type.setMaxWidth(Double.MAX_VALUE);
        CheckBox stepFree = new CheckBox("Step-free (wheelchairs can use it)");
        stepFree.setSelected(w.accessible());
        Button measure = secondary("Measure");
        measure.setMinWidth(Region.USE_PREF_SIZE);
        measure.setTooltip(new javafx.scene.control.Tooltip("Length from the map, at this level's scale"));
        measure.setOnAction(e -> {
            if (a != null && b != null) {
                dist.setText(String.format(Locale.ROOT, "%.1f", measured(a.x(), a.y(), b.x(), b.y())));
            }
        });
        measure.setDisable(a == null || b == null || !Objects.equals(a.floorId(), b.floorId()));
        Button save = primary("Save");
        save.setOnAction(e -> {
            Double m = metres(dist.getText());
            if (m == null) {
                status.setText("The length must be a number of metres, 0 or more.");
                return;
            }
            AdminModels.EdgeForm f = new AdminModels.EdgeForm(w.fromNodeId(), w.toNodeId(), m, type.getValue(),
                    stepFree.isSelected());
            AdminModels.EdgeForm old = formOf(w);
            run("Saved the walkway.", () -> api.updateEdge(w.id(), f), null, w.id(),
                    "changes to the walkway between " + name(a) + " and " + name(b), null,
                    h -> api.updateEdge(h.edge(w.id()), h.ends(old)));
        });
        Button delete = danger("Delete walkway");
        delete.setOnAction(e -> {
            if (confirm("Delete the walkway between " + name(a) + " and " + name(b) + "?")) {
                AdminModels.EdgeForm old = formOf(w);
                run("Deleted the walkway.", () -> api.deleteEdge(w.id()), null, null,
                        "delete of the walkway between " + name(a) + " and " + name(b), null,
                        h -> h.edgeRecreated(w.id(), api.createEdge(h.ends(old))));
            }
        });
        Button openA = linkTo(a);
        Button openB = linkTo(b);
        panel.getChildren().addAll(cardTitle("Walkway #" + w.id()), new VBox(2, openA, openB),
                field("Length (metres)", new HBox(8, dist, measure)), field("Type", type), stepFree,
                new HBox(8, save, delete));
    }

    private Button linkTo(Node n) {
        Button b = new Button(n == null ? "?" : n.name() + "  (" + where(n) + ")");
        b.getStyleClass().add("link-button");
        b.setOnAction(e -> {
            if (n != null) {
                if (!map.shows(n)) {
                    showLevelOf(n);
                }
                select(n, null);
            }
        });
        return b;
    }

    private void newPlaceForm() {
        TextField name = new TextField();
        name.setPromptText("For example EN 421 or Registrar's Office");
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(PlacesView.TYPES));
        type.setValue(level().floorId() == null ? "FACILITY" : "ROOM");
        type.setMaxWidth(Double.MAX_VALUE);
        CheckBox join = new CheckBox("Connect it to the nearest point");
        join.setSelected(true);
        Button save = primary("Add place");
        save.setOnAction(e -> {
            String nm = name.getText().trim();
            if (nm.isEmpty()) {
                status.setText("A place needs a name.");
                return;
            }
            int x = (int) Math.round(pendingPlace[0]);
            int y = (int) Math.round(pendingPlace[1]);
            Level lv = level();
            Node near = join.isSelected() ? nearest(x, y, null) : null;
            AdminModels.NodeForm f = new AdminModels.NodeForm(lv.buildingId(), lv.floorId(), nm, type.getValue(), x, y);
            pendingPlace = null;
            double m = near == null ? 0 : measured(x, y, near.x(), near.y());
            Async.run(() -> {
                int id = api.createNode(f);
                int way = near == null ? -1 : api.createEdge(new AdminModels.EdgeForm(id, near.id(), m, "WALK", true));
                return new int[] {id, way};
            }, ids -> {
                int id = ids[0];
                int way = ids[1];
                remember("new place " + nm, null, h -> {
                    if (way >= 0) {
                        api.deleteEdge(h.edge(way));
                    }
                    api.deleteNode(h.node(id));
                });
                status.setText("Added " + nm + (near != null ? ", joined to " + near.name() + "." : "."));
                reload(id, null);
            }, this::failed);
        });
        Button cancel = secondary("Cancel");
        cancel.setOnAction(e -> {
            pendingPlace = null;
            showPanel();
            map.redrawDecorations();
        });
        panel.getChildren().addAll(cardTitle("New place"), muted("On " + level().label() + ", at the blue dot."),
                field("Name", name), field("Type", type), join, new HBox(8, save, cancel));
        name.requestFocus();
    }

    // ---------------------------------------------------------------- changes

    /** Moves a place and gives its walkways on this level new lengths, in proportion to how they changed. */
    private void moveNode(Node n, double[] to) {
        int x = (int) Math.round(to[0]);
        int y = (int) Math.round(to[1]);
        AdminModels.NodeForm f = new AdminModels.NodeForm(n.buildingId(), n.floorId(), n.name(), n.nodeType(), x, y,
                n.description(), n.openHours());
        List<Edge> ways = data.graph().edgesAt(n.id());
        double scale = metresPerUnit();
        AdminModels.NodeForm old = formOf(n);
        Async.run(() -> {
            api.updateNode(n.id(), f);
            List<SavedWay> changed = new ArrayList<>();
            for (Edge w : ways) {
                Node other = otherEnd(w, n.id());
                if (other == null || w.edgeType() != EdgeType.WALK || !Objects.equals(other.floorId(), n.floorId())) {
                    continue; // stairs, elevators and bridges keep their lengths
                }
                double before = Math.hypot(n.x() - other.x(), n.y() - other.y());
                double after = Math.hypot(x - other.x(), y - other.y());
                double m = before > 0.5 ? w.distanceM() * after / before : after * scale;
                m = Math.max(0.5, Math.round(m * 10) / 10.0);
                api.updateEdge(w.id(), new AdminModels.EdgeForm(w.fromNodeId(), w.toNodeId(), m, w.edgeType().name(),
                        w.accessible()));
                changed.add(new SavedWay(w.id(), formOf(w)));
            }
            return changed;
        }, changed -> {
            remember("move of " + n.name(), n.id(), h -> {
                api.updateNode(h.node(n.id()), old);
                for (SavedWay s : changed) {
                    api.updateEdge(h.edge(s.id()), h.ends(s.form()));
                }
            });
            status.setText("Moved " + n.name() + (changed.isEmpty() ? "." : "; " + changed.size()
                    + " walkway lengths updated."));
            reload(n.id(), null);
        }, e -> {
            failed(e);
            map.redrawDecorations();
        });
    }

    private void connectClick(Node hit) {
        if (hit == null) {
            return;
        }
        if (connectFrom == null) {
            connectFrom = hit;
            showPanel();
            map.redrawDecorations();
            return;
        }
        Node a = connectFrom;
        connectFrom = null;
        mouseAt = null;
        if (a.id() == hit.id()) {
            showPanel();
            map.redrawDecorations();
            return;
        }
        boolean exists = data.graph().edgesAt(a.id()).stream()
                .anyMatch(w -> w.fromNodeId() == hit.id() || w.toNodeId() == hit.id());
        if (exists) {
            status.setText(a.name() + " and " + hit.name() + " are already connected.");
            showPanel();
            map.redrawDecorations();
            return;
        }
        double m = measured(a.x(), a.y(), hit.x(), hit.y());
        Async.run(() -> api.createEdge(new AdminModels.EdgeForm(a.id(), hit.id(), m, "WALK", true)), id -> {
            remember("walkway between " + a.name() + " and " + hit.name(), null, h -> api.deleteEdge(h.edge(id)));
            status.setText(String.format(Locale.ROOT, "Connected %s and %s (%.1f m). Click two more places, or Esc.",
                    a.name(), hit.name(), m));
            reload(null, null);
        }, this::failed);
    }

    /** Joins stairs or an elevator to the nearest one of the same kind on the next floor up or down. */
    private void linkFloor(Node n, int direction) {
        Floor here = data.floor(n.floorId()).orElse(null);
        if (here == null) {
            return;
        }
        Floor next = data.floorsOf(here.buildingId()).stream()
                .filter(f -> direction > 0 ? f.floorNumber() > here.floorNumber() : f.floorNumber() < here.floorNumber())
                .min(Comparator.comparingInt(f -> Math.abs(f.floorNumber() - here.floorNumber()))).orElse(null);
        if (next == null) {
            status.setText("There is no floor " + (direction > 0 ? "above " : "below ") + here.floorName() + ".");
            return;
        }
        Node target = data.graph().nodes().stream()
                .filter(o -> Objects.equals(o.floorId(), next.id()) && o.nodeType().equals(n.nodeType()))
                .min(Comparator.comparingDouble(o -> Math.hypot(o.x() - n.x(), o.y() - n.y()))).orElse(null);
        if (target == null) {
            status.setText("The " + next.floorName() + " has no " + n.nodeType().toLowerCase(Locale.ROOT)
                    + " yet: add one there first.");
            return;
        }
        boolean stairs = "STAIRS".equals(n.nodeType());
        AdminModels.EdgeForm f = new AdminModels.EdgeForm(n.id(), target.id(), stairs ? FLIGHT_M : LIFT_M,
                stairs ? "STAIRS" : "ELEVATOR", !stairs);
        Async.run(() -> api.createEdge(f), id -> {
            remember("link from " + n.name() + " to " + target.name(), n.id(), h -> api.deleteEdge(h.edge(id)));
            status.setText("Linked to " + target.name() + " on the " + next.floorName() + ".");
            reload(n.id(), null);
        }, this::failed);
    }

    private void deletePlace(Node n) {
        List<Edge> ways = data.graph().edgesAt(n.id());
        if (!confirm("Delete " + n.name() + (ways.isEmpty() ? "" : " and its " + ways.size() + " walkway"
                + (ways.size() == 1 ? "" : "s")) + "? Routes will no longer go through it.")) {
            return;
        }
        AdminModels.NodeForm old = formOf(n);
        List<SavedWay> gone = new ArrayList<>(); // filled off the screen thread, read once it is done
        Async.run(() -> {
            for (Edge w : ways) {
                api.deleteEdge(w.id());
                gone.add(new SavedWay(w.id(), formOf(w)));
            }
            api.deleteNode(n.id());
            return n.name();
        }, nm -> {
            remember("delete of " + nm, n.id(), h -> {
                h.nodeRecreated(n.id(), api.createNode(old));
                putBack(h, gone);
            });
            status.setText("Deleted " + nm + ".");
            reload(null, null);
        }, e -> {
            failed(e);
            if (!gone.isEmpty()) { // the walkways went but the place did not: they can still come back
                remember("delete of the walkways at " + n.name(), n.id(), h -> putBack(h, gone));
            }
            if (e.status() != 401) {
                status.setText(status.getText() + "\n(It may still be in the usage log or a route report.)");
                reload(n.id(), null);
            }
        });
    }

    private interface Change {
        void run() throws ApiException;
    }

    /** Makes a change on the server; once it worked, remembers how to undo it and loads the map again. */
    private void run(String done, Change change, Integer selectNode, Integer selectEdge, String undoLabel,
                     Integer undoSelect, EditHistory.Step undoStep) {
        Async.run(() -> {
            change.run();
            return done;
        }, msg -> {
            remember(undoLabel, undoSelect, undoStep);
            status.setText(msg);
            reload(selectNode, selectEdge);
        }, this::failed);
    }

    private void remember(String label, Integer selectNode, EditHistory.Step step) {
        history.push(label, selectNode, step);
        updateUndo();
    }

    /** Creates deleted walkways again, under new ids. Runs off the screen thread. */
    private void putBack(EditHistory h, List<SavedWay> ways) throws ApiException {
        for (SavedWay s : ways) {
            h.edgeRecreated(s.id(), api.createEdge(h.ends(s.form())));
        }
    }

    private void updateUndo() {
        EditHistory.Entry next = history.peek();
        undo.setDisable(next == null || undoing);
        undo.setTooltip(new Tooltip(next == null ? "Nothing to undo" : "Undo the " + next.label() + " (Ctrl+Z)"));
    }

    /** Takes back the last change: the server is asked to put back what was there before. */
    private void undo() {
        if (undoing || data == null || dragNode != null) {
            return;
        }
        EditHistory.Entry e = history.pop();
        if (e == null) {
            status.setText("Nothing to undo.");
            return;
        }
        undoing = true;
        updateUndo();
        connectFrom = null;
        pendingPlace = null;
        Integer before = e.selectNode() == null ? null : history.node(e.selectNode());
        Async.run(() -> {
            e.step().undo(history);
            return before == null ? null : history.node(e.selectNode());
        }, now -> {
            undoing = false;
            updateUndo();
            String back = now != null && !now.equals(before) ? " It came back as place #" + now
                    + ": reprint its QR sign if it had one." : "";
            status.setText("Undid the " + e.label() + "." + back);
            reload(now, null);
        }, ex -> {
            undoing = false;
            updateUndo();
            failed(ex);
            if (ex.status() != 401) {
                status.setText("Could not undo the " + e.label() + ".\n" + status.getText());
                reload(null, null);
            }
        });
    }

    /** For the screen tour: shows a level (by its label) and selects a place or one of its walkways on it. */
    void pickForTour(String levelLabel, String placeName, boolean walkway) {
        levelBox.getItems().stream().filter(l -> l.label().equals(levelLabel)).findFirst().ifPresent(levelBox::setValue);
        Node n = data.graph().nodes().stream().filter(x -> x.name().equals(placeName)).findFirst().orElse(null);
        if (n != null) {
            List<Edge> ways = data.graph().edgesAt(n.id());
            select(walkway ? null : n, walkway && !ways.isEmpty() ? ways.get(0) : null);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Metres between two map points on this level, from how long this level's walkways are on the map. */
    private double measured(double ax, double ay, double bx, double by) {
        return Math.max(0.5, Math.round(Math.hypot(ax - bx, ay - by) * metresPerUnit() * 10) / 10.0);
    }

    /** The middle value of (metres / map length) over this level's walkways: how big the map is drawn here. */
    private double metresPerUnit() {
        List<Double> ratios = new ArrayList<>();
        for (Edge e : data.graph().edges()) {
            Node a = data.graph().node(e.fromNodeId()).orElse(null);
            Node b = data.graph().node(e.toNodeId()).orElse(null);
            if (a == null || b == null || e.edgeType() != EdgeType.WALK || !map.shows(a) || !map.shows(b)
                    || !Objects.equals(a.floorId(), b.floorId())) {
                continue;
            }
            double len = Math.hypot(a.x() - b.x(), a.y() - b.y());
            if (len > 3) {
                ratios.add(e.distanceM() / len);
            }
        }
        if (ratios.isEmpty()) {
            return DEFAULT_M_PER_UNIT;
        }
        ratios.sort(null);
        return ratios.get(ratios.size() / 2);
    }

    private Node nearest(int x, int y, Node except) {
        return map.levelNodes().stream().filter(o -> except == null || o.id() != except.id())
                .min(Comparator.comparingDouble(o -> Math.hypot(o.x() - x, o.y() - y))).orElse(null);
    }

    private Node otherEnd(Edge w, int id) {
        return data.graph().node(w.fromNodeId() == id ? w.toNodeId() : w.fromNodeId()).orElse(null);
    }

    private void showLevelOf(Node n) {
        for (Level l : levelBox.getItems()) {
            if (Objects.equals(l.floorId(), n.floorId())) {
                levelBox.setValue(l);
                return;
            }
        }
    }

    private String where(Node n) {
        if (n.floorId() == null) {
            return "Campus";
        }
        Floor f = data.floor(n.floorId()).orElse(null);
        String b = f == null ? "" : data.building(f.buildingId()).map(x -> MapView.shortName(x.name()) + " · ")
                .orElse("");
        return b + (f == null ? "floor " + n.floorId() : f.floorName());
    }

    private static AdminModels.NodeForm formOf(Node n) {
        return new AdminModels.NodeForm(n.buildingId(), n.floorId(), n.name(), n.nodeType(), n.x(), n.y(),
                n.description(), n.openHours());
    }

    private static AdminModels.EdgeForm formOf(Edge w) {
        return new AdminModels.EdgeForm(w.fromNodeId(), w.toNodeId(), w.distanceM(), w.edgeType().name(),
                w.accessible());
    }

    private static boolean isWaypoint(String type) {
        return "JUNCTION".equals(type) || "STAIRS".equals(type) || "ELEVATOR".equals(type);
    }

    private static String name(Node n) {
        return n == null ? "?" : n.name();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static Double metres(String text) {
        try {
            double m = Double.parseDouble(text.trim().replace(',', '.'));
            return m >= 0 && m < 10_000 ? m : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean confirm(String question) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(null);
        a.setTitle("Map editor");
        if (getScene() != null) {
            a.initOwner(getScene().getWindow());
        }
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private static VBox field(String label, javafx.scene.Node input) {
        if (input instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
        }
        HBox.setHgrow(input instanceof HBox h ? h.getChildren().get(0) : input, Priority.ALWAYS);
        return new VBox(4, label(label.toUpperCase(Locale.ROOT), "field-label"), input);
    }

    private static Label cardTitle(String text) {
        Label l = label(text, "card-title");
        l.setWrapText(true);
        return l;
    }

    private static Label muted(String text) {
        Label l = label(text, "muted");
        l.setWrapText(true);
        return l;
    }

    private static Label label(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        return l;
    }

    private static Button primary(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("primary-button");
        return b;
    }

    private static Button secondary(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("secondary-button");
        return b;
    }

    private static Button danger(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("small-button", "danger");
        return b;
    }
}

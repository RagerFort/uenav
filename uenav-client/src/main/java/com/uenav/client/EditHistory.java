package com.uenav.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/**
 * The admin app's undo list, shared by every page of one sign-in: the Map editor and the Places, Walkways, Buildings
 * and Floors pages. Every change is saved on the server at once, so undoing a change means asking the server to put
 * things back: each entry holds those calls. A place, walkway, building or floor that comes back after a delete gets
 * a new id from the server; older entries still name the old id, so the history keeps a note of old id to new id and
 * every entry looks ids up through {@link #node(int)}, {@link #edge(int)}, {@link #building(int)} and
 * {@link #floor(int)}.
 * Used on the screen thread, except {@link Step#undo} and the id notes it writes, which run off it while
 * {@link #isBusy()} keeps other undos out.
 */
final class EditHistory {

    /** Puts back what one change did. Runs off the screen thread. */
    interface Step {
        void undo(EditHistory h) throws ApiException;
    }

    /**
     * One change that can be undone. {@code label} finishes "Undo …" (for example "move of Registrar's Office");
     * {@code selectNode} is the place to show afterwards, or null.
     */
    record Entry(String label, Integer selectNode, Step step) { }

    /** A page that shows data an undo can change: the admin window reloads it after an undo. */
    interface Page {
        void reloadAfterUndo();
    }

    /** Enough for a working session; the oldest changes drop off. */
    static final int LIMIT = 50;

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Map<Integer, Integer> nodeIds = new HashMap<>();
    private final Map<Integer, Integer> edgeIds = new HashMap<>();
    private final Map<Integer, Integer> buildingIds = new HashMap<>();
    private final Map<Integer, Integer> floorIds = new HashMap<>();
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper tip = new ReadOnlyStringWrapper("Nothing to undo");
    private boolean busy;

    void push(String label, Integer selectNode, Step step) {
        push(new Entry(label, selectNode, step));
    }

    void push(Entry e) {
        entries.push(e);
        while (entries.size() > LIMIT) {
            entries.removeLast();
        }
        changed();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The change Ctrl+Z would undo now, or null. */
    Entry peek() {
        return entries.peek();
    }

    Entry pop() {
        Entry e = entries.poll();
        changed();
        return e;
    }

    /** True while an undo is being sent to the server: no second undo starts until it is done. */
    boolean isBusy() {
        return busy;
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        changed();
    }

    /** True when there is a change to undo and no undo is running (for an Undo button). */
    ReadOnlyBooleanProperty canUndoProperty() {
        return canUndo.getReadOnlyProperty();
    }

    /** "Undo the … (Ctrl+Z)", or "Nothing to undo" (for an Undo button's tooltip). */
    ReadOnlyStringProperty tipProperty() {
        return tip.getReadOnlyProperty();
    }

    private void changed() {
        Entry next = entries.peek();
        canUndo.set(next != null && !busy);
        tip.set(next == null ? "Nothing to undo" : "Undo the " + next.label() + " (Ctrl+Z)");
    }

    /** A place's id now: the same id, or the new one if the place was deleted and brought back. */
    int node(int id) {
        return now(nodeIds, id);
    }

    int edge(int id) {
        return now(edgeIds, id);
    }

    int building(int id) {
        return now(buildingIds, id);
    }

    int floor(int id) {
        return now(floorIds, id);
    }

    private static int now(Map<Integer, Integer> ids, int id) {
        Integer next = ids.get(id);
        return next == null ? id : now(ids, next);
    }

    /** A place came back under a new id. */
    void nodeRecreated(int oldId, int newId) {
        note(nodeIds, oldId, newId);
    }

    void edgeRecreated(int oldId, int newId) {
        note(edgeIds, oldId, newId);
    }

    void buildingRecreated(int oldId, int newId) {
        note(buildingIds, oldId, newId);
    }

    void floorRecreated(int oldId, int newId) {
        note(floorIds, oldId, newId);
    }

    private static void note(Map<Integer, Integer> ids, int oldId, int newId) {
        if (oldId != newId) {
            ids.put(oldId, newId);
        }
    }

    /** A walkway form with both ends looked up, for a walkway between places that may have come back. */
    AdminModels.EdgeForm ends(AdminModels.EdgeForm f) {
        return new AdminModels.EdgeForm(node(f.fromNodeId()), node(f.toNodeId()), f.distanceM(), f.edgeType(),
                f.accessible());
    }

    /** A place form with its building and floor looked up, for a building or floor that may have come back. */
    AdminModels.NodeForm place(AdminModels.NodeForm f) {
        return new AdminModels.NodeForm(f.buildingId() == null ? null : building(f.buildingId()),
                f.floorId() == null ? null : floor(f.floorId()), f.name(), f.nodeType(), f.x(), f.y(),
                f.description(), f.openHours());
    }

    /** A floor form with its building looked up. */
    AdminModels.FloorForm floor(AdminModels.FloorForm f) {
        return new AdminModels.FloorForm(building(f.buildingId()), f.floorNumber(), f.floorName());
    }
}

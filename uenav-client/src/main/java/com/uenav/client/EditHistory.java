package com.uenav.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * The map editor's undo list. The editor saves every change on the server at once, so undoing a change means asking
 * the server to put things back: each entry holds those calls. A place or walkway that comes back after a delete gets
 * a new id from the server; older entries still name the old id, so the history keeps a note of old id to new id and
 * every entry looks ids up through {@link #node(int)} and {@link #edge(int)}.
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

    /** Enough for a working session; the oldest changes drop off. */
    static final int LIMIT = 50;

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Map<Integer, Integer> nodeIds = new HashMap<>();
    private final Map<Integer, Integer> edgeIds = new HashMap<>();

    void push(String label, Integer selectNode, Step step) {
        entries.push(new Entry(label, selectNode, step));
        while (entries.size() > LIMIT) {
            entries.removeLast();
        }
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The change Ctrl+Z would undo now, or null. */
    Entry peek() {
        return entries.peek();
    }

    Entry pop() {
        return entries.poll();
    }

    /** A place's id now: the same id, or the new one if the place was deleted and brought back. */
    int node(int id) {
        Integer now = nodeIds.get(id);
        return now == null ? id : node(now);
    }

    int edge(int id) {
        Integer now = edgeIds.get(id);
        return now == null ? id : edge(now);
    }

    /** A place came back under a new id. */
    void nodeRecreated(int oldId, int newId) {
        if (oldId != newId) {
            nodeIds.put(oldId, newId);
        }
    }

    void edgeRecreated(int oldId, int newId) {
        if (oldId != newId) {
            edgeIds.put(oldId, newId);
        }
    }

    /** A walkway form with both ends looked up, for a walkway between places that may have come back. */
    AdminModels.EdgeForm ends(AdminModels.EdgeForm f) {
        return new AdminModels.EdgeForm(node(f.fromNodeId()), node(f.toNodeId()), f.distanceM(), f.edgeType(),
                f.accessible());
    }
}

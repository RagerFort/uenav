package com.uenav.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The big buttons on the home screen (like a mall kiosk): Offices, Classrooms, Restrooms, Food, Exits and so on.
 * A place belongs to a category by its node type, or by a word in its name for places added before the new types
 * existed (for example a FACILITY named "Canteen").
 */
public enum PlaceCategory {

    OFFICES("Offices", "office", Set.of("OFFICE"), List.of("office", "registrar", "cashier", "guidance")),
    CLASSROOMS("Classrooms", "room", Set.of("ROOM", "CLASSROOM", "LAB"), List.of("classroom", "laboratory")),
    RESTROOMS("Restrooms", "restroom", Set.of("RESTROOM"),
            List.of("restroom", "comfort room", "toilet", "washroom", "bathroom", " cr")),
    FOOD("Food", "food", Set.of("CANTEEN", "FOOD"), List.of("canteen", "cafeteria", "food", "kiosk", "snack")),
    EXITS("Exits", "gate", Set.of("GATE", "EXIT"), List.of("gate", "exit")),
    CLINIC("Clinic", "clinic", Set.of("CLINIC"), List.of("clinic", "infirmary", "medical", "nurse")),
    BUILDINGS("Buildings", "building", Set.of("BUILDING_ENTRANCE", "BUILDING"), List.of()),
    FACILITIES("Facilities", "facility", Set.of("FACILITY", "LIBRARY", "GYM", "CLINIC", "CANTEEN"),
            List.of("library", "gym", "court", "field"));

    private final String label;
    private final String icon;
    private final Set<String> types;
    private final List<String> nameWords;

    PlaceCategory(String label, String icon, Set<String> types, List<String> nameWords) {
        this.label = label;
        this.icon = icon;
        this.types = types;
        this.nameWords = nameWords;
    }

    /** Button text, such as "Restrooms". */
    public String label() {
        return label;
    }

    /** Icon name used by the apps (their Icons classes). */
    public String icon() {
        return icon;
    }

    /** True if the place belongs here, by type or by a word in its name. */
    public boolean matches(Node n) {
        String type = n.nodeType() == null ? "" : n.nodeType().toUpperCase(Locale.ROOT);
        if (types.contains(type)) {
            return true;
        }
        if (!SnapshotReader.isSearchable(type)) {
            return false;
        }
        String name = " " + n.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ") + " ";
        for (String w : nameWords) {
            String word = w.startsWith(" ") ? w + " " : w;
            if (name.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Ids of the places in this category, for PathFinder.findNearest. */
    public List<Integer> ids(CampusData data) {
        List<Integer> ids = new ArrayList<>();
        for (Node n : data.places()) {
            if (matches(n)) {
                ids.add(n.id());
            }
        }
        return ids;
    }

    /** The places in this category, A to Z. */
    public List<Node> places(CampusData data) {
        List<Node> list = new ArrayList<>();
        for (Node n : data.places()) {
            if (matches(n)) {
                list.add(n);
            }
        }
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return list;
    }
}
